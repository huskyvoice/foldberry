package com.foldberry.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.ui.icons.FoldBerryIcons

private val FolderShape = RoundedCornerShape(6.dp)

/**
 * 開いたフォルダ(§4.5。v0.7.1)。メインの一覧の枠に重ね、暗幕の上にカードを出す(下から滑り出すのは HomeScreen の PanelSlide)。
 * カード: 帯(編集・フォルダ名・×)と、中のアプリを一覧と同じ 1 マスの大きさで 2×2(最大 4 個)。
 * アプリのタップ=起動して閉じる、長押し=フォルダの編集シート。暗幕のタップ・×で閉じる(戻る・終話・家・開閉は HomeController)。
 *
 * @param index 自由枠の番号(0 始まり)
 * @param topInset 暗幕だけを掛ける上端の高さ(ステータス行。カードはその下の枠の中央に置く。2026-11-24 オーナー決定)
 */
@Composable
fun FolderPanel(home: HomeController, index: Int, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    val catalogState by app.catalog.state.collectAsState()
    // 中身が消えたら(アンインストール・解除)MainHomePanel が閉じる。閉じる動きの間は何も描かない。
    val folder = assignments.folders[KeyLayout.mainSlotId(index)] ?: return
    val entries = remember(folder, catalogState) {
        val loaded = catalogState as? CatalogState.Loaded
        // 一覧に無いアプリ(読込み中)は名前をパッケージ名にして出す。ショートカットは「Gmail: 新規作成」。
        folder.apps.map { ref -> AppEntry(ref, loaded?.labelOf(ref) ?: ref.packageName) }
    }
    val close = { home.openFolder = null }

    BoxWithConstraints(modifier) {
        // 暗幕。タップで閉じる(下の自由枠・カレンダーには触れを通さない)。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
        )
        // 2×2 の大きなカード(モック docs/mock/folder-mock.html の開き方 C。2026-11-18 オーナー決定)。
        // 1 マスは一覧と同じ大きさ(listPageGrid)で、列の中央にそろえて左右の余白を同じにする(「右が空いている」の指摘)。
        val grid = listPageGrid(maxWidth, maxHeight - topInset - LIST_HEADER_HEIGHT)
        val cell = max((maxWidth - PAGE_PADDING_H * 2) / grid.columns, MinFolderCell)
        val cardWidth = min(cell * 2 + FolderPadH * 2, maxWidth - 16.dp)
        val edit = { home.startEdit(EditTarget.Folder(index)) }
        Box(Modifier.fillMaxSize().padding(top = topInset)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .width(cardWidth)
                .clip(FolderShape)
                .background(ListBackground)
                .border(1.dp, Color(0x1AFFFFFF), FolderShape)
                .pointerInput(Unit) {},
        ) {
            FolderBar(folderName(resources, folder), onEdit = edit, onClose = close)
            // 中のアイコンは一覧と同じくカラー(グレースケールは割り当て先の枠の表示用)。
            entries.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(horizontal = FolderPadH)) {
                    pair.forEach { entry ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            AppCell(
                                entry = entry,
                                catalog = app.catalog,
                                badge = null,
                                badgeDefault = false,
                                haptics = assignments.feedback.haptics,
                                onClick = {
                                    if (app.catalog.launch(entry.ref)) {
                                        close()
                                    } else {
                                        Toast.makeText(context, resources.getString(R.string.launch_failed), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onLongClick = edit,
                            )
                        }
                    }
                    // 奇数個の最後の段は、空きを右に残して列の位置をそろえる(1 個だけ中央に寄せると上の段とずれる)。
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        }
    }
}

private val FolderPadH = 12.dp
private val MinFolderCell = 104.dp
private val FolderBarBrush = Brush.verticalGradient(listOf(Color(0xFF1C1E23), Color(0xFF121317)))
private val FolderBarDivider = Color(0x10FFFFFF)

/** 開いたフォルダの帯: 左=編集、中央=フォルダ名、右=×。左右のボタンは同じ幅で、名前がカードの真ん中に来る。 */
@Composable
private fun FolderBar(title: String, onEdit: () -> Unit, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(LIST_HEADER_HEIGHT).background(FolderBarBrush).bottomLine(Color.Black),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(FolderBarButton).fillMaxHeight().clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(stringResource(R.string.folder_edit_button), style = TextStyle(color = SheetAccent, fontSize = 13.sp))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(FolderBarDivider))
        BasicText(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            style = TextStyle(color = SheetText, fontSize = 17.sp, textAlign = TextAlign.Center),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Box(Modifier.width(1.dp).fillMaxHeight().background(FolderBarDivider))
        Box(
            Modifier.width(FolderBarButton).fillMaxHeight().clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(FoldBerryIcons.Line.Close, Color(0xFFCFD2D8))
        }
    }
}

private val FolderBarButton = 56.dp
