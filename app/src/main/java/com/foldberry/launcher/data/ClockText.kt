package com.foldberry.launcher.data

/** 時計の表記の言語(画面の言語 ja / en に対応。§4.2)。 */
enum class ClockLanguage { JA, EN }

/**
 * 日付・曜日の書式(設定で選ぶ。§4.2 / §8。2026-11-17 オーナー決定の 4 種)。id は保存に使う。
 *
 * | 型 | 日本語 | English |
 * |---|---|---|
 * | 標準 | 11月17日 火曜日 | Tuesday, November 17 |
 * | 短縮 | 11月17日(火) | Tue, Nov 17 |
 * | 年付き | 2026年11月17日 火曜日 | Tuesday, November 17, 2026 |
 * | 数字 | 2026/11/17(火) | Tue 11/17/2026 |
 */
enum class DateStyle(val id: String) {
    STANDARD("standard"),
    SHORT("short"),
    WITH_YEAR("year"),
    NUMERIC("numeric"),
    ;

    companion object {
        val DEFAULT = STANDARD

        /** 未知の名前(手で編集されたファイル・将来の版で増えた型)は既定で表示する。 */
        fun resolve(id: String): DateStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 12/24 時間表記(設定で選ぶ。§8。v0.6.0)。既定は端末の設定に従う。id は保存に使う。
 */
enum class HourFormat(val id: String) {
    SYSTEM("system"),
    H12("12"),
    H24("24"),
    ;

    /** 24 時間表記にするか。SYSTEM のときは端末の設定(systemIs24)をそのまま使う。 */
    fun is24(systemIs24: Boolean): Boolean = when (this) {
        SYSTEM -> systemIs24
        H12 -> false
        H24 -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        /** 未知の名前は既定(端末に従う)にする。 */
        fun resolve(id: String): HourFormat = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 画面部の大時計の表示文字列(仕様書 §4.2)。Android 非依存にして単体テストで検証する(ClockTextTest)。
 * 表記は画面の言語(日本語/英語)と設定の書式で決める(2026-11-17 改訂。旧: 英語に固定)。
 * 端末の言語設定で表記が揺れないよう、ロケール依存の書式は使わず固定の名前を持つ。
 */
object ClockText {
    private val WEEKDAYS_EN = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val MONTHS_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val WEEKDAYS_JA = listOf("月", "火", "水", "木", "金", "土", "日")

    /**
     * @param hour 0〜23
     * @param is24 端末の設定(24 時間表記か)
     * @return 時刻(24 時間表記は 2 桁ずつ、12 時間表記は時を先頭ゼロなし)と、12 時間表記のときの午前/午後(AM/PM)。
     *   24 時間表記は null
     */
    fun time(hour: Int, minute: Int, is24: Boolean, language: ClockLanguage = ClockLanguage.EN): Pair<String, String?> {
        val mm = minute.toString().padStart(2, '0')
        if (is24) return "${hour.toString().padStart(2, '0')}:$mm" to null
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val am = hour < 12
        val marker = when (language) {
            ClockLanguage.JA -> if (am) "午前" else "午後"
            ClockLanguage.EN -> if (am) "AM" else "PM"
        }
        return "$h12:$mm" to marker
    }

    /**
     * @param month 1〜12
     * @param dayOfWeek 1=月曜 … 7=日曜(java.time.DayOfWeek.value と同じ)
     */
    fun date(year: Int, month: Int, day: Int, dayOfWeek: Int, style: DateStyle, language: ClockLanguage): String {
        val w = (dayOfWeek - 1).coerceIn(0, 6)
        val m = (month - 1).coerceIn(0, 11)
        return when (language) {
            ClockLanguage.JA -> {
                val wd = WEEKDAYS_JA[w]
                when (style) {
                    DateStyle.STANDARD -> "${month}月${day}日 ${wd}曜日"
                    DateStyle.SHORT -> "${month}月${day}日($wd)"
                    DateStyle.WITH_YEAR -> "${year}年${month}月${day}日 ${wd}曜日"
                    DateStyle.NUMERIC -> "$year/${pad2(month)}/${pad2(day)}($wd)"
                }
            }
            ClockLanguage.EN -> {
                val wd = WEEKDAYS_EN[w]
                val mon = MONTHS_EN[m]
                when (style) {
                    DateStyle.STANDARD -> "$wd, $mon $day"
                    DateStyle.SHORT -> "${wd.take(3)}, ${mon.take(3)} $day"
                    DateStyle.WITH_YEAR -> "$wd, $mon $day, $year"
                    DateStyle.NUMERIC -> "${wd.take(3)} ${pad2(month)}/${pad2(day)}/$year"
                }
            }
        }
    }

    private fun pad2(n: Int) = n.toString().padStart(2, '0')
}
