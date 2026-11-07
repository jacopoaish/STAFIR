package com.stafir.app.indexing

import android.util.Log
import androidx.room.withTransaction
import com.stafir.ai.DetectedFace
import com.stafir.core.db.FaceEntity
import com.stafir.core.db.PersonEntity
import com.stafir.core.db.StafirDatabase
import com.stafir.domain.FaceClusterer
import com.stafir.domain.FaceGroup
import com.stafir.domain.FaceMath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Volto trovato in un file: [frameMs] = -1 per le foto, istante del fotogramma per i video. */
class FoundFace(val frameMs: Long, val face: DetectedFace)

/**
 * Archivio delle persone: assegna ogni volto nuovo alla persona giusta (o ne crea una nuova)
 * e applica le correzioni dell'utente. Tutte le modifiche passano da qui, una alla volta.
 */
class FaceIndex(private val db: StafirDatabase) {
    private companion object { const val TAG = "FaceIndex" }
    private val mutex = Mutex()
    private val clusterer = FaceClusterer()
    private var groups: HashMap<Long, FaceGroup>? = null
    private val coverQuality = HashMap<Long, Float>()
    private val fd get() = db.faceDao()

    private suspend fun load(): HashMap<Long, FaceGroup> {
        groups?.let { return it }
        val map = HashMap<Long, FaceGroup>()
        fd.allPersons().forEach { p -> map[p.id] = FaceGroup(p.id, FaceMath.fromBytes(p.centroid), p.faceCount, p.name != null) }
        groups = map
        return map
    }

    private fun sub(g: FaceGroup, v: FloatArray) {
        if (g.sum.size == v.size) for (i in v.indices) g.sum[i] -= v[i]
        g.count = (g.count - 1).coerceAtLeast(0)
    }

    private suspend fun save(pid: Long) {
        val g = groups?.get(pid) ?: return
        if (g.count <= 0) {
            fd.deletePerson(pid); groups?.remove(pid); coverQuality.remove(pid); return
        }
        val p = fd.person(pid) ?: return
        var cover = p.coverFaceId
        if (cover == null || fd.face(cover)?.personId != pid) {
            val best = fd.facesOfPerson(pid).maxByOrNull { it.quality }
            cover = best?.id
            coverQuality[pid] = best?.quality ?: 0f
        }
        fd.updatePersonStats(pid, FaceMath.toBytes(g.sum), g.count, cover)
    }

    /** Volti di un file appena analizzato. Se il file era già stato analizzato, i vecchi volti vengono sostituiti. */
    suspend fun addFaces(mediaId: Long, found: List<FoundFace>) = mutex.withLock {
        val g = load()
        val touched = HashSet<Long>()
        db.withTransaction {
            fd.facesOf(mediaId).forEach { old ->
                old.personId?.let { pid -> g[pid]?.let { sub(it, FaceMath.fromBytes(old.embedding)) }; touched += pid }
            }
            fd.deleteFacesOf(listOf(mediaId))
            // nello stesso video la stessa persona compare in più fotogrammi: si tiene il volto migliore
            val kept = mutableListOf<FoundFace>()
            for (f in found.sortedByDescending { it.face.quality }) {
                if (kept.none { FaceMath.cosine(it.face.embedding, f.face.embedding) > 0.7f }) kept += f
            }
            for (f in kept) {
                val v = f.face.embedding
                val match = clusterer.bestMatch(v, g.values)
                val pid = match?.groupId ?: fd.insertPerson(
                    PersonEntity(name = null, centroid = FaceMath.toBytes(FloatArray(v.size)), faceCount = 0, coverFaceId = null, hidden = false, updatedAt = System.currentTimeMillis()),
                ).also { g[it] = FaceGroup(it, FloatArray(v.size), 0, false) }
                g.getValue(pid).add(v)
                val faceId = fd.insertFace(
                    FaceEntity(
                        mediaId = mediaId, frameMs = f.frameMs, boxLeft = f.face.left, boxTop = f.face.top,
                        boxRight = f.face.right, boxBottom = f.face.bottom, embedding = FaceMath.toBytes(v),
                        quality = f.face.quality, personId = pid, confirmed = false, rejectedPersonId = null,
                    ),
                )
                // copertina: il volto più grande e frontale
                val currentCover = coverQuality.getOrPut(pid) { fd.person(pid)?.coverFaceId?.let { fd.face(it)?.quality } ?: -1f }
                if (f.face.quality > currentCover) {
                    coverQuality[pid] = f.face.quality
                    fd.updatePersonStats(pid, FaceMath.toBytes(g.getValue(pid).sum), g.getValue(pid).count, faceId)
                }
                touched += pid
            }
            touched.forEach { save(it) }
            if (found.isNotEmpty()) Log.i(TAG, "file $mediaId: ${kept.size} volti → persone ${touched.joinToString()}")
        }
    }

    /** Unisce i gruppi della stessa persona rimasti separati (eseguito a fine analisi). */
    suspend fun mergePass() = mutex.withLock {
        val g = load()
        val pairs = clusterer.mergeCandidates(g.values.toList())
        if (pairs.isEmpty()) return@withLock
        Log.i(TAG, "unisco ${pairs.size} gruppi della stessa persona")
        db.withTransaction { pairs.forEach { (keep, drop) -> mergeLocked(keep, drop) } }
    }

    private suspend fun mergeLocked(keep: Long, drop: Long) {
        val g = load()
        val a = g[keep] ?: return
        val b = g[drop] ?: return
        val dropPerson = fd.person(drop)
        val keepPerson = fd.person(keep)
        if (keepPerson?.name == null && dropPerson?.name != null) fd.rename(keep, dropPerson.name)
        if (a.sum.isEmpty()) a.sum = FloatArray(b.sum.size)
        for (i in b.sum.indices) a.sum[i] += b.sum[i]
        a.count += b.count
        a.named = a.named || b.named
        fd.mergePersons(keep, drop)
        g.remove(drop)
        if ((coverQuality[drop] ?: 0f) > (coverQuality[keep] ?: 0f)) {
            coverQuality[keep] = coverQuality[drop]!!
            fd.updatePersonStats(keep, FaceMath.toBytes(a.sum), a.count, dropPerson?.coverFaceId)
        }
        coverQuality.remove(drop)
        save(keep)
    }

    /** Unisce due persone scelte dall'utente. */
    suspend fun merge(keep: Long, drop: Long) = mutex.withLock {
        if (keep == drop) return@withLock
        db.withTransaction { mergeLocked(keep, drop) }
    }

    /**
     * Dà un nome a una persona (vale per tutte le sue foto e i suoi video). Se esiste già
     * una persona con lo stesso nome, le due vengono unite. Restituisce l'id della persona finale.
     */
    suspend fun rename(personId: Long, rawName: String): Long = mutex.withLock {
        val name = rawName.trim().replace(Regex("\\s+"), " ").ifEmpty { null }
        val g = load()
        var target = personId
        db.withTransaction {
            if (name != null) {
                val same = fd.allPersons().firstOrNull { it.id != personId && it.name?.equals(name, ignoreCase = true) == true }
                if (same != null) { mergeLocked(same.id, personId); target = same.id }
            }
            fd.rename(target, name)
            g[target]?.named = name != null
            if (name != null) fd.confirmAll(target)
        }
        target
    }

    /** "Questo volto non è lui/lei": il volto diventa una persona a sé e non verrà riassegnato. */
    suspend fun removeFace(faceId: Long): Long? = mutex.withLock {
        val g = load()
        var created: Long? = null
        db.withTransaction {
            val f = fd.face(faceId) ?: return@withTransaction
            val old = f.personId ?: return@withTransaction
            val v = FaceMath.fromBytes(f.embedding)
            g[old]?.let { sub(it, v) }
            val np = fd.insertPerson(PersonEntity(name = null, centroid = FaceMath.toBytes(v), faceCount = 1, coverFaceId = faceId, hidden = false, updatedAt = System.currentTimeMillis()))
            g[np] = FaceGroup(np, v.copyOf(), 1, false)
            coverQuality[np] = f.quality
            fd.setFacePerson(faceId, np, confirmed = false, rejected = old)
            save(old)
            created = np
        }
        created
    }

    /**
     * Dà un nome a un singolo volto (dal visualizzatore). Se la sua persona non ha ancora un nome,
     * il nome vale per tutto il gruppo; se ne ha già un altro, il volto viene spostato sulla persona giusta.
     */
    suspend fun nameFace(faceId: Long, rawName: String): Long? {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return null
        val f = fd.face(faceId) ?: return null
        val pid = f.personId ?: return null
        val current = fd.person(pid)?.name
        if (current == null) return rename(pid, name)
        if (current.equals(name, ignoreCase = true)) { assignFace(faceId, pid); return pid }
        val existing = fd.allPersons().firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
        if (existing != null) { assignFace(faceId, existing.id); return existing.id }
        val np = removeFace(faceId) ?: return null
        return rename(np, name)
    }

    /** Toglie da una persona tutti i suoi volti presenti in un file (dalla griglia della persona). */
    suspend fun removePersonFromMedia(personId: Long, mediaId: Long) {
        fd.facesOf(mediaId).filter { it.personId == personId }.forEach { removeFace(it.id) }
    }

    /** Assegna (e conferma) un volto a una persona esistente. */
    suspend fun assignFace(faceId: Long, personId: Long) = mutex.withLock {
        val g = load()
        db.withTransaction {
            val f = fd.face(faceId) ?: return@withTransaction
            val v = FaceMath.fromBytes(f.embedding)
            val old = f.personId
            if (old == personId) { fd.setFacePerson(faceId, personId, true, null); return@withTransaction }
            old?.let { o -> g[o]?.let { sub(it, v) } }
            g[personId]?.add(v)
            fd.setFacePerson(faceId, personId, confirmed = true, rejected = null)
            save(personId)
            old?.let { save(it) }
        }
    }

    suspend fun hide(personId: Long, hidden: Boolean = true) = mutex.withLock { fd.setHidden(personId, hidden) }
}
