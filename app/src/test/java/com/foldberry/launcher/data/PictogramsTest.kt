package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictogramsTest {

    @Test
    fun 対応表のアプリはその絵記号() {
        assertEquals(Pictogram.MAIL, Pictograms.of("com.google.android.gm", null, emptyMap()))
        assertEquals(Pictogram.WALLET, Pictograms.of("jp.co.mizuhobank.banking", null, emptyMap()))
        assertEquals(Pictogram.PHONE, Pictograms.of("com.samsung.android.dialer", null, emptyMap()))
    }

    @Test
    fun 対応表に無ければアプリが申告する種類で決め_それも無ければ汎用() {
        assertEquals(Pictogram.MUSIC, Pictograms.of("x.unknown", Pictograms.CATEGORY_AUDIO, emptyMap()))
        assertEquals(Pictogram.MAP, Pictograms.of("x.unknown", Pictograms.CATEGORY_MAPS, emptyMap()))
        // ゲーム(0)など当てはまらない種類・読めない(null)は汎用
        assertEquals(Pictogram.GENERIC, Pictograms.of("x.unknown", 0, emptyMap()))
        assertEquals(Pictogram.GENERIC, Pictograms.of("x.unknown", null, emptyMap()))
    }

    @Test
    fun 対応表は申告の種類より優先() {
        // YouTube Music が仮に動画と申告していても音楽
        assertEquals(Pictogram.MUSIC, Pictograms.of("com.google.android.apps.youtube.music", Pictograms.CATEGORY_VIDEO, emptyMap()))
    }

    @Test
    fun オーナーが選んだものが最優先で_未知のidは無視して自動() {
        val overrides = mapOf("com.google.android.gm" to "chat", "x.unknown" to "nope")
        assertEquals(Pictogram.CHAT, Pictograms.of("com.google.android.gm", null, overrides))
        assertEquals(Pictogram.GENERIC, Pictograms.of("x.unknown", null, overrides))
    }

    @Test
    fun idは重ならず引き直せる() {
        assertEquals(Pictogram.entries.size, Pictogram.entries.map { it.id }.toSet().size)
        Pictogram.entries.forEach { assertEquals(it, Pictogram.of(it.id)) }
        assertNull(Pictogram.of("nope"))
    }

    // ── 保存(AppearanceSetting) ──

    @Test
    fun 出し方の無い古いファイルは白黒のオンオフで読む() {
        assertEquals(IconStyle.GRAY, AppearanceSetting(grayscaleIcons = true).style)
        assertEquals(IconStyle.STANDARD, AppearanceSetting(grayscaleIcons = false).style)
    }

    @Test
    fun 出し方を書くと古い版向けの白黒も合わせる() {
        assertEquals(false, AppearanceSetting().withStyle(IconStyle.STANDARD).grayscaleIcons)
        assertEquals(true, AppearanceSetting(grayscaleIcons = false).withStyle(IconStyle.GRAY).grayscaleIcons)
        // 絵記号は古い版では白黒で見せる(色が出るより近い)
        val p = AppearanceSetting(grayscaleIcons = false).withStyle(IconStyle.PICTOGRAM)
        assertEquals(IconStyle.PICTOGRAM, p.style)
        assertEquals(true, p.grayscaleIcons)
    }

    @Test
    fun 絵記号の選択と自動への戻し() {
        val a = AppearanceSetting().withPictogram("p", Pictogram.CHAT)
        assertEquals(mapOf("p" to "chat"), a.pictograms)
        assertEquals(emptyMap<String, String>(), a.withPictogram("p", null).pictograms)
    }

    @Test
    fun 出し方と絵記号を書いて読み戻せ_未知の値は丸める() {
        val a = Assignments.EMPTY.copy(
            appearance = AppearanceSetting().withStyle(IconStyle.PICTOGRAM).withPictogram("p", Pictogram.WALLET),
        )
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))

        val text = """{"version":1,"keys":{},"appearance":{"grayscaleIcons":false,"iconStyle":"nope","pictograms":{"p":"chat","q":"nope"}}}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(IconStyle.STANDARD, ok.value.appearance.style)
        assertEquals(mapOf("p" to "chat"), ok.value.appearance.pictograms)
    }
}
