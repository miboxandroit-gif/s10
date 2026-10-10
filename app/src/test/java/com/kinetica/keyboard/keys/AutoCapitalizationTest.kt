package com.kinetica.keyboard.keys

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoCapitalizationTest {
    @Test
    fun turkishInitialIUsesLocaleSpecificCapitals() {
        assertEquals("İstanbul", AutoCapitalization.forWord("istanbul", "tr"))
        assertEquals("Işık", AutoCapitalization.forWord("ışık", "tr"))
    }

    @Test
    fun turkishDoesNotRewriteOtherInitials() {
        assertEquals("çocuk", AutoCapitalization.forWord("çocuk", "tr"))
        assertEquals("umut", AutoCapitalization.forWord("umut", "tr"))
    }
}
