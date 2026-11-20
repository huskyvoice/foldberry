package com.foldberry.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.ui.icons.FoldBerryIcons

/**
 * アプリ一覧の長押しメニュー(仕様書 §6): 「キーに割り当て」「アプリ情報」「アンインストール」。
 * (「タイルに割り当て」は 2026-11-19 に廃止。タイル T1・T2 は音楽と次の予定に固定した。D26)
 * 行は設定と同じ案 C の部品(CRow。2026-11-18 オーナー要望で旧デザインの行から置き換え)。
 *
 * @param bottomInset 一覧の枠の下端がナビゲーション領域に接しているか(SheetFrame 参照)
 */
@Composable
fun AppMenuSheet(
    entry: AppEntry,
    bottomInset: Boolean,
    onAssignKey: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    /** このアプリのショートカット(固定・動的。2026-11-20)。既定のホームでなければ空で、行を出さない。 */
    shortcuts: List<AppEntry> = emptyList(),
    /** ショートカットの行: そのショートカットでキーの選択待ちに入る(「キーに割り当て」と同じ流れ)。 */
    onAssignShortcut: (AppEntry) -> Unit = {},
) {
    // 高さ 0.7: メイン縦の一覧(上半分)でも帯+行が収まるように(0.6 だと「アンインストール」が切れた)。
    SheetFrame(title = entry.label, onDismiss = onDismiss, heightFraction = 0.7f, bottomInset = bottomInset) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CRow(FoldBerryIcons.Line.Keyboard, stringResource(R.string.menu_assign_key), stringResource(R.string.menu_assign_key_desc), onClick = onAssignKey)
            // ショートカットは「キーに割り当て」のすぐ下(割り当ての仲間なので)。行はショートカット自身のアイコン。
            if (shortcuts.isNotEmpty()) {
                CSectionTitle(stringResource(R.string.assign_shortcuts))
                shortcuts.forEach { sc -> ShortcutMenuRow(sc, onClick = { onAssignShortcut(sc) }) }
                CSectionTitle(stringResource(R.string.menu_app_section))
            }
            CRow(FoldBerryIcons.Line.Info, stringResource(R.string.menu_details), stringResource(R.string.menu_details_desc), onClick = onDetails)
            // 端末に組み込まれたアプリは消せないので、最初から押せない薄い行にする(AppEntry.removable。2026-11-18 オーナー決定)。
            if (entry.removable) {
                CRow(FoldBerryIcons.Line.Trash, stringResource(R.string.menu_uninstall), stringResource(R.string.menu_uninstall_desc), onClick = onUninstall, titleColor = SheetDanger)
            } else {
                CRow(FoldBerryIcons.Line.Trash, stringResource(R.string.menu_uninstall), stringResource(R.string.menu_uninstall_disabled_desc), onClick = null, titleColor = SheetTextDim)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** ショートカットの行(ショートカット自身のアイコン+名前+「キーに割り当て」)。CRow は線画アイコン用なので別に作る。 */
@Composable
private fun ShortcutMenuRow(entry: AppEntry, onClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as FoldBerryApp
    Row(
        Modifier.fillMaxWidth().height(64.dp).bottomLine().clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIcon(app.catalog, entry.ref)
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(28.dp)) else Spacer(Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText(entry.label, style = TextStyle(color = SheetText, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(stringResource(R.string.menu_assign_key), style = TextStyle(color = SheetTextDim, fontSize = 13.sp), maxLines = 1)
        }
    }
}
