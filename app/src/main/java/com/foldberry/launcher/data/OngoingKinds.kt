package com.foldberry.launcher.data

/**
 * ステータス行のロゴの後ろに出す継続表示(消せない常駐通知)の種類(§4.1。2026-11-25 追加)。並び順=表示順。
 * 件数は出さず、出ている種類のアイコンだけを出す(ベル=一時的な通知の件数とは別。§4.2)。
 */
enum class OngoingKind {
    /** 接続中(VPN・Windows にリンク・ウォッチなど)。VPN は通知ではなく回線から見る(StatusRow)。 */
    CONNECTION,

    /** 進行中(通話・ナビ・タイマー・転送など)。 */
    ACTIVITY,

    /** その他の常駐サービス。 */
    SERVICE,
}

/** 常駐通知 1 件の振り分けに使う値(Android 非依存。NotificationCounter が StatusBarNotification から作る)。 */
data class OngoingNotice(
    val packageName: String,
    val channelId: String?,
    val category: String?,
    /** 進み具合(EXTRA_PROGRESS_MAX > 0 か、終わりの見えない進み具合)を持つか */
    val hasProgress: Boolean,
    /** 経過時間を数える表示(EXTRA_SHOW_CHRONOMETER)か */
    val usesChronometer: Boolean,
    /**
     * One UI のライブ通知(Now bar に出る進行中の通知)の印を持つか。Samsung の時計のストップウォッチは
     * category も経過時間の印も付けず、これだけを持っていた(2026-11-25 実機)。
     */
    val liveActivity: Boolean = false,
)

/**
 * 継続表示の振り分け(§4.1)。Android 非依存に保つため、通知の種別は定数名ではなく値の文字列で持つ(NotificationKinds と同じ)。
 */
object OngoingKinds {
    /**
     * 進行中とみなす通知の種別(Notification.CATEGORY_* の値)。
     * transport(メディアの再生)は音楽のタイルがあるので含めない(メディアの通知は拾う側で除いている)。
     */
    private val ACTIVITY_CATEGORIES = setOf(
        "call", "navigation", "progress", "stopwatch", "workout", "location_sharing", "alarm",
    )

    /**
     * 接続を表す常駐通知を出すアプリ(パッケージ名)。種別では見分けられないので表で持つ(data/Pictograms のパッケージ表と同じ考え方)。
     * Windows にリンクは 2026-11-25 の実機で category なしの常駐通知だった。
     */
    private val CONNECTION_PACKAGES = setOf(
        "com.microsoft.appmanager", // Windows にリンク
        "com.tailscale.ipn", // Tailscale
        "com.wireguard.android", // WireGuard
        "com.cloudflare.onedotonedotonedotone", // 1.1.1.1(WARP)
        "com.samsung.android.app.watchmanager", // Galaxy Wearable
        "com.google.android.apps.wear.companion", // Pixel Watch / Wear OS
        "com.google.android.projection.gearhead", // Android Auto
        "org.kde.kdeconnect_tp", // KDE Connect
        "com.samsung.android.smartmirroring", // Smart View
    )

    /** OS が出す VPN の通知(常時接続 VPN の「切断」警告など)のパッケージとチャンネル。VPN の有無は回線で見るので数えない。 */
    private const val SYSTEM_PACKAGE = "android"
    private const val SYSTEM_VPN_CHANNEL = "VPN"

    /**
     * 判定順: 数えない(OS の VPN 通知) → 進行中 → 接続中 → その他の常駐。
     * 進行中を接続中より先に見る: Windows にリンクの転送中など、接続のアプリが進み具合を出しているときは「進行中」にする。
     * @return 数えない通知は null
     */
    fun classify(notice: OngoingNotice): OngoingKind? = when {
        notice.packageName == SYSTEM_PACKAGE && notice.channelId == SYSTEM_VPN_CHANNEL -> null
        notice.category in ACTIVITY_CATEGORIES || notice.hasProgress || notice.usesChronometer || notice.liveActivity -> OngoingKind.ACTIVITY
        notice.packageName in CONNECTION_PACKAGES -> OngoingKind.CONNECTION
        else -> OngoingKind.SERVICE
    }

    /** 常駐通知の一覧から、出ている種類の集合を作る。 */
    fun kinds(notices: List<OngoingNotice>): Set<OngoingKind> = notices.mapNotNullTo(mutableSetOf(), ::classify)
}
