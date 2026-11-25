package com.foldberry.launcher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.graphics.Typeface
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.foldberry.launcher.R
import com.foldberry.launcher.data.NetworkGeneration
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.data.OngoingKind
import androidx.compose.runtime.collectAsState
import com.foldberry.launcher.ui.icons.FoldBerryIcons

private val StatusText = Color(0xE0FFFFFF)

/**
 * 自前描画のステータス行(仕様書 §4.1 / D14)。左=ロゴタイプ、右=Bluetooth・マナーモード・Wi-Fi・電波(SIM ごと)・電池・充電中(充電中だけ)。
 * 時刻は画面部の大時計(M5)で出すので省略する。許可が無い項目は出さない(§10。2026-11-17 改訂)。
 * 通知数は時計の日付の右端へ種類別に移した(§4.2。2026-11-17 改訂)。
 *
 * メイン横向きでは、本体側は [logo] だけ、右の枠の上端は [indicators] だけにして左右で 1 本に見せる
 * (帯はヒンジで分かれるので折り目をまたがない §3.3。2026-11-18 オーナー決定)。
 */
@Composable
fun StatusRow(modifier: Modifier, logo: Boolean = true, indicators: Boolean = true) {
    val openPanel = rememberOpenNotificationPanel()
    val swipeThreshold = with(LocalDensity.current) { SwipeOpenThreshold.toPx() }
    // 行のタップ・行からの下スワイプで通知パネルを開く(§4.1 / U5。2026-11-18 追加)。
    // システムのバーは「スワイプで一時表示」なので、OS 任せだと 2 回のスワイプが要るための近道。
    // 押した跡(リップル)は出さない: 本物のステータスバーと同じく、見た目は変えずにパネルだけ出す。
    val gestures = Modifier
        .pointerInput(openPanel, swipeThreshold) {
            var pulled = 0f
            var opened = false
            detectVerticalDragGestures(
                onDragStart = { pulled = 0f; opened = false },
                onVerticalDrag = { change, dy ->
                    change.consume()
                    pulled += dy
                    // 1 回のスワイプで 1 回だけ開く(動かし続けても呼び直さない)。
                    if (!opened && pulled > swipeThreshold) {
                        opened = true
                        openPanel()
                    }
                },
            )
        }
        .clickable(
            interactionSource = null,
            indication = null,
            onClickLabel = stringResource(R.string.status_open_notifications),
            onClick = openPanel,
        )
    Row(
        modifier.then(gestures).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        // アイコン群だけのときは右寄せ(ロゴだけのときは左寄せになる)。
        horizontalArrangement = if (logo) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        if (logo) {
            // 継続表示はロゴの後ろ(右のアイコン群ではなく。2026-11-25 オーナー指定)。メイン横では本体側に出る。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Logotype()
                OngoingIndicators()
            }
        }
        // データ源(電池・電波・BT など)の登録はアイコン群を出す側だけで行う(左右に分けたとき 2 重に登録しない)。
        if (indicators) StatusIndicators()
    }
}

/**
 * ロゴの後ろに出す継続表示(消せない常駐通知。§4.1。2026-11-25)。接続中・進行中・その他の常駐を、出ている種類のアイコンだけ出す。
 * 件数は出さない。接続中は通知(Windows にリンクなど)か VPN の回線で出す。通知へのアクセスが無ければ VPN だけ。
 */
@Composable
private fun OngoingIndicators() {
    val notices by NotificationCounter.ongoing.collectAsState()
    val vpn by rememberVpnActive()
    val kinds = notices.orEmpty() + (if (vpn) setOf(OngoingKind.CONNECTION) else emptySet())
    if (kinds.isEmpty()) return
    Row(
        Modifier.padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        OngoingKind.entries.filter { it in kinds }.forEach { kind ->
            when (kind) {
                OngoingKind.CONNECTION -> StatusIcon(FoldBerryIcons.Status.OngoingConnection, stringResource(R.string.status_ongoing_connection))
                OngoingKind.ACTIVITY -> StatusIcon(FoldBerryIcons.Status.OngoingActivity, stringResource(R.string.status_ongoing_activity))
                OngoingKind.SERVICE -> StatusIcon(FoldBerryIcons.Status.OngoingService, stringResource(R.string.status_ongoing_service))
            }
        }
    }
}

/** ステータス行の右側のアイコン群。 */
@Composable
private fun StatusIndicators() {
    val battery by rememberBattery()
    val network by rememberDefaultNetwork()
    val bluetooth by rememberBluetoothStatus()
    val ringer by rememberRingerStatus()
    val sims by rememberSimSignals()
    val alarm by rememberNextAlarmSet()
    // カバー画面は約 360dp 幅なので、6 項目が収まるよう間隔は 5dp に詰める。
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // 次のアラームの設定があれば左端にアイコンだけ(時刻は出さない。白。D26。2026-11-19 オーナー決定)。
        if (alarm) StatusIcon(FoldBerryIcons.Status.Alarm, stringResource(R.string.status_alarm))
        when (bluetooth) {
            BluetoothStatus.ON -> StatusIcon(FoldBerryIcons.Status.Bluetooth, stringResource(R.string.status_bluetooth_on), dim = true)
            // 接続中は左右に点の付いた接続中のアイコンで明るく(2026-11-17 オーナー指定。オンだけなら灰色のルーン)。
            BluetoothStatus.CONNECTED -> StatusIcon(FoldBerryIcons.Status.BluetoothConnected, stringResource(R.string.status_bluetooth_connected))
            BluetoothStatus.OFF, null -> Unit
        }
        when (ringer) {
            RingerStatus.VIBRATE -> StatusIcon(FoldBerryIcons.Status.Vibrate, stringResource(R.string.status_vibrate))
            RingerStatus.SILENT -> StatusIcon(FoldBerryIcons.Status.Silent, stringResource(R.string.status_silent))
            RingerStatus.NORMAL -> Unit
        }
        // Wi-Fi は接続中だけ出す(2026-11-17 改訂。旧: 未接続は薄く)。
        if (network.wifi) WifiIcon(network.wifiGeneration)
        // モバイル通信を使っているときだけ、データ通信の SIM の電波に世代を添える。
        sims.forEach { sim -> SignalBars(sim, numbered = sims.size > 1, generation = sim.generation.takeIf { network.cellular && sim.dataActive }) }
        battery?.let { b ->
            // 幅は常に最大の「100%」分を取り、右(電池側)寄せにする。桁数が変わってもアイコン群が左右に動かないように(2026-11-18 オーナー要望)。
            val percentStyle = TextStyle(color = StatusText, fontSize = 12.sp, textAlign = TextAlign.End)
            val measurer = rememberTextMeasurer()
            val percentWidth = with(LocalDensity.current) {
                remember(measurer, percentStyle) { measurer.measure("100%", percentStyle).size.width }.toDp()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("${b.percent}%", Modifier.width(percentWidth), style = percentStyle)
                Spacer(Modifier.width(4.dp))
                BatteryIcon(b.percent, b.charging)
            }
        }
    }
}

/**
 * ステータス行のアイコンの描画部の高さ(電波バー・電池と同じ)。FoldBerryIcons.Status は中身ぎりぎりまで詰めてあるので、
 * 高さをこれにして幅は縦横比で決めると、どのアイコンも描かれている部分が 11dp にそろう(2026-11-18 オーナー指摘)。
 */
private val StatusIconHeight = 11.dp

/** ステータス行から下へこれだけ引いたら通知パネルを開く(§4.1。2026-11-18)。 */
private val SwipeOpenThreshold = 16.dp

@Composable
private fun StatusIcon(icon: ImageVector, description: String, dim: Boolean = false) {
    Image(
        icon, contentDescription = description,
        modifier = Modifier.size(width = StatusIconHeight * (icon.viewportWidth / icon.viewportHeight), height = StatusIconHeight),
        colorFilter = ColorFilter.tint(if (dim) StatusText.copy(alpha = 0.45f) else StatusText),
    )
}

/**
 * アンテナに添える数字(Wi-Fi の世代「6」・電波の世代「5G」・SIM 番号「1」)の大きさ。種類を問わず同じ高さにそろえる
 * (2026-11-18 オーナー指定。旧: 世代 7sp・SIM 番号 8dp)。アイコンの枠は dp 固定なので、文字の大きさの設定に左右されない dp で持つ。
 */
private val AntennaDigitSize = 7.5.dp

/** 数字の上端の位置。11dp の枠の上端から上へはみ出させる量(3 種類とも同じ。2026-11-18 オーナー指定「上にそろえる」)。 */
private val AntennaDigitRise = 1.dp

/** 数字とアイコンの間の隙間(数字のまわりを抜く幅)。 */
private val AntennaDigitGap = 0.8.dp

/**
 * アンテナの数字の輪郭。原点はベースライン上で、横は [align] の位置(左端・中央・右端)。
 * 3 種類とも同じ書体・大きさの輪郭を同じベースラインに置くので、上端も高さもそろう。
 * 輪郭は抜きと描画の両方に使う(TextLayoutResult.getPathForRange は選択範囲の四角で文字の形にならず、
 * drawText に BlendMode.Clear を渡しても実機では抜けなかった 2026-11-18)。
 */
private class AntennaDigit(val path: Path, val capHeight: Float) {
    val bounds = path.getBounds()
}

@Composable
private fun rememberAntennaDigit(text: String?, align: android.graphics.Paint.Align): AntennaDigit? {
    val density = LocalDensity.current
    return text?.let {
        remember(it, align, density) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = with(density) { AntennaDigitSize.toPx() }
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = align
            }
            // 数字の高さ(上端が平らな「1」で測る)。ベースラインの位置決めに使う。
            val cap = android.graphics.Rect().also { r -> paint.getTextBounds("1", 0, 1, r) }.let { r -> -r.top.toFloat() }
            AntennaDigit(android.graphics.Path().also { p -> paint.getTextPath(it, 0, it.length, 0f, 0f, p) }.asComposePath(), cap)
        }
    }
}

/** 数字のベースライン(この描画範囲の上から)。描画範囲はステータス行の中で縦中央に置かれるので、中心から決める。 */
private fun DrawScope.antennaBaseline(digit: AntennaDigit): Float =
    size.height / 2f - (StatusIconHeight / 2 + AntennaDigitRise).toPx() + digit.capHeight

/** Wi-Fi の扇形。世代が分かれば右上に添える(2026-11-17 追加)。 */
@Composable
private fun WifiIcon(generation: String?) {
    val description = if (generation != null) stringResource(R.string.status_wifi_on_gen, generation) else stringResource(R.string.status_wifi_on)
    val digit = rememberAntennaDigit(generation, android.graphics.Paint.Align.RIGHT)
    Image(
        FoldBerryIcons.Wifi, contentDescription = null,
        modifier = Modifier
            .semantics { contentDescription = description }
            .size(width = 16.dp, height = 12.dp)
            // 世代の文字は扇形の右上の角に重ね、文字のまわりだけ扇形を抜いて「アイコンが欠けた」ように見せる
            // (2026-11-17 オーナー指定。One UI 風)。文字は抜いた層の外で描く(層の中だと文字ごと抜けるため)。
            .drawWithContent {
                drawContent()
                digit?.let { translate(left = size.width, top = antennaBaseline(it)) { drawPath(it.path, StatusText) } }
            }
            // 抜く処理は別の層で行う(同じ層だと背景まで透明に抜ける)。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                digit?.let {
                    val gap = WifiDigitGap.toPx()
                    val b = it.bounds
                    drawRoundRect(
                        Color.Black,
                        topLeft = Offset(size.width + b.left - gap, antennaBaseline(it) + b.top - gap),
                        size = Size(b.width + gap * 2, b.height + gap * 2),
                        cornerRadius = CornerRadius(gap * 2),
                        blendMode = BlendMode.Clear,
                    )
                }
            },
        colorFilter = ColorFilter.tint(StatusText),
    )
}

/** Wi-Fi の世代と扇形の間の隙間(扇形を四角く抜く幅)。 */
private val WifiDigitGap = 1.dp

/**
 * 電波 4 本バー。SIM が 2 枚以上のときはスロット番号(1 始まり)をバーの横の中央に重ね、番号の形に沿ってまわりのバーを抜く
 * (2026-11-18 オーナー指定。白いバーの上でも薄いバーの上でも番号が消えない。docs/mock/status-mock.html)。
 * [generation](5G/4G/3G)があればバーの左に添える(2026-11-17 追加。モバイル通信中のデータ通信の SIM だけ)。
 * 番号・世代の縦の位置は Wi-Fi の世代と同じ(上端をそろえる。2026-11-18 オーナー指定)。
 */
@Composable
private fun SignalBars(sim: SimSignal, numbered: Boolean, generation: String?) {
    val description = if (generation != null) {
        stringResource(R.string.status_signal_gen, sim.slot + 1, sim.level, generation)
    } else {
        stringResource(R.string.status_signal, sim.slot + 1, sim.level)
    }
    val slot = rememberAntennaDigit(if (numbered) (sim.slot + 1).toString() else null, android.graphics.Paint.Align.CENTER)
    val gen = rememberAntennaDigit(generation, android.graphics.Paint.Align.LEFT)
    val density = LocalDensity.current
    Row(Modifier.semantics { contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
        gen?.let {
            Canvas(Modifier.size(width = with(density) { it.bounds.right.toDp() }, height = StatusIconHeight)) {
                translate(top = antennaBaseline(it)) { drawPath(it.path, StatusText) }
            }
            Spacer(Modifier.width(1.dp))
        }
        // 番号のまわりを抜く処理は別の層で行う(同じ層だと背景まで透明に抜ける。Wi-Fi の世代と同じ)。
        // 番号そのものは層の外で描く(層は枠の外を切り取るので、枠の上へはみ出す番号の上端が欠ける 2026-11-18 実機で確認)。
        Canvas(
            Modifier
                .size(width = 13.dp, height = StatusIconHeight)
                .drawWithContent {
                    drawContent()
                    slot?.let { translate(left = size.width / 2f, top = antennaBaseline(it)) { drawPath(it.path, StatusText) } }
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val gap = 1.dp.toPx()
            val barW = (size.width - gap * 3) / 4
            for (i in 0 until 4) {
                val h = size.height * (i + 1) / 4f
                drawRoundRect(
                    if (i < sim.level) StatusText else StatusText.copy(alpha = 0.25f),
                    topLeft = Offset(i * (barW + gap), size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(0.6.dp.toPx()),
                )
            }
            slot?.let {
                translate(left = size.width / 2f, top = antennaBaseline(it)) {
                    drawPath(it.path, Color.Black, blendMode = BlendMode.Clear)
                    drawPath(it.path, Color.Black, style = Stroke(AntennaDigitGap.toPx() * 2, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
                }
            }
        }
    }
}

@Composable
private fun Logotype() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // マークは白+紫の 2 色なので tint しない(U3 改訂 2026-11-16)。
        // 枠 15dp で描画部(48 枠の y 6〜42)が 11.25dp になり、右のアイコン(11dp)とほぼそろう(2026-11-18 オーナー確認。旧 14dp は 10.5dp)。
        Image(FoldBerryIcons.Mark, contentDescription = null, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Fold") }
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Berry") }
            },
            style = TextStyle(color = StatusText, fontSize = 12.sp),
        )
    }
}

@Composable
private fun BatteryIcon(pct: Int, charging: Boolean) {
    val bolt = remember { FoldBerryIcons.Status.chargingBolt() }
    val chargingLabel = stringResource(R.string.status_charging)
    Canvas(
        Modifier
            .size(width = 22.dp, height = 11.dp)
            .then(if (charging) Modifier.semantics { contentDescription = chargingLabel } else Modifier)
            // 稲妻のまわりを抜く処理は別の層で行う(同じ層だと背景まで透明に抜ける。電波の SIM 番号と同じ)。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val body = Size(size.width - 2.dp.toPx(), size.height)
        val r = CornerRadius(2.dp.toPx())
        drawRoundRect(StatusText.copy(alpha = 0.8f), size = body, cornerRadius = r, style = Stroke(1.2.dp.toPx()))
        drawRoundRect(
            StatusText.copy(alpha = 0.8f),
            topLeft = Offset(body.width + 0.4.dp.toPx(), size.height * 0.3f),
            size = Size(1.6.dp.toPx(), size.height * 0.4f),
            cornerRadius = CornerRadius(0.6.dp.toPx()),
        )
        val pad = 2.dp.toPx()
        val fullW = body.width - pad * 2
        drawRoundRect(
            StatusText,
            topLeft = Offset(pad, pad),
            size = Size(fullW * pct.coerceIn(0, 100) / 100f, body.height - pad * 2),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        // 充電中は電池の本体の中央に稲妻を重ね、稲妻の形に沿ってまわりを抜く
        // (2026-11-18 オーナー要望。旧: 電池の右に別アイコン)。抜くので残量の白い部分の上でも空の部分の上でも見える。
        if (charging) {
            val h = body.height - 2.dp.toPx()
            val s = h / 21f // 稲妻の枠は 13.5×21(FoldBerryIcons.Status.chargingBolt)
            translate(left = (body.width - h * FoldBerryIcons.Status.CHARGING_ASPECT) / 2f, top = (body.height - h) / 2f) {
                scale(s, pivot = Offset.Zero) {
                    drawPath(bolt, Color.Black, style = Stroke(BoltGap.toPx() * 2 / s, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
                    // 満充電(100%)はロゴのマークと同じ紫にする(2026-11-18 オーナー要望)。
                    drawPath(bolt, if (pct >= 100) FoldBerryIcons.MarkPurple else StatusText)
                }
            }
        }
    }
}

/** 電池の中の稲妻とまわりの間の隙間(抜く幅)。 */
private val BoltGap = 0.8.dp

private data class BatteryState(val percent: Int, val charging: Boolean)

/**
 * 電池残量(%)と充電中か。ACTION_BATTERY_CHANGED は権限不要。破棄時に受信を解除する。
 * 充電中 = 状態が CHARGING、または電源につながったまま満充電(FULL)。
 */
@Composable
private fun rememberBattery(): State<BatteryState?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<BatteryState?>(null) }
    DisposableEffect(context) {
        fun percent(intent: Intent): BatteryState? {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || (plugged && status == BatteryManager.BATTERY_STATUS_FULL)
            return BatteryState(level * 100 / scale, charging)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = percent(intent)
            }
        }
        // システムの放送は NOT_EXPORTED でも届く。登録時に直近の値(sticky)が返る。
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { state.value = percent(it) }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * 既定のネットワーク(今の通信に使っている回線)。wifiGeneration は Wi-Fi の世代(NetworkGeneration.wifi。不明なら null)。
 * どちらでもない(機内モード・データ オフ)ときは wifi・cellular とも偽。
 */
private data class DefaultNetwork(val wifi: Boolean = false, val wifiGeneration: String? = null, val cellular: Boolean = false)

private fun NetworkCapabilities?.toDefaultNetwork(): DefaultNetwork {
    if (this == null) return DefaultNetwork()
    val wifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    // 規格と周波数は位置情報で伏せられる項目ではないので、ACCESS_NETWORK_STATE だけで読める(実機の 11ax で「6」を確認 2026-11-17)。
    // 読めなければ規格が不明=数字なしの扇形に落ちる(誤った数字は出さない)。
    val info = if (wifi) transportInfo as? WifiInfo else null
    return DefaultNetwork(
        wifi = wifi,
        wifiGeneration = info?.let { NetworkGeneration.wifi(it.wifiStandard, it.frequency) },
        cellular = !wifi && hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
    )
}

/** 既定のネットワークが Wi-Fi かモバイル通信か。ACCESS_NETWORK_STATE(通常権限・許可ダイアログなし。§10)。 */
@Composable
private fun rememberDefaultNetwork(): State<DefaultNetwork> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(DefaultNetwork()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        state.value = cm.getNetworkCapabilities(cm.activeNetwork).toDefaultNetwork()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                state.value = caps.toDefaultNetwork()
            }

            override fun onLost(network: Network) {
                state.value = DefaultNetwork()
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return state
}
