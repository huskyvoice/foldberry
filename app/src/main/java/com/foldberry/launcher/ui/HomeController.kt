package com.foldberry.launcher.ui

import android.content.res.Resources
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.home.Panel
import com.foldberry.launcher.home.panelToClose
import com.foldberry.launcher.keyboard.AssignmentSlot
import com.foldberry.launcher.keyboard.FunctionKey
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.keyboard.KeySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 割り当て編集の対象(キー・枠・フォルダ)。同時に 1 つだけ編集する(§7)。 */
internal sealed interface EditTarget {
    /** シートの見出し(画面の言語で引く)。 */
    fun title(resources: Resources): String

    /** アプリ 1 つを割り当てる対象(割り当てシート AssignSheet)。 */
    sealed interface App : EditTarget {
        fun current(a: Assignments): AppRef?
        fun assign(a: Assignments, app: AppRef?): Assignments
    }

    /** メインの自由枠のフォルダ(フォルダの編集シート FolderSheet。§4.5 / §7。v0.7.1)。index は 0 始まり。 */
    data class Folder(val index: Int) : EditTarget {
        val slotId: String get() = KeyLayout.mainSlotId(index)
        override fun title(resources: Resources) = resources.getString(R.string.folder_edit_title, index + 1)
    }

    data class Key(val spec: KeySpec) : App {
        override fun title(resources: Resources) = resources.getString(R.string.assign_key_title, resources.keyName(spec))
        override fun current(a: Assignments) = a.keys[spec.id]
        override fun assign(a: Assignments, app: AppRef?) = a.withKey(spec.id, app)
    }

    /** 機能列の送話・戻る・終話(§5.3)。assignId のあるキーだけを渡す。 */
    data class Function(val key: FunctionKey) : App {
        private val id = requireNotNull(key.assignId) { "$key は割り当てできません" }
        override fun title(resources: Resources) = resources.getString(R.string.assign_key_title, resources.functionKeyName(key))
        override fun current(a: Assignments) = a.keys[id]
        override fun assign(a: Assignments, app: AppRef?) = a.withKey(id, app)
    }

    /** カバー画面の下の行の右端(カメラの枠。§4.3)。未割り当てなら端末のカメラを開く。 */
    data object DockCamera : App {
        override fun title(resources: Resources) = resources.getString(R.string.assign_dock_camera_title)
        override fun current(a: Assignments) = a.keys[KeyLayout.DOCK_CAMERA_ID]
        override fun assign(a: Assignments, app: AppRef?) = a.withKey(KeyLayout.DOCK_CAMERA_ID, app)
    }

    /** メイン画面の一覧の枠の自由枠(§4.5)。index は 0 始まり。 */
    data class MainSlot(val index: Int) : App {
        private val id = KeyLayout.mainSlotId(index)
        override fun title(resources: Resources) = resources.getString(R.string.assign_main_slot_title, index + 1)
        override fun current(a: Assignments) = a.keys[id]
        override fun assign(a: Assignments, app: AppRef?) = a.withKey(id, app)
    }
}

/** 割り当て一覧の枠から、割り当てシートの対象を作る(「変更」。§8)。フォルダの中のアプリはそのフォルダの編集シート。 */
internal fun AssignmentSlot.editTarget(): EditTarget = when (this) {
    is AssignmentSlot.Key -> EditTarget.Key(spec)
    is AssignmentSlot.Function -> EditTarget.Function(key)
    is AssignmentSlot.DockCamera -> EditTarget.DockCamera
    is AssignmentSlot.MainSlot -> if (inFolder) EditTarget.Folder(index) else EditTarget.MainSlot(index)
}

/**
 * ホームで開いているパネルの状態と、機能列の「戻る」「終話」「トラックパッド」(仕様書 §5.3 / §6)。
 *
 * HomeScreen(開閉で作り直されない層)で持つ。BerryBody・一覧はカバー/メインで別の位置から呼ばれて作り直されるため、
 * 「戻る=手前から 1 つ閉じる」「終話=全部閉じる」を正しく行うには、開いているものを 1 か所で知っている必要がある。
 * 端末の戻るは HomeScreen の BackHandler 1 本からここへ来る(シートごとに BackHandler を持たせると二重に効くため)。
 */
@Stable
class HomeController internal constructor(
    val settings: SettingsController,
    private val scope: CoroutineScope,
) {
    /** 割り当てシートの対象(キー・枠の長押し)。 */
    internal var editing by mutableStateOf<EditTarget?>(null)
        private set

    /**
     * 割り当てシートを設定の割り当て一覧の「変更」から開いたか。閉じたら一覧に戻す(§8)。
     * 終話・開閉・ほかのパネルを開いたときは戻さない(closePanels で落とす)。
     */
    private var returnToList = false

    /** カバー構成のアプリ一覧の重ね表示(トラックパッド)。 */
    var overlayOpen by mutableStateOf(false)

    /** メイン構成の一覧の枠に出したアプリ一覧(トラックパッド。普段はカレンダー+自由枠。§4.5)。 */
    var mainListOpen by mutableStateOf(false)

    /** メインの一覧の枠に開いているフォルダ(自由枠の番号。0 始まり。null=閉じている。§4.5。v0.7.1)。 */
    var openFolder by mutableStateOf<Int?>(null)

    /**
     * メインのカレンダーで当月以外を表示しているときのその月(null=当月)。家(下の行)・終話・開閉で当月に戻すため、
     * カレンダーの中ではなくここで持つ(家は本体部、カレンダーは一覧の枠と別の場所にある。§4.5)。
     */
    var mainCalendarMonth by mutableStateOf<YearMonth?>(null)

    /** アプリ一覧の長押しメニューの対象。 */
    var menuApp by mutableStateOf<AppEntry?>(null)

    /** 「キーに割り当て」でキーが押されるのを待っているアプリ。 */
    var pickingApp by mutableStateOf<AppEntry?>(null)

    /** いまカバー構成か(トラックパッドの動作が変わる)。HomeScreen が構成を決めるたびに入れる。 */
    var isCover by mutableStateOf(true)

    /** アプリ一覧のページ数(AppListPanel が一覧の大きさから決めて入れる)。pagerState より先に初期化する。 */
    var appPageCount by mutableIntStateOf(1)

    /** アプリ一覧のページ位置(ページ式。§6)。カバーの重ね表示とメインの一覧は同時に出ないので 1 つを共有する。 */
    val pagerState = PagerState { appPageCount }

    val openPanels: Set<Panel>
        get() = buildSet {
            if (settings.showing) add(Panel.SETTINGS)
            if (editing != null) add(Panel.ASSIGN)
            if (menuApp != null) add(Panel.APP_MENU)
            if (pickingApp != null) add(Panel.KEY_PICK)
            if (overlayOpen) add(Panel.APP_OVERLAY)
            if (mainListOpen) add(Panel.MAIN_LIST)
            if (openFolder != null) add(Panel.FOLDER)
        }

    /** 戻る(機能列・端末の戻る): 手前から 1 つ閉じる。何も開いていなければ何もしない。 */
    fun back() {
        when (panelToClose(openPanels)) {
            Panel.SETTINGS -> settings.back()
            Panel.ASSIGN -> finishEdit()
            Panel.APP_MENU -> menuApp = null
            Panel.KEY_PICK -> pickingApp = null
            Panel.APP_OVERLAY -> overlayOpen = false
            Panel.MAIN_LIST -> mainListOpen = false
            Panel.FOLDER -> openFolder = null
            null -> Unit
        }
    }

    /** 終話(機能列・ホームジェスチャー): すべて閉じてホーム初期状態に戻す。 */
    fun goHome() {
        closePanels()
        mainCalendarMonth = null
        settings.dismiss()
        scope.launch { pagerState.scrollToPage(0) }
    }

    /**
     * トラックパッド: カバーは一覧を重ねて表示。メインは一覧の枠をアプリ一覧に切り替える(1 ページ目から)。
     * メインで一覧を出しているときに押したら閉じてカレンダー+自由枠に戻す(2026-11-17 オーナー決定。§4.5)。
     */
    fun trackpad() {
        if (isCover) {
            dropEdit()
            overlayOpen = true
        } else if (mainListOpen) {
            menuApp = null
            mainListOpen = false
        } else {
            dropEdit()
            // 開いているフォルダは閉じて一覧に替える(同じ枠に重ねるものは 1 つだけ)。
            openFolder = null
            scope.launch { pagerState.scrollToPage(0) }
            mainListOpen = true
        }
    }

    /** 自由枠のフォルダのタップ: 一覧の枠に重ねて開く(§4.5)。 */
    fun showFolder(index: Int) {
        dropEdit()
        mainListOpen = false
        openFolder = index
    }

    /** 一覧からアプリを起動した・割り当てたとき: 一覧(カバーの重ね表示/メインの一覧)を閉じる。 */
    fun closeAppList() {
        overlayOpen = false
        mainListOpen = false
        openFolder = null
    }

    /** BBメニュー: 設定シートを開く(割り当てシートとは同時に出さない)。 */
    fun openSettings() {
        dropEdit()
        settings.resetPage()
        settings.open()
    }

    /**
     * キー・枠の長押しで割り当てシートを開く。設定の一覧から開いた編集の最中でも、別の対象に替えたら一覧へは戻さない
     * (メイン縦では上の枠がシートの暗幕の外にあり、開いたまま長押しできる)。
     */
    internal fun startEdit(target: EditTarget) {
        dropEdit()
        // 設定シートとは同時に出さない(openSettings と対)。メイン縦では上の枠が設定シートの暗幕の外にあり、
        // 開いたまま長押しできる。閉じないと割り当てシートが設定シートの下に隠れる。
        settings.dismiss()
        editing = target
    }

    /** 開いている割り当てシートを別の対象に替える(自由枠 → フォルダの編集)。一覧から開いていれば閉じたら一覧に戻すまま。 */
    internal fun switchEdit(target: EditTarget) {
        editing = target
    }

    /** 一覧の長押しメニューの「キーに割り当て」: 選択待ちにする。割り当てシート・設定シートは閉じ、一覧へも戻さない。 */
    fun startPicking(entry: AppEntry) {
        dropEdit()
        // 設定シートが残ると、選択待ちの帯とキーがその下に隠れる(startEdit と同じ理由)。
        settings.dismiss()
        pickingApp = entry
    }

    /** 割り当てシートを閉じ、一覧へは戻さない(ほかのパネルに切り替えるとき)。 */
    private fun dropEdit() {
        editing = null
        returnToList = false
    }

    /** 設定の割り当て一覧の「変更」: 設定シートを隠して割り当てシートを出す。閉じたら一覧に戻る(finishEdit)。 */
    internal fun editFromList(target: EditTarget) {
        settings.visible = false
        returnToList = true
        editing = target
    }

    /** 割り当てシートを閉じる(選んだ・解除した・閉じた・戻る)。一覧から開いていれば設定シートの一覧に戻す。 */
    fun finishEdit() {
        editing = null
        if (returnToList) {
            returnToList = false
            settings.open()
        }
    }

    /** 開閉(カバー⇔メイン)したとき: ホーム初期状態に戻す(§3.1)。読込みの確認待ちは残す(SettingsController 参照)。 */
    fun onLayoutChanged() {
        closePanels()
        mainCalendarMonth = null
        settings.onLayoutChanged()
        scope.launch { pagerState.scrollToPage(0) }
    }

    private fun closePanels() {
        dropEdit()
        menuApp = null
        pickingApp = null
        overlayOpen = false
        mainListOpen = false
        openFolder = null
    }
}

@Composable
fun rememberHomeController(): HomeController {
    val settings = rememberSettingsController()
    val scope = rememberCoroutineScope()
    return remember { HomeController(settings, scope) }
}
