package com.foldberry.launcher.ui.keyboard

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.keyboard.FunctionKey
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.keyboard.SwipeDirection
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.icons.FoldBerryIcons

private val FunctionRowBrush = Brush.verticalGradient(0f to Color(0xFF1C1D20), 0.6f to Color(0xFF0D0D0F), 1f to Color(0xFF050506))
private val IconColor = Color(0xFFD9DBE0)

/**
 * 機能列(送話 / BBメニュー / トラックパッド / 戻る / 終話。§5.3)。M3 では見た目と押下演出のみ。
 * BBメニューには FoldBerry 独自のマーク(アプリアイコンと同じ傾いたキー 2×2)を使う(BlackBerry のロゴは使わない)。
 */
@Composable
fun FunctionRow(
    height: Dp,
    /** 通知があることをトラックパッドの青い明滅で知らせるか(設定の通知の知らせ方。2026-11-28)。 */
    trackpadLit: Boolean = false,
    onKeyTap: (FunctionKey) -> Unit = {},
    onKeyLongPress: (FunctionKey) -> Unit = {},
    onTrackpadSwipe: (SwipeDirection) -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(height).background(FunctionRowBrush).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeyLayout.functionKeys.forEach { key ->
            // トラックパッドの列だけ少し広い(モックと同じ 1.05)。
            FunctionKeyCell(
                key, height, Modifier.weight(if (key == FunctionKey.TRACKPAD) 1.05f else 1f),
                onTap = { onKeyTap(key) },
                // 長押し=割り当て編集は送話・戻る・終話だけ(BBメニュー・トラックパッドは固定機能)。
                onLongPress = if (key.assignId != null) ({ onKeyLongPress(key) }) else null,
                // 払いはトラックパッドだけ(音楽の操作。§5.3。2026-11-24)。
                onSwipe = if (key == FunctionKey.TRACKPAD) onTrackpadSwipe else null,
                trackpadLit = trackpadLit,
            )
        }
    }
}

@Composable
private fun FunctionKeyCell(
    key: FunctionKey,
    rowHeight: Dp,
    modifier: Modifier,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)?,
    onSwipe: ((SwipeDirection) -> Unit)?,
    trackpadLit: Boolean,
) {
    val pressed = rememberPressState()
    Box(
        modifier
            .fillMaxHeight()
            // 押下判定は縮小前の座標で行う(KeyCap と同じ理由)。
            .pressFeedback(pressed, key, onTap = onTap, onLongPress = onLongPress, onSwipe = onSwipe)
            .graphicsLayer {
                val s = if (pressed.value) 0.95f else 1f
                scaleX = s
                scaleY = s
            }
            .background(if (pressed.value) Color.White.copy(alpha = 0.07f) else Color.Transparent, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val icon = FoldBerryIcons.function(key)
        // アイコンは機能列の高さに合わせて縮む(実機で 34〜38dp 程度)。
        val iconSize = minOf(26.dp, rowHeight * 0.62f)
        if (icon != null) {
            val size = if (key == FunctionKey.MENU) iconSize * 0.85f else iconSize
            // BBメニューのマークは白+紫の 2 色なので tint しない(U3 改訂 2026-11-16)。
            val tint = if (key == FunctionKey.MENU) null else ColorFilter.tint(IconColor)
            Image(icon, contentDescription = key.name, modifier = Modifier.size(size), colorFilter = tint)
        } else {
            Trackpad(trackpadLit, Modifier.fillMaxHeight(0.66f).aspectRatio(1.3f))
        }
    }
}

@Composable
private fun Trackpad(lit: Boolean, modifier: Modifier) {
    if (lit) {
        LitTrackpad(modifier)
    } else {
        TrackpadFace(modifier) { 0f }
    }
}

/**
 * 通知があるときの明滅(設定の通知の知らせ方。2026-11-28。docs/mock/trackpad-mock.html の案 C: 枠が青くなり外へ光がにじむ。面は青くしない)。
 * 周期 2.5 秒・いちばん暗いとき 15%。明滅の値は描画の段階でだけ読むので、再構成は起きず描き直しだけになる。
 * 消灯中は InfiniteTransition を作らない(通知が無いときに毎フレーム描き直さないように)。
 */
@Composable
private fun LitTrackpad(modifier: Modifier) {
    val glow by rememberInfiniteTransition(label = "trackpadGlow").animateFloat(
        initialValue = GLOW_MIN,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(GLOW_HALF_PERIOD_MS, easing = EaseInOut), RepeatMode.Reverse),
        label = "trackpadGlow",
    )
    TrackpadFace(modifier) { glow }
}

/** トラックパッドの見た目。glow(0〜1)が 0 なら通知の無い今までの見た目と同じ。 */
@Composable
private fun TrackpadFace(modifier: Modifier, glow: () -> Float) {
    Box(
        modifier
            .drawBehind {
                // 外へにじむ光(モックの box-shadow 0 0 8px 2px)。パッドの外へはみ出して描く。
                val g = glow()
                if (g <= 0f) return@drawBehind
                val r = size.minDimension * 0.28f
                val spread = 2.dp.toPx() * g
                val paint = Paint().apply {
                    color = SheetAccent.copy(alpha = 0.7f * g).toArgb()
                    maskFilter = BlurMaskFilter(maxOf(8.dp.toPx() * g, 0.5f), BlurMaskFilter.Blur.OUTER)
                }
                drawIntoCanvas {
                    it.nativeCanvas.drawRoundRect(-spread, -spread, size.width + spread, size.height + spread, r + spread, r + spread, paint)
                }
            }
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2B2F), Color(0xFF0B0B0C))), RoundedCornerShape(28))
            .drawBehind {
                // 面は今までの色のまま、枠だけを青くする(2026-11-28 オーナー指定。内側の光は面が青く見えるのでやめた)。
                val r = CornerRadius(size.minDimension * 0.28f)
                drawRoundRect(lerp(TrackpadRim, SheetAccent, glow()), cornerRadius = r, style = Stroke(2.dp.toPx()))
            },
    )
}

private val TrackpadRim = Color(0xFF6D7076)
private const val GLOW_MIN = 0.15f
/** 明滅の半周期。周期 2.5 秒(2026-11-28 オーナー指定で 2 秒から少し遅く)。 */
private const val GLOW_HALF_PERIOD_MS = 1250
