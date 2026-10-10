package com.kinetica.keyboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class DualCircleMathTest {
    @Test
    fun slotAnglesAreInsideTheirSegments() {
        for (count in listOf(5, 6, 8, 9)) {
            val step = 2f * PI.toFloat() / count
            for (index in 0 until count) {
                val center = DualCircleMath.slotAngle(index, count)
                val start = DualCircleMath.normalizeAngle(center - step * 0.40f)
                val end = DualCircleMath.normalizeAngle(center + step * 0.40f)
                assertTrue("$count/$index", DualCircleMath.containsAngle(center, start, end))
            }
        }
    }

    @Test
    fun wrappedAnglesStillHitAcrossMinusPiPlusPiBoundary() {
        val start = 3.0f
        val end = -3.0f
        assertTrue(DualCircleMath.containsAngle(PI.toFloat(), start, end))
        assertTrue(DualCircleMath.containsAngle(-PI.toFloat(), start, end))
        assertFalse(DualCircleMath.containsAngle(0f, start, end))
    }

    @Test
    fun radialDistanceUsesTheSameCoordinateSystemAsHitTesting() {
        assertEquals(5f, DualCircleMath.distance(3f, 4f, 0f, 0f), 0.0001f)
        assertEquals(0f, DualCircleMath.distance(9f, 4f, 9f, 4f), 0.0001f)
    }
}
