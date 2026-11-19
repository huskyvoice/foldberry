package com.foldberry.launcher.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPagesTest {

    @Test
    fun カバーの重ね表示は4列5行() {
        // 幅 475dp、高さ 751 − 見出し 48 − ナビバー 48 − ページの点 26 ≈ 629dp(モック案 B と同じ 4×5)。
        assertEquals(PageGrid(4, 5), PageGrid.of(475f, 629f))
    }

    @Test
    fun 極端に狭い低い領域でも3列2行を下回らない() {
        assertEquals(PageGrid(3, 2), PageGrid.of(200f, 100f))
    }

    @Test
    fun ページ数は端数を切り上げ0件でも1ページ() {
        val g = PageGrid(4, 5)
        assertEquals(1, g.pageCount(0))
        assertEquals(1, g.pageCount(20))
        assertEquals(2, g.pageCount(21))
        assertEquals(3, g.pageCount(41))
    }
}
