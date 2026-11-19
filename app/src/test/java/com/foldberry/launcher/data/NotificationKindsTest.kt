package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationKindsTest {

    private val dialer = "com.samsung.android.dialer"
    private val sms = "com.samsung.android.messaging"

    private fun classify(pkg: String, category: String?, dialerPkg: String? = dialer, smsPkg: String? = sms) =
        NotificationKinds.classify(pkg, category, dialerPkg, smsPkg)

    @Test
    fun 不在着信は種別でも既定の電話アプリでも判定する() {
        assertEquals(NotificationKind.MISSED_CALL, classify("other.phone", "missed_call"))
        assertEquals(NotificationKind.MISSED_CALL, classify(dialer, null))
        assertEquals(NotificationKind.MISSED_CALL, classify(dialer, "status"))
    }

    @Test
    fun SMSは既定のSMSアプリだけでメッセージ種別の他アプリはその他() {
        assertEquals(NotificationKind.SMS, classify(sms, "msg"))
        assertEquals(NotificationKind.SMS, classify(sms, null))
        assertEquals(NotificationKind.OTHER, classify("jp.naver.line.android", "msg"))
    }

    @Test
    fun メールは種別で判定する() {
        assertEquals(NotificationKind.EMAIL, classify("com.google.android.gm", "email"))
        assertEquals(NotificationKind.OTHER, classify("com.google.android.gm", null))
    }

    @Test
    fun 判定順は不在着信がSMSとメールより先() {
        // 電話アプリと SMS アプリが同じパッケージでも不在着信を優先する。
        assertEquals(NotificationKind.MISSED_CALL, classify("same", null, dialerPkg = "same", smsPkg = "same"))
        assertEquals(NotificationKind.MISSED_CALL, classify(sms, "missed_call"))
        assertEquals(NotificationKind.SMS, classify(sms, "email"))
    }

    @Test
    fun 既定アプリが取れなくても落ちずに種別で判定する() {
        assertEquals(NotificationKind.OTHER, classify(dialer, null, dialerPkg = null, smsPkg = null))
        assertEquals(NotificationKind.EMAIL, classify("x", "email", dialerPkg = null, smsPkg = null))
        assertEquals(NotificationKind.MISSED_CALL, classify("x", "missed_call", dialerPkg = null, smsPkg = null))
    }

    @Test
    fun 件数の加算と合計() {
        val c = NotificationCounts()
            .plus(NotificationKind.MISSED_CALL).plus(NotificationKind.SMS).plus(NotificationKind.SMS)
            .plus(NotificationKind.OTHER)
        assertEquals(1, c.count(NotificationKind.MISSED_CALL))
        assertEquals(0, c.count(NotificationKind.EMAIL))
        assertEquals(2, c.count(NotificationKind.SMS))
        assertEquals(1, c.count(NotificationKind.OTHER))
        assertEquals(4, c.total)
    }
}
