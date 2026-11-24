package com.foldberry.launcher.keyboard

import kotlin.math.abs

/** トラックパッドを払った向き(§5.3。2026-11-24)。画面の座標で、上=指を上へ動かした。 */
enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

object TrackpadSwipe {
    /** 主軸の移動量が副軸のこの倍数以上のときだけ向きを決める(斜めの曖昧な払いは何もしない)。 */
    const val DOMINANCE = 1.5f

    /**
     * 押した位置からの移動量 [dx]・[dy](y は下が正)から払いの向きを返す。
     * 主軸が [threshold] 未満、または主軸が副軸の [DOMINANCE] 倍未満なら null(払いではない)。
     */
    fun classify(dx: Float, dy: Float, threshold: Float): SwipeDirection? {
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            ax >= threshold && ax >= ay * DOMINANCE -> if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            ay >= threshold && ay >= ax * DOMINANCE -> if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
            else -> null
        }
    }
}
