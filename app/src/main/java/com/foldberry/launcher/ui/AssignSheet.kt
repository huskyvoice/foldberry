package com.foldberry.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.filterByName
import com.foldberry.launcher.keyboard.AssignmentBadge
import com.foldberry.launcher.keyboard.AssignmentSlot
import com.foldberry.launcher.ui.icons.FoldBerryIcons

/**
 * 割り当て編集シート(仕様書 §7)。本体部の中で下から出す(メイン構成でも折り目をまたがない)。v0.6.2 で案 C(docs/mock/lists-mock.html)。
 * 内容: 帯(題「G キーに割り当て」・右に今のアプリ名)/ 名前で絞り込める検索欄(端末の通常の文字入力。アクセントの下線)/
 * 割り当て済みなら今のアプリの行(左にアクセントの線とチェック)と「割り当てを解除」を一覧の上に固定 /
 * 一覧(各行の右に割り当て済みの枠のバッジを全部)。
 */
@Composable
fun AssignSheet(
    title: String,
    current: AppRef?,
    assignments: Assignments,
    catalogState: CatalogState,
    catalog: AppCatalog,
    onPick: (AppRef) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    /** 「フォルダを作る」(メインの自由枠だけ。§4.5)。null なら行を出さない。 */
    onMakeFolder: (() -> Unit)? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val currentLabel = current?.let { ref -> (catalogState as? CatalogState.Loaded)?.apps?.firstOrNull { it.ref == ref }?.label }
    SheetFrame(title = title, onDismiss = onDismiss, trailing = currentLabel, bounce = true) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = SheetText, fontSize = 16.sp),
            cursorBrush = SolidColor(SheetAccent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp)
                .bottomLine(SheetAccent)
                .padding(vertical = 8.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineIcon(FoldBerryIcons.Line.Search, SheetTextDim)
                    Spacer(Modifier.width(10.dp))
                    Box {
                        if (query.isEmpty()) BasicText(stringResource(R.string.assign_filter_hint), style = TextStyle(color = SheetTextDim, fontSize = 16.sp))
                        inner()
                    }
                }
            },
        )
        when (catalogState) {
            CatalogState.Loading -> SheetMessage(stringResource(R.string.common_loading))
            CatalogState.Failed -> SheetMessage(stringResource(R.string.catalog_failed))
            is CatalogState.Loaded -> {
                val matched = filterByName(catalogState.apps, query) { it.label }
                val currentEntry = current?.let { ref -> catalogState.apps.firstOrNull { it.ref == ref } }
                // 割り当て済みなら、今のアプリの行と「割り当てを解除」を一覧の外(上)に固定する。
                // スクロールしても解除が隠れず、今のアプリを探さずに済むように(2026-11-19 オーナー要望)。絞り込み中も出す。
                if (currentEntry != null) {
                    AppRow(
                        currentEntry, selected = true, badges = AssignmentBadge.slotsOf(assignments, currentEntry.ref, catalogState.defaults),
                        catalog = catalog, onClick = { onPick(currentEntry.ref) },
                    )
                }
                if (current != null) {
                    CRow(FoldBerryIcons.Line.Trash, stringResource(R.string.assign_clear), onClick = onClear, titleColor = SheetDanger)
                }
                // 固定した今のアプリは一覧から除く(二重に出さない)。
                val shown = if (currentEntry == null) matched else matched.filter { it.ref != currentEntry.ref }
                LazyColumn(Modifier.fillMaxWidth()) {
                    if (onMakeFolder != null) {
                        item(key = "folder") {
                            CRow(
                                FoldBerryIcons.Line.Folder, stringResource(R.string.folder_make),
                                pluralStringResource(R.plurals.folder_make_desc, Assignments.MAX_FOLDER_APPS, Assignments.MAX_FOLDER_APPS), onClick = onMakeFolder,
                            ) { CChevron() }
                        }
                    }
                    // 「該当なし」は絞り込みに今のアプリも当たらないときだけ(今のアプリだけが当たるなら上の固定行で足りる)。
                    if (matched.isEmpty()) {
                        item(key = "none") { SheetMessage(stringResource(R.string.assign_no_match)) }
                    }
                    items(shown, key = { "${it.ref.packageName}/${it.ref.activity}" }) { entry ->
                        AppRow(
                            entry, selected = entry.ref == current, badges = AssignmentBadge.slotsOf(assignments, entry.ref, catalogState.defaults),
                            catalog = catalog, onClick = { onPick(entry.ref) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(entry: AppEntry, selected: Boolean, badges: List<AssignmentSlot>, catalog: AppCatalog, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (selected) SheetAccent.copy(alpha = 0x14 / 255f) else Color.Transparent)
            .drawBehind { if (selected) drawRect(SheetAccent, size = Size(3.dp.toPx(), size.height)) }
            .bottomLine()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIcon(catalog, entry.ref)
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
        } else {
            Box(Modifier.size(36.dp).background(SheetFieldColor, RoundedCornerShape(9.dp)))
        }
        Spacer(Modifier.width(14.dp))
        BasicText(
            entry.label, Modifier.weight(1f),
            style = TextStyle(color = SheetText, fontSize = 16.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        badges.forEach { badge ->
            Spacer(Modifier.width(6.dp))
            Badge(AssignmentBadge.label(badge), Modifier, edge = SheetColor, dashed = badge.isDefault)
        }
        if (selected) {
            Spacer(Modifier.width(12.dp))
            CRoundCheck()
        }
    }
}
