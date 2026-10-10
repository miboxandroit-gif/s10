package com.kinetica.keyboard.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishPersonalNameInflectorTest {
    @Test
    fun foreignNameGetsTwoHarmonyCandidatesForCommonCaseFamilies() {
        val forms = TurkishPersonalNameInflector.candidates("Mary").toSet()
        assertTrue(forms.contains("Mary'yi"))
        assertTrue(forms.contains("Mary'yı"))
        assertTrue(forms.contains("Mary'ye"))
        assertTrue(forms.contains("Mary'ya"))
        assertTrue(forms.contains("Mary'nin"))
        assertTrue(forms.contains("Mary'nın"))
        assertTrue(forms.contains("Mary'de"))
        assertTrue(forms.contains("Mary'den"))
        assertTrue(forms.contains("Mary'yle"))
        assertTrue(forms.contains("Mary'yla"))
    }

    @Test
    fun writtenRootIsNotSoftenedForProperNameCandidates() {
        val forms = TurkishPersonalNameInflector.candidates("Zonguldak").toSet()
        assertTrue(forms.contains("Zonguldak'a"))
        assertTrue(forms.contains("Zonguldak'ta"))
        assertFalse(forms.any { "Zonguldağ" in it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSentencesInsteadOfExpandingUnboundedText() {
        TurkishPersonalNameInflector.candidates("Mary Smith")
    }
}
