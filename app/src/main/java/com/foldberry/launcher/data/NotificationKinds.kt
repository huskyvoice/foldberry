package com.foldberry.launcher.data

/** 時計の横に出す通知の種類(仕様書 §4.2)。並び順=表示順。 */
enum class NotificationKind { MISSED_CALL, EMAIL, SMS, OTHER }

/** 種類ごとの通知数。 */
data class NotificationCounts(
    val missedCall: Int = 0,
    val email: Int = 0,
    val sms: Int = 0,
    val other: Int = 0,
) {
    val total: Int get() = missedCall + email + sms + other

    fun count(kind: NotificationKind): Int = when (kind) {
        NotificationKind.MISSED_CALL -> missedCall
        NotificationKind.EMAIL -> email
        NotificationKind.SMS -> sms
        NotificationKind.OTHER -> other
    }

    fun plus(kind: NotificationKind): NotificationCounts = when (kind) {
        NotificationKind.MISSED_CALL -> copy(missedCall = missedCall + 1)
        NotificationKind.EMAIL -> copy(email = email + 1)
        NotificationKind.SMS -> copy(sms = sms + 1)
        NotificationKind.OTHER -> copy(other = other + 1)
    }
}

/**
 * 通知の振り分け(§4.2)。Android 非依存に保つため、通知の種別は定数名ではなく値の文字列で持つ。
 */
object NotificationKinds {
    /** Notification.CATEGORY_MISSED_CALL の値。 */
    const val CATEGORY_MISSED_CALL = "missed_call"

    /** Notification.CATEGORY_EMAIL の値。 */
    const val CATEGORY_EMAIL = "email"

    /**
     * 判定順: 不在着信 → SMS → メール → その他。
     * 既定の電話アプリの通知は種別が無くても不在着信とみなす(Samsung の電話アプリが種別を付けるか未確認のため。
     * 着信中の通知は常駐なので、ここに来る前に数える対象から外れている)。
     * SMS は種別ではなく既定の SMS アプリかどうかで見る(LINE 等のメッセージアプリも CATEGORY_MESSAGE を付けるため)。
     * メールは種別だけで見る(Android には既定のメールアプリを取る手段が無い。2026-11-17 オーナー決定)。
     */
    fun classify(packageName: String, category: String?, dialerPackage: String?, smsPackage: String?): NotificationKind = when {
        category == CATEGORY_MISSED_CALL || (dialerPackage != null && packageName == dialerPackage) -> NotificationKind.MISSED_CALL
        smsPackage != null && packageName == smsPackage -> NotificationKind.SMS
        category == CATEGORY_EMAIL -> NotificationKind.EMAIL
        else -> NotificationKind.OTHER
    }
}
