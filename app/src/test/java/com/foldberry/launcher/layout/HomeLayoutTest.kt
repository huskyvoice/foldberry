package com.foldberry.launcher.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    // 実機計測値(2026-11-15): カバー 1248x1972px、メイン 2448x1848px(横)/ 1848x2448px(縦)、420dpi。

    @Test
    fun ヒンジが無ければカバー構成() {
        assertEquals(HomeLayout.Cover, decideHomeLayout(1248, 1972, null))
    }

    @Test
    fun 縦のヒンジは左右分割で境界をヒンジに合わせる() {
        val hinge = Hinge(PxRect(1224, 0, 1224, 1848), isVertical = true) // 幅 0 の線
        val layout = decideHomeLayout(2448, 1848, hinge)
        assertEquals(
            HomeLayout.Main(Split.LeftRight, body = PxRect(0, 0, 1224, 1848), appList = PxRect(1224, 0, 2448, 1848)),
            layout,
        )
    }

    @Test
    fun 幅のあるヒンジは折り目の領域を両側から外す() {
        val hinge = Hinge(PxRect(1210, 0, 1238, 1848), isVertical = true)
        val layout = decideHomeLayout(2448, 1848, hinge) as HomeLayout.Main
        assertEquals(1210, layout.body.right)
        assertEquals(1238, layout.appList.left)
    }

    @Test
    fun 横のヒンジは上下分割で下が本体_上がアプリ一覧() {
        val hinge = Hinge(PxRect(0, 1224, 1848, 1224), isVertical = false)
        val layout = decideHomeLayout(1848, 2448, hinge)
        assertEquals(
            HomeLayout.Main(Split.TopBottom, body = PxRect(0, 1224, 1848, 2448), appList = PxRect(0, 0, 1848, 1224)),
            layout,
        )
    }

    @Test
    fun 幅のある横ヒンジは折り目の領域を上下から外す() {
        val layout = decideHomeLayout(1848, 2448, Hinge(PxRect(0, 1210, 1848, 1238), isVertical = false)) as HomeLayout.Main
        assertEquals(1210, layout.appList.bottom)
        assertEquals(1238, layout.body.top)
    }

    @Test
    fun ヒンジが端にあって片側が空ならカバー構成() {
        assertEquals(HomeLayout.Cover, decideHomeLayout(2448, 1848, Hinge(PxRect(0, 0, 0, 1848), true)))
        assertEquals(HomeLayout.Cover, decideHomeLayout(2448, 1848, Hinge(PxRect(2448, 0, 2448, 1848), true)))
        assertEquals(HomeLayout.Cover, decideHomeLayout(1848, 2448, Hinge(PxRect(0, 2448, 1848, 2448), false)))
    }

    @Test
    fun ヒンジがウィンドウ外にはみ出しても範囲内に丸める() {
        val layout = decideHomeLayout(2448, 1848, Hinge(PxRect(1224, -10, 1300, 3000), true)) as HomeLayout.Main
        assertEquals(PxRect(0, 0, 1224, 1848), layout.body)
        assertEquals(PxRect(1300, 0, 2448, 1848), layout.appList)
    }

    @Test
    fun ウィンドウサイズ0はカバー構成() {
        assertEquals(HomeLayout.Cover, decideHomeLayout(0, 0, Hinge(PxRect(0, 0, 0, 0), true)))
    }

    @Test
    fun システムバーが無く余白24dpの理想条件なら画面部は幅マイナス30dp() {
        // カバー 475x751dp を丸ごと使え、余白が最小の 24dp の場合(ジェスチャーナビ+ステータスバー非表示を想定)
        val b = computeBodyLayout(475f, 751f, 24f)
        assertEquals(445f, b.panel, 0.01f)
        assertEquals(52.43f, b.keyRow, 0.01f)
        assertEquals(60.29f, b.functionRow, 0.01f) // 機能列はキー行より高い(U1 改訂 2026-11-16)
        assertEquals(751f, b.total, 0.01f)
    }

    @Test
    fun 実機のカバー画面ではキー行40dpを守るため画面部を削る() {
        // 実機(2026-11-15): 3 ボタンナビ(約 48dp)でステータスバー(約 41.9dp)も表示中。
        // BerryBody はステータスバーの分を高さから引き、余白を max(24, ナビバー) にする。
        // (旧配分の実機画面取得では 画面部 475.4×455.3dp・キー行 40.0dp だった。)
        val b = computeBodyLayout(475.4f, 751.2f - 41.9f, 48f)
        assertEquals(40f, b.keyRow, 0.01f)
        assertEquals(443.3f, b.panel, 0.1f)
        assertTrue(b.panel < 475.4f - BodyLayout.PANEL_INSET_DP) // 幅 − 30dp も保てない
    }

    @Test
    fun ステータスバーを隠した3ボタンナビの実機ではキー行は約47_7dp() {
        // ステータスバー非表示・ナビバー 48dp の実機カバー(仕様 U1 の補足)。旧配分では 44.5dp だった。
        val b = computeBodyLayout(475.4f, 751.2f, 48f)
        assertEquals(445.4f, b.panel, 0.01f)
        assertEquals(47.73f, b.keyRow, 0.01f)
    }

    @Test
    fun キー行が40dpを下回るなら画面部を削る() {
        // システムバーを考えない 466x704dp(メイン横の左半分の全体): 幅 − 30dp だとキー行 約 45dp で下限を満たす
        val half = computeBodyLayout(466f, 704f, 24f)
        assertEquals(436f, half.panel, 0.01f)
        assertTrue(half.keyRow >= BodyLayout.MIN_KEY_ROW_DP)

        // 高さが足りない場合: キー行は 40dp に留め、画面部の高さを削って total を収める
        val tight = computeBodyLayout(475f, 600f, 24f)
        assertEquals(40f, tight.keyRow, 0.01f)
        assertEquals(600f - 24f - 12f - 40f * 5.15f, tight.panel, 0.01f)
        assertEquals(600f, tight.total, 0.01f)
    }

    @Test
    fun メイン縦の下半分では幅いっぱいを使いキー行40dpを守って画面部を削る() {
        // 1848x2448px の下半分 = 704x466.3dp、ナビバー余白 48dp。幅の上限は設けない(オーナー指定)。
        val b = computeBodyLayout(704f, 466.3f, 48f)
        assertEquals(40f, b.keyRow, 0.01f)
        assertEquals(466.3f - 48f - 12f - 40f * 5.15f, b.panel, 0.01f)
        assertEquals(466.3f, b.total, 0.01f)
    }

    @Test
    fun メイン縦の下半分をキーボードだけにすると下の行を除いた残りをキーに回す() {
        // D25(2026-11-19): 下半分 704x466.3dp、ナビバー余白 48dp、下の行 52dp。
        val k = computeKeyboardLayout(466.3f, 48f, 52f)
        assertEquals((466.3f - 48f - 52f - 12f) / 5.15f, k.keyRow, 0.01f)
        assertEquals(k.keyRow * 1.15f, k.functionRow, 0.01f)
        assertEquals(466.3f, k.total, 0.01f)
        assertTrue(k.keyRow > 60f)
    }

    @Test
    fun キーボードだけでも狭ければキー行40dpを守る() {
        val k = computeKeyboardLayout(200f, 24f, 52f)
        assertEquals(40f, k.keyRow, 0.01f)
        assertTrue(k.total > 200f)
    }

    @Test
    fun 極端に狭い領域では画面部をステータス行まで縮めてキー行40dpを優先する() {
        val b = computeBodyLayout(700f, 200f, 8f)
        assertEquals(40f, b.keyRow, 0.01f)
        assertEquals(BodyLayout.STATUS_DP, b.panel, 0.01f)
        assertTrue(b.total > 200f) // 収まらないことを呼び出し側が判断できる
    }
}
