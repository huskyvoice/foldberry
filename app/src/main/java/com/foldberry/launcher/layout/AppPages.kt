package com.foldberry.launcher.layout

import kotlin.math.floor

/**
 * アプリ一覧のページ式グリッド(§6。2026-11-17 オーナー決定: 比較モック docs/mock/applist-mock.html の案 B)。
 * 1 ページの列数・行数を一覧の領域の大きさから決める純粋関数(単体テスト対象 AppPagesTest)。
 * カバーの重ね表示(幅 475dp・ページ部 約 600dp)で 4 列 × 5 行になるよう、1 マスの目安を幅 104dp・高さ 112dp とする。
 */
data class PageGrid(val columns: Int, val rows: Int) {
    val perPage: Int get() = columns * rows

    /** 件数を並べるのに要るページ数(0 件でも 1 ページ)。 */
    fun pageCount(items: Int): Int = if (items <= 0) 1 else (items + perPage - 1) / perPage

    companion object {
        const val CELL_WIDTH_DP = 104f
        const val CELL_HEIGHT_DP = 112f

        /** @param widthDp ページの幅 @param heightDp ページの高さ(見出し・ページの点・ナビバーを除いた分) */
        fun of(widthDp: Float, heightDp: Float): PageGrid = PageGrid(
            columns = floor(widthDp / CELL_WIDTH_DP).toInt().coerceIn(3, 8),
            rows = floor(heightDp / CELL_HEIGHT_DP).toInt().coerceIn(2, 8),
        )
    }
}
