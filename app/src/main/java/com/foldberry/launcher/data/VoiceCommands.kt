package com.foldberry.launcher.data

import java.text.Normalizer

/** 音声操作で聞き取った文字から決めた操作(仕様書 §5.5。D27)。 */
sealed interface VoiceCommand {
    /** 名前でアプリを探して開く。 */
    data class OpenApp(val query: String) : VoiceCommand

    /** アラーム(時計アプリ)。hour は 0〜23。日付は指定できない(次に来るその時刻に鳴る)。 */
    data class Alarm(val hour: Int, val minute: Int) : VoiceCommand

    /** タイマー(時計アプリ)。 */
    data class Timer(val seconds: Int) : VoiceCommand

    /** 連絡先の名前で電話(確認してから電話アプリに番号を入れる)。 */
    data class Call(val name: String) : VoiceCommand

    /** ライト。on=null は「つける」と同じに扱う(今の状態を読めないため。§5.5)。 */
    data class Light(val on: Boolean?) : VoiceCommand

    /** マナー(バイブ)。on=null は切り替え。 */
    data class Manner(val on: Boolean?) : VoiceCommand

    /** 英字キー(A〜Z)にアプリを名前で割り当てる。別のアプリが入っていれば確認する(2026-11-20 オーナー決定)。 */
    data class AssignKey(val letter: Char, val app: String) : VoiceCommand

    /** 英字キーの割り当てを解除する。 */
    data class ClearKey(val letter: Char) : VoiceCommand

    /** 英字キーに何が割り当ててあるかを出す。 */
    data class AskKey(val letter: Char) : VoiceCommand

    /** FoldBerry の設定を開く。assignments=true は割り当て一覧のページ。 */
    data class OpenSettings(val assignments: Boolean) : VoiceCommand

    /** カレンダーと Hub の切替。hub=null は入れ替え。 */
    data class SwitchPanel(val hub: Boolean?) : VoiceCommand

    data object Unknown : VoiceCommand
}

/**
 * 音声操作の言い回しの規則(§5.5。D27)。AI は使わず、決まった言い回しだけを読む。Android 非依存(VoiceCommandsTest)。
 * 日本語・英語は画面の言語(ClockLanguage)で切り替える。
 */
object VoiceCommands {

    fun parse(text: String, language: ClockLanguage): VoiceCommand = when (language) {
        ClockLanguage.JA -> parseJa(text)
        ClockLanguage.EN -> parseEn(text)
    }

    // ── 日本語 ──

    // 「してください」は落とさない(「起こしてください」の「して」まで消えて「起こして」が読めなくなる)。
    private val JA_POLITE = Regex("(ください|下さい|お願いします|おねがいします|お願い|おねがい)$")
    private val JA_CALL = Regex("^(.+?)(?:さん|くん|君|ちゃん|様|さま)?(?:に|へ)(?:電話|でんわ|発信|コール)(?:して|する|かけて)?$")
    private val JA_OPEN = Regex("^(.+?)(?:を)?(?:開いて|ひらいて|開く|開けて|起動して|起動|立ち上げて)$")
    private val JA_TIME = Regex("(午前|午後|朝|夜|夕方)?(\\d{1,2})時(?!間)(?:(\\d{1,2})分|(半))?")
    private val JA_COLON = Regex("(午前|午後|朝|夜|夕方)?(\\d{1,2}):(\\d{2})")

    private fun parseJa(text: String): VoiceCommand {
        val base = normalize(text).replace(JA_POLITE, "")
        if (base.isEmpty()) return VoiceCommand.Unknown
        val n = kanjiToDigits(base)

        // 長さ・時刻が読めなければ次の規則へ(「アラームを開いて」はアプリを開く)。
        if ("タイマー" in n) jaDuration(n)?.let { return VoiceCommand.Timer(it) }
        if ("起こして" in n || "アラーム" in n || "目覚まし" in n) jaAlarm(n)?.let { return it }
        // FoldBerry の操作は電話(「〜に」)・アプリを開く(「〜を開いて」)より先に読む(§5.5。2026-11-20)。
        jaFoldBerry(base)?.let { return it }
        // 名前は漢数字を数字にする前の文字で取る(「一郎」が「1郎」にならないように)。
        JA_CALL.find(base)?.let { return VoiceCommand.Call(it.groupValues[1]) }
        // アプリを開くはライト・マナーより先(「ライトルームを開いて」をライトにしない)。
        JA_OPEN.find(base)?.let { return VoiceCommand.OpenApp(it.groupValues[1]) }
        if ("ライト" in n || "懐中電灯" in n) {
            return VoiceCommand.Light(
                when {
                    listOf("消し", "けし", "オフ", "消灯").any { it in n } -> false
                    listOf("つけ", "点け", "オン", "点灯").any { it in n } -> true
                    else -> null
                },
            )
        }
        if ("マナー" in n || "バイブ" in n) {
            return VoiceCommand.Manner(
                when {
                    listOf("解除", "オフ", "やめ", "戻し", "切って", "消して").any { it in n } -> false
                    listOf("にして", "オン", "入れ", "する").any { it in n } -> true
                    else -> null
                },
            )
        }
        return VoiceCommand.Unknown
    }

    /**
     * 英字キーの日本語の読み(カタカナ)。認識は「G キー」と英字で出すことが多いが、読み仮名で出たときにも拾う。
     * ひらがなで出たときはカタカナに直してから引く。
     */
    private val JA_LETTER_NAMES: Map<String, Char> = mapOf(
        "エー" to 'A', "エイ" to 'A', "ビー" to 'B', "シー" to 'C', "ディー" to 'D', "デー" to 'D', "イー" to 'E',
        "エフ" to 'F', "ジー" to 'G', "エイチ" to 'H', "エッチ" to 'H', "アイ" to 'I', "ジェー" to 'J', "ジェイ" to 'J',
        "ケー" to 'K', "ケイ" to 'K', "エル" to 'L', "エム" to 'M', "エヌ" to 'N', "オー" to 'O', "ピー" to 'P',
        "キュー" to 'Q', "アール" to 'R', "エス" to 'S', "ティー" to 'T', "テー" to 'T', "ユー" to 'U', "ブイ" to 'V',
        "ヴィー" to 'V', "ダブリュー" to 'W', "ダブル" to 'W', "エックス" to 'X', "ワイ" to 'Y', "ゼット" to 'Z',
        "ゼッド" to 'Z', "ズィー" to 'Z',
    )

    /** キーの名前の部分(英字 1 文字か読み仮名。ひらがなの読みも)。長い読みを先に試す(「エイチ」を「エイ」にしない)。 */
    private val JA_KEY: String = run {
        val names = JA_LETTER_NAMES.keys.flatMap { listOf(it, toHiragana(it)) }.distinct().sortedByDescending { it.length }
        "([a-z]|" + names.joinToString("|") + ")(?:キー|きー)"
    }
    private const val JA_ASSIGN_VERB = "(?:割り当てて|割当てて|割り当て|割当て|わりあてて|登録して|登録|入れて|いれて|設定して)"
    private val JA_ASSIGN_KEY_FIRST = Regex("^${JA_KEY}に(.+?)(?:アプリ)?を?$JA_ASSIGN_VERB$")
    private val JA_ASSIGN_APP_FIRST = Regex("^(.+?)(?:アプリ)?を${JA_KEY}に$JA_ASSIGN_VERB$")
    private val JA_CLEAR = Regex("^$JA_KEY(?:の割り当て|の割当て|の割当|の登録)?を?(?:解除|はずして|外して|消して|削除)(?:して|する)?$")
    private val JA_ASK = Regex("^$JA_KEY(?:の割り当て|の割当て|に割り当てたの|に入っているの|には|は|って)*(?:何|なに|なん|どれ)(?:ですか|だっけ|か|の)?$")
    private const val JA_SHOW = "(?:開いて|ひらいて|開く|見せて|出して)"
    private val JA_SETTINGS = Regex("^(?:foldberry|フォールドベリー|フォルドベリー|ふぉーるどべりー)の?設定を?$JA_SHOW$")
    private val JA_ASSIGN_LIST = Regex("^(?:キーの)?(?:割り当て|割当て|割当)(?:の)?(?:一覧|リスト)を?$JA_SHOW$")
    private const val JA_SWITCH = "(?:して|切り替えて|切りかえて|切替えて|変えて|かえて|替えて)"
    private val JA_TO_HUB = Regex("^(?:hub|ハブ|はぶ)に$JA_SWITCH$")
    private val JA_TO_CALENDAR = Regex("^カレンダーに$JA_SWITCH$")
    private val JA_FLIP = Regex("^(?:表示|画面|パネル|hubとカレンダー|カレンダーとhub)を?(?:切り替えて|切りかえて|入れ替えて|いれかえて)$")

    private fun jaLetter(token: String): Char? =
        if (token.length == 1 && token[0] in 'a'..'z') token[0].uppercaseChar() else JA_LETTER_NAMES[toKatakana(token)]

    private fun jaFoldBerry(base: String): VoiceCommand? {
        JA_ASSIGN_KEY_FIRST.find(base)?.let { m -> jaLetter(m.groupValues[1])?.let { return VoiceCommand.AssignKey(it, m.groupValues[2]) } }
        JA_ASSIGN_APP_FIRST.find(base)?.let { m -> jaLetter(m.groupValues[2])?.let { return VoiceCommand.AssignKey(it, m.groupValues[1]) } }
        JA_CLEAR.find(base)?.let { m -> jaLetter(m.groupValues[1])?.let { return VoiceCommand.ClearKey(it) } }
        JA_ASK.find(base)?.let { m -> jaLetter(m.groupValues[1])?.let { return VoiceCommand.AskKey(it) } }
        if (JA_SETTINGS.matches(base)) return VoiceCommand.OpenSettings(assignments = false)
        if (JA_ASSIGN_LIST.matches(base)) return VoiceCommand.OpenSettings(assignments = true)
        if (JA_TO_HUB.matches(base)) return VoiceCommand.SwitchPanel(hub = true)
        if (JA_TO_CALENDAR.matches(base)) return VoiceCommand.SwitchPanel(hub = false)
        if (JA_FLIP.matches(base)) return VoiceCommand.SwitchPanel(hub = null)
        return null
    }

    /** 「1時間30分」「5分」「30秒」「1時間半」の長さ(秒)。無ければ null。 */
    private fun jaDuration(n: String): Int? {
        val h = Regex("(\\d+)時間(半)?").find(n)
        val m = Regex("(\\d+)分(半)?").find(n)
        val s = Regex("(\\d+)秒").find(n)
        var total = 0
        h?.let { total += it.groupValues[1].toInt() * 3600 + (if (it.groupValues[2].isNotEmpty()) 1800 else 0) }
        m?.let { total += it.groupValues[1].toInt() * 60 + (if (it.groupValues[2].isNotEmpty()) 30 else 0) }
        s?.let { total += it.groupValues[1].toInt() }
        return total.takeIf { it > 0 }
    }

    private fun jaAlarm(n: String): VoiceCommand? {
        JA_TIME.find(n)?.let { m ->
            val minute = when {
                m.groupValues[4].isNotEmpty() -> 30
                m.groupValues[3].isNotEmpty() -> m.groupValues[3].toInt()
                else -> 0
            }
            return alarm(m.groupValues[2].toInt(), minute, pm = m.groupValues[1] in JA_PM, am = m.groupValues[1] == "午前")
        }
        JA_COLON.find(n)?.let { m ->
            return alarm(m.groupValues[2].toInt(), m.groupValues[3].toInt(), pm = m.groupValues[1] in JA_PM, am = m.groupValues[1] == "午前")
        }
        return null
    }

    private val JA_PM = setOf("午後", "夜", "夕方")

    // ── 英語 ──

    private val EN_CALL = Regex("^(?:please )?(?:call|phone|dial) (.+)$")
    private val EN_OPEN = Regex("^(?:please )?(?:open|launch|start|run) (?:the )?(.+?)(?: app)?$")
    private val EN_TIME = Regex("(?:at|for) (\\d{1,2})(?::(\\d{2}))? ?(am|pm|a\\.m\\.|p\\.m\\.)?")
    private val EN_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "sixty" to 60,
    )

    /** 英字の読みとして認識が出しうる語(同音語)。1 文字の英字はそのまま読む。 */
    private val EN_LETTER_NAMES: Map<String, Char> = mapOf(
        "ay" to 'A', "bee" to 'B', "be" to 'B', "see" to 'C', "sea" to 'C', "dee" to 'D', "ee" to 'E', "ef" to 'F',
        "gee" to 'G', "aitch" to 'H', "eye" to 'I', "jay" to 'J', "kay" to 'K', "el" to 'L', "em" to 'M', "en" to 'N',
        "oh" to 'O', "pee" to 'P', "queue" to 'Q', "cue" to 'Q', "are" to 'R', "ess" to 'S', "tea" to 'T', "tee" to 'T',
        "you" to 'U', "vee" to 'V', "double u" to 'W', "double you" to 'W', "ex" to 'X', "why" to 'Y', "zed" to 'Z', "zee" to 'Z',
    )
    private val EN_KEY: String = run {
        val names = (EN_LETTER_NAMES.keys.sortedByDescending { it.length } + "[a-z]").joinToString("|")
        // 「the G key」「key G」の両方。どちらの形で取れたかは 2 つのグループのどちらが空でないかで見る。
        "(?:the )?(?:($names) key|key ($names))"
    }
    private val EN_ASSIGN_APP_FIRST = Regex("^(?:please )?(?:assign|put|set|map|add) (.+?)(?: app)? (?:to|on|for) $EN_KEY$")
    private val EN_ASSIGN_KEY_FIRST = Regex("^(?:please )?(?:assign|set|map) $EN_KEY to (.+?)(?: app)?$")
    private val EN_CLEAR = Regex("^(?:please )?(?:clear|unassign|remove|reset) $EN_KEY(?: assignment)?$")
    private val EN_ASK = Regex("^what(?:['’]s| is)(?: assigned)?(?: on| to)? $EN_KEY$")
    private val EN_SETTINGS = Regex("^(?:please )?(?:open|show) (?:the )?(?:fold ?berry|fold berry) settings$")
    private val EN_ASSIGN_LIST = Regex("^(?:please )?(?:open|show) (?:the )?(?:key )?assignments?(?: list)?$")
    private val EN_TO_PANEL = Regex("^(?:please )?(?:switch|change) to (?:the )?(hub|calendar)(?: view)?$")
    private val EN_FLIP = Regex("^(?:please )?(?:switch|toggle|flip) (?:the )?(?:panel|view)$")

    private fun enLetter(m: MatchResult, first: Int): Char? {
        val token = m.groupValues[first].ifEmpty { m.groupValues[first + 1] }
        return if (token.length == 1 && token[0] in 'a'..'z') token[0].uppercaseChar() else EN_LETTER_NAMES[token]
    }

    private fun enFoldBerry(t: String): VoiceCommand? {
        EN_ASSIGN_APP_FIRST.find(t)?.let { m -> enLetter(m, 2)?.let { return VoiceCommand.AssignKey(it, m.groupValues[1]) } }
        EN_ASSIGN_KEY_FIRST.find(t)?.let { m -> enLetter(m, 1)?.let { return VoiceCommand.AssignKey(it, m.groupValues[3]) } }
        EN_CLEAR.find(t)?.let { m -> enLetter(m, 1)?.let { return VoiceCommand.ClearKey(it) } }
        EN_ASK.find(t)?.let { m -> enLetter(m, 1)?.let { return VoiceCommand.AskKey(it) } }
        if (EN_SETTINGS.matches(t)) return VoiceCommand.OpenSettings(assignments = false)
        if (EN_ASSIGN_LIST.matches(t)) return VoiceCommand.OpenSettings(assignments = true)
        EN_TO_PANEL.find(t)?.let { return VoiceCommand.SwitchPanel(hub = it.groupValues[1] == "hub") }
        if (EN_FLIP.matches(t)) return VoiceCommand.SwitchPanel(hub = null)
        return null
    }

    private fun parseEn(text: String): VoiceCommand {
        val t = normalize(text, keepSpaces = true).trimEnd('.', '!', '?').trim()
        if (t.isEmpty()) return VoiceCommand.Unknown
        val n = t.split(' ').joinToString(" ") { w -> EN_WORDS[w]?.toString() ?: w }

        if ("timer" in n) enDuration(n)?.let { return VoiceCommand.Timer(it) }
        if ("alarm" in n || "wake me" in n) enAlarm(n)?.let { return it }
        // キーの英字は数詞に直す前の t で読む(「a」が「1」になるため)。電話・アプリを開くより先(§5.5。2026-11-20)。
        enFoldBerry(t)?.let { return it }
        EN_CALL.find(t)?.let { return VoiceCommand.Call(it.groupValues[1]) }
        EN_OPEN.find(t)?.let { return VoiceCommand.OpenApp(it.groupValues[1]) }
        val words = n.split(' ')
        if ("flashlight" in words || "torch" in words || "light" in words) {
            return VoiceCommand.Light(
                when {
                    "off" in words -> false
                    "on" in words -> true
                    else -> null
                },
            )
        }
        if ("vibrate" in words || "vibration" in words || "silent" in words) {
            return VoiceCommand.Manner(
                when {
                    "off" in words || "disable" in words || "normal" in words -> false
                    "on" in words || "enable" in words -> true
                    else -> null
                },
            )
        }
        return VoiceCommand.Unknown
    }

    private fun enDuration(n: String): Int? {
        var total = 0
        Regex("(\\d+) ?(hours?|hrs?|h)\\b").find(n)?.let { total += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+) ?(minutes?|mins?|m)\\b").find(n)?.let { total += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+) ?(seconds?|secs?|s)\\b").find(n)?.let { total += it.groupValues[1].toInt() }
        return total.takeIf { it > 0 }
    }

    private fun enAlarm(n: String): VoiceCommand? {
        val m = EN_TIME.find(n) ?: return null
        val suffix = m.groupValues[3]
        val pm = suffix.startsWith("p") || listOf("evening", "tonight", "at night").any { it in n }
        val am = suffix.startsWith("a")
        return alarm(m.groupValues[1].toInt(), m.groupValues[2].ifEmpty { "0" }.toInt(), pm = pm, am = am)
    }

    // ── 共通 ──

    /** 時刻を 0〜23 時に直す。午後・PM は 1〜11 時に 12 を足し、午前・AM の 12 時は 0 時。印が無ければそのまま(「7時」は 7:00)。 */
    private fun alarm(hour: Int, minute: Int, pm: Boolean, am: Boolean): VoiceCommand? {
        val h = when {
            pm && hour in 1..11 -> hour + 12
            am && hour == 12 -> 0
            else -> hour
        }
        return if (h in 0..23 && minute in 0..59) VoiceCommand.Alarm(h, minute) else null
    }

    /** NFKC・小文字。日本語は空白と読点・句点を除き、英語は空白を 1 つにそろえる。 */
    private fun normalize(text: String, keepSpaces: Boolean = false): String {
        val s = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().trim()
        return if (keepSpaces) s.replace(Regex("[,、。]"), " ").replace(Regex("\\s+"), " ").trim()
        else s.replace(Regex("[\\s、。,.!?！？]"), "")
    }

    private val KANJI_DIGITS = mapOf('〇' to 0, '零' to 0, '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

    /** 漢数字(〇〜九百九十九)を数字にする。「十五」=15、「二十」=20、「三十分」=30分。 */
    internal fun kanjiToDigits(s: String): String = Regex("[〇零一二三四五六七八九十百]+").replace(s) { m ->
        var total = 0
        var digit = -1
        for (c in m.value) {
            when (c) {
                '百' -> { total += (if (digit < 0) 1 else digit) * 100; digit = -1 }
                '十' -> { total += (if (digit < 0) 1 else digit) * 10; digit = -1 }
                else -> digit = if (digit < 0) KANJI_DIGITS.getValue(c) else digit * 10 + KANJI_DIGITS.getValue(c)
            }
        }
        (total + maxOf(digit, 0)).toString()
    }

    private fun toKatakana(s: String): String = String(CharArray(s.length) { i -> s[i].let { c -> if (c in 'ぁ'..'ゖ') c + 0x60 else c } })

    private fun toHiragana(s: String): String = String(CharArray(s.length) { i -> s[i].let { c -> if (c in 'ァ'..'ヶ') c - 0x60 else c } })

    /** 比べるための形: NFKC・小文字・ひらがな→カタカナ・空白と記号を除く。 */
    fun matchKey(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(n.length)
        for (c in n) {
            when {
                c in 'ぁ'..'ゖ' -> sb.append(c + 0x60)
                c.isLetterOrDigit() || c == 'ー' -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * 名前で一番近いアプリ(labels の添字)。完全一致 > 前方一致 > 部分一致の順で、同じ順位なら短い名前。無ければ null。
     */
    fun matchApp(query: String, labels: List<String>): Int? {
        val q = matchKey(query)
        if (q.isEmpty()) return null
        return labels.indices
            .mapNotNull { i -> nameScore(q, matchKey(labels[i]))?.let { Triple(i, it, labels[i].length) } }
            .sortedWith(compareByDescending<Triple<Int, Int, Int>> { it.second }.thenBy { it.third })
            .firstOrNull()?.first
    }

    private val HONORIFICS = Regex("(さん|くん|君|ちゃん|様|さま|サン|クン|チャン)$")

    /** 連絡先と比べるための名前(敬称を落とした matchKey)。 */
    fun contactKey(name: String): String = matchKey(Normalizer.normalize(name, Normalizer.Form.NFKC).trim().replace(HONORIFICS, ""))

    /** 話した名前と連絡先の表示名の近さ。3=完全一致、2=前方一致、1=部分一致、null=違う。 */
    fun contactScore(spoken: String, displayName: String): Int? = nameScore(contactKey(spoken), contactKey(displayName))

    private fun nameScore(q: String, label: String): Int? = when {
        q.isEmpty() || label.isEmpty() -> null
        label == q -> 3
        label.startsWith(q) -> 2
        q in label -> 1
        else -> null
    }

    /** タイマーの長さの表示。日本語「1時間30分」「5分」「1分30秒」、英語「1 h 30 min」「5 min」「30 sec」。 */
    fun durationText(seconds: Int, language: ClockLanguage): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        val parts = buildList {
            if (h > 0) add(if (language == ClockLanguage.JA) "${h}時間" else "$h h")
            if (m > 0) add(if (language == ClockLanguage.JA) "${m}分" else "$m min")
            if (s > 0) add(if (language == ClockLanguage.JA) "${s}秒" else "$s sec")
        }
        return parts.joinToString(if (language == ClockLanguage.JA) "" else " ")
    }
}
