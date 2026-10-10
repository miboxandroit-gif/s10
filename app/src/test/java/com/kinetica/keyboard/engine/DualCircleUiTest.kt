package com.kinetica.keyboard.engine

import com.kinetica.keyboard.layout.Key
import com.kinetica.keyboard.layout.KeyType
import com.kinetica.keyboard.layout.KeyboardLayout
import com.kinetica.keyboard.settings.Prefs
import com.kinetica.keyboard.ui.DualCircleLayout
import com.kinetica.keyboard.ui.DualCircleMath
import com.kinetica.keyboard.ui.DualCircleSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DualCircleUiTest {

    @Test
    fun radialPhaseIsCenteredOnCardinalGap() {
        assertEquals(0f, com.kinetica.keyboard.ui.DualCircleMath.PHASE_DEGREES, 0.0001f)
        assertEquals(-Math.PI.toFloat() / 2f, DualCircleMath.slotAngle(0, 6), 0.0001f)
    }


    @Test
    fun turkishGeometryHasPositiveTouchOverlapAnd29Codes() {
        val geometry = DualCircleLayout().rebuild(
            width = 1200f,
            height = 1400f,
            keys = emptyList(),
            density = 1f,
            isLandscape = false,
        )
        assertEquals(29, DualCircleLayout().ringCodesForTest(Alphabet.TURKISH).sumOf { it.size })
        assertTrue(geometry.touchInflatePx >= 8f)
        assertTrue(geometry.hubRadius > 0f)
    }

    @Test
    fun turkishQwertyPartitionKeepsAll29Letters() {
        val rings = DualCircleLayout().partitionLettersForTest(Alphabet.TURKISH, "qwerty")
        assertEquals(4, rings.size)
        assertEquals(29, rings.sumOf { it.length })
        assertEquals("ertyuı", rings[0])
        assertEquals("opğüasdf", rings[1])
        assertEquals("ghjklş", rings[2])
        assertEquals("izcvbnmöç", rings[3])
    }

    @Test
    fun innerRingGetsMoreAreaWithoutShrinkingHubBelowSafeFloor() {
        val cfg = DualCircleLayout().rebuild(
            1200f, 1400f, emptyList(), 1f, false, 100, 100,
        )
        assertEquals(cfg.radius * 0.60f, cfg.innerOuter, 0.01f)
        assertEquals(cfg.radius * 0.63f, cfg.outerInner, 0.01f)
        assertEquals(maxOf(cfg.radius * 0.27f, 28f), cfg.hubRadius, 0.01f)
        assertTrue("hub diameter must stay at least 56px at density 1", cfg.hubRadius * 2f >= 56f)
        assertTrue("inner/outer rings must remain separated", cfg.innerOuter < cfg.outerInner)
    }

    @Test
    fun largerCircleSettingActuallyGrowsPortraitTarget() {
        val layout = DualCircleLayout()
        val normal = layout.rebuild(1200f, 1400f, emptyList(), 1f, false, 100, 100)
        val large = layout.rebuild(1200f, 1400f, emptyList(), 1f, false, 115, 100)
        assertTrue("large=$${large.radius} normal=$${normal.radius}", large.radius > normal.radius)
    }

    @Test
    fun widerThumbSpreadMovesCirclesOutward() {
        val layout = DualCircleLayout()
        val normal = layout.rebuild(1200f, 1400f, emptyList(), 1f, false, 100, 100)
        val wide = layout.rebuild(1200f, 1400f, emptyList(), 1f, false, 100, 115)
        assertTrue(wide.leftCx < normal.leftCx)
        assertTrue(wide.rightCx > normal.rightCx)
    }

    @Test
    fun symbolLayerUsesCircleHitTargets() {
        val keys = buildList {
            add(Key("mode_alpha", KeyType.MODE_ALPHA, "ABC", "", 0f, 0f, 0.1f, 0.1f))
            for (i in 0 until 12) {
                val s = i.toString()
                add(Key("sym_$i", KeyType.CHAR, s, s, 0f, 0f, 0.1f, 0.1f))
            }
        }
        val keyboard = KeyboardLayout("symbols", "en", keys)
        val surface = DualCircleSurface(1f)
        surface.rebuild(keyboard, 1200f, 1400f, false)
        val rect = surface.rectForKey(keys[1], 1)
        assertTrue(rect.width() > 0f)
        assertTrue(rect.height() > 0f)
        assertEquals(1, surface.keyIndexAt(keyboard, rect.centerX(), rect.centerY()))
    }

    @Test
    fun symbolsPutDigitsOnLeftAndPunctuationOnRight() {
        val keys = buildList {
            for (i in 1..10) {
                val s = if (i == 10) "0" else i.toString()
                add(Key("d$s", KeyType.CHAR, s, s, 0f, 0f, 0.1f, 0.1f))
            }
            for ((i, s) in listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "=", "*", "?", "!", ",").withIndex()) {
                add(Key("sym$i", KeyType.CHAR, s, s, 0f, 0f, 0.1f, 0.1f))
            }
            add(Key("abc", KeyType.MODE_ALPHA, "ABC", "", 0f, 0f, 0.1f, 0.1f))
            add(Key("space", KeyType.SPACE, " ", "", 0f, 0f, 0.1f, 0.1f))
            add(Key("enter", KeyType.ENTER, "⏎", "", 0f, 0f, 0.1f, 0.1f))
        }
        val keyboard = KeyboardLayout("symbols", "en", keys)
        val surface = DualCircleSurface(1f)
        surface.rebuild(keyboard, 1200f, 1400f, false)
        val midpoint = 600f
        for (index in 0 until 10) {
            val rect = surface.rectForKey(keys[index], index)
            assertTrue("digit $index has a radial target", rect.width() > 0f)
            assertTrue("digit $index must stay on the left ring group", rect.centerX() < midpoint)
            assertEquals(index, surface.keyIndexAt(keyboard, rect.centerX(), rect.centerY()))
        }
        for (index in 10 until 24) {
            val rect = surface.rectForKey(keys[index], index)
            assertTrue("symbol $index has a radial target", rect.width() > 0f)
            assertTrue("symbol $index must stay on the right ring group", rect.centerX() > midpoint)
            assertEquals(index, surface.keyIndexAt(keyboard, rect.centerX(), rect.centerY()))
        }
        val abcIndex = keys.indexOfFirst { it.id == "abc" }
        val abcRect = surface.specialRect(abcIndex)
        assertTrue(abcRect != null && abcRect.width() > 0f)
        assertEquals(abcIndex, surface.keyIndexAt(keyboard, abcRect!!.centerX(), abcRect.centerY()))
    }

    @Test
    fun languageChoicesAreOnlyEnglishAndTurkish() {
        assertEquals(listOf("en", "tr"), Prefs.ALL_LANGUAGES)
        assertEquals(listOf("en", "tr"), Prefs.DEFAULT_ENABLED_LANGUAGES)
    }
}
