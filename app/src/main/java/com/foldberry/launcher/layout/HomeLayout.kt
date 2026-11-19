package com.foldberry.launcher.layout

/*
 * ホーム画面の構成判定と、本体部(BlackBerry 本体)の寸法計算。
 * Android API に依存させず、JVM の単体テストで検証する(HomeLayoutTest)。
 */

/** ウィンドウ内の矩形(px)。 */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * ヒンジ(折り目)の位置。androidx.window の FoldingFeature から必要な値だけ写したもの。
 * bounds はウィンドウ座標(px)。端末によっては幅 0 の線になる。
 */
data class Hinge(val bounds: PxRect, val isVertical: Boolean)

enum class Split { LeftRight, TopBottom }

sealed interface HomeLayout {
    /** カバー構成: 本体だけを全面に出す(§3.2)。 */
    data object Cover : HomeLayout

    /** メイン構成: ヒンジで分け、片側に本体、反対側にアプリ一覧(§3.3)。 */
    data class Main(val split: Split, val body: PxRect, val appList: PxRect) : HomeLayout
}

/**
 * ヒンジがウィンドウにかかっていればメイン構成、無ければカバー構成(§3.1)。
 * 分割の境界はヒンジの bounds に合わせ、折り目をまたぐ部品を置かない。
 * ヒンジが縦 → 左右分割(左=本体)、横 → 上下分割(下=本体、上=アプリ一覧。オーナー指定 2026-11-15)。
 * ヒンジがウィンドウの端に寄っていて片側が空になる場合は、分けられないのでカバー構成にする。
 */
fun decideHomeLayout(windowWidth: Int, windowHeight: Int, hinge: Hinge?): HomeLayout {
    if (hinge == null || windowWidth <= 0 || windowHeight <= 0) return HomeLayout.Cover
    return if (hinge.isVertical) {
        val start = hinge.bounds.left.coerceIn(0, windowWidth)
        val end = hinge.bounds.right.coerceIn(start, windowWidth)
        if (start == 0 || end == windowWidth) return HomeLayout.Cover
        HomeLayout.Main(
            split = Split.LeftRight,
            body = PxRect(0, 0, start, windowHeight),
            appList = PxRect(end, 0, windowWidth, windowHeight),
        )
    } else {
        val start = hinge.bounds.top.coerceIn(0, windowHeight)
        val end = hinge.bounds.bottom.coerceIn(start, windowHeight)
        if (start == 0 || end == windowHeight) return HomeLayout.Cover
        HomeLayout.Main(
            split = Split.TopBottom,
            body = PxRect(0, end, windowWidth, windowHeight),
            appList = PxRect(0, 0, windowWidth, start),
        )
    }
}

/** 本体部の各領域の高さ(dp)。上から ステータス行 / 画面部の残り / 機能列 / 4 段 / 最下段の下の余白。 */
data class BodyLayout(
    /** ステータス行を含む画面部全体の高さ。 */
    val panel: Float,
    val status: Float,
    val functionRow: Float,
    val keyRow: Float,
    val fret: Float,
    val bottomMargin: Float,
) {
    val total: Float get() = panel + functionRow + keyRow * KEY_ROWS + fret * FRETS + bottomMargin

    companion object {
        const val STATUS_DP = 28f
        const val FRET_DP = 3f
        const val FRETS = 4
        const val KEY_ROWS = 4
        /**
         * 機能列の高さ = キー行の 1.15 倍。トラックパッドの行をキー行より少し高くする
         * (U1 改訂 2026-11-16。モック docs/mock/cover-mock.html の高さ比 D。旧値 0.85)。
         */
        const val FUNCTION_ROW_RATIO = 1.15f
        /** 画面部の高さ = 幅 − この値。キーボード部を少し高くするため正方形から削る(U1 改訂 2026-11-16)。 */
        const val PANEL_INSET_DP = 30f
        /** キー 1 つの高さの下限(§3.2)。 */
        const val MIN_KEY_ROW_DP = 40f
        /** ジェスチャーナビ領域との干渉を避ける最下段の下の余白の最小値(§14 Warning)。 */
        const val MIN_BOTTOM_MARGIN_DP = 24f
    }
}

/**
 * メイン縦の下半分(キーボードだけ。D25。2026-11-19)の寸法。上から 下の行 → 機能列 → キー 4 段(フレット込み)→ 下の余白。
 */
data class KeyboardLayout(
    val dock: Float,
    val functionRow: Float,
    val keyRow: Float,
    val fret: Float,
    val bottomMargin: Float,
) {
    val total: Float get() = dock + functionRow + keyRow * BodyLayout.KEY_ROWS + fret * BodyLayout.FRETS + bottomMargin
}

/**
 * メイン縦の下半分の寸法(D25)。画面部を上半分へ移したので、下の行を除いた残りを全部キーに回す。
 * キー行 = 残り ÷(4 段 + 機能列 1.15)。下限 40dp(§3.2)は本体と同じ(収まらなければ total が height を超える)。
 */
fun computeKeyboardLayout(heightDp: Float, bottomMarginDp: Float, dockDp: Float): KeyboardLayout {
    val rest = heightDp - bottomMarginDp - dockDp - BodyLayout.FRET_DP * BodyLayout.FRETS
    val keyRow = (rest / (BodyLayout.KEY_ROWS + BodyLayout.FUNCTION_ROW_RATIO)).coerceAtLeast(BodyLayout.MIN_KEY_ROW_DP)
    return KeyboardLayout(
        dock = dockDp,
        functionRow = keyRow * BodyLayout.FUNCTION_ROW_RATIO,
        keyRow = keyRow,
        fret = BodyLayout.FRET_DP,
        bottomMargin = bottomMarginDp,
    )
}

/**
 * U1 決定値(2026-11-16 改訂): 画面部はステータス行込みで「本体の幅 − 30dp」の高さ。残りをキーボード部に割り当てる。
 * キー行が 40dp を下回るなら画面部の高さを削る(§3.2)。それでも足りない狭い領域では
 * キー行 40dp を優先し、画面部はステータス行の高さまで縮める(total が height を超え得る)。
 */
fun computeBodyLayout(widthDp: Float, heightDp: Float, bottomMarginDp: Float): BodyLayout {
    val fixed = bottomMarginDp + BodyLayout.FRET_DP * BodyLayout.FRETS
    val rowUnits = BodyLayout.KEY_ROWS + BodyLayout.FUNCTION_ROW_RATIO

    var panel = (widthDp - BodyLayout.PANEL_INSET_DP).coerceAtLeast(BodyLayout.STATUS_DP)
    var keyRow = (heightDp - panel - fixed) / rowUnits
    if (keyRow < BodyLayout.MIN_KEY_ROW_DP) {
        keyRow = BodyLayout.MIN_KEY_ROW_DP
        panel = (heightDp - fixed - keyRow * rowUnits).coerceAtLeast(BodyLayout.STATUS_DP)
    }
    return BodyLayout(
        panel = panel,
        status = BodyLayout.STATUS_DP,
        functionRow = keyRow * BodyLayout.FUNCTION_ROW_RATIO,
        keyRow = keyRow,
        fret = BodyLayout.FRET_DP,
        bottomMargin = bottomMarginDp,
    )
}
