package com.foldberry.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {

    private fun ja(s: String) = VoiceCommands.parse(s, ClockLanguage.JA)
    private fun en(s: String) = VoiceCommands.parse(s, ClockLanguage.EN)

    @Test
    fun 日本語のアラームは時刻を読み日付語は読み捨てる() {
        assertEquals(VoiceCommand.Alarm(6, 0), ja("明日6時に起こして"))
        assertEquals(VoiceCommand.Alarm(6, 30), ja("6時半に起こして"))
        assertEquals(VoiceCommand.Alarm(6, 15), ja("6時15分にアラーム"))
        assertEquals(VoiceCommand.Alarm(7, 30), ja("7:30に目覚まし"))
        assertEquals(VoiceCommand.Alarm(6, 0), ja("六時に起こしてください"))
    }

    @Test
    fun 午後と夜は12時間を足し印が無ければそのまま() {
        assertEquals(VoiceCommand.Alarm(19, 0), ja("午後7時に起こして"))
        assertEquals(VoiceCommand.Alarm(21, 0), ja("夜9時にアラーム"))
        assertEquals(VoiceCommand.Alarm(7, 0), ja("7時に起こして"))
        assertEquals(VoiceCommand.Alarm(0, 0), ja("午前12時にアラーム"))
        assertEquals(VoiceCommand.Alarm(12, 0), ja("午後12時にアラーム"))
    }

    @Test
    fun 日本語のタイマーは時間分秒と半を足す() {
        assertEquals(VoiceCommand.Timer(300), ja("5分タイマー"))
        assertEquals(VoiceCommand.Timer(300), ja("タイマー5分"))
        assertEquals(VoiceCommand.Timer(1800), ja("三十分のタイマー"))
        assertEquals(VoiceCommand.Timer(5400), ja("1時間半のタイマー"))
        assertEquals(VoiceCommand.Timer(90), ja("1分30秒タイマー"))
        // 「時間」をアラームの「時」と取り違えない。
        assertEquals(VoiceCommand.Timer(3600), ja("1時間タイマー"))
    }

    @Test
    fun 日本語の電話は敬称を除き名前は漢数字のまま() {
        assertEquals(VoiceCommand.Call("田中"), ja("田中さんに電話"))
        assertEquals(VoiceCommand.Call("田中一郎"), ja("田中一郎に電話して"))
        assertEquals(VoiceCommand.Call("お母"), ja("お母さんに電話"))
    }

    @Test
    fun 日本語のアプリを開く() {
        assertEquals(VoiceCommand.OpenApp("マップ"), ja("マップを開いて"))
        assertEquals(VoiceCommand.OpenApp("youtube"), ja("YouTube を起動して"))
        assertEquals(VoiceCommand.OpenApp("カメラ"), ja("カメラ開いてください"))
        // ライト・アラームの語を含む名前でも「開いて」ならアプリ。
        assertEquals(VoiceCommand.OpenApp("ライトルーム"), ja("ライトルームを開いて"))
        assertEquals(VoiceCommand.OpenApp("アラーム"), ja("アラームを開いて"))
    }

    @Test
    fun 日本語のライトとマナー() {
        assertEquals(VoiceCommand.Light(true), ja("ライトをつけて"))
        assertEquals(VoiceCommand.Light(false), ja("ライトを消して"))
        assertEquals(VoiceCommand.Light(null), ja("ライト"))
        assertEquals(VoiceCommand.Manner(true), ja("マナーモードにして"))
        assertEquals(VoiceCommand.Manner(false), ja("マナーモードを解除して"))
        assertEquals(VoiceCommand.Manner(null), ja("マナーモード"))
    }

    @Test
    fun 分からない言い回し() {
        assertEquals(VoiceCommand.Unknown, ja("あしたのてんき"))
        assertEquals(VoiceCommand.Unknown, ja(""))
        assertEquals(VoiceCommand.Unknown, ja("25時に起こして"))
        assertEquals(VoiceCommand.Unknown, en("what is the weather"))
    }

    @Test
    fun 英語の言い回し() {
        assertEquals(VoiceCommand.Alarm(6, 0), en("Wake me up at 6"))
        assertEquals(VoiceCommand.Alarm(18, 30), en("Set an alarm for 6:30 pm"))
        assertEquals(VoiceCommand.Alarm(19, 0), en("wake me up at 7 tonight"))
        assertEquals(VoiceCommand.Timer(300), en("5 minute timer"))
        assertEquals(VoiceCommand.Timer(300), en("Set a timer for five minutes."))
        assertEquals(VoiceCommand.Timer(90), en("timer for 1 minute 30 seconds"))
        assertEquals(VoiceCommand.Call("alex"), en("Call Alex"))
        assertEquals(VoiceCommand.OpenApp("maps"), en("Open Maps"))
        assertEquals(VoiceCommand.OpenApp("lightroom"), en("open lightroom"))
        assertEquals(VoiceCommand.Light(true), en("Turn on the flashlight"))
        assertEquals(VoiceCommand.Light(false), en("turn the light off"))
        assertEquals(VoiceCommand.Manner(true), en("vibrate mode on"))
        assertEquals(VoiceCommand.Manner(false), en("turn off vibrate"))
    }

    @Test
    fun 漢数字() {
        assertEquals("15", VoiceCommands.kanjiToDigits("十五"))
        assertEquals("20", VoiceCommands.kanjiToDigits("二十"))
        assertEquals("120分", VoiceCommands.kanjiToDigits("百二十分"))
        assertEquals("6時", VoiceCommands.kanjiToDigits("六時"))
    }

    @Test
    fun アプリ名は完全一致から前方一致部分一致の順で同じ順位は短い名前() {
        val labels = listOf("Google マップ", "マップ", "マップアプリ", "カメラ", "YouTube Music", "YouTube")
        assertEquals(1, VoiceCommands.matchApp("マップ", labels))
        assertEquals(1, VoiceCommands.matchApp("まっぷ", labels))
        assertEquals(5, VoiceCommands.matchApp("youtube", labels))
        assertEquals(4, VoiceCommands.matchApp("youtube music", labels))
        assertEquals(0, VoiceCommands.matchApp("googleマップ", labels))
        assertNull(VoiceCommands.matchApp("電卓", labels))
        assertNull(VoiceCommands.matchApp("", labels))
    }

    @Test
    fun 連絡先は敬称と空白を除いて比べる() {
        assertEquals(3, VoiceCommands.contactScore("田中一郎さん", "田中 一郎"))
        assertEquals(2, VoiceCommands.contactScore("田中さん", "田中 一郎"))
        assertEquals(1, VoiceCommands.contactScore("一郎", "田中 一郎"))
        assertNull(VoiceCommands.contactScore("佐藤", "田中 一郎"))
        assertEquals(3, VoiceCommands.contactScore("alex", "Alex"))
    }

    @Test
    fun タイマーの長さの表示() {
        assertEquals("5分", VoiceCommands.durationText(300, ClockLanguage.JA))
        assertEquals("1時間30分", VoiceCommands.durationText(5400, ClockLanguage.JA))
        assertEquals("1分30秒", VoiceCommands.durationText(90, ClockLanguage.JA))
        assertEquals("1 h 30 min", VoiceCommands.durationText(5400, ClockLanguage.EN))
        assertEquals("30 sec", VoiceCommands.durationText(30, ClockLanguage.EN))
    }

    @Test
    fun 日本語でキーにアプリを割り当てる_キーが先でもアプリが先でも() {
        assertEquals(VoiceCommand.AssignKey('G', "gmail"), ja("G キーに Gmail を割り当てて"))
        assertEquals(VoiceCommand.AssignKey('G', "gmail"), ja("Gmail を G キーに割り当ててください"))
        assertEquals(VoiceCommand.AssignKey('L', "ライン"), ja("L キーにラインアプリを登録して"))
        assertEquals(VoiceCommand.AssignKey('C', "カメラ"), ja("Ｃキーにカメラを入れて"))
    }

    @Test
    fun 日本語のキーは読み仮名でもひらがなでも読む() {
        assertEquals(VoiceCommand.AssignKey('G', "gmail"), ja("ジーキーにGmailを割り当てて"))
        assertEquals(VoiceCommand.AssignKey('H', "chrome"), ja("エイチキーにChromeを割り当てて"))
        assertEquals(VoiceCommand.AssignKey('W', "メール"), ja("ダブリューキーにメールを割り当てて"))
        assertEquals(VoiceCommand.ClearKey('G'), ja("じーきーを解除して"))
        assertEquals(VoiceCommand.ClearKey('A'), ja("エーキーの割り当てを解除"))
    }

    @Test
    fun 日本語の解除と中身を聞く() {
        assertEquals(VoiceCommand.ClearKey('G'), ja("G キーの割り当てを解除"))
        assertEquals(VoiceCommand.ClearKey('G'), ja("G キーを外して"))
        assertEquals(VoiceCommand.AskKey('G'), ja("G キーは何？"))
        assertEquals(VoiceCommand.AskKey('G'), ja("G キーの割り当ては何ですか"))
    }

    @Test
    fun 日本語のFoldBerryの設定とパネルの切替_既存の言い回しは変えない() {
        assertEquals(VoiceCommand.OpenSettings(assignments = false), ja("FoldBerry の設定を開いて"))
        assertEquals(VoiceCommand.OpenSettings(assignments = false), ja("フォールドベリーの設定を開いて"))
        assertEquals(VoiceCommand.OpenSettings(assignments = true), ja("割り当て一覧を開いて"))
        assertEquals(VoiceCommand.SwitchPanel(hub = true), ja("Hub にして"))
        assertEquals(VoiceCommand.SwitchPanel(hub = true), ja("ハブに切り替えて"))
        assertEquals(VoiceCommand.SwitchPanel(hub = false), ja("カレンダーにして"))
        assertEquals(VoiceCommand.SwitchPanel(hub = null), ja("表示を切り替えて"))
        // ただの「設定」「カレンダー」は今どおり端末のアプリを開く。
        assertEquals(VoiceCommand.OpenApp("設定"), ja("設定を開いて"))
        assertEquals(VoiceCommand.OpenApp("カレンダー"), ja("カレンダーを開いて"))
        assertEquals(VoiceCommand.OpenApp("天気"), ja("天気を開いて"))
        // 「〜キーに」は電話にならない。
        assertEquals(VoiceCommand.Call("田中"), ja("田中さんに電話して"))
    }

    @Test
    fun 英語でキーにアプリを割り当てる_解除_聞く() {
        assertEquals(VoiceCommand.AssignKey('G', "gmail"), en("Assign Gmail to the G key"))
        assertEquals(VoiceCommand.AssignKey('G', "gmail"), en("put Gmail on key G"))
        assertEquals(VoiceCommand.AssignKey('A', "chrome"), en("assign the A key to Chrome"))
        assertEquals(VoiceCommand.AssignKey('C', "camera"), en("assign camera app to the see key"))
        assertEquals(VoiceCommand.AssignKey('W', "maps"), en("assign maps to the double u key"))
        assertEquals(VoiceCommand.ClearKey('G'), en("clear the G key"))
        assertEquals(VoiceCommand.ClearKey('Y'), en("unassign the why key"))
        assertEquals(VoiceCommand.AskKey('G'), en("What's on the G key?"))
        assertEquals(VoiceCommand.AskKey('G'), en("what is assigned to key G"))
    }

    @Test
    fun 英語のFoldBerryの設定とパネルの切替_既存の言い回しは変えない() {
        assertEquals(VoiceCommand.OpenSettings(assignments = false), en("open FoldBerry settings"))
        assertEquals(VoiceCommand.OpenSettings(assignments = true), en("open the assignment list"))
        assertEquals(VoiceCommand.SwitchPanel(hub = true), en("switch to hub"))
        assertEquals(VoiceCommand.SwitchPanel(hub = false), en("switch to calendar"))
        assertEquals(VoiceCommand.SwitchPanel(hub = null), en("switch panel"))
        assertEquals(VoiceCommand.OpenApp("settings"), en("open settings"))
        assertEquals(VoiceCommand.OpenApp("calendar"), en("open calendar"))
        assertEquals(VoiceCommand.Timer(300), en("set a timer for 5 minutes"))
    }
}
