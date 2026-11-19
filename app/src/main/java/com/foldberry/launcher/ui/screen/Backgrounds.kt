package com.foldberry.launcher.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.foldberry.launcher.data.BackgroundPreset
import kotlin.math.hypot
import kotlin.math.max

/*
 * 画面部の内蔵背景(仕様書 §4.4 / U4)。デザイン確定モック docs/mock/background-mock.html の SVG と同じ図形を、
 * 画像素材を使わずに描く(自作。既存の壁紙は使わない)。
 * モックは 475×475 の正方形で設計したので、画面部の縦横比が変わっても破綻しないよう、
 * 位置は幅・高さに対する比率、大きさは長い辺(= 正方形のモックの 1 辺に相当)に対する比率で描く。
 */

private const val MOCK = 475f

@Composable
fun PresetBackground(preset: BackgroundPreset, modifier: Modifier) {
    Canvas(modifier) { drawPreset(preset) }
}

fun DrawScope.drawPreset(preset: BackgroundPreset) {
    when (preset) {
        BackgroundPreset.AURORA -> drawAurora()
        BackgroundPreset.BERRY -> drawBerry()
        BackgroundPreset.GRAPHITE -> drawGraphite()
        BackgroundPreset.ABYSS -> drawAbyss()
    }
}

/** モックの 1px を、この画面部での長さに換算する係数。 */
private fun DrawScope.unit(): Float = max(size.width, size.height) / MOCK

private fun DrawScope.radialGlow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
    drawRect(Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), Offset(cx, cy), radius))
}

/** 中心を通る線から offset だけずらした位置に、angle だけ傾けた光の帯を描く(帯の長さ方向は画面全体を覆う)。 */
private fun DrawScope.band(angle: Float, offset: Float, thickness: Float, color: Color, alpha: Float) {
    val diag = hypot(size.width, size.height)
    rotate(angle, pivot = center) {
        val left = center.x - diag
        val top = center.y + offset - thickness / 2
        drawRect(
            Brush.horizontalGradient(
                0f to color.copy(alpha = 0f), 0.5f to color.copy(alpha = alpha), 1f to color.copy(alpha = 0f),
                startX = left, endX = left + diag * 2,
            ),
            topLeft = Offset(left, top),
            size = Size(diag * 2, thickness),
        )
    }
}

/** 1 オーロラ: 濃紺の地に、斜めに走る水色と紫の光の帯(既定)。 */
private fun DrawScope.drawAurora() {
    val u = unit()
    drawRect(
        Brush.linearGradient(
            0f to Color(0xFF0D1320), 0.55f to Color(0xFF121A28), 1f to Color(0xFF06080C),
            start = Offset.Zero, end = Offset(size.width, size.height),
        ),
    )
    radialGlow(size.width * 0.85f, 0f, 0.8f * MOCK * u, Color(0xFF1F3550), 1f)
    band(-28f, offset = -52.5f * u, thickness = 70f * u, color = Color(0xFF3FB6E8), alpha = 0.28f)
    band(-28f, offset = 67.5f * u, thickness = 110f * u, color = Color(0xFF7A5AD0), alpha = 0.22f)
}

/** 4 ベリー: 黒から深い紫へ。右下にベリー色の柔らかな光。 */
private fun DrawScope.drawBerry() {
    val u = unit()
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0A10), Color(0xFF1A0F22))))
    radialGlow(size.width * 0.85f, size.height * 0.9f, 0.75f * MOCK * u, Color(0xFF9B2C6F), 0.55f)
    radialGlow(size.width * 0.1f, size.height * 0.15f, 0.6f * MOCK * u, Color(0xFF4B2A7A), 0.35f)
}

/** 5 グラファイト: ほぼ黒の地に、細い斜めの線と淡い光沢。 */
private fun DrawScope.drawGraphite() {
    val u = unit()
    drawRect(
        Brush.linearGradient(
            0f to Color(0xFF1B1D21), 0.5f to Color(0xFF121316), 1f to Color(0xFF0A0A0C),
            start = Offset.Zero, end = Offset(size.width, size.height),
        ),
    )
    val diag = hypot(size.width, size.height)
    val step = 6f * u
    val line = Color.White.copy(alpha = 0.035f)
    rotate(-35f, pivot = center) {
        var x = center.x - diag
        while (x < center.x + diag) {
            drawLine(line, Offset(x, center.y - diag), Offset(x, center.y + diag), strokeWidth = max(1f, u))
            x += step
        }
    }
    // 淡い光沢: モックの g-sheen(幅 200 の帯を幅方向にぼかし、translate(180 -200) してから中心で -35° 回す)。
    // band() は長さ方向にぼかすので使わず、帯の幅方向に勾配を掛ける。帯の中心線は回転前に中心から左へ 57(=237-180)。
    rotate(-35f, pivot = center) {
        val left = center.x - (57f + 100f) * u
        val width = 200f * u
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0f), 0.5f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0f),
                startX = left, endX = left + width,
            ),
            topLeft = Offset(left, center.y - diag),
            size = Size(width, diag * 2),
        )
    }
}

/** 6 深海: 深い青緑から黒へ。ぼやけた光の粒がいくつか浮かぶ。 */
private fun DrawScope.drawAbyss() {
    val u = unit()
    drawRect(Brush.verticalGradient(0f to Color(0xFF0C2230), 0.6f to Color(0xFF08121A), 1f to Color(0xFF040608)))
    val particle = Color(0xFF7FD4F0)
    listOf(
        Triple(80f, 90f, 46f), Triple(370f, 60f, 30f), Triple(300f, 200f, 64f),
        Triple(120f, 330f, 38f), Triple(420f, 300f, 22f), Triple(220f, 420f, 50f),
    ).forEach { (x, y, r) ->
        val c = Offset(size.width * x / MOCK, size.height * y / MOCK)
        drawCircle(Brush.radialGradient(listOf(particle.copy(alpha = 0.35f), particle.copy(alpha = 0f)), c, r * u), r * u, c)
    }
}
