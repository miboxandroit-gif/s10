package com.kinetica.keyboard.engine

import com.kinetica.keyboard.ui.DualCircleHubGesture
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DualCircleHubGestureTest {
    @Test
    fun verticalMovementDoesNotBecomeEditGesture() {
        assertTrue(DualCircleHubGesture.moved(0f, 30f, 22f))
        assertFalse(DualCircleHubGesture.isHorizontal(0f, 30f, 22f))
    }

    @Test
    fun horizontalMovementIsAcceptedWhenDominant() {
        assertTrue(DualCircleHubGesture.isHorizontal(40f, 10f, 22f))
        assertTrue(DualCircleHubGesture.isHorizontal(-40f, 8f, 22f))
        assertFalse(DualCircleHubGesture.isHorizontal(18f, 4f, 22f))
    }
}
