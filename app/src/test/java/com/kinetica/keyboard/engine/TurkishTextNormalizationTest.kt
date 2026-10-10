package com.kinetica.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression tests for Turkish dotted/dotless I and apostrophized proper nouns. */
class TurkishTextNormalizationTest {

    @Test
    fun dottedAndDotlessCapitalIUseTurkishCaseRules() {
        assertEquals("ışık", AccentFolder.fold("IŞIK", Alphabet.TURKISH))
        assertEquals("istanbul", AccentFolder.fold("İSTANBUL", Alphabet.TURKISH))
        assertEquals("i", AccentFolder.fold("İ", Alphabet.TURKISH))
        assertEquals("ı", AccentFolder.fold("I", Alphabet.TURKISH))
    }

    @Test
    fun apostropheIsPreservedInTurkishProperNouns() {
        assertEquals("ankara’da", AccentFolder.fold("Ankara’da", Alphabet.TURKISH))
        assertEquals("ali'nin", AccentFolder.fold("Ali'nin", Alphabet.TURKISH))
    }

    @Test
    fun TurkishNativeLettersAreNotFoldedToEnglishBases() {
        assertEquals("çöşüğ", AccentFolder.fold("ÇÖŞÜĞ", Alphabet.TURKISH))
    }
}
