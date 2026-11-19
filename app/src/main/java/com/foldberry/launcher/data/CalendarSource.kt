package com.foldberry.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 端末のカレンダーの予定を読む(次の予定・カレンダーの点。D26。2026-11-19)。許可は READ_CALENDAR(§10)。
 * 読むのは端末に同期されているカレンダーのうち、カレンダーアプリで表示しているものだけ(VISIBLE)。辞退した招待は除く。
 */
object CalendarSource {
    private const val TAG = "FoldBerry"

    fun isAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val PROJECTION = arrayOf(
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.SELF_ATTENDEE_STATUS,
    )

    /**
     * [from] の 0 時から [toExclusive] の 0 時までに掛かる予定(端末の時刻帯)。未許可なら null、許可はあるが読めなければ空。
     * 重いので画面のスレッドでは呼ばない(呼ぶ側が Dispatchers.IO で)。
     */
    fun query(context: Context, from: LocalDate, toExclusive: LocalDate): List<CalendarEvent>? {
        if (!isAllowed(context)) return null
        val zone = ZoneId.systemDefault()
        // 終日の予定は UTC の 0 時で入っているので、時刻帯がずれても取りこぼさないよう前後 1 日広げて問い合わせ、あとで日付で絞る。
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = toExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val rangeStart = from.atStartOfDay()
        val rangeEnd = toExclusive.atStartOfDay()
        return try {
            context.contentResolver.query(uri, PROJECTION, "${CalendarContract.Instances.VISIBLE} = 1", null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(4) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        val allDay = c.getInt(3) != 0
                        val startMs = c.getLong(1)
                        val endMs = c.getLong(2)
                        val (start, stop) = if (allDay) {
                            // 終日は UTC の日付をそのまま端末の日付として読む(時刻帯で前後の日にずれないように)。
                            utcDate(startMs).atStartOfDay() to utcDate(endMs).atStartOfDay()
                        } else {
                            local(startMs, zone) to local(endMs, zone)
                        }
                        if (stop <= rangeStart || start >= rangeEnd) continue
                        add(CalendarEvent(c.getString(0)?.trim().orEmpty(), start, maxOf(stop, start), allDay))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "カレンダーを読めません", e)
            null
        } catch (e: IllegalArgumentException) {
            // 許可はあるのに問い合わせに失敗: 「未許可」の案内を出さないよう空として返す(押しても直らない案内を出さない)。
            Log.w(TAG, "カレンダーの問い合わせに失敗しました", e)
            emptyList()
        }
    }

    private fun utcDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()

    private fun local(ms: Long, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
}
