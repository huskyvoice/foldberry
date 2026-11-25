package com.foldberry.launcher.data

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 時計の横に出す種類別の通知数(§4.2。2026-11-17 改訂、旧: ステータス行に合計だけ)。
 * 「通知へのアクセス」をオーナーが設定画面で許可したときだけ OS がつなぐ(§10)。
 * 件数はプロセス共有の StateFlow で出す(サービスと Activity は同じプロセス)。未接続・切断中は null=表示しない。
 */
class NotificationCounter : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = WeakReference(this)
        recount()
    }

    override fun onListenerDisconnected() {
        // 許可を外された・OS に切られた: 古い件数・一覧を残さない。
        connected = null
        clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = try {
            activeNotifications
        } catch (e: SecurityException) {
            // 接続直後・切断直後に呼ばれると投げることがある。表示を消しておく。
            Log.w(TAG, "通知を読めません", e)
            clear()
            return
        } ?: return
        // 既定の電話・SMS アプリはオーナーが変えられるので毎回取り直す(どちらも権限不要)。
        val dialer = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val sms = Telephony.Sms.getDefaultSmsPackage(this)
        val zone = ZoneId.systemDefault()
        val counted = active.filter(::counts)
        // Hub(§4.6)の一覧と時計の横の件数(§4.2)は同じ通知・同じ振り分けから作る(数が食い違わないように)。
        val items = counted.map { sbn ->
            val extras = sbn.notification.extras
            HubItem(
                key = sbn.key,
                packageName = sbn.packageName,
                kind = NotificationKinds.classify(sbn.packageName, sbn.notification.category, dialer, sms),
                // 会話(MessagingStyle)はグループ名が会話のタイトルに入る。無ければ通常のタイトル。
                title = HubItems.oneLine(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE)),
                text = HubItems.oneLine(extras.getCharSequence(Notification.EXTRA_TEXT)),
                postTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), zone),
            )
        }
        contentIntents = counted.associate { it.key to Opener(it.notification.contentIntent, it.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) }
        mutableItems.value = items
        mutableCounts.value = HubItems.counts(items)
        mutableOngoing.value = OngoingKinds.kinds(active.filter(::isOngoing).map(::toOngoingNotice))
    }

    /**
     * ステータス行のロゴの後ろに出す継続表示(§4.1。2026-11-25)の対象。counts の対象とは重ならない(消せない側だけ)。
     * 常駐(ONGOING_EVENT)かフォアグラウンドサービスの通知で、自分の通知・グループのまとめ・メディアの再生
     * (音楽のタイルがあるため。MediaStyle は EXTRA_MEDIA_SESSION を持つ)は除く。
     * One UI のライブ通知の印(EXTRA_SAMSUNG_ONGOING_ACTIVITY)は除かない: 時計のストップウォッチは本体の通知にこの印を付けている
     * (2026-11-25 実機)。counts で除いている裏の通知(MediaOngoingActivity)は flags=0 なので、ここでは常駐の条件で外れる。
     */
    private fun isOngoing(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        // Android 14 以降はフォアグラウンドサービスの通知も消せることがあり、その場合は counts 側(ベル)で数えるので二重に出さない。
        return (sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) &&
            !counts(sbn) &&
            sbn.packageName != packageName &&
            (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
            !n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
    }

    private fun toOngoingNotice(sbn: StatusBarNotification): OngoingNotice {
        val n = sbn.notification
        val extras = n.extras
        return OngoingNotice(
            packageName = sbn.packageName,
            channelId = n.channelId,
            category = n.category,
            hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX) > 0 || extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE),
            usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER),
            liveActivity = extras.containsKey(EXTRA_SAMSUNG_ONGOING_ACTIVITY),
        )
    }

    /**
     * ユーザーが消せる通知だけを数える(常駐通知・グループのまとめ・自分の通知は数えない)。
     * Samsung の SystemUI がメディア再生中に裏で出す通知(チャンネル MediaOngoingActivity)は
     * flags=0 で消せる扱いのまま題名が内部名・contentIntent なしなので、独自の extras のキーで除く(2026-11-19 実機)。
     */
    private fun counts(sbn: StatusBarNotification): Boolean =
        sbn.isClearable && !sbn.isOngoing && sbn.packageName != packageName &&
            (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
            !sbn.notification.extras.containsKey(EXTRA_SAMSUNG_ONGOING_ACTIVITY)

    /** 通知を開くための情報。@param autoCancel 押したら消える通知(FLAG_AUTO_CANCEL)か */
    private class Opener(val intent: PendingIntent?, val autoCancel: Boolean)

    companion object {
        private const val TAG = "FoldBerry"

        /** One UI の「ライブ通知」用の裏の通知に付く extras のキー(Samsung 独自。counts で除く) */
        private const val EXTRA_SAMSUNG_ONGOING_ACTIVITY = "android.ongoingActivityNoti.style"
        private val mutableCounts = MutableStateFlow<NotificationCounts?>(null)
        private val mutableItems = MutableStateFlow<List<HubItem>?>(null)
        private val mutableOngoing = MutableStateFlow<Set<OngoingKind>?>(null)

        /** 通知の key → 開くための情報。recount のたびに置き換える(サービスのスレッドと画面のスレッドから読むので Volatile)。 */
        @Volatile
        private var contentIntents: Map<String, Opener> = emptyMap()

        /** 接続中のサービス(押した通知を消すのに使う)。サービスを長く握らないよう弱参照。 */
        @Volatile
        private var connected: WeakReference<NotificationCounter>? = null

        private fun clear() {
            contentIntents = emptyMap()
            mutableItems.value = null
            mutableCounts.value = null
            mutableOngoing.value = null
        }

        /** 種類別の通知数。未許可・未接続なら null。 */
        val counts: StateFlow<NotificationCounts?> = mutableCounts.asStateFlow()

        /** Hub(§4.6)に出す通知の一覧(並びは未整理。HubItems.filter で新しい順にする)。未許可・未接続なら null。 */
        val items: StateFlow<List<HubItem>?> = mutableItems.asStateFlow()

        /** ステータス行に出す継続表示の種類(§4.1)。未許可・未接続なら null。VPN は含まない(回線から見る)。 */
        val ongoing: StateFlow<Set<OngoingKind>?> = mutableOngoing.asStateFlow()

        /**
         * Hub の通知を開く(通知パネルで押したのと同じく、通知の contentIntent を送る)。
         * Android 14 以降は、他アプリの PendingIntent から画面を開くには送る側(FoldBerry。表示中)が許可を渡す必要がある。
         * 押したら消える通知(FLAG_AUTO_CANCEL)は、通知パネルと同じく消す。
         * @return 送れたら true。contentIntent が無い・取り消されていたら false(呼び出し側でアプリを起動する)
         */
        fun open(context: Context, key: String): Boolean {
            val opener = contentIntents[key] ?: return false
            val intent = opener.intent ?: return false
            if (!sendFromHome(context, intent)) {
                Log.w(TAG, "通知を開けません: $key")
                return false
            }
            if (opener.autoCancel) {
                try {
                    connected?.get()?.cancelNotification(key)
                } catch (e: SecurityException) {
                    // 切断の直前など。消せなくても開くことはできたので握りつぶさずログだけ残す。
                    Log.w(TAG, "通知を消せません: $key", e)
                }
            }
            return true
        }

        /**
         * Hub の通知を消す(行の横払い・見出しの「すべて消去」。§4.6。2026-11-19)。端末の通知パネルからも消える。
         * 一覧と件数は、OS が呼ぶ onNotificationRemoved → recount で減る(ここでは書き換えない)。
         * @return 消す依頼を出せたら true。未接続・切断の直前(SecurityException)は false
         */
        fun dismiss(keys: List<String>): Boolean {
            if (keys.isEmpty()) return true
            val service = connected?.get() ?: return false
            return try {
                service.cancelNotifications(keys.toTypedArray())
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "通知を消せません: ${keys.size} 件", e)
                false
            }
        }

        /**
         * ホーム(表示中の FoldBerry)から他アプリの PendingIntent で画面を開く(Hub の通知・音楽のタイル。§4.6 / D26)。
         * Android 14 以降は送る側が「表示中なら許可」を渡さないと黙って開かないことがある。取り消されていたら false。
         */
        fun sendFromHome(context: Context, intent: PendingIntent): Boolean {
            val options = ActivityOptions.makeBasic().apply {
                // 36 以降は「表示中なら許可」を明示する定数に分かれた(旧定数は 36 で非推奨)。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                } else {
                    @Suppress("DEPRECATION")
                    setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
            }
            return try {
                intent.send(context, 0, null, null, null, null, options.toBundle())
                true
            } catch (e: PendingIntent.CanceledException) {
                Log.w(TAG, "PendingIntent が取り消されています", e)
                false
            }
        }

        /** 通知へのアクセスの部品名(音楽の札が MediaSessionManager に渡す)。 */
        fun component(context: Context): ComponentName = ComponentName(context, NotificationCounter::class.java)

        /** 「通知へのアクセス」が許可されているか(設定画面の表示に使う)。 */
        fun isAllowed(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(context, NotificationCounter::class.java))
    }
}
