package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OngoingKindsTest {

    private fun notice(
        pkg: String,
        category: String? = null,
        channel: String? = null,
        progress: Boolean = false,
        chronometer: Boolean = false,
    ) = OngoingNotice(pkg, channel, category, progress, chronometer)

    @Test
    fun OSのVPN通知は数えない() {
        // 2026-11-25 の実機: 常時接続 VPN の「切断」警告が pkg=android・channel=VPN の常駐通知だった。
        assertNull(OngoingKinds.classify(notice("android", category = "sys", channel = "VPN")))
        // OS のほかの常駐(USB など)はその他の常駐。
        assertEquals(OngoingKind.SERVICE, OngoingKinds.classify(notice("android", category = "sys", channel = "USB")))
    }

    @Test
    fun 進行中は種別と進み具合と経過時間で判定する() {
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("com.google.android.apps.maps", category = "navigation")))
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("com.sec.android.app.clockpackage", category = "stopwatch")))
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("com.samsung.android.dialer", category = "call")))
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("some.downloader", progress = true)))
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("some.recorder", chronometer = true)))
    }

    @Test
    fun OneUIのライブ通知は種別が無くても進行中() {
        // 2026-11-25 の実機: Samsung の時計のストップウォッチは category なしで、ライブ通知の印だけを持っていた。
        val stopwatch = OngoingNotice("com.sec.android.app.clockpackage", "notification_channel_stopwatch", null, false, false, liveActivity = true)
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(stopwatch))
    }

    @Test
    fun メディアの再生の種別は進行中にしない() {
        assertEquals(OngoingKind.SERVICE, OngoingKinds.classify(notice("some.player", category = "transport")))
    }

    @Test
    fun 接続はパッケージの表で判定する() {
        assertEquals(OngoingKind.CONNECTION, OngoingKinds.classify(notice("com.microsoft.appmanager")))
        assertEquals(OngoingKind.CONNECTION, OngoingKinds.classify(notice("com.tailscale.ipn", category = "service")))
        assertEquals(OngoingKind.SERVICE, OngoingKinds.classify(notice("com.example.daemon", category = "service")))
    }

    @Test
    fun 接続のアプリでも進み具合があれば進行中() {
        assertEquals(OngoingKind.ACTIVITY, OngoingKinds.classify(notice("com.microsoft.appmanager", progress = true)))
    }

    @Test
    fun 種類の集合は重複をまとめて数えない通知を除く() {
        val kinds = OngoingKinds.kinds(
            listOf(
                notice("com.microsoft.appmanager"),
                notice("com.tailscale.ipn"),
                notice("android", channel = "VPN"),
                notice("com.example.daemon"),
            ),
        )
        assertEquals(setOf(OngoingKind.CONNECTION, OngoingKind.SERVICE), kinds)
        assertEquals(emptySet<OngoingKind>(), OngoingKinds.kinds(emptyList()))
    }
}
