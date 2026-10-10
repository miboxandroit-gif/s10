package com.kinetica.keyboard.ui

import kotlin.math.abs

/** Pure geometry rules for the Dual Circle's two centre hubs. */
object DualCircleHubGesture {
    fun moved(dx: Float, dy: Float, travelPx: Float): Boolean =
        abs(dx) >= travelPx || abs(dy) >= travelPx

    fun isHorizontal(dx: Float, dy: Float, travelPx: Float, dominance: Float = 1.2f): Boolean =
        abs(dx) >= travelPx && abs(dx) >= dominance * abs(dy)
}
