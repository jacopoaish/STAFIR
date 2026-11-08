package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FaceClusteringTest {
    private val rnd = Random(42)
    private fun randomUnit(dim: Int = 128) = FaceMath.normalize(FloatArray(dim) { rnd.nextFloat() * 2 - 1 })
    /** Variante di un'impronta: stessa persona, foto diversa. */
    private fun jitter(v: FloatArray, amount: Float) = FaceMath.normalize(FloatArray(v.size) { v[it] + (rnd.nextFloat() * 2 - 1) * amount })

    @Test fun bytesRoundTrip() {
        val v = randomUnit()
        val back = FaceMath.fromBytes(FaceMath.toBytes(v))
        for (i in v.indices) assertEquals(v[i], back[i], 0f)
    }

    @Test fun groupsSamePersonAndSeparatesOthers() {
        val c = FaceClusterer()
        val people = List(5) { randomUnit() }
        val groups = mutableListOf<FaceGroup>()
        var next = 1L
        val assigned = mutableMapOf<Int, MutableSet<Long>>()
        repeat(10) { round ->
            people.forEachIndexed { p, base ->
                val f = jitter(base, 0.08f)
                val m = c.bestMatch(f, groups)
                val gid = if (m != null) { groups.first { it.id == m.groupId }.add(f); m.groupId }
                else FaceGroup(next++, FloatArray(0), 0, false).also { it.add(f); groups += it }.id
                assigned.getOrPut(p) { mutableSetOf() } += gid
            }
        }
        // ogni persona finisce in un solo gruppo, e gruppi diversi per persone diverse
        assigned.values.forEach { assertEquals(1, it.size) }
        assertEquals(5, assigned.values.flatten().toSet().size)
    }

    @Test fun unrelatedFaceStartsNewGroup() {
        val c = FaceClusterer()
        val g = FaceGroup(1, FloatArray(0), 0, false).also { it.add(randomUnit()) }
        assertNull(c.bestMatch(randomUnit(), listOf(g)))
    }

    @Test fun excludedGroupsAreSkipped() {
        val c = FaceClusterer()
        val base = randomUnit()
        val g = FaceGroup(7, FloatArray(0), 0, false).also { it.add(base) }
        assertNotNull(c.bestMatch(jitter(base, .05f), listOf(g)))
        assertNull(c.bestMatch(jitter(base, .05f), listOf(g), excluded = setOf(7L)))
    }

    @Test fun mergeCandidatesJoinSplitGroupsButNotTwoNamedPeople() {
        val c = FaceClusterer()
        val base = randomUnit()
        val a = FaceGroup(1, FloatArray(0), 0, false).also { repeat(3) { _ -> it.add(jitter(base, .05f)) } }
        val b = FaceGroup(2, FloatArray(0), 0, true).also { repeat(2) { _ -> it.add(jitter(base, .05f)) } }
        val other = FaceGroup(3, FloatArray(0), 0, false).also { it.add(randomUnit()) }
        val pairs = c.mergeCandidates(listOf(a, b, other))
        assertEquals(listOf(2L to 1L), pairs) // il gruppo con nome assorbe quello senza nome
        val n1 = FaceGroup(4, FloatArray(0), 0, true).also { it.add(jitter(base, .05f)) }
        val n2 = FaceGroup(5, FloatArray(0), 0, true).also { it.add(jitter(base, .05f)) }
        assertTrue(c.mergeCandidates(listOf(n1, n2)).isEmpty())
    }
}
