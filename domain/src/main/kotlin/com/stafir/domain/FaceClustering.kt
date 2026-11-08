package com.stafir.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Operazioni sulle "impronte" dei volti (vettori di numeri, uno per volto). */
object FaceMath {
    fun normalize(v: FloatArray): FloatArray {
        var s = 0.0
        for (x in v) s += x * x
        val n = sqrt(s).toFloat().takeIf { it > 1e-6f } ?: return v.copyOf()
        return FloatArray(v.size) { v[it] / n }
    }

    /** Somiglianza tra due impronte normalizzate: 1 = identiche, 0 = per nulla simili. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun toBytes(v: FloatArray): ByteArray {
        val bb = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        v.forEach { bb.putFloat(it) }
        return bb.array()
    }

    fun fromBytes(b: ByteArray): FloatArray {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(b.size / 4) { bb.getFloat() }
    }
}

/**
 * Un gruppo di volti della stessa persona. [sum] è la somma delle impronte dei suoi volti:
 * il centro del gruppo è sum normalizzata, e si aggiorna aggiungendo volti senza ricalcolare tutto.
 */
class FaceGroup(
    val id: Long,
    var sum: FloatArray,
    var count: Int,
    var named: Boolean,
) {
    val centroid: FloatArray get() = FaceMath.normalize(sum)

    fun add(v: FloatArray) {
        if (sum.isEmpty()) sum = FloatArray(v.size)
        for (i in v.indices) sum[i] += v[i]
        count++
    }
}

data class FaceMatch(val groupId: Long, val similarity: Float)

/**
 * Raggruppa i volti della stessa persona. Prudente: un volto entra in un gruppo solo se è
 * davvero simile; meglio due gruppi della stessa persona (si uniscono con un tocco) che due persone mescolate.
 */
class FaceClusterer(
    /** Somiglianza minima per aggiungere un volto a un gruppo senza nome. */
    val threshold: Float = 0.48f,
    /** Per le persone con un nome (centro calcolato su volti confermati) si può essere un po' meno severi. */
    val namedThreshold: Float = 0.45f,
    /** Somiglianza minima tra i centri di due gruppi senza nome per unirli automaticamente. */
    val mergeThreshold: Float = 0.6f,
) {
    /** Il gruppo più adatto per un volto, o null se nessuno è abbastanza simile (= nuova persona). */
    fun bestMatch(face: FloatArray, groups: Collection<FaceGroup>, excluded: Set<Long> = emptySet()): FaceMatch? {
        var best: FaceMatch? = null
        for (g in groups) {
            if (g.id in excluded || g.count == 0) continue
            val s = FaceMath.cosine(face, g.centroid)
            val t = if (g.named) namedThreshold else threshold
            if (s >= t && (best == null || s > best.similarity)) best = FaceMatch(g.id, s)
        }
        return best
    }

    /**
     * Coppie di gruppi senza nome da unire (la seconda confluisce nella prima, la più grande).
     * Si considerano solo i [limit] gruppi più grandi per restare veloci anche con archivi enormi.
     */
    fun mergeCandidates(groups: List<FaceGroup>, limit: Int = 1500): List<Pair<Long, Long>> {
        val top = groups.filter { it.count > 0 }.sortedByDescending { it.count }.take(limit)
        val cents = top.map { it.centroid }
        val absorbed = HashSet<Long>()
        val out = mutableListOf<Pair<Long, Long>>()
        for (a in top.indices) {
            if (top[a].id in absorbed) continue
            for (b in a + 1 until top.size) {
                if (top[b].id in absorbed) continue
                if (top[a].named && top[b].named) continue // due persone con nome si uniscono solo a mano
                if (FaceMath.cosine(cents[a], cents[b]) >= mergeThreshold) {
                    // il gruppo con nome (o il più grande) assorbe l'altro
                    val (keep, drop) = if (top[b].named && !top[a].named) top[b] to top[a] else top[a] to top[b]
                    out += keep.id to drop.id
                    absorbed += drop.id
                }
            }
        }
        return out
    }
}
