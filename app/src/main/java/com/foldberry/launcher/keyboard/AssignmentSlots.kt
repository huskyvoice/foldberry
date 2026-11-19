package com.foldberry.launcher.keyboard

import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.DefaultApps
import com.foldberry.launcher.data.sortByName

/**
 * 割り当て先の枠(キー・機能列・カバーのカメラの枠・メインの自由枠)と、そこに割り当てたアプリ。
 * (タイル T1・T2 は 2026-11-19 に割り当てをやめ、音楽と次の予定に固定した。D26)
 * 設定の割り当て一覧(§8)とアプリ一覧の割り当てバッジ(§6)で同じ並びを使う。純粋関数(単体テスト対象)。
 */
sealed interface AssignmentSlot {
    val app: AppRef

    /**
     * 自分で割り当てたのではなく、未割り当てのときに開く端末の既定のアプリ(送話=電話・カメラの枠=カメラ。§8。v0.6.2)。
     * 見た目は点線の印で、解除はできない(変更=割り当てはできる)。
     */
    val isDefault: Boolean get() = false

    data class Key(val spec: KeySpec, override val app: AppRef) : AssignmentSlot
    data class Function(val key: FunctionKey, override val app: AppRef, override val isDefault: Boolean = false) : AssignmentSlot
    data class DockCamera(override val app: AppRef, override val isDefault: Boolean = false) : AssignmentSlot

    /**
     * @param index 0 始まり(画面の表記は M1〜M8)
     * @param inFolder その枠のフォルダの中のアプリか(v0.7.1)。バッジはフォルダの印+番号(内部表記 F1〜F8。AssignmentBadge)
     */
    data class MainSlot(val index: Int, override val app: AppRef, val inFolder: Boolean = false) : AssignmentSlot

}

object AssignmentSlots {
    /**
     * 割り当て済みの枠だけを並べる。並びは キー(キーボードの並び順)→ 機能列(送話・戻る・終話)→ カバーのカメラの枠
     * → メインの自由枠(M1〜M8)。
     * KeyLayout に無いキー ID(手で編集したファイル等)は出さない(画面に枠が無く、変更も解除もできないため)。
     * defaults を渡すと、未割り当ての送話キー・カメラの枠に端末の既定のアプリを isDefault = true で同じ位置に入れる
     * (自分で割り当てていれば既定は出さない)。
     */
    fun assigned(a: Assignments, defaults: DefaultApps = DefaultApps()): List<AssignmentSlot> = buildList {
        KeyLayout.assignableKeys.forEach { spec -> a.keys[spec.id]?.let { add(AssignmentSlot.Key(spec, it)) } }
        KeyLayout.assignableFunctionKeys.forEach { key ->
            key.assignId?.let { id ->
                val own = a.keys[id]
                when {
                    own != null -> add(AssignmentSlot.Function(key, own))
                    key == FunctionKey.SEND && defaults.dialer != null -> add(AssignmentSlot.Function(key, defaults.dialer, isDefault = true))
                }
            }
        }
        val camera = a.keys[KeyLayout.DOCK_CAMERA_ID]
        when {
            camera != null -> add(AssignmentSlot.DockCamera(camera))
            defaults.camera != null -> add(AssignmentSlot.DockCamera(defaults.camera, isDefault = true))
        }
        repeat(KeyLayout.MAIN_SLOT_COUNT) { i ->
            val id = KeyLayout.mainSlotId(i)
            a.keys[id]?.let { add(AssignmentSlot.MainSlot(i, it)) }
            // フォルダの中のアプリも同じ枠として並べる(中の並び=選んだ順)。
            a.folders[id]?.apps?.forEach { add(AssignmentSlot.MainSlot(i, it, inFolder = true)) }
        }
    }

    /**
     * 割り当て一覧の「解除」(§8): その枠からそのアプリを外す。フォルダの中ならフォルダからそのアプリだけ外す
     * (最後の 1 個ならフォルダごと消えて空き枠に戻る)。既定のアプリ(isDefault)は外せないので変えない。
     */
    fun clear(a: Assignments, slot: AssignmentSlot): Assignments = when {
        slot.isDefault -> a
        slot is AssignmentSlot.Key -> a.withKey(slot.spec.id, null)
        slot is AssignmentSlot.Function -> slot.key.assignId?.let { a.withKey(it, null) } ?: a
        slot is AssignmentSlot.DockCamera -> a.withKey(KeyLayout.DOCK_CAMERA_ID, null)
        slot is AssignmentSlot.MainSlot && slot.inFolder -> {
            val id = KeyLayout.mainSlotId(slot.index)
            val folder = a.folders[id]
            if (folder == null) a else a.withFolder(id, folder.copy(apps = folder.apps - slot.app))
        }
        slot is AssignmentSlot.MainSlot -> a.withKey(KeyLayout.mainSlotId(slot.index), null)
        else -> a
    }

    /**
     * 設定の割り当て一覧の並び(§8): アプリ名の順(sortByName。§6 のアプリ一覧と同じ「A#あ」)。
     * 名前が引けないアプリはパッケージ名で比べる(画面の表示と同じ)。同じ名前の別アプリが混ざらないよう
     * 先にパッケージ名・アクティビティ名で並べてから名前で安定ソートし、同じアプリの枠どうしは assigned の枠の並びのまま。
     */
    fun byAppName(a: Assignments, defaults: DefaultApps = DefaultApps(), label: (AppRef) -> String?): List<AssignmentSlot> {
        val byApp = assigned(a, defaults).sortedWith(compareBy({ it.app.packageName }, { it.app.activity }))
        return sortByName(byApp) { label(it.app) ?: it.app.packageName }
    }

    /**
     * 割り当て一覧の行(§8。v0.6.2 でアプリごとに 1 行)。byAppName の並びで、同じアプリの枠を 1 つにまとめる
     * (byAppName は同じアプリを必ず続けて並べる)。行の中の枠は assigned の枠の並び。
     */
    fun byApp(a: Assignments, defaults: DefaultApps = DefaultApps(), label: (AppRef) -> String?): List<AppSlots> {
        val rows = mutableListOf<AppSlots>()
        byAppName(a, defaults, label).forEach { slot ->
            val last = rows.lastOrNull()
            if (last != null && last.app == slot.app) rows[rows.lastIndex] = last.copy(slots = last.slots + slot)
            else rows += AppSlots(slot.app, listOf(slot))
        }
        return rows
    }
}

/** 割り当て一覧の 1 行: アプリと、そのアプリを割り当てた枠(1 つ以上)。 */
data class AppSlots(val app: AppRef, val slots: List<AssignmentSlot>)
