package com.foldberry.launcher.keyboard

import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.Assignments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssignmentBadgeTest {
    private val gmail = AppRef("com.google.android.gm", "Main")
    private val maps = AppRef("com.google.android.apps.maps", "Main")

    @Test
    fun 割り当てが無ければバッジなし() {
        assertNull(AssignmentBadge.of(Assignments.EMPTY, gmail))
    }

    @Test
    fun 文字キーはキーの文字自由枠はM番号() {
        // タイル T1・T2 の割り当ては 2026-11-19 に廃止(D26)。
        val a = Assignments.EMPTY.withKey("W", gmail).withKey(KeyLayout.mainSlotId(0), maps)
        assertEquals("W", AssignmentBadge.of(a, gmail))
        assertEquals("M1", AssignmentBadge.of(a, maps))
    }

    @Test
    fun 複数に割り当てていればキーを先にしてプラスを付ける() {
        val a = Assignments.EMPTY.withKey(KeyLayout.mainSlotId(1), gmail).withKey("P", gmail).withKey("Q", gmail)
        // キーボードの並び順で Q が先。
        assertEquals("Q+", AssignmentBadge.of(a, gmail))
    }

    @Test
    fun 記号キーと機能列は短い表記() {
        assertEquals("SFT", AssignmentBadge.of(Assignments.EMPTY.withKey("LEFT_SHIFT", gmail), gmail))
        assertEquals("TEL", AssignmentBadge.of(Assignments.EMPTY.withKey("FN_SEND", gmail), gmail))
    }

    @Test
    fun カメラの枠はCAMで機能列の後タイルの前() {
        val cam = Assignments.EMPTY.withKey(KeyLayout.DOCK_CAMERA_ID, gmail)
        assertEquals("CAM", AssignmentBadge.of(cam, gmail))
        assertEquals("CAM+", AssignmentBadge.of(cam.withKey(KeyLayout.mainSlotId(0), gmail), gmail))
        assertEquals("TEL+", AssignmentBadge.of(cam.withKey("FN_SEND", gmail), gmail))
    }

    @Test
    fun カメラの枠の割り当ては保存と読込みと突き合わせで残る() {
        val a = Assignments.EMPTY.withKey(KeyLayout.DOCK_CAMERA_ID, gmail)
        val decoded = com.foldberry.launcher.data.AssignmentsCodec.decode(com.foldberry.launcher.data.AssignmentsCodec.encode(a))
        assertEquals(gmail, (decoded as com.foldberry.launcher.data.AssignmentsCodec.Decoded.Ok).value.keys[KeyLayout.DOCK_CAMERA_ID])
        val reconciled = com.foldberry.launcher.data.reconcile(a, listOf(gmail, maps))
        assertEquals(gmail, reconciled!!.assignments.keys[KeyLayout.DOCK_CAMERA_ID])
    }

    @Test
    fun メインの自由枠はM番号でカメラの枠の後タイルの前() {
        // 自由枠は 8 個(2026-11-18 に 12→8)。最後の枠は M8。
        val slot = Assignments.EMPTY.withKey(KeyLayout.mainSlotId(KeyLayout.MAIN_SLOT_COUNT - 1), gmail)
        assertEquals("M8", AssignmentBadge.of(slot, gmail))
        assertEquals("CAM+", AssignmentBadge.of(slot.withKey(KeyLayout.DOCK_CAMERA_ID, gmail), gmail))
    }

    @Test
    fun メインの自由枠の割り当ては保存と読込みと突き合わせで残る() {
        val id = KeyLayout.mainSlotId(0)
        assertEquals("MAIN_SLOT_1", id)
        val a = Assignments.EMPTY.withKey(id, gmail)
        val decoded = com.foldberry.launcher.data.AssignmentsCodec.decode(com.foldberry.launcher.data.AssignmentsCodec.encode(a))
        assertEquals(gmail, (decoded as com.foldberry.launcher.data.AssignmentsCodec.Decoded.Ok).value.keys[id])
        val reconciled = com.foldberry.launcher.data.reconcile(a, listOf(gmail, maps))
        assertEquals(gmail, reconciled!!.assignments.keys[id])
    }

    @Test
    fun 全ての割り当て可能キーに空でない短い表記がある() {
        KeyLayout.assignableKeys.forEach { assert(AssignmentBadge.shortLabel(it.id).isNotEmpty()) { it.id } }
    }

    @Test
    fun ショートカットの割り当てはアプリのバッジに数えない() {
        val compose = gmail.copy(shortcut = "compose", shortcutLabel = "新規作成")
        val a = Assignments.EMPTY.withKey("W", compose)
        assertNull(AssignmentBadge.of(a, gmail))
        assertEquals("W", AssignmentBadge.of(a, compose))
    }
}
