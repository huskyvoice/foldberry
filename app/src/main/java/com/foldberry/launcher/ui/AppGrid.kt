package com.foldberry.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.foldberry.launcher.data.filterByName
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.displayLabel
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.nameGroup
import com.foldberry.launcher.keyboard.AssignmentBadge
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.layout.PageGrid

/** 一覧の背景(BB10 風の暗色。§6)。 */
internal val ListBackground = Color(0xFF0D1118)

/**
 * アプリ一覧(仕様書 §6)。メイン構成の右半分/上半分と、カバー構成の重ね表示(トラックパッド)で共通。
 * 並び順・FoldBerry 自身の除外・インストール等での自動更新は AppCatalog 側で済んでいる。
 * タップ=起動、長押し=メニュー(キーに割り当て/アプリ情報/アンインストール)。メニューはこの枠の中に出す
 * (メイン構成で折り目をまたがないように。§3.3)。
 *
 * @param bottomInset 下端がウィンドウ下端(ナビゲーション領域)に接しているか。接していればその分を空ける
 * @param onClose カバーの重ね表示のときの「閉じる」。メイン構成では null(見出しに出さない)
 */
@Composable
fun AppListPanel(home: HomeController, modifier: Modifier, bottomInset: Boolean, onClose: (() -> Unit)?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val catalogState by app.catalog.state.collectAsState()
    val assignments by app.store.state.collectAsState()
    val navBottom = if (bottomInset) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 名前で絞り込む検索欄(端末のキーボードで打つ。2026-11-19 オーナー要望)。文字は一覧の中だけで持つ:
    // 閉じると AnimatedPanel が作り直すので、次に開いたときは空に戻る。
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun hideKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }
    // 一覧を閉じたら(起動・×・終話・開閉)キーボードも閉じる。
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    // 閉じ始めた時点で文字を消してキーボードを閉じる。閉じる動き(0.5 秒)の間は中身が残るので、onDispose だけだと
    // その間 IME が残り、動きの途中で開き直すと前の文字が残る(2026-11-19 refuter の指摘)。
    val listOpen = if (home.isCover) home.overlayOpen else home.mainListOpen
    LaunchedEffect(listOpen) {
        if (!listOpen) {
            hideKeyboard()
            query = ""
        }
    }
    // 絞り込みが変わったら 1 ページ目から(ページ数が減って今のページが無くなることがある)。
    LaunchedEffect(query) { if (query.isNotEmpty()) home.pagerState.scrollToPage(0) }

    Box(modifier.background(ListBackground)) {
        // 一覧はナビゲーションバーの手前で止める(下に潜ると 3 ボタンの後ろにアイコンが透けて見づらい)。
        Column(Modifier.fillMaxSize().padding(bottom = navBottom)) {
            val shown = (catalogState as? CatalogState.Loaded)?.apps?.let { apps -> filterByName(apps, query) { it.label } }
            // 案 C の帯(高さは今の見出しと同じ 48dp。マス割り listPageGrid・MainHomePanel を変えないため)。
            // 題の位置に検索欄を置く(帯の下に行を足すと一覧のマスが MainHomePanel とずれるため)。件数は絞り込み後。
            CBar(
                title = stringResource(R.string.app_list_title), trailing = shown?.size?.toString(), onClose = onClose,
                height = LIST_HEADER_HEIGHT, titleSize = 17f,
                titleContent = { ListSearchField(query, { query = it }, onDone = ::hideKeyboard) },
            )
            when (val state = catalogState) {
                CatalogState.Loading -> SheetMessage(stringResource(R.string.common_loading))
                CatalogState.Failed -> SheetMessage(stringResource(R.string.catalog_failed))
                is CatalogState.Loaded if shown.isNullOrEmpty() -> SheetMessage(stringResource(R.string.assign_no_match))
                // BB10 風のページ式(横にめくる)。1 ページの列・行は一覧の大きさから決める(カバーで 4×5。layout/AppPages)。
                is CatalogState.Loaded -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val grid = listPageGrid(maxWidth, maxHeight)
                    val pages = shown.orEmpty().chunked(grid.perPage)
                    // ページ数はページャの状態(HomeController)が読む。書き込みは描画のあとに行う。
                    SideEffect { home.appPageCount = grid.pageCount(pages.sumOf { it.size }) }
                    Column(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = home.pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            beyondViewportPageCount = 1,
                        ) { page ->
                            AppPage(
                                entries = pages.getOrElse(page) { emptyList() }, grid = grid,
                                cell = { entry ->
                                    AppCell(
                                        entry, app.catalog,
                                        badge = AssignmentBadge.of(assignments, entry.ref, state.defaults),
                                        badgeDefault = AssignmentBadge.firstIsDefault(assignments, entry.ref, state.defaults),
                                        haptics = assignments.feedback.haptics,
                                        onClick = {
                                            if (app.catalog.launch(entry.ref)) home.closeAppList() else toast(resources.getString(R.string.launch_failed))
                                        },
                                        // 長押しメニューにキーボードが重ならないよう閉じる。
                                        onLongClick = {
                                            hideKeyboard()
                                            home.menuApp = entry
                                        },
                                    )
                                },
                            )
                        }
                        PageBar(pages.getOrElse(home.pagerState.currentPage) { emptyList() }, pages.size, home.pagerState.currentPage, Modifier.fillMaxWidth().height(DOTS_HEIGHT))
                    }
                }
            }
        }
        AnimatedPanel(home.menuApp) { entry ->
            // このアプリのショートカット(既定のホームでなければ空)。開くたびに読む(動的ショートカットは入れ替わるため)。
            val shortcuts by produceState(emptyList<AppEntry>(), entry) { value = app.catalog.shortcuts()[entry.ref].orEmpty() }
            AppMenuSheet(
                entry = entry,
                bottomInset = bottomInset,
                shortcuts = shortcuts,
                onAssignShortcut = { sc ->
                    // 「キーに割り当て」と同じ流れ。帯・トーストには「Gmail: 新規作成」を出す。
                    home.menuApp = null
                    home.closeAppList()
                    home.startPicking(AppEntry(sc.ref, displayLabel(sc.ref, entry.label) ?: sc.label))
                },
                onAssignKey = {
                    // キーボードが見えるよう重ね表示を閉じ、押されたキーに割り当てる(BerryBody 側で受ける)。
                    home.menuApp = null
                    home.closeAppList()
                    home.startPicking(entry)
                },
                onDetails = {
                    if (!app.catalog.openDetails(entry.ref)) toast(resources.getString(R.string.details_failed))
                    home.menuApp = null
                },
                onUninstall = {
                    // 一覧は開いたまま(消えたら LauncherApps の通知で一覧と割り当てが更新される)。
                    if (!app.catalog.uninstall(entry.ref)) toast(resources.getString(R.string.uninstall_failed))
                    home.menuApp = null
                },
                onDismiss = { home.menuApp = null },
            )
        }
    }
}

/**
 * 一覧の帯の検索欄(§6。2026-11-19)。空のときは題「アプリ一覧」を薄く出す。開いた直後に自動でフォーカスしない
 * (勝手にキーボードが出ないように)。キーボードの確定(Enter)はキーボードを閉じるだけ。
 */
@Composable
private fun ListSearchField(query: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = SheetText, fontSize = 16.sp),
        cursorBrush = SolidColor(SheetAccent),
        // キーボードの種類・動作は指定しない(割り当てシートの検索欄と同じ既定のまま。日本語入力を妨げないため。2026-11-19 オーナー指示)。
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LineIcon(FoldBerryIcons.Line.Search, SheetTextDim, 18.dp)
                Spacer(Modifier.width(8.dp))
                Box {
                    if (query.isEmpty()) {
                        BasicText(stringResource(R.string.app_list_title), style = TextStyle(color = SheetTextDim, fontSize = 16.sp), maxLines = 1)
                    }
                    inner()
                }
            }
        },
    )
}

/** 見出しの高さ・ページの点の帯の高さと、ページの左右の余白。 */
internal val LIST_HEADER_HEIGHT = 48.dp

private val DOTS_HEIGHT = 26.dp
internal val PAGE_PADDING_H = 8.dp

/**
 * 一覧の 1 ページのマス割り(見出しの下の大きさから)。メインの一覧の枠のカレンダー+自由枠(§4.5)も同じマスを使い、
 * 1 マスの大きさを一覧とそろえる。
 */
internal fun listPageGrid(width: Dp, heightBelowHeader: Dp): PageGrid =
    PageGrid.of(width.value - PAGE_PADDING_H.value * 2, (heightBelowHeader - DOTS_HEIGHT).value)

/** 1 ページ分。列 × 行のマスに左上から詰める(最後のページの空きマスは空けたまま)。 */
@Composable
private fun AppPage(entries: List<AppEntry>, grid: PageGrid, cell: @Composable (AppEntry) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = PAGE_PADDING_H, vertical = 4.dp)) {
        repeat(grid.rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                repeat(grid.columns) { c ->
                    // 案 C: マスを細い線で区切る(右と下。最後の列は右線なし)。
                    Box(
                        Modifier.weight(1f).fillMaxHeight().drawBehind {
                            val w = 1.dp.toPx()
                            if (c < grid.columns - 1) drawLine(GridLine, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                            if (r < grid.rows - 1) drawLine(GridLine, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        entries.getOrNull(r * grid.columns + c)?.let { cell(it) }
                    }
                }
            }
        }
    }
}

/**
 * ページの帯(案 C)。今のページを短い棒で示し(1 ページなら棒は出さない)、左に今のページ先頭の群(A–Z・#・あ)、
 * 右に最初と最後のアプリ名の頭文字(「A – F」。名前そのままだと実機の長い名前で切れるため)。上に細い線。
 */
@Composable
private fun PageBar(entries: List<AppEntry>, count: Int, current: Int, modifier: Modifier) {
    Box(modifier.background(Color(0xFF101115)).drawBehind { drawLine(Color(0x12FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }) {
        val first = entries.firstOrNull()?.label
        val last = entries.lastOrNull()?.label
        // 頭文字(前の空白を除いた 1 文字目。英字は大文字に)。サロゲートペアの絵文字は 2 単位で取る。
        fun initial(name: String): String = name.trimStart().let { s ->
            if (s.isEmpty()) "#" else s.substring(0, Character.charCount(s.codePointAt(0))).uppercase()
        }
        val dim = TextStyle(color = SheetTextDim, fontSize = 11.5.sp)
        if (first != null) {
            BasicText(stringResource(nameGroup(first).groupLabelRes()), Modifier.align(Alignment.CenterStart).padding(start = 14.dp), style = dim)
            BasicText(
                (initial(first) to initial(last ?: first)).let { (a, b) -> if (a == b) a else "$a – $b" },
                Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).widthIn(max = 130.dp),
                style = dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (count > 1) {
            // ページが多いと棒が左右の文字に重なるので、6 ページ以上は棒を短くする(カバーで 10 ページまで重ならない)。
            val bar = if (count <= 5) 22.dp else 8.dp
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(count) { i ->
                    Box(Modifier.size(width = bar, height = 3.dp).background(if (i == current) SheetAccent else Color(0x26FFFFFF)))
                }
            }
        }
    }
}

private val GridLine = Color(0x0CFFFFFF)

/**
 * アプリ 1 つ。アイコンの右上に割り当てバッジ(キー=キートップ風、自由枠・フォルダ=アクセント色。§6・比較モックの案 E)。
 * 名前は 1 行(ページ式でマスの高さが決まっているため)。
 */
@Composable
internal fun AppCell(
    entry: AppEntry,
    catalog: AppCatalog,
    badge: String?,
    badgeDefault: Boolean,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            // 長押しの触覚は combinedClickable の既定(端末標準の触覚)。設定の触覚オフで止める(§8)。
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, hapticFeedbackEnabled = haptics)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(Modifier.size(ICON_SIZE)) {
            val icon = rememberAppIcon(catalog, entry.ref)
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(SheetFieldColor, RoundedCornerShape(14.dp)))
            }
            badge?.let { Badge(it, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-5).dp), dashed = badgeDefault) }
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            entry.label,
            style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 12.5.sp, textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ICON_SIZE = 58.dp
/** メインの自由枠(M1〜M8)・そのフォルダの中(F1〜F8)はアクセント色、キーはキートップ風。 */
private val SLOT_BADGE = Regex("^[MF]\\d")

/**
 * 割り当てバッジ。アプリ一覧と割り当てシートの一覧(§7)で共通。
 * @param edge 縁取りの色(置く場所の背景色)
 * @param dashed 既定のアプリ(送話=電話・カメラの枠=カメラ。§8)の印。置く場所の背景色で塗り、点線の枠にする
 */
@Composable
internal fun Badge(text: String, modifier: Modifier, edge: Color = ListBackground, dashed: Boolean = false) {
    val isSlot = SLOT_BADGE.containsMatchIn(text)
    val shape = RoundedCornerShape(6.dp)
    val color = if (dashed) DashedColor else if (isSlot) Color(0xFF06141B) else Color.White
    // フォルダの中(F1)は、文字の F の代わりにフォルダの印を描いて番号を添える(2026-11-18 オーナー決定)。
    val folderNumber = AssignmentBadge.folderNumber(text)
    Row(
        modifier
            // 背景色の縁取りで、アイコンから浮かせる。点線の印は縁取りが点線に重なって消えるので付けない(背景色で塗るだけ)。
            .then(if (dashed) Modifier else Modifier.border(1.5.dp, edge, shape))
            .then(
                if (dashed) Modifier.background(edge, shape).dashedOutline(DashedColor, 6.dp)
                else Modifier.background(if (isSlot) SolidColor(SheetAccent) else KeycapBrush, shape),
            )
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (folderNumber != null) {
            Image(FoldBerryIcons.FolderMark, contentDescription = null, modifier = Modifier.size(10.dp), colorFilter = ColorFilter.tint(color))
            Spacer(Modifier.width(2.dp))
        }
        BasicText(
            folderNumber ?: text,
            style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
    }
}

/** 既定のアプリの印(点線の枠と文字)の色。 */
internal val DashedColor = Color(0xFFB8BCC4)

/** キートップ風(本体のキーと同じ上明・下暗)。 */
private val KeycapBrush = Brush.verticalGradient(listOf(Color(0xFF3A3D44), Color(0xFF1B1D21)))
