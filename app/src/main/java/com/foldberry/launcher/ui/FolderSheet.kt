package com.foldberry.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.AppFolder
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.filterByName
import com.foldberry.launcher.ui.icons.FoldBerryIcons

/**
 * 自由枠のフォルダの編集シート(§4.5 / §7。v0.7.1)。割り当てシートと同じ枠(SheetFrame・案 C)。
 * 内容: 帯(題「M3 のフォルダ」・右に「2/4」)/ 名前の欄 / 名前で絞り込める検索欄 /
 * フォルダがあれば「フォルダを解除」を一覧の上に固定 / 一覧(アプリの行は押すたびに入れる・外す。選んだ順に入る。満杯なら選んでいない行は薄く押せない)。
 * 保存は即時(onChange)。中身は保存から読む。開いたときに枠にアプリが入っていれば、そのアプリを入れた状態から始める(まだ保存はしない)。
 */
@Composable
internal fun FolderSheet(
    target: EditTarget.Folder,
    assignments: Assignments,
    catalogState: CatalogState,
    catalog: AppCatalog,
    onChange: (AppFolder) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val saved = assignments.folders[target.slotId]
    // 中身は保存があれば常に保存から読む(編集中にアンインストール・起動画面の付け替えで保存が直っても、古い参照を書き戻さない。
    // 2026-11-18 refuter の指摘)。保存がまだ無い間だけ、開いた時点の枠のアプリ 1 つから始める手元の並び(unsaved)を使う。
    var unsaved by remember(target) { mutableStateOf(listOfNotNull(assignments.keys[target.slotId])) }
    var name by remember(target) { mutableStateOf(saved?.name.orEmpty()) }
    val apps = saved?.apps ?: unsaved
    var query by rememberSaveable(target) { mutableStateOf("") }
    val max = Assignments.MAX_FOLDER_APPS

    fun update(nextApps: List<com.foldberry.launcher.data.AppRef>) {
        unsaved = nextApps
        onChange(AppFolder(name, nextApps))
    }

    SheetFrame(title = target.title(LocalResources.current), onDismiss = onDismiss, trailing = "${apps.size}/$max", bounce = true) {
        SheetTextField(
            value = name,
            onValueChange = {
                name = it
                // 中身が無いうちは保存しない(名前だけのフォルダは作らない)。
                if (apps.isNotEmpty()) onChange(AppFolder(it, apps))
            },
            icon = FoldBerryIcons.Line.Folder,
            hint = stringResource(R.string.folder_default_name),
        )
        SheetTextField(
            value = query,
            onValueChange = { query = it },
            icon = FoldBerryIcons.Line.Search,
            hint = stringResource(R.string.assign_filter_hint),
        )
        when (catalogState) {
            CatalogState.Loading -> SheetMessage(stringResource(R.string.common_loading))
            CatalogState.Failed -> SheetMessage(stringResource(R.string.catalog_failed))
            is CatalogState.Loaded -> {
                val shown = filterByName(catalogState.apps, query) { it.label }
                // 「フォルダを解除」は一覧の外(上)に固定し、スクロールしても隠れないようにする(割り当てシートと同じ。2026-11-19 オーナー要望)。
                if (saved != null) {
                    CRow(FoldBerryIcons.Line.Trash, stringResource(R.string.folder_remove), onClick = onRemove, titleColor = SheetDanger)
                }
                LazyColumn(Modifier.fillMaxWidth()) {
                    // 選択待ちから足したショートカット(2026-11-20)はアプリの一覧に無いので、先頭に選択済みとして出す(押すと外れる)。
                    items(apps.filter { it.shortcut != null }, key = { "sc/${it.packageName}/${it.shortcut}" }) { ref ->
                        FolderAppRow(
                            AppEntry(ref, catalogState.labelOf(ref) ?: ref.packageName), order = apps.indexOf(ref) + 1, enabled = true,
                            catalog = catalog, onClick = { update(apps - ref) },
                        )
                    }
                    if (shown.isEmpty()) {
                        item(key = "none") { SheetMessage(stringResource(R.string.assign_no_match)) }
                    }
                    items(shown, key = { "${it.ref.packageName}/${it.ref.activity}" }) { entry ->
                        val order = apps.indexOf(entry.ref)
                        val selected = order >= 0
                        val enabled = selected || apps.size < max
                        FolderAppRow(
                            entry, order = if (selected) order + 1 else null, enabled = enabled, catalog = catalog,
                            onClick = { update(if (selected) apps - entry.ref else apps + entry.ref) },
                        )
                    }
                }
            }
        }
    }
}

/** 案 C の入力欄(左に線画アイコン、アクセントの下線)。割り当てシートの検索欄と同じ見た目。 */
@Composable
private fun SheetTextField(value: String, onValueChange: (String) -> Unit, icon: ImageVector, hint: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
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
                LineIcon(icon, SheetTextDim)
                Spacer(Modifier.width(10.dp))
                Box {
                    if (value.isEmpty()) BasicText(hint, style = TextStyle(color = SheetTextDim, fontSize = 16.sp))
                    inner()
                }
            }
        },
    )
}

/** アプリの行。入っていれば右に入れた順の番号(丸)、入っていなければ空の丸。満杯で入れられない行は薄く押せない。 */
@Composable
private fun FolderAppRow(entry: AppEntry, order: Int?, enabled: Boolean, catalog: AppCatalog, onClick: () -> Unit) {
    val selected = order != null
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (selected) SheetAccent.copy(alpha = 0x14 / 255f) else Color.Transparent)
            .drawBehind { if (selected) drawRect(SheetAccent, size = Size(3.dp.toPx(), size.height)) }
            .bottomLine()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.35f)
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
        Spacer(Modifier.width(12.dp))
        if (order != null) {
            Box(Modifier.size(22.dp).background(SheetAccent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                BasicText(order.toString(), style = TextStyle(color = Color(0xFF06141B), fontSize = 12.sp))
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, Color(0xFF6A6F78), RoundedCornerShape(50)))
        }
    }
}
