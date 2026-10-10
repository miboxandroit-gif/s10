package com.kinetica.keyboard.ui

import com.kinetica.keyboard.engine.Alphabet
import com.kinetica.keyboard.layout.Key
import com.kinetica.keyboard.layout.KeyType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DualCircleLayoutTest {

    private fun keys(): List<Key> {
        val letters = Alphabet.TURKISH.letters.map { c ->
            Key(
                id = c.toString(),
                type = KeyType.CHAR,
                label = c.toString(),
                output = c.toString(),
                x = 0f,
                y = 0f,
                w = 0.1f,
                h = 0.25f,
                alphabet = Alphabet.TURKISH,
            )
        }
        return letters + listOf(
            Key("space", KeyType.SPACE, " ", " ", 0f, 0f, 0.5f, 0.25f, alphabet = Alphabet.TURKISH),
            Key("backspace", KeyType.BACKSPACE, "⌫", "", 0.9f, 0f, 0.1f, 0.25f, alphabet = Alphabet.TURKISH),
        )
    }

    @Test
    fun turkishCircleHasTouchInflationAndNoBoundaryDeadZone() {
        val layout = DualCircleLayout()
        val cfg = layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = false)
        assertTrue(cfg.touchInflatePx > 0f)
        val normal = layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = false, sizePct = 100, spreadPct = 100)
        val large = layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = false, sizePct = 115, spreadPct = 100)
        assertTrue(large.radius > normal.radius)
        val wide = layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = true, sizePct = 100, spreadPct = 115)
        assertTrue(wide.leftCx < normal.leftCx || wide.rightCx > normal.rightCx)

        val cfgAfterRebuild = layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = false)
        val rings = layout.ringCodesForTest(Alphabet.TURKISH)
        // Probe the true polar gap between the first two inner-ring sectors. The helper
        // returns the cardinal-aligned sector origin; index 1 is the shared boundary.
        val boundaryAngle = layout.slotCenterAngleForTest(1, rings[0].size)
        val boundaryRadius = (cfgAfterRebuild.innerInner + cfgAfterRebuild.innerOuter) * 0.5f
        val hit = layout.hit(
            cfgAfterRebuild.leftCx + cos(boundaryAngle) * boundaryRadius,
            cfgAfterRebuild.cy + sin(boundaryAngle) * boundaryRadius,
        )

        assertTrue(hit is DualCircleLayout.Hit.Letter)
    }

    @Test
    fun allTwentyNineTurkishLettersHaveDistinctHitTargets() {
        val layout = DualCircleLayout()
        layout.rebuild(1080f, 900f, keys(), 3f, isLandscape = false)
        val codes = layout.letterCodes()
        assertTrue(codes.distinct().size == 29)
        for (code in codes) {
            val hit = layout.hit(layout.centerX(code)!!, layout.centerY(code)!!)
            assertTrue(hit is DualCircleLayout.Hit.Letter)
            assertNotEquals(-1, code)
        }
    }
}
