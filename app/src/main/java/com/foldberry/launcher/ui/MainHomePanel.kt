package com.foldberry.launcher.ui

import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.AppFolder
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.FolderAdd
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.flipped
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.layout.MainHomeGrid
import com.foldberry.launcher.layout.PageGrid
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.screen.FittedMonthCalendar
import com.foldberry.launcher.ui.screen.rememberNow
import com.foldberry.launcher.ui.screen.FlipPanel
import com.foldberry.launcher.ui.screen.MusicCard
import java.time.YearMonth

/**
 * メインのカレンダーの日付の文字は 34dp の行の大きさまで(カバーの 22dp より大きく)。
 * 行の高さは上限を設けず 4×2 マスいっぱいに広げ、Hub と枠の高さをそろえる(フリップで高さが変わらないように。
 * 2026-11-18 オーナー指摘。旧: 行も 34dp までで、カードが枠より低く上下中央に置かれていた)。
 */
private val MainCalendarMaxText = 34.dp
private val MainCalendarFillRow = 200.dp
private val SlotIconSize = 58.dp
private val SlotShape = RoundedCornerShape(10.dp)

/** メイン縦の音楽の札が使うマスの数(旧タイル T1・T2 の 2 マス。D26)。 */
private const val MUSIC_CELLS = 2

/**
 * メイン構成の一覧の枠に普段出す「月のカレンダー+自由枠」(§4.5。2026-11-17 オーナー決定)。アプリ一覧はトラックパッドで上に重ねる。
 * マスは一覧と同じ(listPageGrid)。横画面=左上 4×2、縦画面=左右の端を空けた中央 4×2(6 列のとき)をカレンダーにし、残りのマスを自由枠(縦横共通の 8 枠。2026-11-18 に 12 枠から改訂)にする。
 * 自由枠: タップ=起動(キーの選択待ち中ならその枠に割り当て)、長押し=割り当てシート(割り当てシートは本体部に出る)。
 *
 * @param bottomInset 下端がウィンドウ下端(ナビゲーション領域)に接しているか。接していればその分を空ける
 * @param portraitScreen メイン縦の上半分の画面に置くか(D25。2026-11-19)。背景を塗らず壁紙を見せ、マス割りは枠の大きさから決め、
 *   自由枠の後ろに音楽の 2 マスを置く(D26)。false(メイン横の一覧の枠)はマスを一覧とそろえ、音楽は本体部のまま
 */
@Composable
fun MainHomePanel(home: HomeController, bottomInset: Boolean, modifier: Modifier, portraitScreen: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    val catalogState by app.catalog.state.collectAsState()
    // ショートカットは「Gmail: 新規作成」(labelOf)。
    val loaded = catalogState as? CatalogState.Loaded
    val navBottom = if (bottomInset) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val now by rememberNow()
    val today = now.toLocalDate()
    // 表示中の月(当月以外を選んだときだけ HomeController に持つ。家・終話で当月に戻る)。
    val thisMonth = YearMonth.from(today)
    val shown = home.mainCalendarMonth ?: thisMonth
    // 月が変わったら当月表示に戻す(カバーと同じ)。初回・作り直し(回転)でも当月から。
    LaunchedEffect(thisMonth) { home.mainCalendarMonth = null }

    fun onSlotTap(index: Int, ref: AppRef?) {
        val picking = home.pickingApp
        when {
            picking != null -> assignPickedApp(context, home, KeyLayout.mainSlotId(index), resources.getString(R.string.main_slot_name, index + 1), picking)
            ref != null -> if (!app.catalog.launch(ref)) Toast.makeText(context, resources.getString(R.string.launch_failed), Toast.LENGTH_SHORT).show()
            else -> Unit // 空きのタップは何もしない(割り当ては長押し)
        }
    }

    /** 選択待ちのアプリをフォルダに足す(§4.5)。入っている・満杯なら足さずに理由をトーストで出す。 */
    fun onFolderPick(index: Int) {
        val picking = home.pickingApp ?: return
        val id = KeyLayout.mainSlotId(index)
        val folder = assignments.folders[id] ?: return
        val text = if (!app.catalog.isListed(picking.ref)) {
            resources.getString(R.string.app_not_found, picking.label)
        } else {
            when (assignments.addToFolder(id, picking.ref).second) {
                FolderAdd.ADDED -> {
                    app.store.update { it.addToFolder(id, picking.ref).first }
                    resources.getString(R.string.folder_added, folderName(resources, folder), picking.label)
                }
                FolderAdd.ALREADY -> resources.getString(R.string.folder_already, picking.label)
                FolderAdd.FULL -> resources.getQuantityString(R.plurals.folder_full, Assignments.MAX_FOLDER_APPS, Assignments.MAX_FOLDER_APPS)
            }
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        home.pickingApp = null
    }

    // 開いているフォルダが無くなったら(アンインストールで空・すべて解除・読込み・解除)閉じる。
    val openFolder = home.openFolder
    LaunchedEffect(openFolder, assignments.folders) {
        if (openFolder != null && assignments.folders[KeyLayout.mainSlotId(openFolder)] == null) home.openFolder = null
    }

    CompositionLocalProvider(*assignedIconLocals(assignments.appearance)) {
        BoxWithConstraints(
            modifier
                .then(if (portraitScreen) Modifier else Modifier.background(ListBackground))
                .padding(start = PAGE_PADDING_H, end = PAGE_PADDING_H, top = 8.dp, bottom = navBottom + 8.dp),
        ) {
            // 横: マス割りは一覧と同じ大きさ(枠の幅、見出しの下の高さ)から決め、1 マスの大きさを一覧とそろえる。並べるのは枠いっぱいに。
            // 縦の画面(D25): 一覧は画面全体に重ねるので大きさはそろえず、この枠から決める(実機 6×3)。自由枠 8 の後ろに音楽の 2 マス(D26)。
            val grid = if (portraitScreen) {
                MainHomeGrid.of(PageGrid.of(maxWidth.value, maxHeight.value), KeyLayout.MAIN_SLOT_COUNT, tileCount = MUSIC_CELLS)
            } else {
                MainHomeGrid.of(listPageGrid(maxWidth + PAGE_PADDING_H * 2, maxHeight + 16.dp - LIST_HEADER_HEIGHT), KeyLayout.MAIN_SLOT_COUNT)
            }
            val cellW = maxWidth / grid.grid.columns
            val cellH = maxHeight / grid.grid.rows
            // 4×2 マスには設定でカレンダーか Hub を出す(§4.5 / §4.6。v0.7.0)。マス割りはどちらでも同じ。
            // 見出しを下へ払うとフリップして入れ替わる(v0.7.3。カバーとは別に覚える)。
            // 保存の mainPanel を入れ替える(設定の「メインの枠」と同じ保存)。
            FlipPanel(
                kind = assignments.hub.panel,
                onFlip = { app.store.update { it.copy(hub = it.hub.copy(mainPanel = it.hub.panel.flipped().id)) } },
                modifier = Modifier.offset(cellW * grid.calendarStartColumn, 0.dp).size(cellW * grid.calendarColumns, cellH * grid.calendarRows).padding(6.dp),
            ) { face, header ->
                when (face) {
                    MainPanelKind.CALENDAR -> FittedMonthCalendar(
                        shown, today, { home.mainCalendarMonth = it.takeIf { m -> m != thisMonth } },
                        Modifier.fillMaxSize(),
                        maxRow = MainCalendarFillRow,
                        maxTextRow = MainCalendarMaxText,
                        headerModifier = header,
                    )
                    MainPanelKind.HUB -> HubPanel(
                        preview = assignments.hub.previewMode,
                        hourFormat = assignments.clock.hours,
                        picking = home.pickingApp != null,
                        modifier = Modifier.fillMaxSize(),
                        headerModifier = header,
                    )
                }
            }
            grid.slots.forEachIndexed { index, cell ->
                val ref = assignments.keys[KeyLayout.mainSlotId(index)]
                val folder = assignments.folders[KeyLayout.mainSlotId(index)]
                Box(Modifier.offset(cellW * cell.column, cellH * cell.row).size(cellW, cellH), contentAlignment = Alignment.Center) {
                    if (folder != null) {
                        FolderCell(
                            folder = folder,
                            description = resources.getString(R.string.folder_slot_description, index + 1, folderName(resources, folder)),
                            haptics = assignments.feedback.haptics,
                            // 選択待ち中はフォルダに足す(置き換えない。オーナー決定)。それ以外はタップ=開く、長押し=編集。
                            onClick = { if (home.pickingApp != null) onFolderPick(index) else home.showFolder(index) },
                            onLongClick = { if (home.pickingApp != null) onFolderPick(index) else home.startEdit(EditTarget.Folder(index)) },
                        )
                    } else {
                        SlotCell(
                            ref = ref,
                            // 一覧の読込み中・失敗中は名前を出さない(「端末に無いアプリ」と誤って出さないように)。
                            label = ref?.let { loaded?.labelOf(it) ?: if (catalogState is CatalogState.Loaded) resources.getString(R.string.app_missing_label) else null },
                            description = resources.getString(R.string.main_slot_name, index + 1),
                            icon = rememberAssignedIcon(app.catalog, ref),
                            onClick = { onSlotTap(index, ref) },
                            haptics = assignments.feedback.haptics,
                            onLongClick = { if (home.pickingApp == null) home.startEdit(EditTarget.MainSlot(index)) else onSlotTap(index, ref) },
                        )
                    }
                }
            }
            // 音楽(縦の画面だけ。旧タイル T1・T2 の 2 マスをつないだ 1 枚。D26。2026-11-19 オーナー決定)。
            // マスが足りない端末では 2 マス取れないので出さない。
            if (grid.tiles.size == MUSIC_CELLS && grid.tiles.all { it.row == grid.tiles[0].row }) {
                val first = grid.tiles.minBy { it.column }
                MusicCard(
                    wide = false,
                    modifier = Modifier.offset(cellW * first.column, cellH * first.row).size(cellW * MUSIC_CELLS, cellH).padding(6.dp),
                )
            }
        }
    }
}

/** フォルダのアイコン C の重ね方(58dp の枠の中。モックの寸法)。添字 0 が手前の先頭のアプリ。 */
private val FolderCardSize = 44.dp
private val FolderStack = listOf(2.dp to 10.dp, 8.dp to 4.dp, 14.dp to (-2).dp)
private val FolderCardAlpha = listOf(1f, 0.8f, 0.55f)

/** フォルダの名前。空なら既定の名前(「フォルダ」)。 */
internal fun folderName(resources: Resources, folder: AppFolder): String =
    folder.name.trim().ifEmpty { resources.getString(R.string.folder_default_name) }

/** フォルダの自由枠。中のアプリのアイコンを重ねたカード(先頭が手前・最大 3 枚)と右下に件数、下に名前。 */
@Composable
private fun FolderCell(folder: AppFolder, description: String, haptics: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val app = LocalContext.current.applicationContext as FoldBerryApp
    val resources = LocalResources.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SlotShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, hapticFeedbackEnabled = haptics)
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        // 重ねたカード(モック docs/mock/folder-mock.html のアイコン C。2026-11-18 オーナー決定):
        // 先頭のアプリを手前に大きく、2・3 個目を右上へずらして後ろに薄く重ね、右下に件数。
        val pictograms = LocalAssignedIconLook.current.style == IconStyle.PICTOGRAM
        Box(Modifier.size(SlotIconSize)) {
            folder.apps.take(3).withIndex().reversed().forEach { (i, ref) ->
                key(ref) {
                    val icon = rememberAssignedIcon(app.catalog, ref)
                    val offset = FolderStack[i]
                    Box(
                        Modifier
                            .offset(offset.first, offset.second)
                            .size(FolderCardSize)
                            .alpha(FolderCardAlpha[i])
                            // 絵記号は土台なしの線画なので、そのまま重ねると後ろの線が透けて読めない(実機で確認)。
                            // 絵記号のときはカードを背景色で塗って薄い枠線を付け、手前の 1 個目だけが読めるようにする
                            // (docs/mock/appicon-mock.html のフォルダ B。2026-11-20 オーナー決定)。影は線の後ろに黒い角丸が出るので外す。
                            .then(
                                if (pictograms) {
                                    Modifier.background(ListBackground, RoundedCornerShape(11.dp))
                                        .border(1.5.dp, Color.White.copy(alpha = 0x40 / 255f), RoundedCornerShape(11.dp))
                                } else {
                                    Modifier.shadow(3.dp, RoundedCornerShape(11.dp))
                                },
                            ),
                    ) {
                        if (icon != null) {
                            Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = LocalAssignedIconFilter.current)
                        } else {
                            Box(Modifier.fillMaxSize().background(SheetFieldColor, RoundedCornerShape(11.dp)))
                        }
                    }
                }
            }
            CountBadge(folder.apps.size, Modifier.align(Alignment.BottomEnd).offset(6.dp, 4.dp), ring = ListBackground)
        }
        Spacer(Modifier.height(7.dp))
        BasicText(
            folderName(resources, folder),
            style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 12.5.sp, textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 自由枠 1 つ。割り当て済み=アイコン+名前(一覧の AppCell と同じ見た目)、空き=破線の角丸と「+」。 */
@Composable
private fun SlotCell(
    ref: AppRef?,
    label: String?,
    description: String,
    icon: androidx.compose.ui.graphics.ImageBitmap?,
    haptics: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SlotShape)
            // 長押しの触覚は combinedClickable の既定(端末標準の触覚。一覧と同じ)。設定の触覚オフで止める(§8)。
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, hapticFeedbackEnabled = haptics)
            .semantics { contentDescription = description }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (ref != null) {
            Box(Modifier.size(SlotIconSize)) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = LocalAssignedIconFilter.current)
                } else {
                    Box(Modifier.fillMaxSize().background(SheetFieldColor, RoundedCornerShape(14.dp)))
                }
            }
            Spacer(Modifier.height(7.dp))
            BasicText(
                label.orEmpty(),
                style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 12.5.sp, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            // 未割り当て: 破線の枠と薄い「+」(§4.3)。
            Box(
                Modifier.size(SlotIconSize).drawBehind {
                    drawRoundRect(
                        Color(0x30FFFFFF),
                        cornerRadius = CornerRadius(14.dp.toPx()),
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                LineIcon(FoldBerryIcons.Line.Plus, Color(0x73FFFFFF), 22.dp)
            }
            Spacer(Modifier.height(7.dp))
            BasicText(" ", style = TextStyle(fontSize = 12.5.sp))
        }
    }
}
