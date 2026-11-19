package com.foldberry.launcher.ui.screen

import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.CalendarEvent
import com.foldberry.launcher.data.CalendarEvents
import com.foldberry.launcher.data.ClockLanguage
import com.foldberry.launcher.data.ClockText
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.ui.LOG_TAG
import com.foldberry.launcher.ui.LineIcon
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.rememberCalendarPermissionRequest
import com.foldberry.launcher.ui.rememberUpcomingEvents
import java.time.LocalDateTime

/** 横・縦の音楽と次の予定の札の見た目(Hub の札と同じ暗い半透明。D26)。 */
internal val WidgetShape = RoundedCornerShape(8.dp)
internal val WidgetColor = Color(0x9E12141A)
internal val WidgetBorder = Color(0x14FFFFFF)

/** 札の共通の枠(暗い半透明・細い縁)。 */
internal fun Modifier.widgetCard(): Modifier = clip(WidgetShape).background(WidgetColor, WidgetShape).border(1.dp, WidgetBorder, WidgetShape)

/**
 * 横の次の予定の札(本体の画面部の T1・T2 の段の上半分。D26。2026-11-19 オーナー決定。モック docs/mock/tiles-mock.html)。
 * 見出し「次の予定」と、今から明日の終わりまでの直近 2 件(左に色の線・時刻・件名)。無ければ「今日と明日の予定はありません」。
 * 未許可なら「予定を表示」(タップでカレンダーの読み取りの許可を求める)。札のタップでカレンダーアプリを開く。
 */
@Composable
fun NextEventsCard(now: LocalDateTime, hourFormat: HourFormat, modifier: Modifier = Modifier) {
    val events = rememberUpcomingEvents(now)
    val request = rememberCalendarPermissionRequest()
    val context = LocalContext.current
    Column(
        modifier
            .widgetCard()
            .clickable { if (events == null) request() else openCalendarApp(context) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineIcon(FoldBerryIcons.Line.Calendar, Color(0x8CFFFFFF), 13.dp)
            Spacer(Modifier.width(6.dp))
            BasicText(stringResource(R.string.events_title), style = TextStyle(color = Color(0x8CFFFFFF), fontSize = 11.5.sp))
        }
        when {
            events == null -> {
                BasicText(stringResource(R.string.events_permission), style = TextStyle(color = SheetAccent, fontSize = 13.sp))
                BasicText(stringResource(R.string.events_permission_desc), style = TextStyle(color = Color(0x80FFFFFF), fontSize = 11.5.sp))
            }
            events.isEmpty() -> BasicText(stringResource(R.string.events_none), style = TextStyle(color = Color(0x80FFFFFF), fontSize = 13.sp))
            else -> events.forEachIndexed { i, e ->
                Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(EventBar[i % EventBar.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(8.dp))
                    BasicText(eventTime(e, now, hourFormat), style = TextStyle(color = SheetAccent, fontSize = 13.sp))
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        e.title.ifEmpty { "—" }, Modifier.weight(1f),
                        style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 縦の時計の行の日付の右に出す次の予定(2 行。D26)。予定が無ければ何も出さない。
 * 幅が足りなければ 件名を省いて時刻だけ → それも入らなければ出さない(日付と通知を削らない)。
 * 未許可なら小さく「予定を表示」(タップで許可を求める)。
 */
@Composable
fun NextEventsLines(now: LocalDateTime, hourFormat: HourFormat, modifier: Modifier = Modifier) {
    val events = rememberUpcomingEvents(now)
    val request = rememberCalendarPermissionRequest()
    val context = LocalContext.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        // 線とアイコンで約 44dp、時刻は「明日 午後10:30」で約 110dp。時刻が収まらなければ出さず(折り返して行からはみ出さないように)、
        // 件名まで出すのは 200dp 以上あるとき。
        if (maxWidth < 156.dp) return@BoxWithConstraints
        val withTitle = maxWidth >= 200.dp
        when {
            events == null -> BasicText(
                stringResource(R.string.events_permission),
                Modifier.clickable { request() }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                style = TextStyle(color = SheetAccent.copy(alpha = 0.8f), fontSize = 12.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            events.isEmpty() -> Unit
            else -> Row(Modifier.clickable { openCalendarApp(context) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = 12.dp, end = 10.dp).width(1.dp).height(22.dp).background(Color(0x2EFFFFFF)))
                LineIcon(FoldBerryIcons.Line.Calendar, Color(0xB3FFFFFF), 15.dp)
                Spacer(Modifier.width(6.dp))
                Column {
                    events.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicText(eventTime(e, now, hourFormat), style = TextStyle(color = SheetAccent, fontSize = 13.5.sp), maxLines = 1, softWrap = false)
                            if (withTitle) {
                                Spacer(Modifier.width(6.dp))
                                BasicText(
                                    e.title.ifEmpty { "—" },
                                    style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 13.5.sp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 予定の時刻の表記。時計と同じ 12/24 時間表記。明日の予定は「明日 9:00」。 */
@Composable
private fun eventTime(e: CalendarEvent, now: LocalDateTime, hourFormat: HourFormat): String {
    val context = LocalContext.current
    val resources = LocalResources.current
    val language = rememberClockLanguage()
    val (t, marker) = ClockText.time(e.start.hour, e.start.minute, hourFormat.is24(DateFormat.is24HourFormat(context)), language)
    val time = when {
        marker == null -> t
        language == ClockLanguage.JA -> "$marker$t"
        else -> "$t $marker"
    }
    return if (CalendarEvents.isTomorrow(e, now)) resources.getString(R.string.events_tomorrow, time) else time
}

/** 予定の札の左の線の色(1 件目・2 件目)。 */
private val EventBar = listOf(SheetAccent, Color(0xFFF0A640))

/** 端末のカレンダーアプリを開く(今日の表示)。開けなければ何もしない。 */
private fun openCalendarApp(context: android.content.Context) {
    val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: android.content.ActivityNotFoundException) {
        Log.w(LOG_TAG, "カレンダーアプリを開けません", e)
    }
}
