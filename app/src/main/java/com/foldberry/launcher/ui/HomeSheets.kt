package com.foldberry.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.DefaultApps
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.keyboard.AssignmentSlots

/**
 * ホームのシート類: キーの選択待ちの帯・割り当てシート/フォルダの編集シート(§7)・設定シート(§8)。
 * 置き場所は構成で変わる(D25。2026-11-19): カバー・メイン横=本体部の中(BerryBody)、メイン縦=上半分の画面(PortraitTop)。
 * 中身はどこに置いても同じ。開閉の状態は HomeController が持つ。呼ぶ側の Box いっぱいに重ねる。
 *
 * @param topInset 選択待ちの帯を置く位置(ステータス行の下)
 */
@Composable
internal fun HomeSheets(home: HomeController, topInset: Dp) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    val catalogState by app.catalog.state.collectAsState()
    val settings = home.settings
    val picking = home.pickingApp
    // ショートカットは「Gmail: 新規作成」(labelOf)。
    val labelOf: (AppRef) -> String? = { ref -> (catalogState as? CatalogState.Loaded)?.labelOf(ref) }
    val catalogDefaults = (catalogState as? CatalogState.Loaded)?.defaults ?: DefaultApps()

    if (picking != null) {
        PickBanner(picking.label, onCancel = { home.pickingApp = null }, modifier = Modifier.padding(top = topInset))
    }
    AnimatedPanel(home.editing) { target ->
        when (target) {
            is EditTarget.App -> AssignSheet(
                title = target.title(resources),
                current = target.current(assignments),
                assignments = assignments,
                catalogState = catalogState,
                catalog = app.catalog,
                // 保存は即時。カバー/メインは同じ保存を共有する(§7)。
                onPick = { ref ->
                    app.store.update { target.assign(it, ref) }
                    home.finishEdit()
                },
                onClear = {
                    app.store.update { target.assign(it, null) }
                    home.finishEdit()
                },
                // 自由枠だけフォルダにできる(§4.5。オーナー決定)。同じシートをフォルダの編集に切り替える。
                onMakeFolder = (target as? EditTarget.MainSlot)?.let { slot -> { home.switchEdit(EditTarget.Folder(slot.index)) } },
                onDismiss = { home.finishEdit() },
                shortcutsOf = home.assignShortcutsOf,
                onShowShortcuts = { home.assignShortcutsOf = it },
                pictogramOpen = home.assignPictogramOpen,
                onShowPictogram = { home.assignPictogramOpen = it },
                onPickPictogram = settings::setPictogram,
            )
            is EditTarget.Folder -> FolderSheet(
                target = target,
                assignments = assignments,
                catalogState = catalogState,
                catalog = app.catalog,
                // 保存は即時(§7)。枠のアプリは消してフォルダに置き換える(空ならフォルダも外れて空き枠)。
                onChange = { folder -> app.store.update { it.withKey(target.slotId, null).withFolder(target.slotId, folder) } },
                onRemove = {
                    app.store.update { it.withFolder(target.slotId, null) }
                    home.finishEdit()
                },
                onDismiss = { home.finishEdit() },
                pictogramOpen = home.assignPictogramOpen,
                pictogramApp = home.folderPictogramApp,
                onShowPictogram = { home.assignPictogramOpen = it },
                onShowPictogramFor = { home.folderPictogramApp = it },
                onPickPictogram = settings::setPictogram,
            )
        }
    }
    AnimatedPanel(if (settings.showing) Unit else null) {
        val permissionTick by StatusPermissionEvents.tick.collectAsState()
        SettingsSheet(
            versionText = settings.versionText,
            page = settings.page,
            // 一覧は保存の状態から毎回作る(アンインストールで外れた枠は次の描画で消える)。
            assignmentList = AssignmentListModel(
                // 一覧はアプリ名順でアプリごとに 1 行(§8)。バッジは AssignmentSlots.assigned の枠の並びのまま使う。
                // 既定のアプリ(送話=電話・カメラの枠=カメラ)も「既定」として並べる。件数は自分で割り当てた枠だけ(既定は外せない)。
                rows = AssignmentSlots.byApp(assignments, catalogDefaults, labelOf),
                count = AssignmentSlots.assigned(assignments).size,
                confirmClearAll = settings.confirmClearAll,
                scroll = settings.assignmentScroll,
                appLabel = labelOf,
                appIcon = { ref -> rememberAppIcon(app.catalog, ref) },
                onOpen = settings::openAssignments,
                onBack = settings::back,
                onEdit = { slot -> home.editFromList(slot.editTarget()) },
                // フォルダの中のアプリはフォルダからそのアプリだけ外す(AssignmentSlots.clear)。
                onClear = { slot -> app.store.update { AssignmentSlots.clear(it, slot) } },
                onAskClearAll = { settings.confirmClearAll = true },
                onConfirmClearAll = settings::clearAllAssignments,
                onCancelClearAll = { settings.confirmClearAll = false },
            ),
            pendingImport = settings.pendingImport,
            background = assignments.background,
            language = AppLanguage.current(context),
            dateStyle = assignments.clock.style,
            onSelectDateStyle = settings::setDateStyle,
            hourFormat = assignments.clock.hours,
            onSelectHourFormat = settings::setHourFormat,
            iconStyle = assignments.appearance.style,
            onSelectIconStyle = settings::setIconStyle,
            haptics = assignments.feedback.haptics,
            onHaptics = settings::setHaptics,
            mainPanel = assignments.hub.panel,
            onSelectMainPanel = settings::setMainPanel,
            coverPanel = assignments.hub.cover,
            onSelectCoverPanel = settings::setCoverPanel,
            hubPreview = assignments.hub.previewMode,
            onSelectHubPreview = settings::setHubPreview,
            notificationIndicator = assignments.hub.indicatorMode,
            onSelectNotificationIndicator = settings::setNotificationIndicator,
            onHomeSettings = settings::openHomeSettings,
            // 許可画面から戻ったとき(MainActivity.onResume が合図を進める)に読み直す。
            notificationAllowed = remember(permissionTick) { NotificationCounter.isAllowed(context) },
            onSelectPreset = settings::selectPreset,
            onSelectLanguage = settings::selectLanguage,
            onNotificationAccess = settings::openNotificationAccess,
            onAppPermissions = settings::openAppPermissions,
            onPickPhoto = settings::pickPhoto,
            onOverlay = settings::setOverlay,
            onExport = settings::startExport,
            onImport = settings::startImport,
            onConfirmImport = settings::confirmImport,
            onCancelImport = settings::cancelImport,
            confirmReset = settings.confirmReset,
            onAskReset = { settings.confirmReset = true },
            onConfirmReset = settings::resetSettings,
            onCancelReset = { settings.confirmReset = false },
            onDismiss = settings::dismiss,
        )
    }
}

/**
 * キーの選択待ちの案内(画面部のステータス行の下に重ねる)。「中止」と戻るキーで抜ける。
 * 見た目は案 C(角 2dp・暗い帯の色・左端にアクセントの縦線・枠つきの文字ボタン。2026-11-18 オーナー要望で旧デザインの角丸から置き換え)。
 */
@Composable
private fun PickBanner(appLabel: String, onCancel: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(2.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .background(Color(0xFF121317), shape) // 不透明(半透明だと後ろの大時計が透けて読みにくい)
            .border(1.dp, CFrame, shape)
            // 左端の縦線は割り当てシートの選択行と同じ 3dp。
            .drawBehind { drawRect(SheetAccent, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 17.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(stringResource(R.string.pick_prompt, appLabel), style = TextStyle(color = SheetText, fontSize = 14.sp))
            Spacer(Modifier.height(2.dp))
            BasicText(stringResource(R.string.pick_cancel_hint), style = TextStyle(color = SheetTextDim, fontSize = 12.sp))
        }
        Spacer(Modifier.width(10.dp))
        SheetButton(stringResource(R.string.pick_cancel), SheetAccent, onCancel)
    }
}
