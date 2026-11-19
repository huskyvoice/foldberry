package com.foldberry.launcher.keyboard

import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.DefaultApps

/**
 * アプリ一覧のアイコン右上に出す割り当てバッジの文字(§6。比較モックの案 E。2026-11-17 オーナー決定)。
 * キーは刻印に近い短い文字、自由枠は「M1」〜。記号(⌫ ⇧ ☎ など)は端末のフォントに無い(豆腐になる)おそれがあるので、記号・修飾・機能列のキーは英字の略記にする。複数に割り当てていれば先頭 1 つに「+」を付ける。
 * 先頭は キー(キーボードの並び順)→ 機能列(送話・戻る・終話)→ カバーのカメラの枠(CAM)→ メインの自由枠(M1〜M8)の順
 * (AssignmentSlots と同じ並び)で選ぶ。純粋関数(単体テスト対象)。
 */
object AssignmentBadge {
    fun of(a: Assignments, ref: AppRef, defaults: DefaultApps = DefaultApps()): String? {
        val labels = slotsOf(a, ref, defaults).map(::label)
        val first = labels.firstOrNull() ?: return null
        return if (labels.size > 1) "$first+" else first
    }

    /** そのアプリを割り当てた枠すべての短い表記(枠の並び)。割り当てシートの行に全部並べる(§7)。 */
    fun all(a: Assignments, ref: AppRef, defaults: DefaultApps = DefaultApps()): List<String> = slotsOf(a, ref, defaults).map(::label)

    /**
     * そのアプリの枠。自分で割り当てた枠を先に、既定(送話=電話・カメラの枠=カメラ)を後に並べる(それぞれの中は枠の並び)。
     * 既定が先だと、自由枠に自分で割り当てた電話アプリのバッジまで点線の「TEL+」になり、既定に見えてしまうため。
     */
    fun slotsOf(a: Assignments, ref: AppRef, defaults: DefaultApps = DefaultApps()): List<AssignmentSlot> =
        AssignmentSlots.assigned(a, defaults).filter { it.app == ref }.sortedBy { it.isDefault }

    /** バッジ(先頭の枠)が既定のアプリか=自分で割り当てた枠が 1 つも無い。点線の印にする(§6。v0.6.2)。割り当てが無ければ false。 */
    fun firstIsDefault(a: Assignments, ref: AppRef, defaults: DefaultApps = DefaultApps()): Boolean =
        slotsOf(a, ref, defaults).firstOrNull()?.isDefault == true

    /** 枠の短い表記(バッジの文字)。 */
    fun label(slot: AssignmentSlot): String = when (slot) {
        is AssignmentSlot.Key -> shortLabel(slot.spec.id)
        is AssignmentSlot.Function -> functionLabel(slot.key)
        is AssignmentSlot.DockCamera -> "CAM"
        // フォルダの中は「F+枠の番号」。画面ではフォルダの印+番号で描く(folderNumber。2026-11-18 オーナー決定)。
        is AssignmentSlot.MainSlot -> if (slot.inFolder) "$FOLDER_PREFIX${slot.index + 1}" else "M${slot.index + 1}"
    }

    /** フォルダの中のアプリの表記の頭。文字キーの F は 1 文字なので、後ろに数字が続くかで見分けられる。 */
    const val FOLDER_PREFIX = "F"
    private val FOLDER_LABEL = Regex("^$FOLDER_PREFIX(\\d.*)$")

    /**
     * フォルダの中の表記(「F1」「F1+」)なら、印の後ろに出す文字(「1」「1+」)を返す。それ以外は null。
     * 記号はフォントに無いおそれがあるので文字には入れず、印は画面側でアイコンとして描く。
     */
    fun folderNumber(label: String): String? = FOLDER_LABEL.matchEntire(label)?.groupValues?.get(1)

    /** 文字キーは id そのもの(W など)。記号・修飾キーは英字の短い略記。 */
    fun shortLabel(id: String): String = when (id) {
        "BACKSPACE" -> "BS"
        "ENTER" -> "ENT"
        "ALT" -> "alt"
        "DOLLAR" -> "$"
        "LEFT_SHIFT", "RIGHT_SHIFT" -> "SFT"
        "ZERO" -> "0"
        "SPACE" -> "SPC"
        "SYM" -> "sym"
        else -> id
    }

    private fun functionLabel(key: FunctionKey): String = when (key) {
        FunctionKey.SEND -> "TEL"
        FunctionKey.BACK -> "BACK"
        FunctionKey.END -> "END"
        FunctionKey.MENU, FunctionKey.TRACKPAD -> ""
    }
}
