package com.foldberry.launcher.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.foldberry.launcher.BuildConfig
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AssignmentStore
import com.foldberry.launcher.data.AssignmentsCodec
import com.foldberry.launcher.data.BackgroundPhoto
import com.foldberry.launcher.data.BackgroundPreset
import com.foldberry.launcher.data.BackgroundSetting
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.DateStyle
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.data.HubPreview
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.data.ImportResult
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.data.Pictogram
import com.foldberry.launcher.data.SettingsTransfer
import com.foldberry.launcher.data.clearedAssignments
import com.foldberry.launcher.data.importSettings
import com.foldberry.launcher.data.resetSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 「通知へのアクセス」の許可画面を開く(設定シートと、未許可の Hub の案内から。§4.6 / §10)。
 * 個別の許可画面が無い端末では一覧画面にする。
 */
fun openNotificationAccessSettings(context: Context) {
    val component = ComponentName(context, NotificationCounter::class.java).flattenToString()
    val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component)
    if (!startSettings(context, detail)) startSettings(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

private fun startSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    Log.w(LOG_TAG, "設定画面を開けません: ${intent.action}", e)
    false
}

/** 設定シートの中のページ(§8)。割り当て一覧は同じシートの中で切り替える(2026-11-17 オーナー決定)。 */
enum class SettingsPage { MAIN, ASSIGNMENTS }

/**
 * 設定シートの状態と、書出し/読込み(仕様書 §8 / §9)。
 *
 * HomeScreen(開閉で作り直されない層)で持つ。BerryBody はカバー/メインで別の位置から呼ばれ、開閉のたびに
 * 作り直されるので、そこに置くと「ファイル画面を開いている間に開閉 → 選んだ結果が何も知らされず消える」ことになる。
 * シートの表示は開閉で閉じる(§3.1)が、届いた読込み結果(置き換えの確認待ち)は残し、開閉後の画面で確認を出す。
 */
@Stable
class SettingsController internal constructor(
    private val appContext: Context,
    private val store: AssignmentStore,
    private val onExport: () -> Unit,
    private val onImport: () -> Unit,
    private val onPickPhoto: () -> Unit,
    private val onConfirm: (ImportResult.Ok) -> Unit,
    val versionText: String,
) {
    /** 背景を内蔵プリセットにする(§4.4)。即時保存。 */
    fun selectPreset(preset: BackgroundPreset) = store.update {
        it.copy(background = it.background.copy(kind = BackgroundSetting.KIND_PRESET, preset = preset.id))
    }

    /** オーバーレイの濃さ(%)を変える(§4.4 / §8)。即時保存。 */
    fun setOverlay(percent: Int) = store.update {
        it.copy(background = it.background.copy(overlay = percent.coerceIn(0, BackgroundSetting.MAX_OVERLAY)))
    }

    /** 大時計の日付・曜日の書式を変える(§4.2 / §8)。即時保存。 */
    fun setDateStyle(style: DateStyle) = store.update { it.copy(clock = it.clock.copy(dateStyle = style.id)) }

    /** 割り当てアイコンの出し方(標準・白黒・絵記号)を変える(§5.2 / §8)。即時保存。 */
    fun setIconStyle(style: IconStyle) = store.update { it.copy(appearance = it.appearance.withStyle(style)) }

    /** アプリの絵記号を選ぶ(null=自動に戻す。§5.2)。パッケージ単位なので同じアプリを置いた他の枠にも効く。即時保存。 */
    fun setPictogram(packageName: String, pictogram: Pictogram?) =
        store.update { it.copy(appearance = it.appearance.withPictogram(packageName, pictogram)) }

    /** 大時計の 12/24 時間表記を変える(§4.2 / §8)。即時保存。 */
    fun setHourFormat(format: HourFormat) = store.update { it.copy(clock = it.clock.copy(hourFormat = format.id)) }

    /** 触覚フィードバックのオン/オフ(§5.4 / §8)。即時保存。 */
    fun setHaptics(on: Boolean) = store.update { it.copy(feedback = it.feedback.copy(haptics = on)) }

    /** メインの一覧の枠の 4×2 マスに出す部品(カレンダー/Hub。§4.5 / §8)。即時保存。 */
    fun setMainPanel(kind: MainPanelKind) = store.update { it.copy(hub = it.hub.copy(mainPanel = kind.id)) }

    /** カバーのタイル部に出す部品(カレンダー/Hub。§4.3.1 / §8。v0.7.3)。即時保存。 */
    fun setCoverPanel(kind: MainPanelKind) = store.update { it.copy(hub = it.hub.copy(coverPanel = kind.id)) }

    /** Hub の本文の出し方(§4.6 / §8)。即時保存。 */
    fun setHubPreview(preview: HubPreview) = store.update { it.copy(hub = it.hub.copy(preview = preview.id)) }

    /** 端末の写真から背景を選ぶ(標準の写真選択画面。ストレージ権限なし)。 */
    fun pickPhoto() = onPickPhoto()

    var visible by mutableStateOf(false)
    var pendingImport by mutableStateOf<ImportResult.Ok?>(null)

    /** 今出しているページ。閉じる・開閉でメインに戻す。 */
    var page by mutableStateOf(SettingsPage.MAIN)

    /** 割り当て一覧のスクロール位置(「変更」で割り当てシートを往復しても保つ)。一覧をトップから開くたびに先頭へ戻す。 */
    var assignmentScroll by mutableStateOf(ScrollState(0))
        private set

    /** 割り当て一覧の「すべて解除」の確認を出しているか。 */
    var confirmClearAll by mutableStateOf(false)

    /** 「設定を初期化」の確認を出しているか(§8。2026-11-20)。 */
    var confirmReset by mutableStateOf(false)

    /** 設定シートを出すか(開いている、または読込みの確認待ち)。 */
    val showing: Boolean get() = visible || pendingImport != null

    fun open() { visible = true }

    fun openAssignments() {
        confirmClearAll = false
        assignmentScroll = ScrollState(0)
        page = SettingsPage.ASSIGNMENTS
    }

    /** 設定を初期状態に戻す(確認の後。§8)。割り当ては残す(resetSettings)。 */
    fun resetSettings() {
        store.update { it.resetSettings() }
        confirmReset = false
    }

    /** 割り当てを全部外す(確認の後。§8)。背景などの設定は残す。 */
    fun clearAllAssignments() {
        store.update { it.clearedAssignments() }
        confirmClearAll = false
    }

    /**
     * 戻る(機能列・端末の戻る): 手前から 1 段ずつ戻す。読込みの確認 → すべて解除の確認 → 割り当て一覧 → シートを閉じる の順。
     */
    fun back() {
        when {
            pendingImport != null -> pendingImport = null
            confirmClearAll -> confirmClearAll = false
            confirmReset -> confirmReset = false
            page != SettingsPage.MAIN -> page = SettingsPage.MAIN
            else -> dismiss()
        }
    }

    fun startExport() = onExport()

    fun startImport() = onImport()

    fun confirmImport() {
        pendingImport?.let(onConfirm)
        pendingImport = null
        visible = false
    }

    fun cancelImport() { pendingImport = null }

    fun dismiss() {
        pendingImport = null
        visible = false
        resetPage()
    }

    /** メインのページに戻す(BBメニューで開き直したときもメインから。§8)。 */
    fun resetPage() {
        page = SettingsPage.MAIN
        confirmClearAll = false
        confirmReset = false
    }

    /** 画面の言語を切り替える(§8)。OS が Activity を作り直すので、シートは閉じてホームに戻る。 */
    fun selectLanguage(tag: String) = AppLanguage.set(appContext, tag)

    /** 「通知へのアクセス」の許可画面を開く(時計の横の通知数・Hub。§4.2 / §4.6 / §10)。 */
    fun openNotificationAccess() = openNotificationAccessSettings(appContext)

    /** 端末の「既定のホームアプリ」画面(§8)。無い端末では設定のトップを開く。 */
    fun openHomeSettings() {
        if (!startSettings(appContext, Intent(Settings.ACTION_HOME_SETTINGS))) startSettings(appContext, Intent(Settings.ACTION_SETTINGS))
    }

    /** このアプリの「アプリ情報」画面(Bluetooth・電話の許可を変える。§10)。 */
    fun openAppPermissions() {
        startSettings(appContext, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", appContext.packageName, null)))
    }

    /** 開閉したとき: シートは閉じる(§3.1)。確認待ちの読込み結果は残す。 */
    fun onLayoutChanged() {
        visible = false
        resetPage()
    }
}

@Composable
fun rememberSettingsController(): SettingsController {
    val context = LocalContext.current
    val app = context.applicationContext as FoldBerryApp
    val appContext = context.applicationContext
    fun toast(text: String) = Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()

    // 結果の受け取りから先はアプリ全体のスコープで行い、画面の開閉・回転で止まらないようにする。
    lateinit var controller: SettingsController

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // キャンセル
        val text = AssignmentsCodec.encode(app.store.state.value)
        app.appScope.launch(Dispatchers.Main) {
            toast(if (SettingsTransfer.write(appContext, uri, text)) appContext.getString(R.string.export_done) else appContext.getString(R.string.export_failed))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // キャンセル
        app.appScope.launch(Dispatchers.Main) {
            when (val read = SettingsTransfer.read(appContext, uri)) {
                SettingsTransfer.ReadResult.TooLarge -> toast(appContext.getString(R.string.import_too_large))
                SettingsTransfer.ReadResult.Failed -> toast(appContext.getString(R.string.import_read_failed))
                is SettingsTransfer.ReadResult.Ok -> {
                    val installed = (app.catalog.state.value as? CatalogState.Loaded)?.apps?.map { it.ref }
                    when (val result = importSettings(read.text, installed)) {
                        is ImportResult.Invalid -> {
                            Log.w(LOG_TAG, "設定ファイルとして読めません: ${result.reason}")
                            toast(appContext.getString(R.string.import_invalid))
                        }
                        // 置き換える前に確認を出す(§8)。
                        is ImportResult.Ok -> controller.pendingImport = result
                    }
                }
            }
        }
    }

    // 背景の写真(§4.4): 選んだ画像を縮小してアプリ内にコピーしてから、背景を「写真」にする。
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // キャンセル
        app.appScope.launch(Dispatchers.Main) {
            if (BackgroundPhoto.importFrom(appContext, uri)) {
                app.store.update { it.copy(background = it.background.copy(kind = BackgroundSetting.KIND_PHOTO)) }
            } else {
                toast(appContext.getString(R.string.photo_failed))
            }
        }
    }

    controller = remember {
        SettingsController(
            appContext = appContext,
            store = app.store,
            onExport = { exportLauncher.launch(SettingsTransfer.defaultFileName()) },
            onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
            onPickPhoto = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onConfirm = { result ->
                app.store.update { result.assignments }
                val missing = result.missing ?: 0
                val base = if (missing > 0) appContext.resources.getQuantityString(R.plurals.import_done_missing, missing, missing) else appContext.getString(R.string.import_done)
                // 書出しには写真そのものを含めない(§9)。この端末に写真が無ければ選び直してもらう。
                val photoNote = if (result.assignments.background.isPhoto && !BackgroundPhoto.exists(appContext)) {
                    "\n" + appContext.getString(R.string.import_photo_note)
                } else {
                    ""
                }
                toast(base + photoNote)
            },
            versionText = versionText(appContext),
        )
    }
    return controller
}

/**
 * 設定シートに出すバージョン(§8)。ビルド日時は scripts/fb.sh build が version.properties に書いた buildTime(日本時間)。
 * build を通さずに作った APK(check だけ等)は空なので出さない。
 */
private fun versionText(context: Context): String {
    val base = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "FoldBerry ${info.versionName}(${info.longVersionCode})"
    } catch (e: Exception) {
        "FoldBerry"
    }
    val built = BuildConfig.BUILD_TIME
    return if (built.isBlank()) base else context.getString(R.string.version_with_build, base, built)
}
