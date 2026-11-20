package com.foldberry.launcher.ui

import android.Manifest
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.foldberry.launcher.data.CalendarEvent
import com.foldberry.launcher.data.CalendarEvents
import com.foldberry.launcher.data.CalendarSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [from] から [toExclusive] までの予定(次の予定・カレンダーの点。D26)。未許可なら null。
 * 予定が変わったとき(カレンダーの変更通知)・許可が変わったとき(StatusPermissionEvents)に読み直す。
 * 日付・表示月が変わったら呼ぶ側の引数が変わって読み直しになる。
 */
@Composable
fun rememberCalendarEvents(from: LocalDate, toExclusive: LocalDate): State<List<CalendarEvent>?> {
    val context = LocalContext.current
    val permissionTick by StatusPermissionEvents.tick.collectAsState()
    var changed by remember { mutableIntStateOf(0) }
    val allowed = remember(permissionTick) { CalendarSource.isAllowed(context) }
    DisposableEffect(context, allowed) {
        // 未許可のまま監視を始めると端末によっては SecurityException になるので、許可があるときだけ監視する。
        if (!allowed) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { changed++ }
        }
        val registered = try {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            true
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "カレンダーの変更を監視できません", e)
            false
        }
        onDispose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }
    // 初期値は許可の有無で分ける: 許可済みなら読込み中は空(「予定を表示」の案内を一瞬出さない。2026-11-19 refuter の指摘)。
    return produceState(if (allowed) emptyList() else null, from, toExclusive, allowed, changed) {
        value = withContext(Dispatchers.IO) { CalendarSource.query(context, from, toExclusive) }
    }
}

/**
 * 次の予定(今から明日の終わりまでの直近 2 件。D26)。未許可なら null。今日・明日の分を日付ごとに読み、分ごとに [now] で絞り直す。
 */
@Composable
fun rememberUpcomingEvents(now: LocalDateTime): List<CalendarEvent>? {
    val today = now.toLocalDate()
    val events by rememberCalendarEvents(today, today.plusDays(2))
    return events?.let { CalendarEvents.upcoming(it, now) }
}

/**
 * カレンダーの読み取りの許可を求める(予定の場所のタップ。§10)。断られたあと、もう確認を出せない(2 回断られた)ときはアプリ情報の画面を開く。
 * 結果は StatusPermissionEvents で知らせ、rememberCalendarEvents が読み直す。
 */
@Composable
fun rememberCalendarPermissionRequest(): () -> Unit {
    val request = rememberPermissionRequest(Manifest.permission.READ_CALENDAR, deniedKey = "calendar_permission_denied") {
        StatusPermissionEvents.changed()
    }
    return { request() }
}
