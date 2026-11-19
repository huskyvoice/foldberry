package com.foldberry.launcher.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 開閉の動きの長さ。0.22 秒では動きが見えにくいため、比較モック(docs/mock/panel-anim-mock.html)で
 * オーナーが 0.5 秒に決めた(2026-11-17)。暗幕・背面ぼかしも同じ長さで変わる。
 */
private const val PANEL_ANIM_MS = 500

/** いま開閉の動きの中にいるパネルのスコープ(シート本体を下から滑らせるのに使う)。 */
private val LocalPanelScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** 閉じる動きを省くパネル(開閉で作り直された直後に閉じる場合)か。PanelSlide / BouncySlide が見る。 */
private val LocalPanelInstantClose = compositionLocalOf { false }

/**
 * 開いた状態で生まれてから、この時間内に閉じたら「開閉による作り直し直後の後始末」とみなし、閉じる動きを省く。
 * カバー⇔メインの切り替えでは BerryBody が開いた状態のまま作り直され、HomeController.onLayoutChanged が
 * 合成のあとに閉じるため、そのままだと新しい画面で 0.5 秒の閉じる動きが見えてしまう(§8「開閉では動かずに消える」)。
 */
private const val INSTANT_CLOSE_WINDOW_MS = 300L

/** null 化した直後も閉じる動きの間は中身を描けるよう、最後に出した値などを覚える入れ物(状態にしない=再構成を起こさない)。 */
private class Held<T : Any>(val bornOpen: Boolean, val bornAt: Long) {
    var value: T? = null
    var closedOnce = false
}

/**
 * value が null 以外のあいだパネルを出し、開くときは暗幕ごとフェードイン、閉じるときはフェードアウトする。
 * 状態(HomeController の editing / menuApp など)は今までどおり null で閉じる。ここでは最後の値を覚えて
 * 閉じる動きの間も同じ中身を描くだけで、状態の持ち方は変えない。
 * 閉じる動きの間はタッチを受け止め、消えかけのシートで古い対象を操作できないようにする。
 */
@Composable
fun <T : Any> AnimatedPanel(value: T?, content: @Composable (T) -> Unit) {
    val held = remember { Held<T>(bornOpen = value != null, bornAt = SystemClock.uptimeMillis()) }
    if (value != null) held.value = value
    val instantClose = value == null && held.bornOpen && !held.closedOnce &&
        SystemClock.uptimeMillis() - held.bornAt < INSTANT_CLOSE_WINDOW_MS
    if (value == null) held.closedOnce = true
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(PANEL_ANIM_MS)),
        exit = if (instantClose) ExitTransition.None else fadeOut(tween(PANEL_ANIM_MS)),
    ) {
        val shown = value ?: held.value ?: return@AnimatedVisibility
        val closing = value == null
        CompositionLocalProvider(LocalPanelScope provides this, LocalPanelInstantClose provides instantClose) {
            Box(if (closing) Modifier.pointerInput(Unit) { swallowAll() } else Modifier) { content(shown) }
        }
    }
}

/**
 * 背面ぼかしの半径(表示ギミック 5。2026-11-17 オーナー決定)。RenderEffect は半径を sigma ≈ 0.577r+0.5 に変換するので、
 * 比較モック(CSS blur(10px) = sigma 10)と同じ強さになるよう 16dp にする(変換式はフレームワークの実装知識による推定)。
 */
private val BACKDROP_BLUR = 16.dp

/**
 * シートを開いている間、後ろの本体をぼかす。開閉の動きと同じ長さで 0 ⇔ BACKDROP_BLUR に変わる。
 * 半径は描画(graphicsLayer)の中で読むので、変化の間も合成のやり直しは起きない。
 * 開いた状態で作り直された直後に閉じる(開閉)ときは、パネルと同じく動かさずに消す。
 * 暗幕のフェード・シートのスライドはそのままで、奥行きを足す(Android 12 以降の RenderEffect。minSdk 35 なので常に効く)。
 */
@Composable
internal fun rememberBackdropBlur(open: Boolean): State<Dp> {
    val born = remember { Held<Unit>(bornOpen = open, bornAt = SystemClock.uptimeMillis()) }
    val instant = !open && born.bornOpen && !born.closedOnce && SystemClock.uptimeMillis() - born.bornAt < INSTANT_CLOSE_WINDOW_MS
    if (!open) born.closedOnce = true
    return animateDpAsState(
        if (open) BACKDROP_BLUR else 0.dp,
        if (instant) snap() else tween(PANEL_ANIM_MS),
        label = "backdropBlur",
    )
}

/** rememberBackdropBlur の半径を描画の中で読んでぼかす(GraphicsLayerScope 用)。 */
internal fun GraphicsLayerScope.applyBackdropBlur(radius: State<Dp>) {
    val r = radius.value.toPx()
    renderEffect = if (r > 0f) BlurEffect(r, r) else null
}

/**
 * AnimatedPanel の中なら、中身を下から滑り出させ下へ滑り去らせる。外なら modifier だけを掛けて置く。
 * @param bounce 弾むせり上がり(表示ギミック案 4。設定・割り当てシートで使う。2026-11-17 オーナー決定)
 */
@Composable
internal fun PanelSlide(modifier: Modifier = Modifier, bounce: Boolean = false, content: @Composable () -> Unit) {
    val scope = LocalPanelScope.current
    val instantClose = LocalPanelInstantClose.current
    if (bounce && scope != null) {
        BouncySlide(scope, instantClose, modifier, content)
        return
    }
    val slide = if (scope == null) {
        Modifier
    } else {
        with(scope) {
            Modifier.animateEnterExit(
                enter = slideInVertically(tween(PANEL_ANIM_MS)) { it },
                exit = if (instantClose) ExitTransition.None else slideOutVertically(tween(PANEL_ANIM_MS)) { it },
            )
        }
    }
    Box(modifier.then(slide)) { content() }
}

/**
 * 弾むせり上がり。進み具合 0(隠れている)〜1(定位置)を keyframes で動かし、シートの高さ × (1 − 進み具合) だけ下へずらす。
 * 標準の slideInVertically は高さに対する割合でキーフレームを書けないため、こうしている。
 * 開くとき: 3% 行き過ぎ(1.03)、1% 戻り(0.99)、最後に定位置。閉じるときはその逆順(モックは逆再生で閉じる)。
 * 時刻はモック docs/mock/panel-anim-mock.html の案 4(全体に ease-out を掛けた 72%・88%)を実時間に直した 53%・71%。
 * 動きの途中で向きが変わった(開く途中で閉じた等)ときは、keyframes の 0 時点の値で跳ばないよう、今の位置から素直に戻す。
 * 開閉による作り直し直後に閉じるときは動かさずに消す(LocalPanelInstantClose)。
 * 進み具合は AnimatedVisibility の transition に載せるので、閉じる動きが終わるまで中身は残る。
 */
@Composable
private fun BouncySlide(scope: AnimatedVisibilityScope, instantClose: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    // 直前の進み具合(状態にしない)。止まっている(0 か 1)ときだけ keyframes を使う判定に使う。
    val last = remember { floatArrayOf(0f) }
    val progress by scope.transition.animateFloat(
        transitionSpec = {
            val atRest = last[0] <= 0.001f || (last[0] >= 0.999f && last[0] <= 1.001f)
            when {
                targetState != EnterExitState.Visible && instantClose -> snap()
                !atRest -> tween(PANEL_ANIM_MS / 2, easing = FastOutSlowInEasing)
                targetState == EnterExitState.Visible -> keyframes {
                    durationMillis = PANEL_ANIM_MS
                    0f at 0 using LinearOutSlowInEasing
                    1.03f at (PANEL_ANIM_MS * 0.53f).toInt()
                    0.99f at (PANEL_ANIM_MS * 0.71f).toInt()
                }
                else -> keyframes {
                    durationMillis = PANEL_ANIM_MS
                    1f at 0
                    0.99f at (PANEL_ANIM_MS * 0.29f).toInt()
                    1.03f at (PANEL_ANIM_MS * 0.47f).toInt() using FastOutLinearInEasing
                }
            }
        },
        label = "bouncySlide",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // 進み具合は描画の中でだけ読む(合成で読むとシート全体が毎フレーム作り直される)。
    Box(
        modifier.graphicsLayer {
            val p = progress
            last[0] = p
            translationY = (1f - p) * size.height
        },
    ) { content() }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.swallowAll() {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
