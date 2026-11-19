package com.foldberry.launcher.data

import java.time.LocalDateTime

/**
 * Hub(§4.6)に出す通知 1 件。NotificationCounter が数える対象と同じ通知から作る(件数と一覧が必ず一致する)。
 * Android 非依存に保ち、開くための PendingIntent は持たない(NotificationCounter が key で引く)。
 *
 * @param key 通知の key(StatusBarNotification.key)。開く・一覧の key に使う
 * @param title 通知のタイトル(差出人や件名)。改行は空白にしてある
 * @param text 通知の本文。改行は空白にしてある
 * @param postTime 通知の時刻(端末の時刻帯の日時)
 */
data class HubItem(
    val key: String,
    val packageName: String,
    val kind: NotificationKind,
    val title: String,
    val text: String,
    val postTime: LocalDateTime,
)

/**
 * 1 行目・2 行目の表示。
 * @param body 2 行目。null なら本文を隠す決まり文句(画面の言語のリソース。HubPreview で文言が変わる)を出す
 */
data class HubDisplay(val title: String, val body: String?)

/** Hub の並び・表示の純粋関数(単体テスト対象 HubItemsTest)。 */
object HubItems {
    private val SPACES = Regex("\\s+")

    /** 通知の文字列を 1 行にする(改行・連続した空白を 1 つの空白に)。 */
    fun oneLine(text: CharSequence?): String = text?.toString()?.replace(SPACES, " ")?.trim().orEmpty()

    /** 種類で絞り(null=すべて)、新しい順に並べる。 */
    fun filter(items: List<HubItem>, kind: NotificationKind?): List<HubItem> =
        items.filter { kind == null || it.kind == kind }.sortedByDescending { it.postTime }

    /** 種類別の件数(時計の横の件数と同じ数え方。§4.2)。 */
    fun counts(items: List<HubItem>): NotificationCounts = items.fold(NotificationCounts()) { acc, item -> acc.plus(item.kind) }

    /**
     * 本文の出し方に従って 1 行目・2 行目を決める(§4.6)。
     * タイトルの無い通知はアプリ名を 1 行目にする。本文が空なら 2 行目も空にする。
     */
    fun display(item: HubItem, preview: HubPreview, appLabel: String): HubDisplay = when (preview) {
        HubPreview.FULL -> HubDisplay(item.title.ifBlank { appLabel }, item.text)
        HubPreview.SENDER -> HubDisplay(item.title.ifBlank { appLabel }, null)
        HubPreview.APP -> HubDisplay(appLabel, null)
    }

    /**
     * 右上の時刻。今日(と時計がずれて未来になった通知)=時刻、昨日=「昨日」、それより前=「月/日」。
     * 12 時間表記は日本語「午後3:05」、英語「3:05 PM」(大時計と同じ ClockText.time)。
     */
    fun timeLabel(post: LocalDateTime, now: LocalDateTime, is24: Boolean, language: ClockLanguage): String {
        val day = post.toLocalDate()
        val today = now.toLocalDate()
        return when {
            !day.isBefore(today) -> {
                val (time, marker) = ClockText.time(post.hour, post.minute, is24, language)
                when {
                    marker == null -> time
                    language == ClockLanguage.JA -> "$marker$time"
                    else -> "$time $marker"
                }
            }
            day == today.minusDays(1) -> if (language == ClockLanguage.JA) "昨日" else "Yesterday"
            else -> "${day.monthValue}/${day.dayOfMonth}"
        }
    }
}
