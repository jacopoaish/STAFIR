package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLayoutTest {
    // griglia: [H0] m0 m1 m2 [H1] m3 [H2] m4 m5
    private val l = SectionLayout(listOf(3, 1, 2))

    @Test fun counts() {
        assertEquals(6, l.mediaCount)
        assertEquals(9, l.gridCount)
        assertEquals(listOf(0, 4, 6), (0 until 3).map { l.headerIndex(it) })
    }

    @Test fun mediaToGridAndBack() {
        val grid = (0 until 6).map { l.gridOfMedia(it) }
        assertEquals(listOf(1, 2, 3, 5, 7, 8), grid)
        grid.forEachIndexed { media, g -> assertEquals(media, l.mediaOfGrid(g)) }
    }

    @Test fun headersPointToFirstFileOfTheirMonth() {
        assertEquals(0, l.mediaOfGrid(0))
        assertEquals(3, l.mediaOfGrid(4))
        assertEquals(4, l.mediaOfGrid(6))
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 2, 2, 2), (0 until 9).map { l.sectionOfGrid(it) })
        assertEquals(listOf(0, 0, 0, 1, 2, 2), (0 until 6).map { l.sectionOfMedia(it) })
    }

    @Test fun flatGridIsIdentity() {
        val f = SectionLayout.flat(5)
        assertEquals(5, f.gridCount)
        assertEquals(3, f.gridOfMedia(3))
        assertEquals(3, f.mediaOfGrid(3))
        assertEquals(0, SectionLayout.flat(0).mediaOfGrid(4))
    }
}
