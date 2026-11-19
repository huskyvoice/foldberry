package com.foldberry.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
) {
    // 高さ 0.7: メイン縦の一覧(上半分)でも帯+行が収まるように(0.6 だと「アンインストール」が切れた)。
    SheetFrame(title = entry.label, onDismiss = onDismiss, heightFraction = 0.7f, bottomInset = bottomInset) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CRow(FoldBerryIcons.Line.Keyboard, stringResource(R.string.menu_assign_key), stringResource(R.string.menu_assign_key_desc), onClick = onAssignKey)
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
