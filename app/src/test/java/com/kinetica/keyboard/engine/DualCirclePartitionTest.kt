package com.kinetica.keyboard.engine

import com.kinetica.keyboard.ui.DualCircleLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DualCirclePartitionTest {
    @Test
    fun latinPartitionStillCoversAll26Letters() {
        val rings = DualCircleLayout().partitionLettersForTest(Alphabet.LATIN)
        val combined = rings.joinToString("")
        assertEquals(listOf(5, 8, 5, 8), rings.map(String::length))
        assertEquals(26, combined.length)
        assertEquals(26, combined.toSet().size)
        assertEquals(('a'..'z').toSet(), combined.toSet())
    }

    @Test
    fun turkishPartitionCovers29LettersWithBalancedRings() {
        val layout = DualCircleLayout()
        val rings = layout.partitionLettersForTest(Alphabet.TURKISH)
        val combined = rings.joinToString("")
        assertEquals(listOf(6, 8, 6, 9), rings.map(String::length))
        assertEquals(29, combined.length)
        assertEquals(29, combined.toSet().size)
        assertEquals(Alphabet.TURKISH.letters.toSet(), combined.toSet())
    }

    @Test
    fun dualCircleUsesAlphabetLocalCodesNotUnicodeCodePoints() {
        val layout = DualCircleLayout()
        val rings = layout.ringCodesForTest(Alphabet.TURKISH)
        val codes = rings.flatMap { it.asIterable() }
        assertEquals(29, codes.size)
        assertEquals(29, codes.toSet().size)
        assertTrue(codes.all { it in 0 until Alphabet.TURKISH.letterCount })
        assertEquals(Alphabet.TURKISH.codeOf('i'), codes[3])
        assertEquals(Alphabet.TURKISH.codeOf('ğ'), codes.last())
    }
}
