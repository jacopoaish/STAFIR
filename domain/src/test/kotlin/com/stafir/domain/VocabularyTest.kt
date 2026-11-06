package com.stafir.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {
    @Test fun specificConceptsWin() {
        assertEquals("dog", Vocabulary.conceptForWord("cane")!!.id)
        assertEquals("snow", Vocabulary.conceptForWord("neve")!!.id)
        assertEquals("selfie", Vocabulary.conceptForWord("selfie")!!.id)
    }

    @Test fun labelsToConcepts() {
        val ids = Vocabulary.conceptsForLabels(listOf("Beach", "Sky")).map { it.id }
        assertTrue("sea" in ids)
        assertTrue("sunset" in ids)
    }

    @Test fun conceptIdsAreUnique() {
        assertEquals(Vocabulary.concepts.size, Vocabulary.concepts.map { it.id }.toSet().size)
    }

    @Test fun translations() {
        assertEquals("spiaggia", Vocabulary.italianLabel("Beach"))
        assertEquals("unknownthing", Vocabulary.italianLabel("UnknownThing"))
    }

    @Test fun impliedConcepts() {
        assertEquals(setOf("dog", "animals"), Vocabulary.withImplied(setOf("dog")))
        assertEquals(setOf("sea"), Vocabulary.withImplied(setOf("sea")))
    }
}
