package com.foldberry.launcher.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.keyboard.KeySpec

private val KeyGap = 4.dp
private val RowPaddingV = 3.dp
private val SidePadding = 5.dp

/** 金属調のフレット(各段の間の横バー。§5.4)。 */
private val FretBrush = Brush.verticalGradient(
    0f to Color(0xFF4A4D52), 0.35f to Color(0xFFC9CCD1), 0.5f to Color(0xFFF2F3F5), 0.7f to Color(0xFF9DA0A6), 1f to Color(0xFF3B3D41),
)

@Composable
fun Fret(height: Dp) {
    Box(Modifier.fillMaxWidth().height(height).background(FretBrush, alpha = 0.8f))
}

/** Shift 面の間に押せないキー(英字以外)の濃さ(D28。docs/mock/shift-mock.html。2026-11-22 オーナー決定)。 */
internal const val SHIFT_DIMMED_ALPHA = 0.2f

/**
 * 機能列の下のフレットから 4 段目までの、自由割り当てキーの部分。
 * @param iconFor キーに割り当てたアプリのアイコン(未割り当て・読込み前は null)
 * @param highlightKey 刻印と縁を青くするキー(Shift 面の間の左 Shift。D28)
 * @param dimmed 薄くして押せなくするキー(Shift 面の間の英字以外。D28)
 */
@Composable
fun KeyRows(
    keyRowHeight: Dp,
    fretHeight: Dp,
    iconFor: @Composable (KeySpec) -> ImageBitmap? = { null },
    onTap: (KeySpec) -> Unit = {},
    onLongPress: (KeySpec) -> Unit = {},
    highlightKey: String? = null,
    dimmed: (KeySpec) -> Boolean = { false },
) {
    Column(Modifier.fillMaxWidth()) {
        KeyLayout.rows.forEach { row ->
            Fret(fretHeight)
            KeyRow(row, keyRowHeight, iconFor, onTap, onLongPress, highlightKey, dimmed)
        }
    }
}

/** 10 列の格子にキーを置く。列位置と列幅は KeyLayout のとおり。 */
@Composable
private fun KeyRow(
    row: List<KeySpec>,
    height: Dp,
    iconFor: @Composable (KeySpec) -> ImageBitmap?,
    onTap: (KeySpec) -> Unit,
    onLongPress: (KeySpec) -> Unit,
    highlightKey: String?,
    dimmed: (KeySpec) -> Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).padding(horizontal = SidePadding, vertical = RowPaddingV)) {
        val colWidth = (maxWidth - KeyGap * (KeyLayout.COLUMNS - 1)) / KeyLayout.COLUMNS
        val keyHeight = maxHeight
        row.forEach { spec ->
            val off = dimmed(spec)
            KeyCap(
                spec,
                rowHeight = height,
                modifier = Modifier
                    .offset(x = (colWidth + KeyGap) * (spec.column - 1))
                    .size(width = colWidth * spec.span + KeyGap * (spec.span - 1), height = keyHeight)
                    .graphicsLayer { alpha = if (off) SHIFT_DIMMED_ALPHA else 1f },
                assignedIcon = iconFor(spec),
                onTap = { onTap(spec) },
                onLongPress = { onLongPress(spec) },
                highlight = spec.id == highlightKey,
                enabled = !off,
            )
        }
    }
}
