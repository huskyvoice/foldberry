package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HubItemsTest {

    private val now = LocalDateTime.of(2026, 11, 18, 15, 30)

    private fun item(key: String, kind: NotificationKind, at: LocalDateTime, title: String = "佐藤", text: String = "本文") =
        HubItem(key, "com.example.$key", kind, title, text, at)

    @Test
    fun 改行と連続した空白は1つの空白にして前後を落とす() {
        assertEquals("1行目 2行目 3行目", HubItems.oneLine("  1行目\n2行目\r\n\t3行目  "))
        assertEquals("", HubItems.oneLine(null))
    }

    @Test
    fun 新しい順に並べ種類で絞れる() {
        val a = item("a", NotificationKind.EMAIL, now.minusMinutes(30))
        val b = item("b", NotificationKind.SMS, now.minusMinutes(5))
        val c = item("c", NotificationKind.EMAIL, now.minusMinutes(1))
        assertEquals(listOf(c, b, a), HubItems.filter(listOf(a, b, c), null))
        assertEquals(listOf(c, a), HubItems.filter(listOf(a, b, c), NotificationKind.EMAIL))
        assertEquals(emptyList<HubItem>(), HubItems.filter(listOf(a, b, c), NotificationKind.MISSED_CALL))
    }

    @Test
    fun 件数は種類ごとに数える() {
        val items = listOf(
            item("a", NotificationKind.EMAIL, now),
            item("b", NotificationKind.EMAIL, now),
            item("c", NotificationKind.OTHER, now),
        )
        assertEquals(NotificationCounts(email = 2, other = 1), HubItems.counts(items))
        assertEquals(NotificationCounts(), HubItems.counts(emptyList()))
    }

    @Test
    fun 本文の出し方() {
        val i = item("a", NotificationKind.SMS, now, title = "母", text = "夕飯いる?")
        assertEquals(HubDisplay("母", "夕飯いる?"), HubItems.display(i, HubPreview.FULL, "メッセージ"))
        assertEquals(HubDisplay("母", null), HubItems.display(i, HubPreview.SENDER, "メッセージ"))
        assertEquals(HubDisplay("メッセージ", null), HubItems.display(i, HubPreview.APP, "メッセージ"))
    }

    @Test
    fun タイトルの無い通知は1行目をアプリ名にする() {
        val i = item("a", NotificationKind.OTHER, now, title = " ", text = "")
        assertEquals(HubDisplay("カレンダー", ""), HubItems.display(i, HubPreview.FULL, "カレンダー"))
        assertEquals(HubDisplay("カレンダー", null), HubItems.display(i, HubPreview.SENDER, "カレンダー"))
    }

    @Test
    fun 今日の通知は時刻で12時間表記は言語ごとの並び() {
        val at = LocalDateTime.of(2026, 11, 18, 15, 5)
        assertEquals("15:05", HubItems.timeLabel(at, now, is24 = true, ClockLanguage.JA))
        assertEquals("午後3:05", HubItems.timeLabel(at, now, is24 = false, ClockLanguage.JA))
        assertEquals("3:05 PM", HubItems.timeLabel(at, now, is24 = false, ClockLanguage.EN))
    }

    @Test
    fun 昨日とそれより前と未来() {
        assertEquals("昨日", HubItems.timeLabel(LocalDateTime.of(2026, 11, 17, 23, 59), now, true, ClockLanguage.JA))
        assertEquals("Yesterday", HubItems.timeLabel(LocalDateTime.of(2026, 11, 17, 0, 0), now, true, ClockLanguage.EN))
        assertEquals("11/16", HubItems.timeLabel(LocalDateTime.of(2026, 11, 16, 12, 0), now, true, ClockLanguage.JA))
        // 年をまたいだ昨日
        assertEquals("昨日", HubItems.timeLabel(LocalDateTime.of(2025, 12, 31, 8, 0), LocalDateTime.of(2026, 1, 1, 9, 0), true, ClockLanguage.JA))
        // 端末の時計がずれて未来の時刻になった通知は時刻で出す
        assertEquals("16:00", HubItems.timeLabel(LocalDateTime.of(2026, 11, 18, 16, 0), now, true, ClockLanguage.JA))
        assertEquals("09:00", HubItems.timeLabel(LocalDateTime.of(2026, 11, 19, 9, 0), now, true, ClockLanguage.JA))
    }
}
