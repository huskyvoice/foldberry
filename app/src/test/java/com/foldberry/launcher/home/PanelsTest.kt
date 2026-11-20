package com.foldberry.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PanelsTest {

    @Test
    fun 何も開いていなければ閉じるものは無い() {
        assertNull(panelToClose(emptySet()))
    }

    @Test
    fun 手前に重なっているものから1つだけ閉じる() {
        // カバー: 重ね表示 → 長押しメニュー → その上に設定シート
        val open = setOf(Panel.APP_OVERLAY, Panel.APP_MENU, Panel.SETTINGS)
        assertEquals(Panel.SETTINGS, panelToClose(open))
        assertEquals(Panel.APP_MENU, panelToClose(open - Panel.SETTINGS))
        assertEquals(Panel.APP_OVERLAY, panelToClose(setOf(Panel.APP_OVERLAY)))
    }

    @Test
    fun 音声操作はいちばん手前で最初に閉じる() {
        assertEquals(Panel.VOICE, panelToClose(setOf(Panel.VOICE, Panel.SETTINGS, Panel.KEY_PICK)))
        assertEquals(Panel.SETTINGS, panelToClose(setOf(Panel.SETTINGS, Panel.KEY_PICK)))
    }

    @Test
    fun キー選択待ちはいちばん奥で最後に閉じる() {
        assertEquals(Panel.ASSIGN, panelToClose(setOf(Panel.KEY_PICK, Panel.ASSIGN)))
        // 選択待ち中にトラックパッドで一覧を重ねたら、見えている一覧を先に閉じて選択待ちに戻る。
        assertEquals(Panel.APP_OVERLAY, panelToClose(setOf(Panel.KEY_PICK, Panel.APP_OVERLAY)))
        assertEquals(Panel.KEY_PICK, panelToClose(setOf(Panel.KEY_PICK)))
    }

    @Test
    fun メインの一覧は長押しメニューの後で選択待ちの前に閉じる() {
        assertEquals(Panel.APP_MENU, panelToClose(setOf(Panel.MAIN_LIST, Panel.APP_MENU)))
        assertEquals(Panel.ASSIGN, panelToClose(setOf(Panel.MAIN_LIST, Panel.ASSIGN)))
        assertEquals(Panel.MAIN_LIST, panelToClose(setOf(Panel.MAIN_LIST, Panel.KEY_PICK)))
    }

    @Test
    fun 開いたフォルダは割り当てシートの後で選択待ちの前に閉じる() {
        assertEquals(Panel.ASSIGN, panelToClose(setOf(Panel.FOLDER, Panel.ASSIGN)))
        assertEquals(Panel.FOLDER, panelToClose(setOf(Panel.FOLDER, Panel.KEY_PICK)))
        assertEquals(Panel.FOLDER, panelToClose(setOf(Panel.FOLDER)))
    }
}
