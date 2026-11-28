package com.foldberry.launcher.ui.screen

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.ClockLanguage
import com.foldberry.launcher.data.ClockText
import com.foldberry.launcher.data.DateStyle
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.ui.AppLanguage
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/** 時計の表記の言語。画面の言語(AppLanguage)に合わせる。構成から読むので言語の切り替えに追従する。 */
@Composable
internal fun rememberClockLanguage(): ClockLanguage =
    if (LocalConfiguration.current.locales[0].language == AppLanguage.ENGLISH) ClockLanguage.EN else ClockLanguage.JA

/**
 * 画面部の大時計(仕様書 §4.2)。時:分と日付・曜日。12/24 時間表記は設定(hourFormat。既定は端末の設定に従う。v0.6.0)。
 * 日付・曜日・午前/午後は画面の言語で、日付の書式は設定(dateStyle)で出す(2026-11-17 改訂。旧: 英語に固定)。
 * 日付の行の右端に種類別の通知数を出す(NotificationBadges。2026-11-17 追加)。
 * showNotifications=false(設定の通知の知らせ方がトラックパッドだけ。2026-11-28)なら出さず、件数の行の高さも取らない。
 * 左半分のタップで端末の時計アプリを開く(固定機能、割り当て対象外)。
 *
 * @param height 時計の領域の高さ。文字の大きさはここから決める(端末の文字サイズ設定には左右されない)。
 */
@Composable
fun ClockBlock(
    now: LocalDateTime,
    height: Dp,
    dateStyle: DateStyle,
    hourFormat: HourFormat,
    modifier: Modifier = Modifier,
    showNotifications: Boolean = true,
) {
    val density = LocalDensity.current
    val (time, ampm, date) = rememberClockText(now, dateStyle, hourFormat)

    val timeSize = with(density) { (height * 0.68f).toSp() }
    val subSize = with(density) { (height * 0.17f).toSp() }
    val notifications by NotificationCounter.counts.collectAsState()
    Column(
        modifier
            .fillMaxWidth()
            .clockAppTap()
            .padding(horizontal = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(time, style = TextStyle(color = Color.White, fontSize = timeSize, fontWeight = FontWeight.ExtraLight))
            ampm?.let {
                BasicText(it, Modifier.padding(start = 6.dp, bottom = height * 0.1f), style = TextStyle(color = Color(0xC8FFFFFF), fontSize = subSize, fontWeight = FontWeight.Light))
            }
        }
        // 日付の行の右端に種類別の通知(§4.2)。未許可・未接続(null)なら出さず、件数の行の高さも取らない。
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // 最長は英語の年付き "Wednesday, September 30, 2026"。狭い画面部でも折り返してタイルに食い込まないよう 1 行に限る。
            // 通知と並んで幅が足りないときは日付のほうを省略する(通知のアイコンは削らない)。
            BasicText(
                date, Modifier.weight(1f),
                style = TextStyle(color = Color(0xC8FFFFFF), fontSize = subSize, fontWeight = FontWeight.Light),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            notifications?.takeIf { showNotifications }?.let { NotificationBadges(it, iconSize = height * 0.19f, modifier = Modifier.padding(start = 8.dp)) }
        }
    }
}

/**
 * 1 段の時計(メイン縦の上半分。D25。2026-11-19。モック portrait-mock.html の案 A)。
 * 左から 時刻(大)・日付、右端に種類別の通知(§4.2)。左半分のタップで時計アプリ(ClockBlock と同じ)。
 *
 * @param height 行の高さ。時刻の文字はこの 0.68 倍
 */
@Composable
fun ClockLine(
    now: LocalDateTime,
    height: Dp,
    dateStyle: DateStyle,
    hourFormat: HourFormat,
    modifier: Modifier = Modifier,
    showNotifications: Boolean = true,
) {
    val density = LocalDensity.current
    val (time, ampm, date) = rememberClockText(now, dateStyle, hourFormat)
    val timeSize = with(density) { (height * 0.68f).toSp() }
    val subSize = with(density) { (height * 0.27f).toSp() }
    val notifications by NotificationCounter.counts.collectAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clockAppTap()
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(time, style = TextStyle(color = Color.White, fontSize = timeSize, fontWeight = FontWeight.ExtraLight))
        ampm?.let {
            BasicText(it, Modifier.padding(start = 4.dp), style = TextStyle(color = Color(0xC8FFFFFF), fontSize = subSize, fontWeight = FontWeight.Light))
        }
        // 日付は 1 行で縮めない(最長の英語の年付きでも約 300dp。時刻と通知 4 種を足しても 704dp に収まる)。
        BasicText(
            date, Modifier.padding(start = 16.dp),
            style = TextStyle(color = Color(0xD8FFFFFF), fontSize = subSize, fontWeight = FontWeight.Light),
            maxLines = 1,
        )
        // 日付の右に次の予定 2 行(D26)。残りの幅に合わせて 件名 → 時刻だけ → 出さない の順に縮める。
        NextEventsLines(now, hourFormat, Modifier.weight(1f))
        notifications?.takeIf { showNotifications }?.let { NotificationBadges(it, iconSize = height * 0.3f, modifier = Modifier.padding(start = 8.dp)) }
    }
}

/** 時計の文字(時刻・午前/午後・日付)。ClockBlock と ClockLine で共通。 */
private data class ClockStrings(val time: String, val ampm: String?, val date: String)

@Composable
private fun rememberClockText(now: LocalDateTime, dateStyle: DateStyle, hourFormat: HourFormat): ClockStrings {
    val context = LocalContext.current
    // 12/24 の端末設定は分の描き直しを待たずに反映する(待つと切り替えてから最長 1 分は古い表記のまま)。
    // Settings.System の監視は権限不要(§10)。
    // 手動で 12/24 を選んでいるときも監視は続ける(端末に従うへ戻したとき、すぐ今の端末設定で出すため)。
    var systemIs24 by remember { mutableStateOf(DateFormat.is24HourFormat(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemIs24 = DateFormat.is24HourFormat(context) }
        }
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.TIME_12_24), false, observer)
        // 監視を始めるまでの間に変わっていた分も拾う。
        systemIs24 = DateFormat.is24HourFormat(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val language = rememberClockLanguage()
    val (time, ampm) = ClockText.time(now.hour, now.minute, hourFormat.is24(systemIs24), language)
    val date = ClockText.date(now.year, now.monthValue, now.dayOfMonth, now.dayOfWeek.value, dateStyle, language)
    return ClockStrings(time, ampm, date)
}

/**
 * 時計の左半分のタップで端末の時計アプリを開く(2026-11-17 オーナー指定)。右側の空き=通知の位置で時計アプリが開かないように。
 * 幅いっぱいの並びは崩さず、タップの位置で振り分ける。
 */
@Composable
private fun Modifier.clockAppTap(): Modifier {
    val context = LocalContext.current
    val resources = LocalResources.current
    return pointerInput(context, resources) {
        detectTapGestures { offset ->
            if (offset.x < size.width / 2f && !openClockApp(context)) {
                Toast.makeText(context, resources.getString(R.string.clock_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * 今の時刻(分単位で進む)。時計とカバーのカレンダーの「今日」で共有する(§4.2 / §4.3)。
 */
@Composable
fun rememberNow(): State<LocalDateTime> {
    val context = LocalContext.current
    // 分が変わるたびに描き直す。delay は端末のスリープ中に進まないので、それだけだとスリープ復帰の直後に
    // 最長 1 分(日をまたげば日付も)古い表示が残る。画面点灯・時刻/タイムゾーン/日付の変更の通知で
    // refresh を進め、ループを今の時刻から始め直す。他アプリ表示中も composition は残るためループは動き続ける。
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) { refresh++ }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        // システムからの通知は NOT_EXPORTED でも届く(他アプリからは受けない)。権限不要(§10)。
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return produceState(LocalDateTime.now(), refresh) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
}

/**
 * 端末の時計アプリを開く(§4.2)。開けたら true。
 * SHOW_ALARMS を直接投げると、Samsung の時計はその受け口に SET_ALARM 権限を要求していて SecurityException で落ちる
 * (v0.4.0 の実機で確認)。権限を増やさないよう(§10)、SHOW_ALARMS を受けるアプリを調べてランチャーと同じ起動で開く。
 * 通常の起動口が無いときだけ SHOW_ALARMS を試す。どの失敗でも落とさない。
 * 受け先が複数あり既定が決まっていないと resolveActivity は選択画面(パッケージ "android")を返すので、
 * 既定 → 受け先の一覧の順に、通常の起動口を持つ最初のアプリを開く。
 */
private fun openClockApp(context: Context): Boolean {
    val pm = context.packageManager
    val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
    val candidates = listOfNotNull(pm.resolveActivity(alarms, 0)?.activityInfo?.packageName) +
        pm.queryIntentActivities(alarms, 0).map { it.activityInfo.packageName }
    val launch = candidates.distinct().firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    return try {
        context.startActivity((launch ?: alarms).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
