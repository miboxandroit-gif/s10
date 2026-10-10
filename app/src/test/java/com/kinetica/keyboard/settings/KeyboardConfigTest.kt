package com.kinetica.keyboard.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** `parseEnterAlternates` is pure `String? -> List<String>`, testable without SharedPreferences. */
class KeyboardConfigTest {

    private val default = listOf("?", "!", ",")

    @Test
    fun blankOrNullFallsBackToDefault() {
        assertEquals(default, KeyboardConfig.parseEnterAlternates(null))
        assertEquals(default, KeyboardConfig.parseEnterAlternates(""))
        assertEquals(default, KeyboardConfig.parseEnterAlternates("   "))
    }

    @Test
    fun parsesTheDefaultString() {
        assertEquals(default, KeyboardConfig.parseEnterAlternates("? ! ,"))
    }

    @Test
    fun parsesCustomSymbolsAndTrimsWhitespace() {
        assertEquals(listOf(";", ".", "?"), KeyboardConfig.parseEnterAlternates("  ;   .   ?  "))
    }

    @Test
    fun capsAtThreeSymbols() {
        assertEquals(listOf("(", ")", ":"), KeyboardConfig.parseEnterAlternates("( ) : ; ."))
    }

    @Test
    fun fewerThanThreeSymbolsKept() {
        assertEquals(listOf(":"), KeyboardConfig.parseEnterAlternates(":"))
        assertEquals(listOf("(", ")"), KeyboardConfig.parseEnterAlternates("( )"))
    }
    @Test
    fun dualCircleLayoutDefaultsToOptimizedAndAcceptsQwerty() {
        assertEquals("optimized", KeyboardConfig.parseDualCircleLayout(null))
        assertEquals("optimized", KeyboardConfig.parseDualCircleLayout("optimized"))
        assertEquals("qwerty", KeyboardConfig.parseDualCircleLayout("qwerty"))
        assertEquals("optimized", KeyboardConfig.parseDualCircleLayout("unknown"))
    }

    @Test
    fun legacyCircleArrangementMigratesWithoutChangingStandardArrangement() {
        assertEquals("qwerty", KeyboardConfig.parseDualCircleLayout(null, "qwerty_circle"))
        assertEquals("optimized", KeyboardConfig.parseDualCircleLayout(null, "qwerty"))
    }

    @Test
    fun dualCircleChoiceStringsAreParsedAndClamped() {
        assertEquals(100, KeyboardConfig.parseChoiceInt("100", 100, 90, 115))
        assertEquals(115, KeyboardConfig.parseChoiceInt("999", 100, 90, 115))
        assertEquals(100, KeyboardConfig.parseChoiceInt("bad", 100, 90, 115))
        assertEquals(100, KeyboardConfig.parseChoiceInt(null, 100, 90, 115))
    }
}
