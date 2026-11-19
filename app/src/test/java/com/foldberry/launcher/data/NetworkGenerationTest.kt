package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkGenerationTest {

    @Test
    fun Wi_Fiの規格を世代の数字にする() {
        assertEquals("4", NetworkGeneration.wifi(4, 2412))
        assertEquals("5", NetworkGeneration.wifi(5, 5220))
        assertEquals("6", NetworkGeneration.wifi(6, 5220))
        assertEquals("7", NetworkGeneration.wifi(8, 5220))
    }

    @Test
    fun 六GHz帯の11axは6E() {
        assertEquals("6", NetworkGeneration.wifi(6, 5924))
        assertEquals("6E", NetworkGeneration.wifi(6, 5925))
        assertEquals("6E", NetworkGeneration.wifi(6, 7125))
        assertEquals("6", NetworkGeneration.wifi(6, 7126))
        // 周波数が取れない(-1)ときは 6 に落とす。
        assertEquals("6", NetworkGeneration.wifi(6, -1))
    }

    @Test
    fun 旧規格と不明のWi_Fiは数字なし() {
        assertNull(NetworkGeneration.wifi(0, 2412)) // UNKNOWN
        assertNull(NetworkGeneration.wifi(1, 2412)) // LEGACY
        assertNull(NetworkGeneration.wifi(7, 60480)) // 11AD
    }

    @Test
    fun 五Gは方式でも上書きの種別でも判定する() {
        assertEquals("5G", NetworkGeneration.mobile(20, 0))
        assertEquals("5G", NetworkGeneration.mobile(13, 3))
        assertEquals("5G", NetworkGeneration.mobile(13, 4))
        assertEquals("5G", NetworkGeneration.mobile(13, 5))
    }

    @Test
    fun LTEは4Gと書く() {
        assertEquals("4G", NetworkGeneration.mobile(13, 0))
        assertEquals("4G", NetworkGeneration.mobile(13, 1)) // LTE_CA
        assertEquals("4G", NetworkGeneration.mobile(13, 2)) // LTE_ADVANCED_PRO
    }

    @Test
    fun 三Gと2Gと不明() {
        assertEquals("3G", NetworkGeneration.mobile(10, 0)) // HSPA
        assertEquals("3G", NetworkGeneration.mobile(3, 0)) // UMTS
        assertNull(NetworkGeneration.mobile(2, 0)) // EDGE
        assertNull(NetworkGeneration.mobile(18, 0)) // IWLAN
        assertNull(NetworkGeneration.mobile(0, 0)) // UNKNOWN
    }
}
