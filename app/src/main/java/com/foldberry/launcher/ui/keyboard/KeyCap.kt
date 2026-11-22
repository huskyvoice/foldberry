package com.foldberry.launcher.ui.keyboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.keyboard.KeySpec
import com.foldberry.launcher.keyboard.Legend
import com.foldberry.launcher.ui.LocalAssignedIconFilter
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import kotlin.math.tan

/*
 * キー 1 つ。画像素材を使わずプログラムで描く(仕様書 §5.4)。
 * 値はデザイン確定モック docs/mock/cover-mock.html の質感「標準」(仕様 D17)と同じ。
 */
private val KeyTop = Color(0xFF36373C)
private val KeyMid = Color(0xFF151618)
private val KeyBottom = Color(0xFF08080A)
private const val GLOSS = 0.22f
private const val EDGE = 0.24f
private val MainLegend = Color(0xFFF4F4F5)
private val SecondaryLegend = Color(0x80C8CBD2)

/** 主刻印の大きさ = キー行の高さ × 0.36、副刻印 = × 0.22(モックと同じ比率)。 */
private const val MAIN_RATIO = 0.36f
private const val SECONDARY_RATIO = 0.22f
/** 副刻印の位置に出すアプリアイコンの大きさ = キー行の高さ × 0.3(モックと同じ)。 */
private const val ASSIGNED_ICON_RATIO = 0.3f

/**
 * @param assignedIcon 割り当て済みアプリのアイコン。null なら未割り当て(実機の副刻印を薄く出す)。D13
 * @param highlight 主刻印と縁をアクセントの青にする(Shift 面の間の左 Shift。D28。docs/mock/shift-mock.html の案 B)
 * @param enabled false なら押下の演出・触覚・タップをすべて出さない(Shift 面の間の英字以外。D28)
 */
@Composable
fun KeyCap(
    spec: KeySpec,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
    assignedIcon: ImageBitmap? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
) {
    val pressed = rememberPressState()
    val tilt = KeyLayout.tiltOf(spec)
    val density = LocalDensity.current
    Box(
        modifier
            // 押下判定は縮小・沈みを掛ける前の座標で行う(後ろに置くと、押下で縮んだ分だけ縁の指が「外」になり点滅する)。
            .then(if (enabled) Modifier.pressFeedback(pressed, spec.id, onTap = onTap, onLongPress = onLongPress) else Modifier)
            .graphicsLayer {
                // 押下: わずかに縮んで 1dp 沈む。
                val s = if (pressed.value) 0.965f else 1f
                scaleX = s
                scaleY = s
                translationY = if (pressed.value) 1.dp.toPx() else 0f
            }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val rTop = 6.dp.toPx()
                val rBottom = 11.dp.toPx()
                // Classic のキーは下側が丸く膨らむ彫刻形状。下の角は縦に長い楕円の角丸にする。
                val face = Path().apply {
                    addRoundRect(
                        RoundRect(
                            0f, 0f, w, h,
                            topLeftCornerRadius = CornerRadius(rTop),
                            topRightCornerRadius = CornerRadius(rTop),
                            bottomRightCornerRadius = CornerRadius(rBottom, rBottom * 1.4f),
                            bottomLeftCornerRadius = CornerRadius(rBottom, rBottom * 1.4f),
                        ),
                    )
                }
                // 傾きに応じてグラデーションの向きとハイライトの位置をずらす。
                // モックの CSS linear-gradient(180deg + tilt*14deg) と同じ向き: 右側のキー(tilt>0)は明るい側が右上、暗い側が左下。
                val dx = (tan(Math.toRadians(tilt * 14.0)) * h / 2).toFloat()
                val base = Brush.linearGradient(
                    0f to KeyTop, 0.48f to KeyMid, 1f to KeyBottom,
                    start = Offset(w / 2 + dx, 0f), end = Offset(w / 2 - dx, h),
                )
                val hlCenter = Offset(w * (0.5f + tilt * 0.22f), h * 0.18f)
                val hlRadius = w * 0.7f
                val gloss = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = GLOSS), Color.Transparent),
                    center = hlCenter, radius = hlRadius,
                )
                val innerShade = Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))
                val hairline = 1.dp.toPx()

                onDrawBehind {
                    val isPressed = pressed.value
                    // 落ち影(押下時は薄く短く)。
                    val drop = if (isPressed) 0.5.dp.toPx() else 2.dp.toPx()
                    translate(top = drop) { drawPath(face, Color.Black.copy(alpha = if (isPressed) 0.5f else 0.85f)) }
                    drawPath(face, base)
                    clipPath(face) {
                        // ハイライトは横長の楕円(モックの 70% × 45%)。
                        scale(1f, 0.45f / 0.7f, pivot = hlCenter) { drawCircle(gloss, hlRadius, hlCenter) }
                        drawRect(innerShade)
                        drawLine(
                            Color.White.copy(alpha = if (isPressed) EDGE / 2 else EDGE),
                            Offset(rTop * 0.6f, hairline / 2), Offset(w - rTop * 0.6f, hairline / 2), hairline,
                        )
                        if (isPressed) drawRect(Color.Black.copy(alpha = 0.12f))
                    }
                    // Shift 面の左 Shift: 縁を青く。
                    if (highlight) drawPath(face, SheetAccent, style = Stroke(width = 1.5.dp.toPx()))
                }
            },
    ) {
        // 刻印の大きさはキー行の高さから決め、端末の文字サイズ設定に左右されないようにする(toSp は fontScale を打ち消す)。
        val mainSize = with(density) { (rowHeight * MAIN_RATIO).toSp() }
        val secondarySize = with(density) { (rowHeight * SECONDARY_RATIO).toSp() }

        if (assignedIcon != null) {
            // 割り当て済み: 副刻印の位置にアプリの小アイコン(小さく控えめに。既定はグレースケールで、設定で切れる。§5.2)。
            Image(
                assignedIcon, contentDescription = null,
                modifier = Modifier.align(Alignment.TopStart)
                    .padding(start = rowHeight * 0.1f, top = rowHeight * 0.07f)
                    .size(rowHeight * ASSIGNED_ICON_RATIO),
                colorFilter = LocalAssignedIconFilter.current,
            )
        } else {
            spec.secondary?.let { sec ->
                Box(Modifier.align(Alignment.TopStart).padding(start = rowHeight * 0.14f, top = rowHeight * 0.07f)) {
                    Legend(sec, color = SecondaryLegend, size = rowHeight * SECONDARY_RATIO, fontSize = secondarySize, weight = FontWeight.Medium)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = rowHeight * 0.12f)) {
            val isWord = (spec.main as? Legend.Text)?.text?.let { it.length > 1 } == true
            val size = if (isWord) mainSize * 0.78f else mainSize
            Legend(spec.main, color = if (highlight) SheetAccent else MainLegend, size = rowHeight * MAIN_RATIO * 1.1f, fontSize = size, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun Legend(legend: Legend, color: Color, size: Dp, fontSize: androidx.compose.ui.unit.TextUnit, weight: FontWeight) {
    when (legend) {
        is Legend.Text -> BasicText(legend.text, style = TextStyle(color = color, fontSize = fontSize, fontWeight = weight))
        is Legend.Icon -> {
            val vec = FoldBerryIcons.glyph(legend.glyph)
            // 横長のアイコン(スペース)は縦横比を保って幅を広げる。
            val aspect = vec.viewportWidth / vec.viewportHeight
            Image(vec, contentDescription = null, modifier = Modifier.size(width = size * aspect, height = size), colorFilter = ColorFilter.tint(color))
        }
    }
}
