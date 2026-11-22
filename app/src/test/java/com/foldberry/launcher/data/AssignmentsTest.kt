package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentsTest {

    private val mail = AppRef("com.example.mail", "com.example.mail.Main")
    private val camera = AppRef("com.example.camera", "com.example.camera.Main")

    @Test
    fun JSONに書いて読み戻すと同じになる() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("SPACE", camera)
        val decoded = AssignmentsCodec.decode(AssignmentsCodec.encode(a))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), decoded)
    }

    @Test
    fun JSONは人が読める形でパッケージ名とバージョンを含む() {
        val text = AssignmentsCodec.encode(Assignments.EMPTY.withKey("W", mail))
        assertTrue(text.contains("\"version\": 1"))
        assertTrue(text.contains("\"package\": \"com.example.mail\""))
        assertTrue(text.contains("\n")) // 改行入り(手で読める)
    }

    @Test
    fun 壊れたJSONはInvalid() {
        assertTrue(AssignmentsCodec.decode("{ not json") is AssignmentsCodec.Decoded.Invalid)
        assertTrue(AssignmentsCodec.decode("") is AssignmentsCodec.Decoded.Invalid)
    }

    @Test
    fun 未対応のバージョンはInvalid() {
        val decoded = AssignmentsCodec.decode("""{"version":2,"keys":{},"tiles":[]}""")
        assertTrue(decoded is AssignmentsCodec.Decoded.Invalid)
    }

    @Test
    fun 旧ファイルのタイルの割り当ては読み捨ててほかは読める() {
        // 2026-11-19 にタイルの割り当てを廃止(T1・T2 は音楽と次の予定に固定。D26)。保存・書出しの "tiles" は読み捨てる。
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"tiles":[{"package":"q","activity":"b"},null]}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(mapOf("W" to AppRef("p", "a")), ok.value.keys)
        assertFalse(AssignmentsCodec.encode(ok.value).contains("tiles"))
    }

    @Test
    fun 書き直しは廃止した枠が残る古いファイルのときだけ() {
        assertTrue(AssignmentsCodec.hasRetiredEntries("""{"version":1,"tiles":[null,null,null,null]}"""))
        // タイルの割り当て自体を廃止したので、2 枚の "tiles" も書き直しの対象(D26)。
        assertTrue(AssignmentsCodec.hasRetiredEntries("""{"version":1,"tiles":[null,null]}"""))
        assertFalse(AssignmentsCodec.hasRetiredEntries("""{"version":1}"""))
        assertFalse(AssignmentsCodec.hasRetiredEntries("壊れたファイル"))
        // 書き直した後のファイルはもう対象にならない(毎回書き直さない)。
        val old = AssignmentsCodec.decode("""{"version":1,"tiles":[null,null,null,null]}""") as AssignmentsCodec.Decoded.Ok
        assertFalse(AssignmentsCodec.hasRetiredEntries(AssignmentsCodec.encode(old.value)))
    }

    @Test
    fun 左Shiftの割り当ては読込みで捨て_Shift面の割り当ては残す() {
        // 2026-11-22 に左 Shift を Shift 面の切替専用にした(D28)。
        val text = """{"version":1,"keys":{"LEFT_SHIFT":{"package":"p","activity":"a"},""" +
            """"SHIFT_Q":{"package":"q","activity":"b"},"RIGHT_SHIFT":{"package":"r","activity":"c"}}}"""
        assertTrue(AssignmentsCodec.hasRetiredEntries(text))
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(mapOf("SHIFT_Q" to AppRef("q", "b"), "RIGHT_SHIFT" to AppRef("r", "c")), decoded.value.keys)
        assertFalse(AssignmentsCodec.hasRetiredEntries(AssignmentsCodec.encode(decoded.value)))
    }

    @Test
    fun 廃止した自由枠M9からM12は読込みで捨てる() {
        val text = """{"version":1,"keys":{"MAIN_SLOT_8":{"package":"p","activity":"a"},""" +
            """"MAIN_SLOT_9":{"package":"q","activity":"b"},"MAIN_SLOT_12":{"package":"r","activity":"c"}}}"""
        assertTrue(AssignmentsCodec.hasRetiredEntries(text))
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(mapOf("MAIN_SLOT_8" to AppRef("p", "a")), decoded.value.keys)
        // 書き直した後(M8 だけ)はもう対象にならない。
        assertFalse(AssignmentsCodec.hasRetiredEntries(AssignmentsCodec.encode(decoded.value)))
    }

    @Test
    fun 知らない項目があっても読める() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"keys":{},"theme":"x"}""")
        assertTrue(decoded is AssignmentsCodec.Decoded.Ok)
    }

    @Test
    fun 背景の項目だけ壊れていても割り当ては読める() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"background":"x"}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertEquals(BackgroundSetting(), ok.value.background)
        val badType = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"background":{"overlay":"濃い"}}"""
        assertEquals(AppRef("p", "a"), (AssignmentsCodec.decode(badType) as AssignmentsCodec.Decoded.Ok).value.keys["W"])
    }

    @Test
    fun 背景以外が壊れていればInvalid() {
        val text = """{"version":1,"keys":"broken","background":{"preset":"berry"}}"""
        assertTrue(AssignmentsCodec.decode(text) is AssignmentsCodec.Decoded.Invalid)
    }

    @Test
    fun 背景設定も書いて読み戻せる() {
        val a = Assignments.EMPTY.copy(background = BackgroundSetting(kind = BackgroundSetting.KIND_PHOTO, preset = "berry", overlay = 35))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
    }

    @Test
    fun 背景設定の無いv03のファイルは既定の背景で読める() {
        val old = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"tiles":[null,null,null,null]}"""
        val ok = AssignmentsCodec.decode(old) as AssignmentsCodec.Decoded.Ok
        assertEquals(BackgroundSetting(), ok.value.background)
        assertEquals("aurora", ok.value.background.preset)
        assertEquals(20, ok.value.background.overlay)
    }

    @Test
    fun 未知のプリセット名や範囲外の値は既定に丸める() {
        val text = """{"version":1,"background":{"kind":"video","preset":"sunset","overlay":150}}"""
        val bg = (AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok).value.background
        assertEquals(BackgroundSetting(kind = "preset", preset = "aurora", overlay = 70), bg)
        assertEquals(BackgroundPreset.AURORA, BackgroundPreset.resolve("sunset"))
        assertEquals(BackgroundPreset.GRAPHITE, BackgroundPreset.resolve("graphite"))
    }

    @Test
    fun keysが無くてもversionがあれば読める() {
        val decoded = AssignmentsCodec.decode("""{"version":1}""")
        assertEquals(AssignmentsCodec.Decoded.Ok(Assignments.EMPTY), decoded)
    }

    @Test
    fun 割り当て解除でキーが消える() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("W", null)
        assertTrue(a.keys.isEmpty())
    }

    @Test
    fun 同じアプリを複数のキーに割り当てられる() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("E", mail)
        assertEquals(mail, a.keys["W"])
        assertEquals(mail, a.keys["E"])
    }

    @Test
    fun 突き合わせ_アンインストールされたアプリは未割り当てに戻る() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("E", camera)
        val r = reconcile(a, installed = listOf(mail))!!
        assertEquals(mapOf("W" to mail), r.assignments.keys)
        assertEquals(1, r.removed)
        assertEquals(0, r.remapped)
    }

    @Test
    fun 突き合わせ_起動画面名が変わったら同じパッケージの起動画面に付け替える() {
        val renamed = AppRef(mail.packageName, "com.example.mail.NewMain")
        val r = reconcile(Assignments.EMPTY.withKey("W", mail), installed = listOf(renamed))!!
        assertEquals(renamed, r.assignments.keys["W"])
        assertEquals(1, r.remapped)
        assertEquals(0, r.removed)
    }

    @Test
    fun 突き合わせ_一覧が空なら何もしない() {
        assertNull(reconcile(Assignments.EMPTY.withKey("W", mail), installed = emptyList()))
    }

    @Test
    fun 突き合わせ_全部そろっていれば変化なし() {
        val a = Assignments.EMPTY.withKey("W", mail)
        val r = reconcile(a, listOf(mail, camera))!!
        assertEquals(a, r.assignments)
        assertEquals(0, r.removed + r.remapped)
    }

    @Test
    fun 読込み_書出したファイルをそのまま読める() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("E", camera)
        val r = importSettings(AssignmentsCodec.encode(a), installed = listOf(mail, camera))
        assertEquals(ImportResult.Ok(a, missing = 0, remapped = 0), r)
    }

    @Test
    fun 読込み_端末に無いアプリは未割り当てにして件数を返す() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("E", camera)
        val r = importSettings(AssignmentsCodec.encode(a), installed = listOf(mail)) as ImportResult.Ok
        assertEquals(mapOf("W" to mail), r.assignments.keys)
        assertEquals(1, r.missing)
        assertEquals(1, r.assignments.assignedCount())
    }

    @Test
    fun 読込み_アプリ一覧が無ければそのまま読み件数は不明() {
        val a = Assignments.EMPTY.withKey("W", mail)
        val r = importSettings(AssignmentsCodec.encode(a), installed = null) as ImportResult.Ok
        assertEquals(a, r.assignments)
        assertNull(r.missing)
    }

    @Test
    fun 読込み_壊れたファイルや他のJSONはInvalid() {
        assertTrue(importSettings("not json", listOf(mail)) is ImportResult.Invalid)
        assertTrue(importSettings("""{"version":9}""", listOf(mail)) is ImportResult.Invalid)
        assertTrue(importSettings("[1,2,3]", listOf(mail)) is ImportResult.Invalid)
    }

    @Test
    fun 読込み_versionの無い他アプリのJSONはInvalidで割り当てを消さない() {
        // version は既定値を持つため、確かめないと空の設定として読めてしまい、置き換えで全部消える。
        assertTrue(importSettings("{}", listOf(mail)) is ImportResult.Invalid)
        assertTrue(importSettings("""{"name":"foo","keys":{}}""", listOf(mail)) is ImportResult.Invalid)
        assertTrue(AssignmentsCodec.decode("""{"theme":"dark"}""") is AssignmentsCodec.Decoded.Invalid)
    }

    @Test
    fun 読込み_BOM付きのファイルも読める() {
        val a = Assignments.EMPTY.withKey("W", mail)
        val r = importSettings(Char(0xFEFF).toString() + AssignmentsCodec.encode(a), listOf(mail))
        assertEquals(ImportResult.Ok(a, missing = 0, remapped = 0), r)
    }

    @Test
    fun 名前の部分一致で大文字小文字を区別せず絞り込む() {
        val names = listOf("Gmail", "カメラ", "Google マップ", "Chrome")
        assertEquals(listOf("Gmail", "Google マップ"), filterByName(names, " g ") { it })
        assertEquals(listOf("カメラ"), filterByName(names, "カメ") { it })
        assertEquals(names, filterByName(names, "  ") { it })
    }

    @Test
    fun 名前順は五十音とアルファベット順() {
        val sorted = sortByName(listOf("ブラウザ", "カメラ", "アラーム")) { it }
        assertEquals(listOf("アラーム", "カメラ", "ブラウザ"), sorted)
    }

    @Test
    fun 名前の群は先頭の文字で決まる() {
        assertEquals(NameGroup.LATIN, nameGroup("Chrome"))
        assertEquals(NameGroup.LATIN, nameGroup(" gmail"))
        assertEquals(NameGroup.LATIN, nameGroup("Ｘ"))
        assertEquals(NameGroup.OTHER, nameGroup("1Password"))
        assertEquals(NameGroup.OTHER, nameGroup("#tag"))
        assertEquals(NameGroup.OTHER, nameGroup(""))
        assertEquals(NameGroup.OTHER, nameGroup("   "))
        assertEquals(NameGroup.JAPANESE, nameGroup("あいう"))
        assertEquals(NameGroup.JAPANESE, nameGroup("カメラ"))
        assertEquals(NameGroup.JAPANESE, nameGroup("ｶﾒﾗ"))
        assertEquals(NameGroup.JAPANESE, nameGroup("電卓"))
    }

    @Test
    fun 名前順は英字_数字記号_日本語の順() {
        val sorted = sortByName(listOf("カメラ", "1Password", "chrome", "電卓", "Gmail", "あいう")) { it }
        assertEquals(listOf("chrome", "Gmail", "1Password", "あいう", "カメラ", "電卓"), sorted)
    }

    @Test
    fun 時計の設定も書いて読み戻せる() {
        val a = Assignments.EMPTY.copy(clock = ClockSetting(dateStyle = DateStyle.NUMERIC.id))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
    }

    @Test
    fun 時計の設定の無いv050のファイルは既定の書式で読める() {
        val old = """{"version":1,"keys":{},"background":{"preset":"berry"}}"""
        val ok = AssignmentsCodec.decode(old) as AssignmentsCodec.Decoded.Ok
        assertEquals(DateStyle.STANDARD, ok.value.clock.style)
        assertEquals("berry", ok.value.background.preset)
    }

    @Test
    fun 未知の書式名は標準に丸める() {
        val text = """{"version":1,"clock":{"dateStyle":"fancy"}}"""
        assertEquals(DateStyle.STANDARD.id, (AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok).value.clock.dateStyle)
    }

    @Test
    fun 時計の項目だけ壊れていても割り当てと背景は読める() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"background":{"preset":"berry"},"clock":"x"}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertEquals("berry", ok.value.background.preset)
        assertEquals(ClockSetting(), ok.value.clock)
    }

    @Test
    fun 見た目の設定も書いて読み戻せ無いファイルは標準で読む() {
        val a = Assignments.EMPTY.copy(appearance = AppearanceSetting(grayscaleIcons = false))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
        val old = """{"version":1,"keys":{}}"""
        assertEquals(IconStyle.STANDARD, (AssignmentsCodec.decode(old) as AssignmentsCodec.Decoded.Ok).value.appearance.style)
    }

    @Test
    fun 見た目の項目だけ壊れていても割り当ては読める() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"appearance":{"grayscaleIcons":"x"}}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertEquals(AppearanceSetting(), ok.value.appearance)
    }

    @Test
    fun 任意項目が2つ壊れていても正常な残りの項目は生かす() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},""" +
            """"background":{"kind":"preset","preset":"berry","overlay":20},"clock":3,"appearance":"x"}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertEquals("berry", ok.value.background.preset)
        assertEquals(ClockSetting(), ok.value.clock)
        assertEquals(AppearanceSetting(), ok.value.appearance)
    }

    @Test
    fun 背景と時計の両方が壊れていても割り当ては読める() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"background":"x","clock":3}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertEquals(BackgroundSetting(), ok.value.background)
        assertEquals(ClockSetting(), ok.value.clock)
    }

    @Test
    fun 触覚と12_24の項目が無いファイルは既定で読む() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"clock":{"dateStyle":"short"}}""") as AssignmentsCodec.Decoded.Ok
        assertTrue(decoded.value.feedback.haptics)
        assertEquals(HourFormat.SYSTEM, decoded.value.clock.hours)
        assertEquals(DateStyle.SHORT, decoded.value.clock.style)
    }

    @Test
    fun 触覚と12_24を書いて読み戻すと同じになる() {
        val a = Assignments.EMPTY.withKey("W", mail)
            .copy(clock = ClockSetting(hourFormat = HourFormat.H24.id), feedback = FeedbackSetting(haptics = false))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
    }

    @Test
    fun 未知の12_24表記は端末に従うに丸める() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"clock":{"hourFormat":"13"}}""") as AssignmentsCodec.Decoded.Ok
        assertEquals(HourFormat.SYSTEM.id, decoded.value.clock.hourFormat)
    }

    @Test
    fun Hubの項目が無いv06のファイルはカレンダーと差出人と本文で読む() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"keys":{"W":{"package":"p","activity":"a"}}}""") as AssignmentsCodec.Decoded.Ok
        assertEquals(MainPanelKind.CALENDAR, decoded.value.hub.panel)
        assertEquals(HubPreview.FULL, decoded.value.hub.previewMode)
    }

    @Test
    fun Hubの設定を書いて読み戻すと同じになる() {
        val a = Assignments.EMPTY.withKey("W", mail)
            .copy(hub = HubSetting(mainPanel = MainPanelKind.HUB.id, preview = HubPreview.APP.id))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
    }

    @Test
    fun カバーの枠はメインと別に覚え無いファイルはカレンダー() {
        val old = AssignmentsCodec.decode("""{"version":1,"hub":{"mainPanel":"hub"}}""") as AssignmentsCodec.Decoded.Ok
        assertEquals(MainPanelKind.HUB, old.value.hub.panel)
        assertEquals(MainPanelKind.CALENDAR, old.value.hub.cover)
        val a = Assignments.EMPTY.copy(hub = HubSetting(coverPanel = MainPanelKind.HUB.id))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
        val odd = AssignmentsCodec.decode("""{"version":1,"hub":{"coverPanel":"clock"}}""") as AssignmentsCodec.Decoded.Ok
        assertEquals(MainPanelKind.CALENDAR.id, odd.value.hub.coverPanel)
    }

    @Test
    fun カレンダーとHubの入れ替え() {
        assertEquals(MainPanelKind.HUB, MainPanelKind.CALENDAR.flipped())
        assertEquals(MainPanelKind.CALENDAR, MainPanelKind.HUB.flipped())
    }

    @Test
    fun Hubの未知の値は既定に丸める() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"hub":{"mainPanel":"widget","preview":"secret"}}""") as AssignmentsCodec.Decoded.Ok
        assertEquals(HubSetting(), decoded.value.hub)
    }

    @Test
    fun Hubの項目だけ壊れていても割り当てと時計は残る() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"clock":{"hourFormat":"12"},"hub":{"mainPanel":["hub"]}}"""
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), decoded.value.keys["W"])
        assertEquals(HourFormat.H12, decoded.value.clock.hours)
        assertEquals(HubSetting(), decoded.value.hub)
    }

    @Test
    fun すべて解除してもHubの設定は残す() {
        val a = Assignments.EMPTY.withKey("W", mail).copy(hub = HubSetting(mainPanel = MainPanelKind.HUB.id))
        assertEquals(MainPanelKind.HUB, a.clearedAssignments().hub.panel)
    }

    @Test
    fun 触覚の項目だけ壊れていても割り当てと時計は残る() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"clock":{"hourFormat":"12"},"feedback":{"haptics":"yes?"}}"""
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), decoded.value.keys["W"])
        assertEquals(HourFormat.H12, decoded.value.clock.hours)
        assertTrue(decoded.value.feedback.haptics)
    }

    @Test
    fun すべて解除は割り当てだけを外して設定を残す() {
        val a = Assignments.EMPTY.withKey("W", mail).withKey("MAIN_SLOT_3", camera)
            .copy(
                background = BackgroundSetting(preset = BackgroundPreset.BERRY.id, overlay = 40),
                clock = ClockSetting(dateStyle = DateStyle.NUMERIC.id, hourFormat = HourFormat.H12.id),
                appearance = AppearanceSetting(grayscaleIcons = true),
                feedback = FeedbackSetting(haptics = false),
            )
        val cleared = a.clearedAssignments()
        assertEquals(0, cleared.assignedCount())
        assertEquals(a.copy(keys = emptyMap()), cleared)
    }

    @Test
    fun 設定の初期化は割り当てを残して設定を既定に戻す() {
        val folder = AppFolder("f", listOf(mail, camera))
        val a = Assignments.EMPTY.withKey("W", mail).withFolder("MAIN_SLOT_2", folder)
            .copy(
                background = BackgroundSetting(preset = BackgroundPreset.BERRY.id, overlay = 40),
                clock = ClockSetting(dateStyle = DateStyle.NUMERIC.id, hourFormat = HourFormat.H12.id),
                appearance = AppearanceSetting().withStyle(IconStyle.PICTOGRAM).withPictogram("p", Pictogram.MAIL),
                feedback = FeedbackSetting(haptics = false),
                hub = HubSetting(mainPanel = MainPanelKind.HUB.id),
            )
        val reset = a.resetSettings()
        assertEquals(Assignments.EMPTY.copy(keys = a.keys, folders = a.folders), reset)
        assertEquals(IconStyle.STANDARD, reset.appearance.style)
    }

    @Test
    fun 時計の12_24表記の判定() {
        assertTrue(HourFormat.SYSTEM.is24(true))
        assertFalse(HourFormat.SYSTEM.is24(false))
        assertFalse(HourFormat.H12.is24(true))
        assertTrue(HourFormat.H24.is24(false))
    }

    // ── 自由枠のフォルダ(v0.7.1) ──

    private val chat = AppRef("com.example.chat", "com.example.chat.Main")
    private val maps = AppRef("com.example.maps", "com.example.maps.Main")
    private val music = AppRef("com.example.music", "com.example.music.Main")

    @Test
    fun フォルダを書いて読み戻すと同じになる() {
        val a = Assignments.EMPTY.withKey("W", mail).withFolder("MAIN_SLOT_2", AppFolder("仕事", listOf(mail, chat)))
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
    }

    @Test
    fun フォルダの無いv07のファイルはフォルダなしで読む() {
        val decoded = AssignmentsCodec.decode("""{"version":1,"keys":{"W":{"package":"p","activity":"a"}}}""") as AssignmentsCodec.Decoded.Ok
        assertTrue(decoded.value.folders.isEmpty())
    }

    @Test
    fun 枠はアプリかフォルダのどちらか一方() {
        val withFolder = Assignments.EMPTY.withKey("MAIN_SLOT_1", mail).withFolder("MAIN_SLOT_1", AppFolder("", listOf(chat)))
        assertNull(withFolder.keys["MAIN_SLOT_1"])
        assertEquals(listOf(chat), withFolder.folders["MAIN_SLOT_1"]?.apps)
        val withApp = withFolder.withKey("MAIN_SLOT_1", camera)
        assertEquals(camera, withApp.keys["MAIN_SLOT_1"])
        assertTrue(withApp.folders.isEmpty())
        // 解除(null)ではフォルダは消さない(アプリが入っていない枠の解除は何もしない)。
        assertEquals(withFolder, withFolder.withKey("MAIN_SLOT_1", null))
    }

    @Test
    fun フォルダは重複を除き4個までで空なら外れ自由枠以外には置けない() {
        val five = AppFolder("x", listOf(mail, chat, mail, camera, maps, music))
        assertEquals(listOf(mail, chat, camera, maps), Assignments.EMPTY.withFolder("MAIN_SLOT_1", five).folders["MAIN_SLOT_1"]?.apps)
        val a = Assignments.EMPTY.withFolder("MAIN_SLOT_1", AppFolder("", listOf(mail)))
        assertTrue(a.withFolder("MAIN_SLOT_1", AppFolder("", emptyList())).folders.isEmpty())
        assertTrue(a.withFolder("MAIN_SLOT_1", null).folders.isEmpty())
        assertEquals(Assignments.EMPTY, Assignments.EMPTY.withFolder("W", AppFolder("", listOf(mail))))
        assertEquals(Assignments.EMPTY, Assignments.EMPTY.withFolder("MAIN_SLOT_9", AppFolder("", listOf(mail))))
    }

    @Test
    fun 選択待ちからフォルダに足す() {
        val a = Assignments.EMPTY.withFolder("MAIN_SLOT_1", AppFolder("", listOf(mail, chat, camera)))
        val (added, r1) = a.addToFolder("MAIN_SLOT_1", maps)
        assertEquals(FolderAdd.ADDED, r1)
        assertEquals(listOf(mail, chat, camera, maps), added.folders["MAIN_SLOT_1"]?.apps)
        assertEquals(added to FolderAdd.FULL, added.addToFolder("MAIN_SLOT_1", music))
        assertEquals(a to FolderAdd.ALREADY, a.addToFolder("MAIN_SLOT_1", chat))
    }

    @Test
    fun 読込みでフォルダを正規化する() {
        val text = """{"version":1,"keys":{"MAIN_SLOT_1":{"package":"k","activity":"a"}},"folders":{""" +
            """"MAIN_SLOT_1":{"name":"重なり","apps":[{"package":"p","activity":"a"}]},""" +
            """"MAIN_SLOT_2":{"name":"多い","apps":[{"package":"a","activity":"a"},{"package":"a","activity":"a"},{"package":"b","activity":"a"},""" +
            """{"package":"c","activity":"a"},{"package":"d","activity":"a"},{"package":"e","activity":"a"}]},""" +
            """"MAIN_SLOT_3":{"name":"空","apps":[]},"MAIN_SLOT_10":{"apps":[{"package":"p","activity":"a"}]},"W":{"apps":[{"package":"p","activity":"a"}]}}}"""
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("k", "a"), decoded.value.keys["MAIN_SLOT_1"])
        assertEquals(setOf("MAIN_SLOT_2"), decoded.value.folders.keys)
        assertEquals(listOf("a", "b", "c", "d"), decoded.value.folders["MAIN_SLOT_2"]?.apps?.map { it.packageName })
        assertTrue(AssignmentsCodec.hasRetiredEntries(text))
    }

    @Test
    fun フォルダの項目だけ壊れていても割り当ては読める() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}},"folders":{"MAIN_SLOT_1":{"apps":"broken"}}}"""
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), decoded.value.keys["W"])
        assertTrue(decoded.value.folders.isEmpty())
    }

    @Test
    fun フォルダが1つ壊れていてもほかのフォルダは残る() {
        val text = """{"version":1,"folders":{"MAIN_SLOT_1":{"apps":[{"package":"a","activity":"x"}]},""" +
            """"MAIN_SLOT_2":{"apps":[{"package":"b"}]},"MAIN_SLOT_3":"broken"}}"""
        val decoded = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(setOf("MAIN_SLOT_1"), decoded.value.folders.keys)
        assertEquals(listOf(AppRef("a", "x")), decoded.value.folders["MAIN_SLOT_1"]?.apps)
    }

    @Test
    fun アンインストールでフォルダから外れ空なら消え起動画面の変更は付け替える() {
        val moved = AppRef(chat.packageName, "com.example.chat.New")
        val a = Assignments.EMPTY
            .withFolder("MAIN_SLOT_1", AppFolder("", listOf(mail, chat)))
            .withFolder("MAIN_SLOT_2", AppFolder("", listOf(maps)))
        val r = reconcile(a, listOf(mail, moved))!!
        assertEquals(listOf(mail, moved), r.assignments.folders["MAIN_SLOT_1"]?.apps)
        assertNull(r.assignments.folders["MAIN_SLOT_2"])
        assertEquals(1, r.removed)
        assertEquals(1, r.remapped)
    }

    @Test
    fun すべて解除でフォルダも消え件数にフォルダの中を数える() {
        val a = Assignments.EMPTY.withKey("W", mail).withFolder("MAIN_SLOT_1", AppFolder("", listOf(chat, maps)))
        assertEquals(3, a.assignedCount())
        assertTrue(a.clearedAssignments().folders.isEmpty())
    }

    // --- ショートカット(固定・動的。2026-11-20) ---

    private val gm = AppRef("com.google.android.gm", "Main")
    private val compose = gm.copy(shortcut = "compose", shortcutLabel = "新規作成")

    @Test
    fun ショートカットも書いて読み戻せアプリには項目を書かない() {
        val a = Assignments.EMPTY.withKey("W", compose).withKey("E", gm)
        assertEquals(AssignmentsCodec.Decoded.Ok(a), AssignmentsCodec.decode(AssignmentsCodec.encode(a)))
        val text = AssignmentsCodec.encode(Assignments.EMPTY.withKey("E", gm))
        assertFalse(text.contains("shortcut"))
    }

    @Test
    fun ショートカットの項目が無いファイルはアプリとして読む() {
        val text = """{"version":1,"keys":{"W":{"package":"p","activity":"a"}}}"""
        val ok = AssignmentsCodec.decode(text) as AssignmentsCodec.Decoded.Ok
        assertEquals(AppRef("p", "a"), ok.value.keys["W"])
        assertNull(ok.value.keys["W"]?.shortcut)
    }

    @Test
    fun ショートカットの等価は保存した名前を見ない() {
        val renamed = compose.copy(shortcutLabel = "Compose")
        assertEquals(compose, renamed)
        assertEquals(compose.hashCode(), renamed.hashCode())
        assertFalse(compose == gm)
        // 名前だけ違う同じショートカットをフォルダに足しても重複しない(一覧のキーがぶつかって落ちないように)。
        val a = Assignments.EMPTY.withFolder("MAIN_SLOT_1", AppFolder("", listOf(compose)))
        assertEquals(FolderAdd.ALREADY, a.addToFolder("MAIN_SLOT_1", renamed).second)
        assertEquals(1, a.withFolder("MAIN_SLOT_1", AppFolder("", listOf(compose, renamed))).folders["MAIN_SLOT_1"]?.apps?.size)
    }

    @Test
    fun ショートカットのappは持ち主のアプリ() {
        assertEquals(gm, compose.app)
        assertEquals(gm, gm.app)
    }

    @Test
    fun 表示名はアプリ名とショートカット名をつなぐ() {
        assertEquals("Gmail: 新規作成", displayLabel(compose, "Gmail"))
        assertEquals("新規作成", displayLabel(compose, null))
        assertEquals("Gmail", displayLabel(gm, "Gmail"))
        assertNull(displayLabel(gm, null))
    }

    @Test
    fun 突き合わせはショートカットを保つ() {
        val a = Assignments.EMPTY.withKey("W", compose)
        val same = reconcile(a, installed = listOf(gm))!!
        assertEquals(compose, same.assignments.keys["W"])
        assertEquals(0, same.remapped)
        // 起動画面名が変わっても、付け替えた先でショートカットは保つ。
        val renamed = reconcile(a, installed = listOf(AppRef(gm.packageName, "Main2")))!!
        assertEquals(compose.copy(activity = "Main2"), renamed.assignments.keys["W"])
        assertEquals(1, renamed.remapped)
        // パッケージごと消えたら外す。
        val gone = reconcile(a, installed = listOf(AppRef("other", "Main")))!!
        assertNull(gone.assignments.keys["W"])
        assertEquals(1, gone.removed)
    }
}
