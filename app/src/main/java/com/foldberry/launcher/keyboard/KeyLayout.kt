package com.foldberry.launcher.keyboard

/*
 * キーボード部のキー配列(仕様書 §5.1)。BlackBerry Classic の配列と刻印を再現する。
 * 描画から切り離し、Android API に依存させない(KeyLayoutTest で検証)。
 */

/** 刻印に使う自作アイコン(ui/icons/FoldBerryIcons.kt と対応)。 */
enum class KeyGlyph { BACKSPACE, ENTER, SHIFT, MIC, SPACE, SPEAKER }

sealed interface Legend {
    data class Text(val text: String) : Legend
    data class Icon(val glyph: KeyGlyph) : Legend
}

/**
 * 自由割り当てキー 1 つ(§5.2)。
 * @param id 割り当て保存のキー(M4 以降で使う。変えると保存済みの割り当てが外れる)
 * @param column 1 始まりの列(全 10 列)
 * @param span 占める列数
 */
data class KeySpec(
    val id: String,
    val main: Legend,
    val secondary: Legend?,
    val column: Int,
    val span: Int = 1,
)

/** 機能列の固定機能キー(§5.3。割り当て不可)。並びは左から。 */
/**
 * 機能列の 5 キー(§5.3)。
 * @param assignId 自由割り当ての保存キー(Assignments.keys のキー。変えると保存済みの割り当てが外れる)。
 *   BBメニュー・トラックパッドは固定機能のみなので null。送話・戻る・終話は割り当てればそのアプリを起動し、
 *   未割り当てなら固定機能(2026-11-17 オーナー決定)。
 */
enum class FunctionKey(val assignId: String?) {
    SEND("FN_SEND"),
    MENU(null),
    TRACKPAD(null),
    BACK("FN_BACK"),
    END("FN_END"),
}

object KeyLayout {
    const val COLUMNS = 10

    private fun t(text: String) = Legend.Text(text)
    private fun i(glyph: KeyGlyph) = Legend.Icon(glyph)

    /** 主刻印が文字 1 つで、id も同じ文字のキー。 */
    private fun letter(ch: String, secondary: String, column: Int) = KeySpec(ch, t(ch), t(secondary), column)

    val rows: List<List<KeySpec>> = listOf(
        listOf(
            letter("Q", "#", 1), letter("W", "1", 2), letter("E", "2", 3), letter("R", "3", 4), letter("T", "(", 5),
            letter("Y", ")", 6), letter("U", "_", 7), letter("I", "-", 8), letter("O", "+", 9), letter("P", "@", 10),
        ),
        listOf(
            letter("A", "*", 1), letter("S", "4", 2), letter("D", "5", 3), letter("F", "6", 4), letter("G", "/", 5),
            letter("H", ":", 6), letter("J", ";", 7), letter("K", "'", 8), letter("L", "\"", 9),
            KeySpec("BACKSPACE", i(KeyGlyph.BACKSPACE), null, 10),
        ),
        listOf(
            KeySpec("ALT", t("alt"), null, 1),
            letter("Z", "7", 2), letter("X", "8", 3), letter("C", "9", 4), letter("V", "?", 5),
            letter("B", "!", 6), letter("N", ",", 7), letter("M", ".", 8),
            KeySpec("DOLLAR", t("$"), i(KeyGlyph.SPEAKER), 9),
            KeySpec("ENTER", i(KeyGlyph.ENTER), null, 10),
        ),
        // 4 段目は実機どおり両端の列(1 と 10)を空ける。
        listOf(
            KeySpec("LEFT_SHIFT", i(KeyGlyph.SHIFT), null, 2),
            KeySpec("ZERO", i(KeyGlyph.MIC), t("0"), 3),
            KeySpec("SPACE", i(KeyGlyph.SPACE), null, 4, span = 3),
            KeySpec("SYM", t("sym"), null, 7, span = 2),
            KeySpec("RIGHT_SHIFT", i(KeyGlyph.SHIFT), null, 9),
        ),
    )

    /** 自由割り当て対象の全キー(§5.1: 35 キー)。 */
    val assignableKeys: List<KeySpec> = rows.flatten()

    val functionKeys: List<FunctionKey> = FunctionKey.entries

    /** 機能列のうち自由割り当てできるキー(送話・戻る・終話)。 */
    val assignableFunctionKeys: List<FunctionKey> = FunctionKey.entries.filter { it.assignId != null }

    /**
     * カバー画面の下の行の右端(カメラの枠。§4.3)の保存キー(Assignments.keys のキー。変えると保存済みの割り当てが外れる)。
     * 未割り当てなら端末のカメラを開く(2026-11-17 オーナー決定)。
     */
    const val DOCK_CAMERA_ID = "DOCK_CAMERA"

    /** 長押しで音声操作を始めるキー(0・マイクの刻印。§5.5。D27)。短押しは他のキーと同じく割り当てアプリの起動。 */
    const val VOICE_KEY_ID = "ZERO"

    /**
     * メイン画面の一覧の枠に置く自由枠(アプリの割り当て枠。§4.5。2026-11-17 オーナー決定)の数と保存キー。
     * 縦横で共通の 8 枠。保存キーは `MAIN_SLOT_1`〜`MAIN_SLOT_8`(変えると割り当てが外れる)。
     * 2026-11-18 に 12→8(メイン横の右上にステータス行の帯を置いて 4×4 になったため。オーナー決定)。
     * 廃止した `MAIN_SLOT_9`〜`MAIN_SLOT_12` は読込みで捨てる(data/Assignments の RETIRED_KEYS)。
     */
    const val MAIN_SLOT_COUNT = 8

    fun mainSlotId(index: Int): String = "MAIN_SLOT_${index + 1}"

    /** 割り当てシートなどに出すキーの呼び名(例「W キー」。§7)。 */
    fun displayName(key: KeySpec): String = when (key.id) {
        "BACKSPACE" -> "Backspace キー"
        "ALT" -> "alt キー"
        "DOLLAR" -> "$ キー"
        "ENTER" -> "Enter キー"
        "LEFT_SHIFT" -> "左 shift キー"
        "RIGHT_SHIFT" -> "右 shift キー"
        "ZERO" -> "0・マイク キー"
        "SPACE" -> "スペースキー"
        "SYM" -> "sym キー"
        else -> "${key.id} キー"
    }

    /**
     * Classic のキー面は左半分が左、右半分が右へ傾いた彫刻形状(§5.4)。キーの中心列から
     * 左端 -1 〜 右端 +1 の傾きを返し、描画でハイライトの位置をずらすのに使う。
     */
    fun tiltOf(key: KeySpec): Float {
        val center = (key.column - 1) + (key.span - 1) / 2f
        return (center - (COLUMNS - 1) / 2f) / ((COLUMNS - 1) / 2f)
    }
}
