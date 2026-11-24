package com.foldberry.launcher.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.keyboard.SwipeDirection
import com.foldberry.launcher.keyboard.TrackpadSwipe

/** 払いとみなす移動量(§5.3)。これ未満のずれはタップのまま。実機で調整する。 */
private val SWIPE_THRESHOLD = 24.dp

/** 触覚フィードバックを出すか(設定の「触覚フィードバック」。§8)。BerryBody が設定に従って配る(上の枠の自由枠・一覧は combinedClickable の hapticFeedbackEnabled で同じ設定を使う)。オフでも沈む演出は出す。 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** キーの押下状態(押している間 true)。pressFeedback に渡す。 */
@Composable
fun rememberPressState(): MutableState<Boolean> = remember { mutableStateOf(false) }

/**
 * キーの押下状態を持ち、触れた瞬間に押下演出と触覚フィードバックを出す(仕様書 §5.4)。
 * 離すまで待たないのは、連打しても遅延を感じさせないため(§13)。
 * キーごとに独立して判定するので、2 本指の同時押しも扱える。
 * 指がキーの外へ出たら演出を戻し、離す・キャンセルでも必ず戻す。
 *
 * タップ(§5.2): 離した位置がキーの内側なら onTap。外で離したら何もしない。
 * 長押し(§5.2): 端末の長押し時間を超えても内側にいれば onLongPress(以後離してもタップにしない)。
 * 払い(§5.3。2026-11-24): onSwipe を渡したキーだけ。押した位置から SWIPE_THRESHOLD 動いて向きが決まった時点で 1 回呼び、
 * 以後はタップ・長押しにしない(指がキーの外に出ても払いを優先する。トラックパッドは小さく、払うと外に出るため)。
 * 押下の状態は rememberPressState で作って渡す。
 */
@Composable
fun Modifier.pressFeedback(
    pressed: MutableState<Boolean>,
    key: Any,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onSwipe: ((SwipeDirection) -> Unit)? = null,
): Modifier {
    val view = LocalView.current
    val haptics by rememberUpdatedState(LocalHapticsEnabled.current)
    // pointerInput は key が変わらない限り作り直されないので、最新の処理を参照する。
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val swipe by rememberUpdatedState(onSwipe)
    return this.pointerInput(key) {
        awaitEachGesture {
            try {
                val first = awaitFirstDown(requireUnconsumed = false)
                pressed.value = true
                val swipeThreshold = SWIPE_THRESHOLD.toPx()
                // 長押しが出た後は払いにしない(長押しを持つキーで二重に動かないように)。
                var swipeAllowed = swipe != null
                var swiped = false
                // 端末標準の触覚(権限不要)。端末の「タッチ時の振動」設定に従う。
                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                // 48dp 未満のキーは、Compose がタッチの当たり範囲を広げる(extendedTouchPadding)。
                // キーの隙間に触れて反応した指を即「外」と判定しないよう、広げた範囲までを内側とみなす。
                val padX = extendedTouchPadding.width
                val padY = extendedTouchPadding.height
                var inside = true

                /**
                 * 全部の指が離れるまで追う。離した時点で内側だったかを返す。
                 * キャンセル(戻るジェスチャーでシステムに指を奪われた等)は、Compose が「消費済みの指離し」として
                 * 流してくるので、内側でも false にする(タップとして誤起動しない)。
                 */
                suspend fun AwaitPointerEventScope.trackUntilUp(): Boolean {
                    while (true) {
                        val event = awaitPointerEvent()
                        val down = event.changes.filter { it.pressed }
                        if (down.isEmpty()) return !swiped && inside && event.changes.none { it.isConsumed }
                        if (swiped) continue
                        if (swipeAllowed) {
                            val c = down.firstOrNull { it.id == first.id }
                            val dir = c?.let { TrackpadSwipe.classify(it.position.x - first.position.x, it.position.y - first.position.y, swipeThreshold) }
                            if (dir != null) {
                                swiped = true
                                pressed.value = false
                                if (haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                swipe?.invoke(dir)
                                continue
                            }
                        }
                        inside = down.any { c ->
                            c.position.x in -padX..(size.width + padX) && c.position.y in -padY..(size.height + padY)
                        }
                        pressed.value = inside
                    }
                }

                val releasedInside: Boolean? = if (longPress != null) {
                    withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { trackUntilUp() }
                } else {
                    trackUntilUp()
                }

                if (releasedInside == null) {
                    // 長押し時間を超えた(まだ押している)。払いの途中なら長押しにしない。
                    swipeAllowed = false
                    if (inside && !swiped) {
                        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        longPress?.invoke()
                    }
                    trackUntilUp() // 離すまで待つ。長押しの後はタップにしない。
                } else if (releasedInside) {
                    tap?.invoke()
                }
            } finally {
                pressed.value = false
            }
        }
    }
}
