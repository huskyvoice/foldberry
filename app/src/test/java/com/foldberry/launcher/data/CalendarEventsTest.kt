package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventsTest {

    private val now = LocalDateTime.of(2026, 11, 19, 9, 54)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)
    private fun ev(title: String, start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false) = CalendarEvent(title, start, end, allDay)

    @Test
    fun 次の予定は今から明日の終わりまでの直近2件() {
        val events = listOf(
            ev("夕方", at(19, 16, 30), at(19, 17, 30)),
            ev("終わった", at(19, 8), at(19, 9)),
            ev("会議", at(19, 14), at(19, 15)),
            ev("明日", at(20, 9), at(20, 10)),
        )
        assertEquals(listOf("会議", "夕方"), CalendarEvents.upcoming(events, now).map { it.title })
    }

    @Test
    fun 今日始まった進行中の予定は入り_昨日からの予定と終日とあさって以降は入らない() {
        val events = listOf(
            ev("進行中", at(19, 9), at(19, 11)),
            ev("昨日から", at(18, 20), at(19, 12)),
            ev("終日", at(19, 0), at(20, 0), allDay = true),
            ev("あさって", at(21, 9), at(21, 10)),
        )
        assertEquals(listOf("進行中"), CalendarEvents.upcoming(events, now).map { it.title })
    }

    @Test
    fun 今日が終わっていれば明日の予定() {
        val late = LocalDateTime.of(2026, 11, 19, 23, 0)
        val events = listOf(ev("明日の朝会", at(20, 9), at(20, 9, 30)), ev("明日の昼", at(20, 12), at(20, 13)))
        val up = CalendarEvents.upcoming(events, late)
        assertEquals(listOf("明日の朝会", "明日の昼"), up.map { it.title })
        assertTrue(CalendarEvents.isTomorrow(up.first(), late))
        assertFalse(CalendarEvents.isTomorrow(ev("今日", at(19, 23, 30), at(19, 23, 45)), late))
    }

    @Test
    fun 点は予定のある日_終日は終わりの日を含まない() {
        val events = listOf(
            ev("会議", at(19, 14), at(19, 15)),
            ev("終日 2 日", at(24, 0), at(26, 0), allDay = true),
            ev("夜をまたぐ", at(27, 23), at(28, 1)),
            ev("0 時に終わる", at(3, 22), at(4, 0)),
        )
        assertEquals(
            setOf(19, 24, 25, 27, 28, 3).map { LocalDate.of(2026, 11, it) }.toSet(),
            CalendarEvents.days(events, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
        )
    }

    @Test
    fun 長い予定は表示範囲に切って点を付ける() {
        val long = ev("長期", LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 6, 1, 0, 0), allDay = true)
        val from = LocalDate.of(2026, 10, 26)
        val to = LocalDate.of(2026, 12, 6)
        val days = CalendarEvents.days(listOf(long), from, to)
        assertEquals(42, days.size)
        assertEquals(from, days.min())
        assertEquals(to, days.max())
    }

    @Test
    fun 表示範囲の外の予定には点を付けない() {
        val events = listOf(ev("先月", at(1, 9).minusMonths(1), at(1, 10).minusMonths(1)))
        assertTrue(CalendarEvents.days(events, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)).isEmpty())
    }
}
