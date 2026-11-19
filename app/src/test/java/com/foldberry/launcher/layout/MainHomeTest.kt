package com.foldberry.launcher.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class MainHomeTest {

    // 自由枠の上限は KeyLayout.MAIN_SLOT_COUNT(8。2026-11-18 に 12→8)。layout は keyboard に依存しないので数で書く。
    private val max = 8

    @Test
    fun 横画面は上4x2がカレンダーで下4x2の8枠() {
        // 実機の横はステータス行の帯の分だけ低く 4×4(2026-11-18)。
        val m = MainHomeGrid.of(PageGrid(4, 4), maxSlots = max)
        assertEquals(0, m.calendarStartColumn)
        assertEquals(4, m.calendarColumns)
        assertEquals(2, m.calendarRows)
        assertEquals(8, m.slots.size)
        assertEquals(MainHomeGrid.Cell(2, 0), m.slots.first())
        assertEquals(MainHomeGrid.Cell(3, 3), m.slots.last())
        assertEquals(emptyList<MainHomeGrid.Cell>(), m.tiles)
    }

    @Test
    fun 縦画面は左上4x2がカレンダーで右2列と下の段の左4つが自由枠_下の段の右2つがタイル() {
        // メイン縦の組み替え(D25。モック portrait-mock.html の案 A)。実機は 6×3。
        val m = MainHomeGrid.of(PageGrid(6, 3), maxSlots = max, tileCount = 2)
        assertEquals(0, m.calendarStartColumn)
        assertEquals(4, m.calendarColumns)
        assertEquals(2, m.calendarRows)
        assertEquals(
            listOf(
                MainHomeGrid.Cell(0, 4), MainHomeGrid.Cell(0, 5), MainHomeGrid.Cell(1, 4), MainHomeGrid.Cell(1, 5),
                MainHomeGrid.Cell(2, 0), MainHomeGrid.Cell(2, 1), MainHomeGrid.Cell(2, 2), MainHomeGrid.Cell(2, 3),
            ),
            m.slots,
        )
        assertEquals(listOf(MainHomeGrid.Cell(2, 4), MainHomeGrid.Cell(2, 5)), m.tiles)
    }

    @Test
    fun マスが足りなければタイルから出なくなる() {
        // 5 列×3 段: 空きは 1+1+5=7 マス。自由枠 7 つで埋まり、タイルは出ない。
        val five = MainHomeGrid.of(PageGrid(5, 3), maxSlots = max, tileCount = 2)
        assertEquals(7, five.slots.size)
        assertEquals(emptyList<MainHomeGrid.Cell>(), five.tiles)
        // 7 列×3 段: 空きは 3+3+7=13 マス。自由枠 8 → タイル 2。
        val seven = MainHomeGrid.of(PageGrid(7, 3), maxSlots = max, tileCount = 2)
        assertEquals(8, seven.slots.size)
        assertEquals(listOf(MainHomeGrid.Cell(2, 2), MainHomeGrid.Cell(2, 3)), seven.tiles)
    }

    @Test
    fun 自由枠は上限で打ち切る() {
        // 横で 4×5 になる端末でも 8 枠まで(5 段目は空く)。
        val m = MainHomeGrid.of(PageGrid(4, 5), maxSlots = max)
        assertEquals(8, m.slots.size)
        assertEquals(MainHomeGrid.Cell(3, 3), m.slots.last())
    }

    @Test
    fun マスが足りなければカレンダーを縮める() {
        val m = MainHomeGrid.of(PageGrid(3, 2), maxSlots = max, tileCount = 2)
        assertEquals(3, m.calendarColumns)
        assertEquals(2, m.calendarRows)
        assertEquals(0, m.slots.size)
        assertEquals(0, m.tiles.size)
    }
}
