package com.foldberry.launcher.ui

import android.Manifest
import android.app.AlarmManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.foldberry.launcher.data.NetworkGeneration
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * 自前ステータス行(§4.1)の Bluetooth・マナーモード・電波のデータ源。
 * どれも「DisposableEffect で登録し、破棄時に解除する」形で、StatusRow の電池・Wi-Fi と同じ作り。
 * 権限が無い・断られた項目は null / 空にして表示しない(§10)。
 */

/**
 * 実行時の許可が変わったかもしれない合図。MainActivity が許可ダイアログの結果を受けたとき・画面に戻ったとき
 * (アプリ情報画面で許可を変えて戻った場合)に進める。値が変わると権限を使う登録をやり直す。
 */
object StatusPermissionEvents {
    val tick = MutableStateFlow(0)

    fun changed() {
        tick.value++
    }
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

enum class BluetoothStatus { OFF, ON, CONNECTED }

/** Bluetooth の状態。アダプタが無い・許可が無いときは null(表示しない)。 */
@Composable
fun rememberBluetoothStatus(): State<BluetoothStatus?> {
    val context = LocalContext.current
    val tick by StatusPermissionEvents.tick.collectAsState()
    val state = remember { mutableStateOf<BluetoothStatus?>(null) }
    DisposableEffect(context, tick) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !context.granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            state.value = null
            return@DisposableEffect onDispose {}
        }
        fun read(): BluetoothStatus? = try {
            when {
                !adapter.isEnabled -> BluetoothStatus.OFF
                // ヘッドセット・音楽・LE Audio のどれかがつながっていれば「接続中」。
                AUDIO_PROFILES.any { adapter.getProfileConnectionState(it) == BluetoothAdapter.STATE_CONNECTED } ->
                    BluetoothStatus.CONNECTED
                else -> BluetoothStatus.ON
            }
        } catch (e: SecurityException) {
            // 確認した直後に許可を外された場合。表示を消して次の合図を待つ。
            Log.w(LOG_TAG, "Bluetooth の状態を読めません", e)
            null
        }
        state.value = read()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = read()
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        // ACTION_CONNECTION_STATE_CHANGED は Bluetooth アプリ(uid 1002)が送るため、NOT_EXPORTED では届かない。
        // 受けたら実際の状態を読み直すだけ(通知の中身は使わない)なので、他アプリが偽の通知を送っても表示は狂わない。
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

private val AUDIO_PROFILES = listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP, BluetoothProfile.LE_AUDIO)

enum class RingerStatus { NORMAL, VIBRATE, SILENT }

/** マナーモード(着信音の状態)。AudioManager は権限不要。 */
@Composable
fun rememberRingerStatus(): State<RingerStatus> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(RingerStatus.NORMAL) }
    DisposableEffect(context) {
        val audio = context.getSystemService(AudioManager::class.java)
        fun read() = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_VIBRATE -> RingerStatus.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> RingerStatus.SILENT
            else -> RingerStatus.NORMAL
        }
        state.value = read()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = read()
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * 次のアラームの設定があるか(ステータス行のアラームのアイコン。D26。2026-11-19)。AlarmManager は権限不要。
 * 時計アプリ以外が設定したアラームも含む(端末のステータスバーと同じ)。設定が変わると ACTION_NEXT_ALARM_CLOCK_CHANGED が届く。
 */
@Composable
fun rememberNextAlarmSet(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        fun read() = alarms?.nextAlarmClock != null
        state.value = read()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state.value = read()
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

/**
 * VPN に接続中か(ステータス行の継続表示「接続中」。§4.1。2026-11-25)。ACCESS_NETWORK_STATE だけで読める。
 * 通知では見ない: 実機では OS の VPN 通知が「常時接続 VPN から切断」の警告で、Tailscale の通知は常駐フラグなしだった。
 * 既定のネットワークではなく VPN の回線そのものを数える(分割トンネルで FoldBerry が VPN の外でも出すため)。
 */
@Composable
fun rememberVpnActive(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val vpns = mutableSetOf<Network>()
        val callback = object : ConnectivityManager.NetworkCallback() {
            // コールバックは ConnectivityManager の 1 本のスレッドで順に呼ばれる。
            override fun onAvailable(network: Network) {
                vpns += network
                state.value = true
            }

            override fun onLost(network: Network) {
                vpns -= network
                state.value = vpns.isNotEmpty()
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            // 既定は自分の UID が使える回線だけ。FoldBerry を外した VPN(分割トンネル)も数えるため他の UID の回線も含める。
            .setIncludeOtherUidNetworks(true)
            .build()
        cm.registerNetworkCallback(request, callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return state
}

/**
 * SIM 1 枚分の電波。slot は 0 始まりの SIM スロット、level は 0〜4(SignalStrength.getLevel)。
 * generation は今の通信方式の世代(5G/4G/3G。NetworkGeneration.mobile)、dataActive はモバイルデータ通信に使う SIM か(2026-11-17 追加)。
 */
data class SimSignal(val slot: Int, val level: Int, val generation: String? = null, val dataActive: Boolean = false)

/**
 * 有効な SIM ごとの電波(スロット順)。許可が無ければ空(表示しない)。
 * SIM の抜き差し・有効化の切り替えは購読し直して追う(同じスロットで SIM が変わっても古い購読を残さない)。
 * 世代は DisplayInfoListener(5G NSA の上書きも届く)、データ通信の SIM は ActiveDataSubscriptionIdListener で追う。
 */
@Composable
fun rememberSimSignals(): State<List<SimSignal>> {
    val context = LocalContext.current
    val tick by StatusPermissionEvents.tick.collectAsState()
    val state = remember { mutableStateOf(emptyList<SimSignal>()) }
    DisposableEffect(context, tick) {
        if (!context.granted(Manifest.permission.READ_PHONE_STATE)) {
            state.value = emptyList()
            return@DisposableEffect onDispose {}
        }
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val executor = ContextCompat.getMainExecutor(context)
        val registered = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()

        fun unregisterAll() {
            registered.forEach { (tm, cb) -> tm.unregisterTelephonyCallback(cb) }
            registered.clear()
        }

        fun update(slot: Int, change: (SimSignal) -> SimSignal) {
            state.value = state.value.map { if (it.slot == slot) change(it) else it }
        }

        // スロット → 購読 ID。データ通信の SIM は購読 ID で届くので、表示中の各スロットに当てはめる。
        var slotOfSub = emptyMap<Int, Int>()
        fun setDataSub(subId: Int) {
            val dataSlot = slotOfSub[subId]
            state.value = state.value.map { it.copy(dataActive = it.slot == dataSlot) }
        }

        fun rebuild() {
            unregisterAll()
            val subs = try {
                subscriptions.activeSubscriptionInfoList.orEmpty().sortedBy { it.simSlotIndex }
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "SIM の一覧を読めません", e)
                state.value = emptyList()
                return
            }
            slotOfSub = subs.associate { it.subscriptionId to it.simSlotIndex }
            val dataSlot = slotOfSub[SubscriptionManager.getActiveDataSubscriptionId()]
            // 先に一覧を置いてから購読する(登録直後に届く値を update が当てはめられるように)。
            state.value = subs.map { sub ->
                val tm = telephony.createForSubscriptionId(sub.subscriptionId)
                SimSignal(sub.simSlotIndex, tm.signalStrength?.level ?: 0, dataActive = sub.simSlotIndex == dataSlot)
            }
            subs.forEachIndexed { index, sub ->
                val tm = telephony.createForSubscriptionId(sub.subscriptionId)
                val slot = sub.simSlotIndex
                val cb = if (index == 0) {
                    // データ通信の SIM の切り替えは端末で 1 つなので、1 つ目の SIM の購読でだけ受ける。
                    object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DisplayInfoListener,
                        TelephonyCallback.ActiveDataSubscriptionIdListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = update(slot) { it.copy(level = signalStrength.level) }
                        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) = update(slot) { it.copy(generation = info.generation()) }
                        override fun onActiveDataSubscriptionIdChanged(subId: Int) = setDataSub(subId)
                    }
                } else {
                    object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DisplayInfoListener {
                        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = update(slot) { it.copy(level = signalStrength.level) }
                        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) = update(slot) { it.copy(generation = info.generation()) }
                    }
                }
                try {
                    tm.registerTelephonyCallback(executor, cb)
                    registered += tm to cb
                } catch (e: SecurityException) {
                    // DisplayInfoListener は READ_PHONE_STATE が要る。確認の直後に外された場合は落とさず、
                    // そのスロットは置いた初期値のバーのまま(更新は止まる)。次の許可の合図で購読し直す。
                    Log.w(LOG_TAG, "SIM の購読に失敗しました", e)
                }
            }
        }

        val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
            override fun onSubscriptionsChanged() = rebuild()
        }
        rebuild()
        subscriptions.addOnSubscriptionsChangedListener(executor, listener)
        onDispose {
            subscriptions.removeOnSubscriptionsChangedListener(listener)
            unregisterAll()
        }
    }
    return state
}

private fun TelephonyDisplayInfo.generation(): String? = NetworkGeneration.mobile(networkType, overrideNetworkType)
