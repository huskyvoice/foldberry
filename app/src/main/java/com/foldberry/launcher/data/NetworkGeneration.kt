package com.foldberry.launcher.data

/**
 * ステータス行に添える通信の世代の文字(仕様書 §4.1。2026-11-17 追加)。
 * Android 非依存に保つため、規格・通信方式は Android の定数と同じ数値を自前の定数で持つ(NotificationKinds と同じ考え方)。
 */
object NetworkGeneration {
    // ScanResult.WIFI_STANDARD_*
    const val WIFI_11N = 4
    const val WIFI_11AC = 5
    const val WIFI_11AX = 6
    const val WIFI_11BE = 8

    /** 6GHz 帯の範囲(MHz)。11ax をこの帯で使っていれば Wi-Fi 6E。 */
    private val BAND_6GHZ = 5925..7125

    // TelephonyManager.NETWORK_TYPE_*
    const val NETWORK_UMTS = 3
    const val NETWORK_EVDO_0 = 5
    const val NETWORK_EVDO_A = 6
    const val NETWORK_HSDPA = 8
    const val NETWORK_HSUPA = 9
    const val NETWORK_HSPA = 10
    const val NETWORK_EVDO_B = 12
    const val NETWORK_LTE = 13
    const val NETWORK_EHRPD = 14
    const val NETWORK_HSPAP = 15
    const val NETWORK_TD_SCDMA = 17
    const val NETWORK_NR = 20

    // TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_*
    const val OVERRIDE_NR_NSA = 3
    const val OVERRIDE_NR_NSA_MMWAVE = 4
    const val OVERRIDE_NR_ADVANCED = 5

    private val NETWORK_3G = setOf(
        NETWORK_UMTS, NETWORK_EVDO_0, NETWORK_EVDO_A, NETWORK_HSDPA, NETWORK_HSUPA, NETWORK_HSPA,
        NETWORK_EVDO_B, NETWORK_EHRPD, NETWORK_HSPAP, NETWORK_TD_SCDMA,
    )

    /** Wi-Fi の世代("4"〜"7"、6GHz 帯の 11ax は "6E")。旧規格・不明は null(数字を出さない)。 */
    fun wifi(standard: Int, frequencyMhz: Int): String? = when (standard) {
        WIFI_11N -> "4"
        WIFI_11AC -> "5"
        WIFI_11AX -> if (frequencyMhz in BAND_6GHZ) "6E" else "6"
        WIFI_11BE -> "7"
        else -> null
    }

    /**
     * モバイル通信の世代。5G の NSA(LTE に 5G を重ねる方式)は通信方式が LTE のまま上書きの種別で届くので、上書きを先に見る。
     * LTE は「4G」と書く(2026-11-17 オーナー決定。日本の Galaxy の表記に合わせる)。2G・不明は null。
     */
    fun mobile(networkType: Int, overrideType: Int): String? = when {
        overrideType == OVERRIDE_NR_NSA || overrideType == OVERRIDE_NR_NSA_MMWAVE || overrideType == OVERRIDE_NR_ADVANCED -> "5G"
        networkType == NETWORK_NR -> "5G"
        networkType == NETWORK_LTE -> "4G"
        networkType in NETWORK_3G -> "3G"
        else -> null
    }
}
