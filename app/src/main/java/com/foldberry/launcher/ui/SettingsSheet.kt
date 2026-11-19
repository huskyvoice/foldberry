package com.foldberry.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.BackgroundPreset
import com.foldberry.launcher.data.BackgroundSetting
import com.foldberry.launcher.data.ClockText
import com.foldberry.launcher.data.DateStyle
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.data.HubPreview
import com.foldberry.launcher.data.ImportResult
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.assignedCount
import com.foldberry.launcher.data.nameGroup
import com.foldberry.launcher.keyboard.AppSlots
import com.foldberry.launcher.keyboard.AssignmentBadge
import com.foldberry.launcher.keyboard.AssignmentSlot
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.screen.drawPreset
import com.foldberry.launcher.ui.screen.rememberClockLanguage
import java.time.LocalDate
import kotlin.math.abs

/** オーバーレイの濃さの段階(%)。0〜70(§4.4)。 */
private val OverlaySteps = listOf(0, 10, 20, 30, 40, 50, 60, 70)

/**
 * 割り当て一覧ページ(§8)に渡すもの。
 * @param rows アプリごとの行(AssignmentSlots.byApp。保存の状態から毎回作る)
 * @param count 割り当て済みの枠の数(題の右と「すべて解除」の確認に出す)
 * @param appLabel アプリ名(一覧の読込み前・端末に無いときは null)
 * @param appIcon アプリのアイコン
 */
class AssignmentListModel(
    val rows: List<AppSlots>,
    val count: Int,
    val confirmClearAll: Boolean,
    /** 一覧のスクロール位置。シートは閉じる動きの後に破棄されるので、「変更」から戻っても同じ位置になるよう外で持つ。 */
    val scroll: ScrollState,
    val appLabel: (AppRef) -> String?,
    val appIcon: @Composable (AppRef) -> ImageBitmap?,
    val onOpen: () -> Unit,
    val onBack: () -> Unit,
    val onEdit: (AssignmentSlot) -> Unit,
    val onClear: (AssignmentSlot) -> Unit,
    val onAskClearAll: () -> Unit,
    val onConfirmClearAll: () -> Unit,
    val onCancelClearAll: () -> Unit,
)

/**
 * 設定シート(仕様書 §8。BBメニューキーで開く)。v0.6.2 で案 C(BlackBerry 10 風。docs/mock/settings-mock.html)に。
 * 小見出し: 背景(見本・暗さ)/ 表示(言語・時刻の表記・日付の書き方・グレー表示)/ キーボード(割り当て一覧・触覚)/
 * ステータス行(通知の件数・Bluetooth と電波)/ システム(ホームアプリ・書き出し・読み込み)、末尾にバージョン。
 * 帯の ‹ は onBack(= SettingsController.back。端末の戻ると同じ 1 段ずつ)。
 *
 * @param pendingImport 選んだファイルの内容(置き換えの確認待ち)。null なら確認表示を出さない
 */
@Composable
fun SettingsSheet(
    versionText: String,
    page: SettingsPage,
    assignmentList: AssignmentListModel,
    pendingImport: ImportResult.Ok?,
    background: BackgroundSetting,
    language: String,
    dateStyle: DateStyle,
    hourFormat: HourFormat,
    grayscaleIcons: Boolean,
    haptics: Boolean,
    notificationAllowed: Boolean,
    mainPanel: MainPanelKind,
    coverPanel: MainPanelKind,
    hubPreview: HubPreview,
    onSelectMainPanel: (MainPanelKind) -> Unit,
    onSelectCoverPanel: (MainPanelKind) -> Unit,
    onSelectHubPreview: (HubPreview) -> Unit,
    onSelectPreset: (BackgroundPreset) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onSelectDateStyle: (DateStyle) -> Unit,
    onSelectHourFormat: (HourFormat) -> Unit,
    onGrayscaleIcons: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onNotificationAccess: () -> Unit,
    onAppPermissions: () -> Unit,
    onHomeSettings: () -> Unit,
    onPickPhoto: () -> Unit,
    onOverlay: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val onList = page == SettingsPage.ASSIGNMENTS && pendingImport == null
    val title = stringResource(if (onList) R.string.assign_list_title else R.string.settings_title)
    val trailing = if (onList && !assignmentList.confirmClearAll && assignmentList.count > 0) {
        pluralStringResource(R.plurals.assign_list_total, assignmentList.count, assignmentList.count)
    } else {
        null
    }
    SheetFrame(title = title, onDismiss = onDismiss, onBack = assignmentList.onBack, trailing = trailing, bounce = true) {
        if (pendingImport != null) {
            ImportConfirm(pendingImport, onConfirmImport, onCancelImport)
            return@SheetFrame
        }
        if (page == SettingsPage.ASSIGNMENTS) {
            AssignmentListPage(assignmentList)
            return@SheetFrame
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CSectionTitle(stringResource(R.string.section_background))
            BackgroundSection(background, onSelectPreset, onPickPhoto, onOverlay)

            CSectionTitle(stringResource(R.string.section_display))
            // 値は行のタップで次へ順送り(案 C)。言語は 2 つなのでもう一方へ(切り替えると OS が作り直し、シートは閉じる)。
            val languageName = if (language == AppLanguage.ENGLISH) "English" else "日本語"
            CRow(FoldBerryIcons.Line.Globe, stringResource(R.string.section_language), onClick = {
                onSelectLanguage(if (language == AppLanguage.ENGLISH) AppLanguage.JAPANESE else AppLanguage.ENGLISH)
            }) { CValue(languageName) }
            CRow(FoldBerryIcons.Line.Clock, stringResource(R.string.hour_format_title), onClick = {
                onSelectHourFormat(HourFormat.entries.next(hourFormat))
            }) { CValue(stringResource(hourFormat.labelRes())) }
            val clockLanguage = rememberClockLanguage()
            val today = remember { LocalDate.now() }
            CRow(
                FoldBerryIcons.Line.Clock, stringResource(R.string.date_style_title),
                ClockText.date(today.year, today.monthValue, today.dayOfMonth, today.dayOfWeek.value, dateStyle, clockLanguage),
                onClick = { onSelectDateStyle(DateStyle.entries.next(dateStyle)) },
            ) { CValue(stringResource(dateStyle.labelRes())) }
            // メインの一覧の枠の 4×2 マス(§4.5)と Hub の本文(§4.6)。行のタップで順送り。
            CRow(FoldBerryIcons.Line.Calendar, stringResource(R.string.main_panel_title), stringResource(R.string.main_panel_desc), onClick = {
                onSelectMainPanel(MainPanelKind.entries.next(mainPanel))
            }) { CValue(stringResource(mainPanel.labelRes())) }
            CRow(FoldBerryIcons.Line.Calendar, stringResource(R.string.cover_panel_title), stringResource(R.string.cover_panel_desc), onClick = {
                onSelectCoverPanel(MainPanelKind.entries.next(coverPanel))
            }) { CValue(stringResource(coverPanel.labelRes())) }
            CRow(FoldBerryIcons.Line.Eye, stringResource(R.string.hub_preview_title), stringResource(R.string.hub_preview_desc), onClick = {
                onSelectHubPreview(HubPreview.entries.next(hubPreview))
            }) { CValue(stringResource(hubPreview.labelRes())) }
            CRow(FoldBerryIcons.Line.Drop, stringResource(R.string.grayscale_title), stringResource(R.string.grayscale_desc), onClick = {
                onGrayscaleIcons(!grayscaleIcons)
            }) { CCheck(grayscaleIcons) }

            CSectionTitle(stringResource(R.string.section_keyboard))
            val count = assignmentList.count
            CRow(
                FoldBerryIcons.Line.Keyboard, stringResource(R.string.assign_list_title),
                if (count == 0) stringResource(R.string.assign_list_empty) else pluralStringResource(R.plurals.assign_list_desc, count, count),
                onClick = assignmentList.onOpen,
            ) { CChevron() }
            CRow(FoldBerryIcons.Line.Vibrate, stringResource(R.string.haptics_title), stringResource(R.string.haptics_desc), onClick = {
                onHaptics(!haptics)
            }) { CCheck(haptics) }

            CSectionTitle(stringResource(R.string.section_status))
            CRow(
                FoldBerryIcons.Line.Bell, stringResource(R.string.notif_access_title),
                stringResource(if (notificationAllowed) R.string.notif_access_on else R.string.notif_access_off),
                onClick = onNotificationAccess,
            ) { CChevron() }
            CRow(FoldBerryIcons.Line.Signal, stringResource(R.string.perm_title), stringResource(R.string.perm_desc), onClick = onAppPermissions) { CChevron() }

            CSectionTitle(stringResource(R.string.section_system))
            CRow(FoldBerryIcons.Line.Home, stringResource(R.string.home_app_title), stringResource(R.string.home_app_desc), onClick = onHomeSettings) { CChevron() }
            CRow(FoldBerryIcons.Line.Upload, stringResource(R.string.export_title), stringResource(R.string.export_desc), onClick = onExport) { CChevron() }
            CRow(FoldBerryIcons.Line.Download, stringResource(R.string.import_title), stringResource(R.string.import_desc), onClick = onImport) { CChevron() }
            BasicText(versionText, Modifier.padding(18.dp), style = TextStyle(color = Color(0xFF5B5F68), fontSize = 12.sp))
        }
    }
}

/** 選択肢の次(最後の次は最初)。行のタップで値を順送りする(案 C)。 */
private fun <T> List<T>.next(current: T): T = this[(indexOf(current) + 1).mod(size)]

/**
 * 背景(§4.4 / §8): 枠線で区切った見本 5 マス(内蔵 4 種+写真)と、暗さの 8 段階。
 * 選択中のマスは薄いアクセントの背景と下端の線(案 C)。
 */
@Composable
private fun BackgroundSection(
    background: BackgroundSetting,
    onSelectPreset: (BackgroundPreset) -> Unit,
    onPickPhoto: () -> Unit,
    onOverlay: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 12.dp).height(IntrinsicSize.Min).border(1.dp, CFrame),
    ) {
        BackgroundPreset.entries.forEach { preset ->
            val selected = !background.isPhoto && BackgroundPreset.resolve(background.preset) == preset
            SwatchCell(stringResource(preset.labelRes()), selected, last = false, onClick = { onSelectPreset(preset) }) {
                // drawPreset は斜めの帯などをマスの外まで描くので切り抜く(切らないと暗さの区切りまではみ出す)。
                Canvas(Modifier.fillMaxWidth().height(70.dp).clipToBounds()) { drawPreset(preset) }
            }
        }
        // 写真は選び直しも同じマスから(選択中でももう一度選べる)。
        SwatchCell(stringResource(R.string.photo), background.isPhoto, last = true, onClick = onPickPhoto) {
            Box(Modifier.fillMaxWidth().height(70.dp).background(SheetFieldColor), contentAlignment = Alignment.Center) {
                LineIcon(FoldBerryIcons.Line.Image, SheetTextDim, 22.dp)
            }
        }
    }
    BasicText(
        stringResource(R.string.overlay_title),
        Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp),
        style = TextStyle(color = SheetTextDim, fontSize = 12.5.sp),
    )
    // 手で編集したファイルなどで 10% 刻み以外(35% 等)でも、いちばん近い段階を選択中として見せる。
    val nearest = OverlaySteps.minBy { abs(it - background.overlay) }
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 6.dp).height(IntrinsicSize.Min).border(1.dp, CFrame)) {
        OverlaySteps.forEachIndexed { i, step ->
            val selected = step == nearest
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(if (selected) SheetAccent else Color.Transparent)
                    .clickable { onOverlay(step) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    "$step",
                    style = TextStyle(
                        color = if (selected) Color(0xFF06141B) else Color(0xFFB8BCC4), fontSize = 12.5.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                )
            }
            if (i < OverlaySteps.lastIndex) Box(Modifier.width(1.dp).fillMaxHeight().background(CLine))
        }
    }
}

/** 背景の見本の 1 マス。右に区切り線(最後のマス以外)。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SwatchCell(
    label: String,
    selected: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxHeight()
            .background(if (selected) CSelected else Color.Transparent)
            .drawBehind {
                if (!last) drawLine(CLine, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                if (selected) {
                    val h = 3.dp.toPx()
                    drawLine(SheetAccent, Offset(0f, size.height - h / 2), Offset(size.width, size.height - h / 2), h)
                }
            }
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        preview()
        Spacer(Modifier.height(6.dp))
        BasicText(
            label,
            style = TextStyle(color = if (selected) SheetAccent else SheetTextDim, fontSize = 11.sp, textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 割り当て一覧ページ(§8。v0.6.2 で案 C・アプリごとに 1 行)。英字・#・あ の群ごとに小見出し。
 * 行=アイコン・アプリ名・割り当てた枠のキートップ。キートップをタップすると行の下に「枠の名前 | 変更 | 解除」の帯が開く
 * (もう一度タップで閉じる)。末尾の「すべて解除」は件数を出して確認してから外す。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentListPage(model: AssignmentListModel) {
    val resources = LocalResources.current
    if (model.confirmClearAll) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            BasicText(pluralStringResource(R.plurals.assign_list_clear_all_confirm, model.count, model.count), style = TextStyle(color = SheetText, fontSize = 15.sp))
            Spacer(Modifier.height(16.dp))
            Row {
                SheetButton(stringResource(R.string.assign_list_clear_all_ok), SheetDanger, model.onConfirmClearAll)
                Spacer(Modifier.width(12.dp))
                SheetButton(stringResource(R.string.import_cancel), SheetTextDim, model.onCancelClearAll)
            }
        }
        return
    }
    if (model.rows.isEmpty()) {
        BasicText(stringResource(R.string.assign_list_empty), Modifier.padding(18.dp), style = TextStyle(color = SheetTextDim, fontSize = 14.sp))
        return
    }
    // 開いている帯の枠。解除などで一覧から消えたら閉じた扱いにする。
    var selected by remember { mutableStateOf<AssignmentSlot?>(null) }
    val open = selected?.takeIf { s -> model.rows.any { s in it.slots } }
    val name: (AppSlots) -> String = { model.appLabel(it.app) ?: it.app.packageName }
    val groups = model.rows.groupBy { nameGroup(name(it)) }
    // 枠は最大 53 個なので LazyColumn にせず、まとめてスクロールする。
    Column(Modifier.fillMaxWidth().verticalScroll(model.scroll)) {
        // 並びは byApp のまま(群の順 = 英字 → # → 日本語)。groupBy は最初に出た順を保つ。
        groups.forEach { (group, rows) ->
            CSectionTitle(stringResource(group.groupLabelRes()), pluralStringResource(R.plurals.assign_list_group_count, rows.size, rows.size))
            rows.forEach { row ->
                key(row.app) {
                    Row(
                        Modifier.fillMaxWidth().bottomLine().padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = model.appIcon(row.app)
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                        else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            // 一覧の読込み前・端末に無いアプリは名前が引けないのでパッケージ名を出す。
                            BasicText(name(row), style = TextStyle(color = SheetText, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.slots.forEach { slot ->
                                    SlotKeycap(slot, selected = slot == open) { selected = if (slot == open) null else slot }
                                }
                            }
                        }
                    }
                    if (open != null && open in row.slots) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(SheetAccent.copy(alpha = 0x12 / 255f)).bottomLine()) {
                            BandCell(resources.slotName(open), SheetTextDim, 1.6f, onClick = null)
                            // 既定のアプリ(§8)は外せないので「変更」だけ(割り当てれば既定は消える)。
                            BandCell(stringResource(R.string.assign_list_change), SheetAccent, 1f, divider = !open.isDefault) { selected = null; model.onEdit(open) }
                            if (!open.isDefault) {
                                BandCell(stringResource(R.string.assign_list_clear), SheetDanger, 1f, divider = false) { selected = null; model.onClear(open) }
                            }
                        }
                    }
                }
            }
        }
        // 既定のアプリだけ(自分の割り当てが 0 件)なら外せるものが無いので出さない。
        if (model.count > 0) BasicText(
            stringResource(R.string.assign_list_clear_all),
            Modifier.fillMaxWidth().padding(18.dp)
                .border(1.dp, SheetDanger.copy(alpha = 0.4f))
                .clickable(onClick = model.onAskClearAll)
                .padding(12.dp),
            style = TextStyle(color = SheetDanger, fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

/** 帯の 1 区画(右に区切り線)。onClick が null なら押せない(枠の名前)。 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.BandCell(
    text: String,
    color: Color,
    weight: Float,
    divider: Boolean = true,
    onClick: (() -> Unit)?,
) {
    Box(
        Modifier.weight(weight).fillMaxHeight()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind { if (divider) drawLine(Color(0x10FFFFFF), Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(color = color, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * 枠のキートップ(割り当て一覧の行)。文字はバッジと同じ略記(AssignmentBadge.label)で、略記だけでは分かりにくい枠
 * (記号・修飾キー・機能列・カメラ・自由枠・タイル)には短い呼び名を添える。自由枠・タイルはアクセント色(バッジと同じ)。
 * 既定のアプリ(送話=電話・カメラの枠=カメラ)は点線の枠で、呼び名に「既定」を添える(§8。v0.6.2)。
 */
@Composable
private fun SlotKeycap(slot: AssignmentSlot, selected: Boolean, onClick: () -> Unit) {
    val resources = LocalResources.current
    val accent = slot is AssignmentSlot.MainSlot
    val shape = RoundedCornerShape(5.dp)
    Row(
        Modifier
            .height(24.dp)
            .then(
                when {
                    slot.isDefault -> Modifier.dashedOutline(DashedColor, 5.dp)
                    accent -> Modifier.background(SheetAccent, shape)
                    else -> Modifier.background(KeycapGradient, shape)
                },
            )
            .then(if (selected) Modifier.border(2.dp, if (accent) Color.White else SheetAccent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (slot.isDefault) DashedColor else if (accent) Color(0xFF06141B) else Color.White
        val label = AssignmentBadge.label(slot)
        // フォルダの中はバッジと同じくフォルダの印+番号(§8)。
        val folderNumber = AssignmentBadge.folderNumber(label)
        if (folderNumber != null) {
            Image(FoldBerryIcons.FolderMark, contentDescription = null, modifier = Modifier.size(11.dp), colorFilter = ColorFilter.tint(fg))
            Spacer(Modifier.width(2.dp))
        }
        BasicText(folderNumber ?: label, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        val hint = resources.slotHint(slot)
        (if (slot.isDefault) listOfNotNull(hint, resources.getString(R.string.slot_default)).joinToString("・") else hint)?.let { hint ->
            Spacer(Modifier.width(6.dp))
            BasicText(hint, style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 11.sp), maxLines = 1)
        }
    }
}

private val KeycapGradient = Brush.verticalGradient(listOf(Color(0xFF3A3D44), Color(0xFF1B1D21)))

/** 置き換える前の確認(§8 / §9)。端末に無いアプリがあれば件数も出す。 */
@Composable
private fun ImportConfirm(pending: ImportResult.Ok, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        BasicText(
            pluralStringResource(R.plurals.import_confirm_body, pending.assignments.assignedCount(), pending.assignments.assignedCount()),
            style = TextStyle(color = SheetText, fontSize = 15.sp),
        )
        val note = when {
            pending.missing == null -> stringResource(R.string.import_missing_unknown)
            pending.missing > 0 -> pluralStringResource(R.plurals.import_missing, pending.missing, pending.missing)
            else -> null
        }
        note?.let {
            Spacer(Modifier.height(6.dp))
            BasicText(it, style = TextStyle(color = SheetTextDim, fontSize = 13.sp))
        }
        Spacer(Modifier.height(16.dp))
        Row {
            SheetButton(stringResource(R.string.import_replace), SheetAccent, onConfirm)
            Spacer(Modifier.width(12.dp))
            SheetButton(stringResource(R.string.import_cancel), SheetTextDim, onCancel)
        }
    }
}
