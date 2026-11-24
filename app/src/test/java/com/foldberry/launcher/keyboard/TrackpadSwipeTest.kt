package com.foldberry.launcher.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackpadSwipeTest {
    private val t = 24f

    @Test
    fun 四方向() {
        assertEquals(SwipeDirection.RIGHT, TrackpadSwipe.classify(30f, 0f, t))
        assertEquals(SwipeDirection.LEFT, TrackpadSwipe.classify(-30f, 5f, t))
        assertEquals(SwipeDirection.DOWN, TrackpadSwipe.classify(3f, 40f, t))
        assertEquals(SwipeDirection.UP, TrackpadSwipe.classify(0f, -40f, t))
    }

    @Test
    fun 閾値ちょうどは払い未満は払いでない() {
        assertEquals(SwipeDirection.RIGHT, TrackpadSwipe.classify(24f, 0f, t))
        assertNull(TrackpadSwipe.classify(23.9f, 0f, t))
        assertNull(TrackpadSwipe.classify(0f, 0f, t))
    }

    @Test
    fun 斜めは何もしない() {
        // 主軸が副軸の 1.5 倍未満。
        assertNull(TrackpadSwipe.classify(40f, 30f, t))
        assertNull(TrackpadSwipe.classify(-30f, -40f, t))
        // 1.5 倍ちょうどは主軸の向き。
        assertEquals(SwipeDirection.RIGHT, TrackpadSwipe.classify(45f, 30f, t))
    }
}
