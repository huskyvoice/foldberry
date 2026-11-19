package com.foldberry.launcher.layout

/**
 * メイン画面の「月のカレンダー(または Hub)+自由枠(+タイル)」のマス割り(§4.5。2026-11-17 オーナー決定)。
 * カレンダーは左上から最大 4 列 × 2 段。列・段が足りなければ縮める。
 * 残りのマスを読み順(左上から右へ、次の段へ)に、自由枠 [maxSlots] 個 → タイル [tileCount] 個の順に充てる。
 * 横画面(一覧の枠。4×4)は自由枠 8 だけ。縦画面は上半分を画面にした組み替え(D25。2026-11-19 オーナー決定。モック
 * docs/mock/portrait-mock.html の案 A)で、6×3 なら自由枠=右 2 列×2 段+下の段の左 4、音楽の 2 マス(D26)=下の段の右 2。
 * (旧: 縦は左右の端を空けた中央 4×2。2026-11-19 で廃止)
 * マスが足りない端末では、後ろのタイルから出なくなる。
 * 純粋関数(単体テスト対象 MainHomeTest)。
 */
data class MainHomeGrid(
    val grid: PageGrid,
    /** カレンダーの左端の列(0 始まり)。今は常に 0。 */
    val calendarStartColumn: Int,
    val calendarColumns: Int,
    val calendarRows: Int,
    /** 自由枠のマスの位置(段, 列)。並び順が自由枠の番号(0 始まり)。 */
    val slots: List<Cell>,
    /** 縦の音楽のマスの位置(D26。2 マスをつないで 1 枚にする)。 */
    val tiles: List<Cell> = emptyList(),
) {
    data class Cell(val row: Int, val column: Int)

    companion object {
        fun of(grid: PageGrid, maxSlots: Int, tileCount: Int = 0): MainHomeGrid {
            val columns = 4.coerceAtMost(grid.columns)
            val rows = 2.coerceAtMost(grid.rows)
            val free = buildList {
                for (r in 0 until grid.rows) {
                    for (c in 0 until grid.columns) {
                        if (r < rows && c < columns) continue
                        add(Cell(r, c))
                    }
                }
            }
            val slots = free.take(maxSlots)
            val tiles = free.drop(slots.size).take(tileCount)
            return MainHomeGrid(grid, 0, columns, rows, slots, tiles)
        }
    }
}
