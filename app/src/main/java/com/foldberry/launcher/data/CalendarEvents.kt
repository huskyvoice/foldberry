package com.foldberry.launcher.data

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * カレンダーの予定 1 件(1 回分。繰り返しの予定は CalendarContract.Instances が 1 回ずつ展開済み)。D26(2026-11-19)。
 * 終日の予定は 開始日の 0 時 〜 終了日(含まない)の 0 時 を端末の時刻帯の日時で持つ(UTC の日付を読み替えたもの。CalendarSource)。
 * 辞退した招待・非表示のカレンダーの予定は CalendarSource が読む段階で除く。
 */
data class CalendarEvent(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean,
)

/** 次の予定・カレンダーの点の選び方(純粋関数。単体テスト対象 CalendarEventsTest)。 */
object CalendarEvents {
    /** 次の予定に出す件数(縦・横とも。2026-11-19 オーナー決定)。 */
    const val UPCOMING_COUNT = 2

    /**
     * 次の予定: 今から明日の終わりまでに終わっていない、時刻のある予定を始まりの早い順に [UPCOMING_COUNT] 件。
     * 進行中の予定は今日始まったものだけ入れる(昨日以前からの複数日の予定は「今日のその時刻」に見えてしまうので入れない。
     * 2026-11-19 refuter の指摘)。終日の予定は入れない(点だけ)。
     */
    fun upcoming(events: List<CalendarEvent>, now: LocalDateTime): List<CalendarEvent> {
        val today = now.toLocalDate().atStartOfDay()
        val limit = now.toLocalDate().plusDays(2).atStartOfDay()
        return events
            .filter { !it.allDay && it.end > now && it.start >= today && it.start < limit }
            .sortedWith(compareBy({ it.start }, { it.title }))
            .take(UPCOMING_COUNT)
    }

    /**
     * [from]〜[to](両端を含む。表示中の範囲)のうち予定のある日(カレンダーの点。終日の予定も含む)。
     * 終わりちょうど 0 時の予定はその日に掛からない扱い。長い予定も表示範囲に切ってから数える(どの月を見ても点が付く)。
     */
    fun days(events: List<CalendarEvent>, from: LocalDate, to: LocalDate): Set<LocalDate> = buildSet {
        events.forEach { e ->
            val first = maxOf(e.start.toLocalDate(), from)
            // 終わりが 0 時ちょうどならその日は含めない(終日の予定・日をまたいで 0 時に終わる予定)。0 分の予定は始まりの日だけ。
            val last = if (e.end > e.start && e.end.toLocalTime() == java.time.LocalTime.MIDNIGHT) e.end.toLocalDate().minusDays(1)
            else maxOf(e.end.toLocalDate(), e.start.toLocalDate())
            var d = first
            while (d <= minOf(last, to)) {
                add(d)
                d = d.plusDays(1)
            }
        }
    }

    /** その予定が明日のものか(画面で時刻の前に「明日」を付ける)。 */
    fun isTomorrow(event: CalendarEvent, now: LocalDateTime): Boolean =
        event.start.toLocalDate() == now.toLocalDate().plusDays(1)
}
