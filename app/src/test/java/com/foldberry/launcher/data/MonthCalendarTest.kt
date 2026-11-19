package com.foldberry.launcher.data

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthCalendarTest {

    @Test
    fun 年2026の祝日は内閣府の一覧と一致する() {
        val expected = listOf(
            "01-01", "01-12", "02-11", "02-23", "03-20", "04-29", "05-03", "05-04", "05-05", "05-06",
            "07-20", "08-11", "09-21", "09-22", "09-23", "10-12", "11-03", "11-23",
        )
        val actual = (0 until 365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }
            .filter(JapaneseHolidays::isHoliday)
            .map { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }
        assertEquals(expected, actual)
    }

    @Test
    fun 春分と秋分は年によって動く() {
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2027, 3, 21)))
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.of(2027, 3, 20)))
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2025, 9, 23)))
    }

    @Test
    fun 日曜の祝日の翌日は振替休日() {
        // 2027-02-11(木)は振替なし、2029-02-11(日)→ 2/12 が振替
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.of(2027, 2, 12)))
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2029, 2, 12)))
    }

    @Test
    fun 前後の月を含めて常に42日で日曜から始まる() {
        val cells = MonthCalendar.cells(2026, 11)
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 11, 1), cells.first().date) // 11/1 は日曜
        assertEquals(LocalDate.of(2026, 12, 12), cells.last().date)
        assertEquals(30, cells.count { it.inMonth })
        assertTrue(cells.all { (it.date.dayOfWeek == DayOfWeek.SUNDAY) == (cells.indexOf(it) % 7 == 0) })
    }

    @Test
    fun 月の途中から始まる月は前の月の日で埋める() {
        val cells = MonthCalendar.cells(2026, 8) // 8/1 は土曜、6 週を使う月
        assertEquals(LocalDate.of(2026, 7, 26), cells.first().date)
        assertFalse(cells.first().inMonth)
        assertEquals(LocalDate.of(2026, 8, 31), cells[36].date)
        assertTrue(cells[36].inMonth)
    }

    @Test
    fun うるう年の二月と月曜始まり() {
        val cells = MonthCalendar.cells(2028, 2, DayOfWeek.MONDAY)
        assertEquals(29, cells.count { it.inMonth })
        assertEquals(DayOfWeek.MONDAY, cells.first().date.dayOfWeek)
    }

    @Test
    fun 見出しと曜日の文字() {
        assertEquals("2026年11月", MonthCalendar.title(2026, 11, ClockLanguage.JA))
        assertEquals("November 2026", MonthCalendar.title(2026, 11, ClockLanguage.EN))
        assertEquals("日", MonthCalendar.weekdayLabels(ClockLanguage.JA).first())
        assertEquals(7, MonthCalendar.weekdayLabels(ClockLanguage.EN).size)
    }
}
