package com.foldberry.launcher.keyboard

import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import com.foldberry.launcher.data.DefaultApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentSlotsTest {
    private val a1 = AppRef("p1", "A")
    private val a2 = AppRef("p2", "A")

    @Test
    fun 割り当て一覧はアプリ名順で_同名の別アプリは混ざらず_同じアプリは枠の並び() {
        val cam1 = AppRef("z.cam", "C")
        val cam2 = AppRef("a.cam", "C")
        val chrome = AppRef("chrome", "C")
        val unknown = AppRef("1unknown", "C")
        val a = Assignments.EMPTY
            .withKey("Q", cam1).withKey("W", cam2).withKey("E", cam1)
            .withKey("R", chrome).withKey("T", unknown).withKey(KeyLayout.mainSlotId(0), chrome)
        val names = mapOf(cam1 to "カメラ", cam2 to "カメラ", chrome to "Chrome")
        val labels = AssignmentSlots.byAppName(a) { names[it] }.map(AssignmentBadge::label)
        // Chrome(英字)→ 名前が無いのでパッケージ名の 1unknown(#)→ カメラ(a.cam → z.cam の Q・E)。
        assertEquals(listOf("R", "M1", "T", "W", "Q", "E"), labels)
    }

    @Test
    fun 割り当て一覧の行はアプリごとにまとまり_同名の別アプリは別の行() {
        val cam1 = AppRef("z.cam", "C")
        val cam2 = AppRef("a.cam", "C")
        val chrome = AppRef("chrome", "C")
        val a = Assignments.EMPTY
            .withKey("Q", cam1).withKey("W", cam2).withKey("E", cam1)
            .withKey("R", chrome).withKey(KeyLayout.mainSlotId(0), chrome)
        val names = mapOf(cam1 to "カメラ", cam2 to "カメラ", chrome to "Chrome")
        val rows = AssignmentSlots.byApp(a) { names[it] }
        assertEquals(listOf(chrome, cam2, cam1), rows.map { it.app })
        assertEquals(listOf(listOf("R", "M1"), listOf("W"), listOf("Q", "E")), rows.map { r -> r.slots.map(AssignmentBadge::label) })
        assertTrue(AssignmentSlots.byApp(Assignments.EMPTY) { null }.isEmpty())
    }

    @Test
    fun 割り当てシートのバッジは枠をすべて並べる() {
        val a = Assignments.EMPTY.withKey(KeyLayout.mainSlotId(0), a1).withKey("Q", a1).withKey("P", a2)
        assertEquals(listOf("Q", "M1"), AssignmentBadge.all(a, a1))
        assertEquals(listOf("P"), AssignmentBadge.all(a, a2))
        assertTrue(AssignmentBadge.all(a, AppRef("none", "X")).isEmpty())
    }

    @Test
    fun 未割り当ての送話とカメラの枠には既定のアプリが既定として入る() {
        val dialer = AppRef("phone", "D")
        val camera = AppRef("cam", "C")
        val defaults = DefaultApps(dialer = dialer, camera = camera)
        val slots = AssignmentSlots.assigned(Assignments.EMPTY.withKey("Q", a1), defaults)
        assertEquals(listOf("Q", "TEL", "CAM"), slots.map(AssignmentBadge::label))
        assertEquals(listOf(false, true, true), slots.map { it.isDefault })
        // バッジも既定を数え、先頭が既定なら点線。
        assertEquals("TEL", AssignmentBadge.of(Assignments.EMPTY, dialer, defaults))
        assertTrue(AssignmentBadge.firstIsDefault(Assignments.EMPTY, dialer, defaults))
        assertFalse(AssignmentBadge.firstIsDefault(Assignments.EMPTY.withKey("Q", dialer), dialer, defaults))
        assertEquals("Q+", AssignmentBadge.of(Assignments.EMPTY.withKey("Q", dialer), dialer, defaults))
    }

    @Test
    fun 既定のアプリを自由枠やタイルにも割り当てたらバッジは自分の枠を先に出して点線にしない() {
        val dialer = AppRef("phone", "D")
        val defaults = DefaultApps(dialer = dialer)
        // 枠の並びでは送話(既定)が自由枠より前に来るが、バッジは自分の割り当てを優先する。
        val a = Assignments.EMPTY.withKey(KeyLayout.mainSlotId(0), dialer).withKey(KeyLayout.mainSlotId(1), dialer)
        assertEquals("M1+", AssignmentBadge.of(a, dialer, defaults))
        assertFalse(AssignmentBadge.firstIsDefault(a, dialer, defaults))
        assertEquals(listOf("M1", "M2", "TEL"), AssignmentBadge.all(a, dialer, defaults))
        assertEquals(listOf(false, false, true), AssignmentBadge.slotsOf(a, dialer, defaults).map { it.isDefault })
    }

    @Test
    fun 自分で割り当てていれば既定は出さず_既定が無ければ何も足さない() {
        val defaults = DefaultApps(dialer = AppRef("phone", "D"), camera = AppRef("cam", "C"))
        val own = Assignments.EMPTY.withKey(FunctionKey.SEND.assignId!!, a1).withKey(KeyLayout.DOCK_CAMERA_ID, a2)
        val slots = AssignmentSlots.assigned(own, defaults)
        assertEquals(listOf(a1, a2), slots.map { it.app })
        assertTrue(slots.none { it.isDefault })
        assertTrue(AssignmentSlots.assigned(Assignments.EMPTY, DefaultApps()).isEmpty())
        assertTrue(AssignmentSlots.assigned(Assignments.EMPTY).isEmpty())
    }

    @Test
    fun 割り当て一覧の行にも既定が入る() {
        val dialer = AppRef("phone", "D")
        val rows = AssignmentSlots.byApp(Assignments.EMPTY.withKey("Q", dialer), DefaultApps(dialer = dialer)) { "電話" }
        assertEquals(1, rows.size)
        assertEquals(listOf(false, true), rows[0].slots.map { it.isDefault })
    }

    @Test
    fun 何も割り当てていなければ空() {
        assertTrue(AssignmentSlots.assigned(Assignments.EMPTY).isEmpty())
    }

    @Test
    fun キー_機能列_カメラ_自由枠の順に並ぶ() {
        val a = Assignments.EMPTY
            .withKey(KeyLayout.mainSlotId(7), a1)
            .withKey(KeyLayout.mainSlotId(2), a2)
            .withKey(KeyLayout.DOCK_CAMERA_ID, a1)
            .withKey(FunctionKey.END.assignId!!, a2)
            .withKey(FunctionKey.SEND.assignId!!, a1)
            .withKey("P", a2)
            .withKey("Q", a1)
        val labels = AssignmentSlots.assigned(a).map(AssignmentBadge::label)
        // キーはキーボードの並び順(Q が P より先)、機能列は送話 → 終話。
        assertEquals(listOf("Q", "P", "TEL", "END", "CAM", "M3", "M8"), labels)
    }

    @Test
    fun 枠の無いキーIDは出さない() {
        // 手で編集したファイル等。画面に枠が無く変更・解除できないので一覧に出さない。
        val a = Assignments.EMPTY.withKey("NO_SUCH_KEY", a1).withKey("W", a2)
        val slots = AssignmentSlots.assigned(a)
        assertEquals(1, slots.size)
        assertEquals(AssignmentSlot.Key(KeyLayout.assignableKeys.first { it.id == "W" }, a2), slots.single())
    }

    @Test
    fun バッジの先頭は一覧の先頭と同じ() {
        val a = Assignments.EMPTY.withKey(KeyLayout.mainSlotId(1), a1).withKey(KeyLayout.mainSlotId(0), a1).withKey("Z", a1)
        assertEquals("Z+", AssignmentBadge.of(a, a1))
        assertEquals("Z", AssignmentBadge.label(AssignmentSlots.assigned(a).first()))
    }

    @Test
    fun 自由枠と同じキー文字列でフォルダを置ける() {
        assertEquals(Assignments.MAIN_SLOT_KEYS, (0 until KeyLayout.MAIN_SLOT_COUNT).map(KeyLayout::mainSlotId).toSet())
    }

    @Test
    fun フォルダの中のアプリはその枠として並びバッジはF付きの枠の番号() {
        val id = KeyLayout.mainSlotId(2)
        val a = Assignments.EMPTY.withKey("W", a1)
            .withFolder(id, com.foldberry.launcher.data.AppFolder("仕事", listOf(a2, a1)))
        val slots = AssignmentSlots.assigned(a)
        assertEquals(listOf("W", "F3", "F3"), slots.map(AssignmentBadge::label))
        assertTrue(slots.drop(1).all { it is AssignmentSlot.MainSlot && it.inFolder })
        assertEquals("W+", AssignmentBadge.of(a, a1))
        assertEquals("F3", AssignmentBadge.of(a, a2))
    }

    @Test
    fun 解除はフォルダの中ならそのアプリだけ外し最後の1つでフォルダごと消える() {
        val id = KeyLayout.mainSlotId(0)
        val a = Assignments.EMPTY.withFolder(id, com.foldberry.launcher.data.AppFolder("", listOf(a1, a2)))
        val one = AssignmentSlots.clear(a, AssignmentSlot.MainSlot(0, a1, inFolder = true))
        assertEquals(listOf(a2), one.folders[id]?.apps)
        val none = AssignmentSlots.clear(one, AssignmentSlot.MainSlot(0, a2, inFolder = true))
        assertTrue(none.folders.isEmpty())
        assertFalse(id in none.keys)
    }

    @Test
    fun 解除はキー_機能列_カメラ_自由枠の枠から外し既定は変えない() {
        val a = Assignments.EMPTY.withKey("W", a1).withKey(KeyLayout.DOCK_CAMERA_ID, a1).withKey(KeyLayout.mainSlotId(1), a1)
        val cleared = AssignmentSlots.assigned(a).fold(a) { acc, slot -> AssignmentSlots.clear(acc, slot) }
        assertTrue(cleared.keys.isEmpty())
        val withDefault = AssignmentSlot.DockCamera(a2, isDefault = true)
        assertEquals(a, AssignmentSlots.clear(a, withDefault))
    }

    @Test
    fun フォルダのバッジの番号だけを取り出す() {
        assertEquals("1", AssignmentBadge.folderNumber("F1"))
        assertEquals("8+", AssignmentBadge.folderNumber("F8+"))
        // 文字キーの F(数字が続かない)・ほかの枠はフォルダではない。
        assertEquals(null, AssignmentBadge.folderNumber("F"))
        assertEquals(null, AssignmentBadge.folderNumber("M1"))
        assertEquals(null, AssignmentBadge.folderNumber("T1"))
    }
}
