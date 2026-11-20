package com.foldberry.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.foldberry.launcher.data.DefaultApps
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.data.Pictogram
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
    /** › で開いたアプリ(そのショートカットのページを出す。HomeController.assignShortcutsOf)。null ならアプリの一覧。 */
    shortcutsOf: AppEntry? = null,
    /** › を押した(アプリ)・ページの ‹(null)。 */
    onShowShortcuts: (AppEntry?) -> Unit = {},
    /** 今のアプリの絵記号を選ぶページを出す(HomeController.assignPictogramOpen。§5.2)。 */
    pictogramOpen: Boolean = false,
    /** 「絵記号」の行を押した(true)・ページの ‹(false)。 */
    onShowPictogram: (Boolean) -> Unit = {},
    /** 絵記号を選んだ(null=自動に戻す)。パッケージ単位で保存する。 */
    onPickPictogram: (String, Pictogram?) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    val loaded = catalogState as? CatalogState.Loaded
    val currentLabel = current?.let { loaded?.labelOf(it) }
    // 絵記号は割り当てアイコンを絵記号で出すときだけ選べる(オーナー決定 2026-11-20。docs/mock/appicon-mock.html の入口 A)。
    val pictograms = assignments.appearance.style == IconStyle.PICTOGRAM
    // ページを開いたまま割り当てが外れた(アンインストール等)・絵記号の表示をやめたときは、見えないフラグを下ろす
    // (残すと端末の戻るが 1 回空振りする。2026-11-20 反証で発見)。
    val canShowPictogram = pictograms && current != null
    LaunchedEffect(pictogramOpen, canShowPictogram) { if (pictogramOpen && !canShowPictogram) onShowPictogram(false) }
    if (pictogramOpen && canShowPictogram && current != null) {
        PictogramPage(
            appLabel = loaded?.entryOf(current)?.label ?: currentLabel ?: current.packageName,
            chosen = assignments.appearance.pictograms[current.packageName]?.let { Pictogram.of(it) },
            auto = rememberAutoPictogram(catalog, current.packageName),
            onPick = { onPickPictogram(current.packageName, it) },
            onBack = { onShowPictogram(false) },
            onDismiss = onDismiss,
        )
        return
    }
    // ショートカット(固定・動的。2026-11-20)。読めるのは既定のホームのときだけで、読めなければ空(› が出ない)。
    val shortcuts by produceState(emptyMap<AppRef, List<AppEntry>>(), catalogState) { value = catalog.shortcuts() }
    if (shortcutsOf != null) {
        ShortcutPage(shortcutsOf, shortcuts[shortcutsOf.ref].orEmpty(), current, assignments, loaded, catalog, onPick, onShowShortcuts, onDismiss)
        return
    }
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
                // 今の割り当てがショートカットなら、その名前(「Gmail: 新規作成」)の行にする。
                val currentEntry = current?.let { ref ->
                    catalogState.entryOf(ref)?.let { if (ref.shortcut == null) it else AppEntry(ref, catalogState.labelOf(ref) ?: it.label) }
                }
                // 割り当て済みなら、今のアプリの行と「割り当てを解除」を一覧の外(上)に固定する。
                // スクロールしても解除が隠れず、今のアプリを探さずに済むように(2026-11-19 オーナー要望)。絞り込み中も出す。
                if (currentEntry != null) {
                    AppRow(
                        currentEntry, selected = true, badges = AssignmentBadge.slotsOf(assignments, currentEntry.ref, catalogState.defaults),
                        catalog = catalog, onClick = { onPick(currentEntry.ref) },
                    )
                }
                if (current != null && pictograms) {
                    val chosen = assignments.appearance.pictograms[current.packageName]?.let { Pictogram.of(it) }
                    val auto = rememberAutoPictogram(catalog, current.packageName)
                    CRow(
                        (chosen ?: auto)?.let { FoldBerryIcons.Pictograms.of(it) }, stringResource(R.string.pictogram_row),
                        pictogramDescription(chosen, auto),
                        onClick = { onShowPictogram(true) },
                    ) { CChevron() }
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
                            // 行を押す=今までどおりアプリを割り当て。右端の › だけがショートカットのページへ進む。
                            onChevron = if (shortcuts[entry.ref].isNullOrEmpty()) null else ({ onShowShortcuts(entry) }),
                        )
                    }
                }
            }
        }
    }
}

/**
 * › で開いたアプリのショートカットのページ(2026-11-20)。帯は ‹ アプリ名。先頭に「〇〇 を開く」(アプリそのもの)、
 * 続けてショートカット(rank 順)。各行の右に、その項目を割り当てた枠のバッジ。
 */
@Composable
private fun ShortcutPage(
    owner: AppEntry,
    items: List<AppEntry>,
    current: AppRef?,
    assignments: Assignments,
    loaded: CatalogState.Loaded?,
    catalog: AppCatalog,
    onPick: (AppRef) -> Unit,
    onShowShortcuts: (AppEntry?) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = loaded?.defaults ?: DefaultApps()
    SheetFrame(title = owner.label, onDismiss = onDismiss, onBack = { onShowShortcuts(null) }, bounce = true) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item(key = "app") {
                AppRow(
                    AppEntry(owner.ref, stringResource(R.string.assign_open_app, owner.label)), selected = owner.ref == current,
                    badges = AssignmentBadge.slotsOf(assignments, owner.ref, defaults), catalog = catalog, onClick = { onPick(owner.ref) },
                )
            }
            item(key = "title") { CSectionTitle(stringResource(R.string.assign_shortcuts)) }
            // キーは "sc/" を付ける: ショートカット ID はアプリが自由に決めるので、上の "app"・"title" とぶつからないように。
            items(items, key = { "sc/${it.ref.shortcut}" }) { entry ->
                // AppRef の等価は保存した名前を見ないので、言語を変えた後の割り当ても同じ ID なら今のものになる。
                val selected = entry.ref == current
                AppRow(
                    entry, selected = selected, badges = AssignmentBadge.slotsOf(assignments, entry.ref, defaults),
                    catalog = catalog, onClick = { onPick(entry.ref) },
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    selected: Boolean,
    badges: List<AssignmentSlot>,
    catalog: AppCatalog,
    onClick: () -> Unit,
    /** ショートカットを持つアプリなら › を出し、押すとショートカットのページへ。null なら出さない。 */
    onChevron: (() -> Unit)? = null,
) {
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
        if (onChevron != null) {
            // 押し間違えないよう、行の高さいっぱい・幅 56dp を › の押せる範囲にする。
            Box(
                Modifier.width(56.dp).fillMaxHeight().clickable(onClick = onChevron),
                contentAlignment = Alignment.Center,
            ) { CChevron() }
        }
    }
}

/**
 * 今のアプリの絵記号を選ぶページ(§5.2。2026-11-20。docs/mock/appicon-mock.html の「選ぶページ」)。帯は ‹ 「Gmail の絵記号」。
 * 先頭に「自動」(いま自動で決まる絵記号)、下に絵記号の格子。押すと保存して割り当てシートへ戻る。
 */
@Composable
internal fun PictogramPage(
    appLabel: String,
    chosen: Pictogram?,
    auto: Pictogram?,
    onPick: (Pictogram?) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun pick(p: Pictogram?) {
        onPick(p)
        onBack()
    }
    SheetFrame(title = stringResource(R.string.pictogram_page_title, appLabel), onDismiss = onDismiss, onBack = onBack, bounce = true) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            CRow(
                auto?.let { FoldBerryIcons.Pictograms.of(it) }, stringResource(R.string.pictogram_auto),
                auto?.let { stringResource(R.string.pictogram_auto_desc, stringResource(it.labelRes())) },
                onClick = { pick(null) },
            ) { if (chosen == null) CRoundCheck() }
            CSectionTitle(stringResource(R.string.pictogram_choose))
            Pictogram.entries.chunked(PICTOGRAM_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    row.forEach { p -> PictogramCell(p, selected = p == chosen, onClick = { pick(p) }, modifier = Modifier.weight(1f)) }
                    // 最後の段の空きを埋めて、格子の幅をそろえる。
                    repeat(PICTOGRAM_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private const val PICTOGRAM_COLUMNS = 4

@Composable
private fun PictogramCell(pictogram: Pictogram, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .height(84.dp)
            .padding(2.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(SheetAccent.copy(alpha = 0x1F / 255f)).border(1.5.dp, SheetAccent, shape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(FoldBerryIcons.Pictograms.of(pictogram), contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        BasicText(
            stringResource(pictogram.labelRes()),
            style = TextStyle(color = if (selected) SheetText else Color(0xFFC9CCD2), fontSize = 11.5.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「絵記号」の行の説明(今の絵記号の名前。自動なら「(自動)」を添える)。読込み中は null。割り当てシートとフォルダの編集シートで共通。 */
@Composable
internal fun pictogramDescription(chosen: Pictogram?, auto: Pictogram?): String? {
    val shown = chosen ?: auto ?: return null
    val name = stringResource(shown.labelRes())
    return if (chosen == null) stringResource(R.string.pictogram_row_auto, name) else name
}
