package com.foldberry.launcher.data

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.floor

/** カレンダーの 1 マス。inMonth=表示中の月の日か(前後の月の日は false)。 */
data class CalendarDay(val date: LocalDate, val inMonth: Boolean)

/**
 * カバー画面の月のカレンダー(仕様書 §4.3。2026-11-17 オーナー決定)の日付と文字。
 * Android 非依存にして単体テストで検証する(MonthCalendarTest)。
 */
object MonthCalendar {
    /** 常に 6 週 × 7 日。5 週で足りる月も 6 週描いて、月を送ってもカレンダーの高さが変わらないようにする。 */
    const val CELLS = 42

    private val MONTHS_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )

    /** 日曜始まりの曜日の短い名前。 */
    private val WEEKDAYS_JA = listOf("日", "月", "火", "水", "木", "金", "土")
    private val WEEKDAYS_EN = listOf("S", "M", "T", "W", "T", "F", "S")

    /** [year] 年 [month] 月(1〜12)を含む 42 日。先頭は [weekStart] の曜日。 */
    fun cells(year: Int, month: Int, weekStart: DayOfWeek = DayOfWeek.SUNDAY): List<CalendarDay> {
        val first = LocalDate.of(year, month, 1)
        val offset = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val start = first.minusDays(offset.toLong())
        return List(CELLS) { i ->
            val d = start.plusDays(i.toLong())
            CalendarDay(d, d.monthValue == month && d.year == year)
        }
    }

    /** 見出し。日本語「2026年11月」、英語「November 2026」。 */
    fun title(year: Int, month: Int, language: ClockLanguage): String = when (language) {
        ClockLanguage.JA -> "${year}年${month}月"
        ClockLanguage.EN -> "${MONTHS_EN[month - 1]} $year"
    }

    /** 日曜始まりの 7 つの曜日の短い名前。 */
    fun weekdayLabels(language: ClockLanguage): List<String> = when (language) {
        ClockLanguage.JA -> WEEKDAYS_JA
        ClockLanguage.EN -> WEEKDAYS_EN
    }
}

/**
 * 日本の祝日(内閣府「国民の祝日」の規則を計算で出す。年ごとの表を持たないので毎年直さなくてよい)。
 * 春分・秋分は 1980〜2099 年に使える近似式。2020・2021 年の五輪による移動などの特例は持たない
 * (カレンダーで過去の年を見るのはまれなので許容。2026-11-17)。法改正があればここを直す。
 */
object JapaneseHolidays {
    fun isHoliday(date: LocalDate): Boolean = isNamed(date) || isSubstitute(date) || isCitizens(date)

    /** 名前のある祝日(振替休日・国民の休日を除く)。 */
    fun isNamed(d: LocalDate): Boolean {
        val y = d.year
        val day = d.dayOfMonth
        return when (d.monthValue) {
            1 -> day == 1 || isNthMonday(d, 2)                         // 元日・成人の日
            2 -> day == 11 || (y >= 2020 && day == 23)                 // 建国記念の日・天皇誕生日
            3 -> day == vernalEquinox(y)                               // 春分の日
            4 -> day == 29                                             // 昭和の日
            5 -> day in 3..5                                           // 憲法記念日・みどりの日・こどもの日
            7 -> y >= 2003 && isNthMonday(d, 3)                        // 海の日
            8 -> y >= 2016 && day == 11                                // 山の日
            9 -> (y >= 2003 && isNthMonday(d, 3)) || day == autumnalEquinox(y) // 敬老の日・秋分の日
            10 -> isNthMonday(d, 2)                                    // スポーツの日
            11 -> day == 3 || day == 23                                // 文化の日・勤労感謝の日
            12 -> y in 1989..2018 && day == 23                         // 旧天皇誕生日
            else -> false
        }
    }

    /** 振替休日: 日曜の祝日のあと、最初の祝日でない日。 */
    private fun isSubstitute(d: LocalDate): Boolean {
        if (isNamed(d)) return false
        var prev = d.minusDays(1)
        while (isNamed(prev)) {
            if (prev.dayOfWeek == DayOfWeek.SUNDAY) return true
            prev = prev.minusDays(1)
        }
        return false
    }

    /** 国民の休日: 祝日にはさまれた平日(9 月の敬老の日と秋分の日の間など)。 */
    private fun isCitizens(d: LocalDate): Boolean =
        d.dayOfWeek != DayOfWeek.SUNDAY && !isNamed(d) && isNamed(d.minusDays(1)) && isNamed(d.plusDays(1))

    private fun isNthMonday(d: LocalDate, n: Int) = d.dayOfWeek == DayOfWeek.MONDAY && (d.dayOfMonth - 1) / 7 == n - 1

    private fun vernalEquinox(y: Int) = equinox(20.8431, y)

    private fun autumnalEquinox(y: Int) = equinox(23.2488, y)

    private fun equinox(base: Double, y: Int): Int = floor(base + 0.242194 * (y - 1980) - floor((y - 1980) / 4.0)).toInt()
}
