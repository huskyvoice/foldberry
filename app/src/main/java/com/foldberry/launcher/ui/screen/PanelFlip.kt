package com.foldberry.launcher.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.flipped
import kotlinx.coroutines.launch

/**
 * カレンダー ⇔ Hub をフリップで入れ替える枠(§4.5 / §4.3.1。v0.7.3。2026-11-18 オーナー要望)。
 * 見出しの行([face] に渡す header)を下へ払うと、枠が横の軸で回り(上端が手前に倒れる向き)、裏の面が起き上がってくる。
 *
 * なめらかに見せるための作り(2026-11-18 オーナー指摘「もっとスムーズに」):
 * - 指に付いて 0〜180° を連続して回す(枠の高さぶん払うと 180°)。90° で止めて作り直す旧方式はやめた。
 * - 離したら指の速さを引き継いだばね(弾まない)で 180° か 0° へ。速く払えば短い距離でも返る。
 * - 裏の面は払い始めた時点で組み立てておき、真横を越えたら見せる(90° の瞬間に組み立てると引っかかる)。表と同じ親に key で並べ、裏返り終えても作り直さない。
 * - 回り終えたら、表示の面(override)と角度 0 を同じフレームで入れ替えてから保存を切り替える(保存の反映が 1 フレーム遅れても元の面がちらつかない)。
 * 払いは見出しの行だけで受ける(Hub の一覧の縦スクロール・カレンダーの左右の月送り・‹ › と取り合わないように)。
 *
 * @param kind 保存されている面
 * @param onFlip 保存の切り替え(回り終えたときに 1 回)
 * @param face 面の中身。header は見出しの行に付ける受け口(裏の面には何もしない Modifier を渡す)
 */
@Composable
internal fun FlipPanel(
    kind: MainPanelKind,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    face: @Composable (kind: MainPanelKind, header: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnFlip by rememberUpdatedState(onFlip)
    // 払いの受け口は 1 回しか作らないので、保存の面は最新を読む(作ったときの値のままだと 2 回目以降に逆へ切り替わる)。
    val latestKind by rememberUpdatedState(kind)
    // 回り終えた直後、保存の反映を待たずに新しい面を出すための一時的な面。保存が追いついたら外す。
    var override by remember { mutableStateOf<MainPanelKind?>(null) }
    LaunchedEffect(kind) { if (override == kind) override = null }
    val shown = override ?: kind

    var heightPx by remember { mutableFloatStateOf(1f) }
    val settle = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragDeg by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    // 描画の中で読む今の角度(0=表、180=裏が正面)。
    val angle = { if (dragging) dragDeg else settle.value }
    // 裏の面を組み立てておくか(払っている・回っている間)。角度そのものでは再構成しないよう、切り替わりだけを見る。
    val active by remember { derivedStateOf { dragging || busy || settle.value != 0f } }

    // 見出しの行の位置(画面の座標)。回っていないときだけ記録する(回した面の中で測ると歪むため)。
    var headerBounds by remember { mutableStateOf(Rect.Zero) }
    var boxOrigin by remember { mutableStateOf(Offset.Zero) }
    val header = Modifier.onGloballyPositioned { if (!dragging && !busy && settle.value == 0f) headerBounds = it.boundsInRoot() }

    fun release(velocityPx: Float) {
        val from = dragDeg
        val velocityDeg = velocityPx / heightPx * 180f
        busy = true
        dragging = false
        scope.launch {
                try {
                    settle.snapTo(from)
                    // 半分(90°)を越えたか、下へ速く払ったら裏返す。上へ速く払ったら戻す。
                    val turn = when {
                        velocityDeg > FLING_DEG_PER_SEC -> true
                        velocityDeg < -FLING_DEG_PER_SEC -> false
                        else -> from >= 90f
                    }
                    val target = if (turn) 180f else 0f
                    settle.animateTo(
                        target,
                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                        initialVelocity = velocityDeg,
                    )
                    if (turn) {
                        // 裏の面を表にして角度を 0 に戻す(同じフレームで切り替えるので、見た目は 180° のまま変わらない)。
                        override = (override ?: latestKind).flipped()
                        settle.snapTo(0f)
                        latestOnFlip()
                    }
                } finally {
                    busy = false
                }
            }
    }

    // 払いは回らない外の枠で受ける(回した面の中で受けると、傾くほど指の移動量が歪んで途中で 0° に戻ってしまった)。
    // 押し始めが見出しの行の中のときだけ、縦のしきい値を越えたら払いとして扱う。横の払い・タップは中の部品に任せる。
    val gesture = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (busy || !headerBounds.contains(boxOrigin + down.position)) return@awaitEachGesture
            var pastSlop = 0f
            // 下向きだけを払いとして受ける(§4.5)。上向きは消費せずに見送り、‹ › のタップなどを取り消さない。
            val start = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                if (over > 0f) {
                    change.consume()
                    pastSlop = over
                }
            } ?: return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(start.uptimeMillis, start.position)
            dragDeg = settle.value
            dragging = true
            fun add(dy: Float) {
                dragDeg = (dragDeg + dy / heightPx * 180f).coerceIn(0f, 180f)
            }
            add(pastSlop)
            val ended = verticalDrag(start.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position)
                add(change.positionChange().y)
                change.consume()
            }
            release(if (ended) tracker.calculateVelocity().y else 0f)
        }
    }

    // ばねで回り切るまでの間は、中の部品へのタップを止める(alpha 0 の面にも当たり判定が残り、見えていない面の行やタブが反応したため)。
    // 親の Initial パスで消費すると、子の clickable・スクロールは消費済みとして受けない。
    val blockWhileSettling = Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (busy) event.changes.forEach { it.consume() }
            }
        }
    }

    Box(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .onGloballyPositioned { boxOrigin = it.positionInRoot() }
            .then(blockWhileSettling)
            .then(gesture),
    ) {
        // 裏の面(先に描いて下に置く)と表の面を、同じ親の中で key を付けて並べる。
        // 同じ親の中なので、裏返り終えて裏の面が表になっても作り直さない(別々の親に置くと捨てて作り直し、Hub のアイコンが一瞬ちらついた)。
        val faces = if (active) listOf(shown.flipped(), shown) else listOf(shown)
        for (k in faces) {
            key(k) {
                val front = k == shown
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val a = angle()
                        // 表は 0→-180° と倒れ、裏は 180→0° と起き上がる。真横(90°)で見せる面を入れ替える。
                        rotationX = if (front) -a else 180f - a
                        alpha = if (front == (a < 90f)) 1f else 0f
                        cameraDistance = CAMERA * density
                    },
                ) { face(k, if (front) header else Modifier) }
            }
        }
    }
}

/** カメラの距離(dp 単位の倍率)。近いと回したときに手前が大きく歪む。 */
private const val CAMERA = 16f

/** これより速く払えば、半分に届かなくても裏返す(度/秒。枠の高さ 180°換算)。 */
private const val FLING_DEG_PER_SEC = 300f
