package com.foldberry.launcher.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyLayoutTest {

    @Test
    fun 自由割り当てキーは35個で機能列は5個() {
        assertEquals(35, KeyLayout.assignableKeys.size)
        assertEquals(5, KeyLayout.functionKeys.size)
    }

    @Test
    fun キーIDに重複が無い() {
        val ids = KeyLayout.assignableKeys.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun AからZの26キーがそろっている() {
        val letters = KeyLayout.assignableKeys.map { it.id }.filter { it.length == 1 && it[0] in 'A'..'Z' }
        assertEquals(('A'..'Z').map { it.toString() }.toSet(), letters.toSet())
    }

    @Test
    fun 各段のキーは10列の範囲に収まり重ならない() {
        KeyLayout.rows.forEachIndexed { r, row ->
            val used = mutableSetOf<Int>()
            row.forEach { key ->
                assertTrue("段${r + 1} ${key.id} の列が範囲外", key.column >= 1 && key.column + key.span - 1 <= KeyLayout.COLUMNS)
                (key.column until key.column + key.span).forEach { c ->
                    assertTrue("段${r + 1} 列$c が重複", used.add(c))
                }
            }
        }
    }

    @Test
    fun 上3段は10列を埋め最下段は両端の列を空ける() {
        KeyLayout.rows.take(3).forEach { row -> assertEquals(10, row.sumOf { it.span }) }
        val bottom = KeyLayout.rows[3]
        assertEquals(8, bottom.sumOf { it.span })
        assertTrue(bottom.none { it.column == 1 || it.column + it.span - 1 == 10 })
    }

    @Test
    fun キーの呼び名は文字キーと記号キーで分かれ全キーに付く() {
        val byId = KeyLayout.assignableKeys.associateBy { it.id }
        assertEquals("W キー", KeyLayout.displayName(byId.getValue("W")))
        assertEquals("左 shift キー", KeyLayout.displayName(byId.getValue("LEFT_SHIFT")))
        assertEquals("スペースキー", KeyLayout.displayName(byId.getValue("SPACE")))
        // 記号キーの ID(英大文字の長い名前)がそのまま出ていないこと
        KeyLayout.assignableKeys.filter { it.id.length > 1 }.forEach { key ->
            assertTrue("${key.id} の呼び名が未定義", !KeyLayout.displayName(key).startsWith(key.id))
        }
    }

    @Test
    fun 傾きは左端マイナス1から右端プラス1で中央は0() {
        val q = KeyLayout.assignableKeys.first { it.id == "Q" }
        val p = KeyLayout.assignableKeys.first { it.id == "P" }
        val space = KeyLayout.assignableKeys.first { it.id == "SPACE" } // 列 4〜6 の中心 = 5 列目
        assertEquals(-1f, KeyLayout.tiltOf(q), 0.001f)
        assertEquals(1f, KeyLayout.tiltOf(p), 0.001f)
        assertEquals(-1f / 9f, KeyLayout.tiltOf(space), 0.001f)
    }

    @Test
    fun 機能列は送話と戻ると終話だけ割り当てでき保存キーは文字キーと重ならない() {
        assertEquals(listOf(FunctionKey.SEND, FunctionKey.BACK, FunctionKey.END), KeyLayout.assignableFunctionKeys)
        val fnIds = KeyLayout.assignableFunctionKeys.mapNotNull { it.assignId }
        assertEquals(fnIds.size, fnIds.toSet().size)
        assertTrue(fnIds.none { id -> KeyLayout.assignableKeys.any { it.id == id } })
    }
}
