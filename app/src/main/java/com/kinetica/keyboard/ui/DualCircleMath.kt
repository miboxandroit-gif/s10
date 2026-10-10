package com.kinetica.keyboard.ui

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** Pure radial hit-test math kept separate so geometry boundaries can be JVM-tested. */
object DualCircleMath {
    /** Shared radial phase; zero aligns the segment gap to 12/3/6/9. */
    const val PHASE_DEGREES: Float = 0f

    fun distance(x: Float, y: Float, cx: Float, cy: Float): Float =
        sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))

    fun normalizeAngle(angle: Float): Float {
        var value = angle
        while (value <= -PI) value += (2f * PI).toFloat()
        while (value > PI) value -= (2f * PI).toFloat()
        return value
    }

    fun angleAt(x: Float, y: Float, cx: Float, cy: Float): Float =
        normalizeAngle(atan2(y - cy, x - cx))

    fun slotAngle(index: Int, count: Int, phaseDegrees: Float = PHASE_DEGREES): Float =
        normalizeAngle(-PI.toFloat() / 2f +
            Math.toRadians(phaseDegrees.toDouble()).toFloat() +
            index * (2f * PI.toFloat() / count.toFloat()))

    fun containsAngle(angle: Float, start: Float, end: Float, extra: Float = 0f): Boolean {
        val a = normalizeAngle(angle)
        val s = normalizeAngle(start - extra)
        val e = normalizeAngle(end + extra)
        return if (s <= e) a in s..e else a >= s || a <= e
    }
}
