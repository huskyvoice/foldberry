package com.foldberry.launcher.data

/**
 * 割り当てアイコンを絵記号で出すときの絵記号の種類(仕様書 §5.2。2026-11-20 追加。docs/mock/appicon-mock.html)。
 * アプリのロゴは描かず、役割の絵記号(メール=封筒など)だけを使う。当てはまらないアプリはすべて GENERIC。
 * id は保存(AppearanceSetting.pictograms)に使うので変えない。並びは選ぶページの格子の順。
 */
enum class Pictogram(val id: String) {
    MAIL("mail"),
    CHAT("chat"),
    SNS("sns"),
    BROWSER("browser"),
    MAP("map"),
    PHOTO("photo"),
    CAMERA("camera"),
    PHONE("phone"),
    CLOCK("clock"),
    CALENDAR("calendar"),
    CALCULATOR("calculator"),
    NOTE("note"),
    VIDEO("video"),
    MUSIC("music"),
    FILES("files"),
    SETTINGS("settings"),
    WALLET("wallet"),
    SHOPPING("shopping"),
    GENERIC("generic"),
    ;

    companion object {
        /** 未知の id(手で編集されたファイル・将来の版で増えた絵記号)は null。 */
        fun of(id: String): Pictogram? = entries.firstOrNull { it.id == id }
    }
}

/**
 * アプリの絵記号を決める(純粋。PictogramsTest)。
 * 順は オーナーが選んだもの(overrides。パッケージ名→id)→ パッケージ名の対応表 → アプリが申告する種類 → GENERIC。
 * キーはパッケージ名だけなので、ショートカットは持ち主のアプリの絵記号になる。
 */
object Pictograms {
    // ApplicationInfo.CATEGORY_*(Android 非依存に保つため数値を自前で持つ。NetworkGeneration と同じ考え方)
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_IMAGE = 3
    const val CATEGORY_SOCIAL = 4
    const val CATEGORY_MAPS = 6

    /**
     * パッケージ名の対応表。実機の調査(2026-11-20。tmp/icon-survey.tsv の 79 アプリ)と、よく使われるアプリから作った。
     * どれにも当たらないアプリ(ニュース・健康など)は書かない(=申告の種類か GENERIC)。
     */
    private val BY_PACKAGE: Map<String, Pictogram> = buildMap {
        fun put(p: Pictogram, vararg packages: String) = packages.forEach { put(it, p) }
        put(Pictogram.MAIL, "com.google.android.gm", "com.samsung.android.email.provider", "com.microsoft.office.outlook")
        put(
            Pictogram.CHAT,
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "jp.co.rakuten.mobile.rcs",
            "com.discord", "com.Slack", "jp.naver.line.android", "com.whatsapp", "org.telegram.messenger",
        )
        put(
            Pictogram.BROWSER,
            "com.android.chrome", "com.sec.android.app.sbrowser", "com.google.android.googlequicksearchbox",
            "org.mozilla.firefox", "com.microsoft.emmx",
        )
        put(Pictogram.MAP, "com.google.android.apps.maps", "com.samsung.android.app.find", "jp.co.nttdocomo.imadocosearch_sha2")
        // SNS(2026-11-21 追加)。LINE・Discord・Slack など 1 対 1 のやり取りが主のものは CHAT のまま。
        // Instagram は写真から移した(オーナー決定)。
        put(
            Pictogram.SNS,
            "com.twitter.android", "com.facebook.katana", "com.instagram.android", "com.instagram.barcelona",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "xyz.blueskyweb.app", "org.joinmastodon.android",
            "com.reddit.frontpage", "com.linkedin.android", "com.pinterest", "com.snapchat.android",
        )
        put(Pictogram.PHOTO, "com.google.android.apps.photos", "com.sec.android.gallery3d")
        put(Pictogram.CAMERA, "com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.google.ar.lens")
        put(
            Pictogram.PHONE,
            "com.samsung.android.dialer", "com.samsung.android.app.contacts", "com.google.android.dialer",
            "com.google.android.contacts", "com.google.android.apps.tachyon",
        )
        put(Pictogram.CLOCK, "com.sec.android.app.clockpackage", "com.google.android.deskclock")
        put(Pictogram.CALENDAR, "com.samsung.android.calendar", "com.google.android.calendar")
        put(Pictogram.CALCULATOR, "com.sec.android.app.popupcalculator", "com.google.android.calculator")
        put(
            Pictogram.NOTE,
            "com.google.android.keep", "com.samsung.android.app.notes", "com.google.android.apps.labs.language.tailwind",
        )
        put(Pictogram.VIDEO, "com.google.android.youtube")
        put(Pictogram.MUSIC, "com.google.android.apps.youtube.music", "com.spotify.music", "com.sec.android.app.music")
        put(Pictogram.FILES, "com.sec.android.app.myfiles", "com.google.android.apps.nbu.files", "com.google.android.apps.docs")
        put(Pictogram.SETTINGS, "com.android.settings")
        put(
            Pictogram.WALLET,
            "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "com.felicanetworks.mfm.main",
            "com.mobilepasmo.android", "com.donki.majica", "com.smbc_card.vpass", "com.smbc_card.vpoint", "com.tkcard",
            "jp.coinplus.app", "jp.co.mizuhobank.banking", "jp.co.nestegg.finbee", "jp.co.netbk", "jp.co.smbc.direct",
            "jp.mufg.bk.applisp.app",
        )
        put(
            Pictogram.SHOPPING,
            "com.amazon.mShop.android.shopping", "com.android.vending", "com.sec.android.app.samsungapps",
            "com.samsung.ecomm.global.gbr", "jp.co.mcdonalds.android",
        )
    }

    /** アプリが manifest で申告する種類(ApplicationInfo.category)からの絵記号。ゲームなどは当てはまらないので null。 */
    private fun byCategory(category: Int?): Pictogram? = when (category) {
        CATEGORY_AUDIO -> Pictogram.MUSIC
        CATEGORY_VIDEO -> Pictogram.VIDEO
        CATEGORY_IMAGE -> Pictogram.PHOTO
        CATEGORY_SOCIAL -> Pictogram.SNS
        CATEGORY_MAPS -> Pictogram.MAP
        else -> null
    }

    /** オーナーが選んでいなければ自動で決まる絵記号(選ぶページの「自動」に出す)。 */
    fun auto(packageName: String, category: Int?): Pictogram =
        BY_PACKAGE[packageName] ?: byCategory(category) ?: Pictogram.GENERIC

    fun of(packageName: String, category: Int?, overrides: Map<String, String>): Pictogram =
        overrides[packageName]?.let { Pictogram.of(it) } ?: auto(packageName, category)
}
