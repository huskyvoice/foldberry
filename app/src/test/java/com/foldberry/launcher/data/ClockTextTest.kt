package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTextTest {

    @Test
    fun 二十四時間表記は2桁でAMPMなし() {
        assertEquals("09:05" to null, ClockText.time(9, 5, is24 = true))
        assertEquals("00:00" to null, ClockText.time(0, 0, is24 = true))
        assertEquals("23:59" to null, ClockText.time(23, 59, is24 = true, language = ClockLanguage.JA))
    }

    @Test
    fun 十二時間表記は0時と12時を12と表しAMPMを付ける() {
        assertEquals("12:00" to "AM", ClockText.time(0, 0, is24 = false))
        assertEquals("9:05" to "AM", ClockText.time(9, 5, is24 = false))
        assertEquals("11:59" to "AM", ClockText.time(11, 59, is24 = false))
        assertEquals("12:30" to "PM", ClockText.time(12, 30, is24 = false))
        assertEquals("11:59" to "PM", ClockText.time(23, 59, is24 = false))
    }

    @Test
    fun 日本語の十二時間表記は午前午後() {
        assertEquals("12:00" to "午前", ClockText.time(0, 0, is24 = false, language = ClockLanguage.JA))
        assertEquals("12:30" to "午後", ClockText.time(12, 30, is24 = false, language = ClockLanguage.JA))
    }

    @Test
    fun 英語の標準は米国式で省略しない() {
        fun en(m: Int, d: Int, w: Int) = ClockText.date(2026, m, d, w, DateStyle.STANDARD, ClockLanguage.EN)
        assertEquals("Sunday, November 15", en(11, 15, 7))
        assertEquals("Monday, January 1", en(1, 1, 1))
        assertEquals("Wednesday, September 30", en(9, 30, 3))
        assertEquals("Saturday, December 31", en(12, 31, 6))
    }

    @Test
    fun 四つの書式を日本語と英語で出し分ける() {
        // 2026-11-17 は火曜日(オーナーと決めた見本と同じ)。
        fun f(style: DateStyle, lang: ClockLanguage) = ClockText.date(2026, 11, 17, 2, style, lang)
        assertEquals("11月17日 火曜日", f(DateStyle.STANDARD, ClockLanguage.JA))
        assertEquals("11月17日(火)", f(DateStyle.SHORT, ClockLanguage.JA))
        assertEquals("2026年11月17日 火曜日", f(DateStyle.WITH_YEAR, ClockLanguage.JA))
        assertEquals("2026/11/17(火)", f(DateStyle.NUMERIC, ClockLanguage.JA))
        assertEquals("Tuesday, November 17", f(DateStyle.STANDARD, ClockLanguage.EN))
        assertEquals("Tue, Nov 17", f(DateStyle.SHORT, ClockLanguage.EN))
        assertEquals("Tuesday, November 17, 2026", f(DateStyle.WITH_YEAR, ClockLanguage.EN))
        assertEquals("Tue 11/17/2026", f(DateStyle.NUMERIC, ClockLanguage.EN))
    }

    @Test
    fun 数字の書式は月日を2桁にそろえる() {
        assertEquals("2027/01/05(火)", ClockText.date(2027, 1, 5, 2, DateStyle.NUMERIC, ClockLanguage.JA))
        assertEquals("Tue 01/05/2027", ClockText.date(2027, 1, 5, 2, DateStyle.NUMERIC, ClockLanguage.EN))
    }

    @Test
    fun 未知の書式名は標準として扱う() {
        assertEquals(DateStyle.STANDARD, DateStyle.resolve("unknown"))
        assertEquals(DateStyle.NUMERIC, DateStyle.resolve("numeric"))
    }
}
