package com.foldberry.launcher.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.Collator
import java.util.Locale

/*
 * 割り当ての保存形式と、それを扱う純粋関数(仕様書 §7 / §9)。
 * Android API に依存させず、JVM の単体テストで検証する(AssignmentsTest)。
 * この JSON は M7 の書出しファイルの形式にもそのまま使う(人が読める形式+バージョン番号。§9)。
 */

/**
 * アプリの識別 = パッケージ名+起動画面名(§9)。
 * shortcut があればそのアプリのショートカット(固定=manifest・動的=dynamic。2026-11-20 追加)を指す。
 * shortcutLabel は割り当てた時点のショートカットの名前(既定のホームでないとショートカットを読めないので、名前を出すために持つ)。
 * どちらも無いファイルは null(アプリそのもの)で読み、null のときは書かない(古い版の読込みと見た目を変えないため)。
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AppRef(
    @SerialName("package") val packageName: String,
    val activity: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shortcut: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shortcutLabel: String? = null,
) {
    /** ショートカットを外したアプリそのものの参照。アプリ一覧との突き合わせ・名前の引き当てに使う。 */
    val app: AppRef get() = if (shortcut == null && shortcutLabel == null) this else AppRef(packageName, activity)

    // 等価は shortcutLabel を除く(表示用の控えで、言語の切替やアプリ側の改名で変わる)。含めると同じショートカットが
    // フォルダ・バッジで別物になり、重複した項目で一覧のキーがぶつかって落ちる(2026-11-20 反証で発見)。
    override fun equals(other: Any?): Boolean =
        other is AppRef && packageName == other.packageName && activity == other.activity && shortcut == other.shortcut

    override fun hashCode(): Int = (packageName.hashCode() * 31 + activity.hashCode()) * 31 + shortcut.hashCode()
}

/**
 * 画面に出す名前。アプリの名前(引けなければ null)に、ショートカットならその名前を「: 」でつなぐ(例: 「Gmail: 新規作成」)。
 * @param appLabel アプリそのもの(ref.app)の名前
 */
fun displayLabel(ref: AppRef, appLabel: String?): String? {
    val label = ref.shortcutLabel ?: return appLabel
    return if (appLabel == null) label else "$appLabel: $label"
}

/** 画面部の内蔵背景プリセット(U4 決定: モック docs/mock/background-mock.html の 1・4・5・6)。id は保存に使う。 */
enum class BackgroundPreset(val id: String, val label: String) {
    AURORA("aurora", "オーロラ"),
    BERRY("berry", "ベリー"),
    GRAPHITE("graphite", "グラファイト"),
    ABYSS("abyss", "深海"),
    ;

    companion object {
        val DEFAULT = AURORA

        /** 未知の名前(手で編集されたファイル・将来の版で増えた案)は既定で表示する。 */
        fun resolve(id: String): BackgroundPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 画面部の背景(仕様書 §4.4 / §9)。写真そのものは保存・書出しに含めず、「写真背景だった」ことだけを持つ。
 * @param overlay 背景の上に敷く暗いオーバーレイの濃さ(%)
 */
@Serializable
data class BackgroundSetting(
    val kind: String = KIND_PRESET,
    val preset: String = BackgroundPreset.DEFAULT.id,
    val overlay: Int = DEFAULT_OVERLAY,
) {
    val isPhoto: Boolean get() = kind == KIND_PHOTO

    /** 範囲外・未知の値を既定に丸める(読込み時に通す)。 */
    fun normalized(): BackgroundSetting = BackgroundSetting(
        kind = if (kind == KIND_PHOTO) KIND_PHOTO else KIND_PRESET,
        preset = BackgroundPreset.resolve(preset).id,
        overlay = overlay.coerceIn(0, MAX_OVERLAY),
    )

    companion object {
        const val KIND_PRESET = "preset"
        const val KIND_PHOTO = "photo"
        const val DEFAULT_OVERLAY = 20
        const val MAX_OVERLAY = 70
    }
}

/**
 * 大時計の設定(§4.2 / §8。v0.5.1 で追加、12/24 時間表記は v0.6.0 で追加)。
 * @param dateStyle 日付・曜日の書式(DateStyle.id)
 * @param hourFormat 12/24 時間表記(HourFormat.id)。無いファイル(v0.5.x)は端末に従う
 */
@Serializable
data class ClockSetting(
    val dateStyle: String = DateStyle.DEFAULT.id,
    val hourFormat: String = HourFormat.DEFAULT.id,
) {
    val style: DateStyle get() = DateStyle.resolve(dateStyle)
    val hours: HourFormat get() = HourFormat.resolve(hourFormat)

    /** 未知の書式名を既定に丸める(読込み時に通す)。 */
    fun normalized(): ClockSetting = ClockSetting(dateStyle = style.id, hourFormat = hours.id)
}

/**
 * 操作の手応えの設定(§5.4 / §8。v0.6.0 で追加)。
 * @param haptics キー・機能列・下の行に触れたとき/長押しが成立したときに振動するか(既定 true)。
 *   オフでも押下の沈む演出は出す
 */
@Serializable
data class FeedbackSetting(
    val haptics: Boolean = true,
)

/** 割り当てアイコン(キーの副刻印・自由枠・フォルダ・カメラの枠)の出し方(§5.2 / §8。v0.7.8)。白黒は Hub の行にも掛かるが、絵記号は Hub には掛けない。id は保存に使う。 */
enum class IconStyle(val id: String) {
    /** アプリのアイコンをそのまま出す(既定。2026-11-20 オーナー決定。id は保存済みのファイルとの互換のため "color" のまま)。 */
    STANDARD("color"),
    GRAY("gray"),

    /** アプリのアイコンの代わりに役割の絵記号(data/Pictograms)。 */
    PICTOGRAM("pictogram"),
    ;

    companion object {
        fun of(id: String?): IconStyle? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 見た目の設定(§5.2 / §8。v0.5.1 で追加、iconStyle・pictograms は v0.7.8)。
 * @param grayscaleIcons v0.7.7 までの白黒のオン/オフ。iconStyle が無いファイルはこれで読む(true=白黒・false=標準)。
 *   既定は false=標準(2026-11-20 オーナー決定。v0.5.1〜v0.7.7 は白黒が既定。その間に書いたファイルは値を必ず持つので、選んでいた方で読む)
 *   古い版で読まれても近い見た目になるよう、書くときは iconStyle と合わせて書く(標準だけ false)
 * @param iconStyle 割り当てアイコンの出し方(IconStyle.id)。null は v0.7.7 までのファイル
 * @param pictograms オーナーが選んだ絵記号(パッケージ名 → Pictogram.id)。無いアプリは自動で決める
 */
@Serializable
data class AppearanceSetting(
    val grayscaleIcons: Boolean = false,
    val iconStyle: String? = null,
    val pictograms: Map<String, String> = emptyMap(),
) {
    val style: IconStyle get() = IconStyle.of(iconStyle) ?: if (grayscaleIcons) IconStyle.GRAY else IconStyle.STANDARD

    fun withStyle(s: IconStyle): AppearanceSetting = copy(iconStyle = s.id, grayscaleIcons = s != IconStyle.STANDARD)

    /** アプリの絵記号を選ぶ(null=自動に戻す)。 */
    fun withPictogram(packageName: String, p: Pictogram?): AppearanceSetting =
        copy(pictograms = if (p == null) pictograms - packageName else pictograms + (packageName to p.id))

    /** 未知の出し方は null(=grayscaleIcons で読む)に、未知の絵記号は捨てる(読込み時に通す)。 */
    fun normalized(): AppearanceSetting =
        copy(iconStyle = IconStyle.of(iconStyle)?.id, pictograms = pictograms.filterValues { Pictogram.of(it) != null })
}

/** メインの一覧の枠の 4×2 マス(§4.5)に出す部品。id は保存に使う。 */
enum class MainPanelKind(val id: String) {
    CALENDAR("calendar"),
    HUB("hub"),
    ;

    companion object {
        val DEFAULT = CALENDAR

        fun resolve(id: String): MainPanelKind = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Hub(§4.6)の本文の出し方。のぞき見対策で段階的に隠す。id は保存に使う。 */
enum class HubPreview(val id: String) {
    /** 差出人(通知のタイトル)と本文。 */
    FULL("full"),

    /** 差出人だけ。本文は「新着」。 */
    SENDER("sender"),

    /** アプリ名だけ。本文は「1 件の通知」。 */
    APP("app"),
    ;

    companion object {
        val DEFAULT = FULL

        fun resolve(id: String): HubPreview = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * メインの枠と Hub の設定(§4.5 / §4.6 / §8。v0.7.0 で追加)。
 * @param mainPanel メインの一覧の枠の 4×2 マスに出す部品(MainPanelKind.id)
 * @param preview Hub の本文の出し方(HubPreview.id)
 */
@Serializable
data class HubSetting(
    val mainPanel: String = MainPanelKind.DEFAULT.id,
    val preview: String = HubPreview.DEFAULT.id,
    /** カバーのタイル部(§4.3.1)に出す部品(MainPanelKind.id)。メインとは別に覚える(v0.7.3。2026-11-18 オーナー決定)。 */
    val coverPanel: String = MainPanelKind.DEFAULT.id,
) {
    val panel: MainPanelKind get() = MainPanelKind.resolve(mainPanel)
    val cover: MainPanelKind get() = MainPanelKind.resolve(coverPanel)
    val previewMode: HubPreview get() = HubPreview.resolve(preview)

    /** 未知の値を既定に丸める(読込み時に通す)。 */
    fun normalized(): HubSetting = HubSetting(mainPanel = panel.id, preview = previewMode.id, coverPanel = cover.id)
}

/** カレンダー ⇔ Hub の入れ替え(枠の見出しの下払い・設定の行。§4.5 / §4.3.1)。 */
fun MainPanelKind.flipped(): MainPanelKind = if (this == MainPanelKind.CALENDAR) MainPanelKind.HUB else MainPanelKind.CALENDAR

/**
 * メインの自由枠に置くフォルダ(§4.5。v0.7.1)。中身は選んだ順で最大 Assignments.MAX_FOLDER_APPS 個。
 * @param name フォルダ名。空なら画面では既定の名前(「フォルダ」)を出す
 */
@Serializable
data class AppFolder(
    val name: String = "",
    val apps: List<AppRef> = emptyList(),
)

/** 選択待ちからフォルダに足した結果(§6 / §4.5)。 */
enum class FolderAdd { ADDED, ALREADY, FULL }

/**
 * FoldBerry の設定(割り当て+背景+時計+見た目+触覚+メインの枠と Hub+フォルダ)。
 * 名前とファイル名(assignments.json)は、端末に保存済みのデータとの互換のため M4 のまま変えない。
 */
@Serializable
data class Assignments(
    val version: Int = CURRENT_VERSION,
    /** キー ID(keyboard/KeyLayout の KeySpec.id)→ アプリ。未割り当てのキーは入れない。 */
    val keys: Map<String, AppRef> = emptyMap(),
    /** v0.4.0 で追加。無いファイル(v0.3.x)は既定値で読む。 */
    val background: BackgroundSetting = BackgroundSetting(),
    /** v0.5.1 で追加。無いファイル(v0.5.0 以前)は既定値で読む。 */
    val clock: ClockSetting = ClockSetting(),
    /** v0.5.1 で追加。無いファイルは既定値(標準。v0.7.7 までは白黒)で読む。 */
    val appearance: AppearanceSetting = AppearanceSetting(),
    /** v0.6.0 で追加。無いファイルは既定値(触覚あり)で読む。 */
    val feedback: FeedbackSetting = FeedbackSetting(),
    /** v0.7.0 で追加。無いファイルは既定値(カレンダー・差出人と本文)で読む。 */
    val hub: HubSetting = HubSetting(),
    /**
     * v0.7.1 で追加。メインの自由枠の保存キー(MAIN_SLOT_1〜8)→ フォルダ。無いファイルはフォルダなしで読む。
     * 1 つの枠は keys(アプリ 1 つ)か folders のどちらか一方だけ(withKey / withFolder が相手を消す)。
     */
    val folders: Map<String, AppFolder> = emptyMap(),
) {
    /** キー・枠にアプリを割り当てる(null=解除)。自由枠にアプリを入れたら、その枠のフォルダは消える(枠は一方だけ)。 */
    fun withKey(keyId: String, app: AppRef?): Assignments =
        if (app == null) copy(keys = keys - keyId) else copy(keys = keys + (keyId to app), folders = folders - keyId)

    /**
     * 自由枠のフォルダを置き換える。null・中身が空ならフォルダを外して空き枠に戻す。
     * 中身は重複を除いて MAX_FOLDER_APPS 個までに切り、その枠のアプリ(keys)は消す。自由枠以外のキーは無視する。
     */
    fun withFolder(slotId: String, folder: AppFolder?): Assignments {
        if (slotId !in MAIN_SLOT_KEYS) return this
        val apps = folder?.apps?.distinct()?.take(MAX_FOLDER_APPS).orEmpty()
        if (folder == null || apps.isEmpty()) return copy(folders = folders - slotId)
        return copy(keys = keys - slotId, folders = folders + (slotId to folder.copy(apps = apps)))
    }

    /** 選択待ちからフォルダにアプリを足す(置き換えない。オーナー決定)。入っている・満杯なら変えずに理由を返す。 */
    fun addToFolder(slotId: String, app: AppRef): Pair<Assignments, FolderAdd> {
        val folder = folders[slotId] ?: return this to FolderAdd.FULL
        return when {
            app in folder.apps -> this to FolderAdd.ALREADY
            folder.apps.size >= MAX_FOLDER_APPS -> this to FolderAdd.FULL
            else -> withFolder(slotId, folder.copy(apps = folder.apps + app)) to FolderAdd.ADDED
        }
    }

    companion object {
        const val CURRENT_VERSION = 1

        /**
         * 廃止した割り当て枠の保存キー。読込みで捨てる。2026-11-18 にメインの自由枠を 12→8 個に減らした(オーナー決定。保存からも消す)。
         * data は keyboard(KeyLayout.mainSlotId)に依存しないので、キーの文字列をここで持つ。
         */
        val RETIRED_KEYS: Set<String> = (9..12).map { "MAIN_SLOT_$it" }.toSet() +
            // 左 Shift は 2026-11-22 に Shift 面の切替専用にした(割り当て不可。D28。KeyLayout.SHIFT_KEY_ID と同じ文字列)。
            "LEFT_SHIFT"

        /** フォルダを置ける枠=メインの自由枠の保存キー(KeyLayout.mainSlotId と同じ文字列。単体テストで一致を確かめる)。 */
        val MAIN_SLOT_KEYS: Set<String> = (1..8).map { "MAIN_SLOT_$it" }.toSet()

        /** フォルダの中身の上限(2026-11-18 オーナー決定)。 */
        const val MAX_FOLDER_APPS = 4

        val EMPTY = Assignments()

        /**
         * 読込み時のフォルダの正規化: 自由枠以外・廃止枠のキーは捨てる、同じ枠に keys があれば keys を優先してフォルダを捨てる、
         * 重複を除いて上限で切る、空のフォルダは捨てる。
         */
        fun normalizeFolders(keys: Map<String, AppRef>, folders: Map<String, AppFolder>): Map<String, AppFolder> =
            folders.filterKeys { it in MAIN_SLOT_KEYS && it !in keys }
                .mapValues { (_, f) -> f.copy(apps = f.apps.distinct().take(MAX_FOLDER_APPS)) }
                .filterValues { it.apps.isNotEmpty() }
    }
}

object AssignmentsCodec {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // 手で編集されたファイルなど、知らない項目があっても読めるようにする。
        ignoreUnknownKeys = true
    }

    sealed interface Decoded {
        data class Ok(val value: Assignments) : Decoded
        data class Invalid(val reason: String) : Decoded
    }

    fun encode(a: Assignments): String = json.encodeToString(Assignments.serializer(), a)

    /**
     * 保存ファイルに廃止した項目・枠が残っているか: タイルの割り当て "tiles"(2026-11-19 に廃止。T1・T2 は音楽と次の予定に固定。D26)、
     * または keys・folders に RETIRED_KEYS(2026-11-18 に廃止した自由枠 M9〜M12、2026-11-22 に割り当て不可にした左 Shift)がある。
     * あれば起動時に 1 回だけ書き直して消す(AssignmentStore)。読めないファイルは false(書き直さない)。
     */
    fun hasRetiredEntries(text: String): Boolean = try {
        val obj = json.parseToJsonElement(text.removePrefix(Char(0xFEFF).toString())) as? JsonObject
        val tiles = obj?.containsKey("tiles") == true
        val keys = obj?.get("keys") as? JsonObject
        val folders = obj?.get("folders") as? JsonObject
        tiles || (keys != null && Assignments.RETIRED_KEYS.any { it in keys }) ||
            (folders != null && Assignments.RETIRED_KEYS.any { it in folders })
    } catch (e: Exception) {
        false
    }

    fun decode(text: String): Decoded {
        // 手で編集して BOM 付きで保存されたファイルも読めるようにする。
        val body = text.removePrefix(Char(0xFEFF).toString())
        val parsed = try {
            // version は既定値を持つので、そのまま読むと version の無い他アプリの JSON({} など)まで
            // 「空の設定」として読めてしまい、読込みで割り当てが全部消える。version があることを先に確かめる。
            val obj = json.parseToJsonElement(body) as? JsonObject
                ?: return Decoded.Invalid("JSON のオブジェクトではありません")
            if ("version" !in obj) return Decoded.Invalid("version がありません(FoldBerry の設定ファイルではありません)")
            // フォルダは 1 つずつ読む: 1 つ壊れただけで正しいフォルダ(=中の割り当て)まで道連れにしない(2026-11-18 refuter の指摘)。
            val base = decodeSparingOptional(JsonObject(obj - "folders"))
            base.copy(folders = decodeFolders(obj["folders"]))
        } catch (e: Exception) {
            // kotlinx.serialization の例外の型は版で変わり得るので、まとめて「壊れている」として扱う。
            return Decoded.Invalid("JSON として読めません: ${e.message?.take(200)}")
        }
        if (parsed.version != Assignments.CURRENT_VERSION) {
            return Decoded.Invalid("未対応のバージョンです: ${parsed.version}")
        }
        // 旧ファイルのタイルの割り当て "tiles" は ignoreUnknownKeys で読み捨てる(D26。version は 1 のまま: 項目の削除は前後どちらの版でも読める)。
        // 廃止した自由枠 M9〜M12・左 Shift は捨てる(書出しファイルの読込みも同じ decode を通る)。
        val keys = parsed.keys - Assignments.RETIRED_KEYS
        return Decoded.Ok(
            parsed.copy(
                keys = keys, background = parsed.background.normalized(), clock = parsed.clock.normalized(),
                appearance = parsed.appearance.normalized(), hub = parsed.hub.normalized(), folders = Assignments.normalizeFolders(keys, parsed.folders),
            ),
        )
    }

    /** 壊れていても割り当てを道連れにしない、後から足した項目(背景 v0.4.0・時計と見た目 v0.5.1・触覚 v0.6.0・Hub v0.7.0)。フォルダ(v0.7.1)は decodeFolders でフォルダごとに読む。 */
    private val OPTIONAL_SECTIONS = listOf("background", "clock", "appearance", "feedback", "hub")

    /** フォルダ(v0.7.1)をフォルダごとに読む。オブジェクトでない・読めないフォルダだけ捨てる(正規化は decode の normalizeFolders)。 */
    private fun decodeFolders(element: kotlinx.serialization.json.JsonElement?): Map<String, AppFolder> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (slot, value) ->
            try {
                slot to json.decodeFromJsonElement(AppFolder.serializer(), value)
            } catch (_: Exception) {
                null
            }
        }.toMap()
    }

    /**
     * 背景・時計などの項目だけが壊れている(手で編集した等)なら、その項目を既定にして割り当ては生かす。
     * それらのせいで割り当てまで読めなくなる(=退避して空になる)のを避ける。外す項目はなるべく少なくする
     * (外す組み合わせを個数の少ない順に全部試す。項目は 5 つなので高々 31 通り)。割り当て自体が壊れていれば最初の例外を投げる。
     */
    private fun decodeSparingOptional(obj: JsonObject): Assignments {
        val first = try {
            return json.decodeFromJsonElement(Assignments.serializer(), obj)
        } catch (e: Exception) {
            e
        }
        val present = OPTIONAL_SECTIONS.filter { it in obj }
        val candidates = (1 until (1 shl present.size))
            .map { mask -> present.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }
            .sortedBy { it.size }
        for (drop in candidates) {
            try {
                return json.decodeFromJsonElement(Assignments.serializer(), JsonObject(obj - drop.toSet()))
            } catch (_: Exception) {
                // 次の組み合わせを試す。
            }
        }
        throw first
    }
}

data class ReconcileResult(val assignments: Assignments, val removed: Int, val remapped: Int)

/**
 * 端末にある起動可能なアプリと割り当てを突き合わせる(§5.2「アンインストールされたら未割り当てに戻す」)。
 * - 同じパッケージ名+起動画面名がある → そのまま
 * - パッケージはあるが起動画面名が無い(アプリ更新で変わった)→ そのパッケージの起動画面に付け替える
 * - パッケージごと無い → 未割り当てに戻す
 * 一覧が空のとき(取得に失敗した可能性がある)は、誤って全部外さないよう何もしないで null を返す。
 */
fun reconcile(current: Assignments, installed: List<AppRef>): ReconcileResult? {
    if (installed.isEmpty()) return null
    val exact = installed.toSet()
    val firstByPackage = LinkedHashMap<String, AppRef>()
    installed.forEach { firstByPackage.putIfAbsent(it.packageName, it) }

    var removed = 0
    var remapped = 0
    // ショートカットはアプリ部分(ref.app)で突き合わせ、付け替えてもショートカットは保つ。
    // ショートカット自体が消えたかは見ない(既定のホームでないと読めず、全部外してしまうおそれがあるため。起動の失敗で分かる)。
    fun resolve(ref: AppRef): AppRef? = when {
        ref.app in exact -> ref
        else -> firstByPackage[ref.packageName]?.let { found ->
            remapped++
            ref.copy(activity = found.activity)
        } ?: run { removed++; null }
    }

    val keys = current.keys.mapNotNull { (id, ref) -> resolve(ref)?.let { id to it } }.toMap()
    // フォルダの中も同じく付け替え/外す。空になったフォルダは消して空き枠に戻す(1 個になってもフォルダのまま)。
    val folders = current.folders
        .mapValues { (_, f) -> f.copy(apps = f.apps.mapNotNull(::resolve).distinct()) }
        .filterValues { it.apps.isNotEmpty() }
    return ReconcileResult(current.copy(keys = keys, folders = folders), removed, remapped)
}

sealed interface ImportResult {
    /**
     * @param missing 端末に無かったため未割り当てにした件数。アプリ一覧がまだ無く確認できなかったときは null
     * @param remapped 起動画面名が変わっていたため同じパッケージに付け替えた件数
     */
    data class Ok(val assignments: Assignments, val missing: Int?, val remapped: Int) : ImportResult
    data class Invalid(val reason: String) : ImportResult
}

/**
 * 書出しファイルの読込み(仕様書 §8 / §9)。今の割り当てを置き換える前提で、読み込む内容を決める。
 * 端末に無いアプリの割り当ては未割り当てにして件数を返す(§9)。
 * @param installed 端末のアプリ一覧。まだ取得できていなければ null(突き合わせは後の再読込に任せる)
 */
fun importSettings(text: String, installed: List<AppRef>?): ImportResult =
    when (val decoded = AssignmentsCodec.decode(text)) {
        is AssignmentsCodec.Decoded.Invalid -> ImportResult.Invalid(decoded.reason)
        is AssignmentsCodec.Decoded.Ok -> {
            val result = installed?.let { reconcile(decoded.value, it) }
            if (result == null) {
                ImportResult.Ok(decoded.value, missing = null, remapped = 0)
            } else {
                ImportResult.Ok(result.assignments, missing = result.removed, remapped = result.remapped)
            }
        }
    }

/** 割り当て済みの件数(キー+フォルダの中のアプリ)。読込みの確認表示に使う。 */
fun Assignments.assignedCount(): Int = keys.size + folders.values.sumOf { it.apps.size }

/** 割り当てを全部外す(設定の割り当て一覧の「すべて解除」。§8)。フォルダも消す。背景・時計・見た目・触覚・Hub の設定は残す。 */
fun Assignments.clearedAssignments(): Assignments = copy(keys = emptyMap(), folders = emptyMap())

/**
 * 設定の初期化(§8。2026-11-20 オーナー決定): 割り当て(キー・自由枠・フォルダ)は残し、背景・時計・見た目(アイコンの表示と
 * 選んだ絵記号)・触覚・Hub を既定に戻す。表示言語は OS 側の設定なので対象外。
 */
fun Assignments.resetSettings(): Assignments = Assignments.EMPTY.copy(keys = keys, folders = folders)

/**
 * 未割り当てのときに開く端末の既定のアプリ(§8。v0.6.2)。送話キー=電話(ダイヤル画面)、カメラの枠=カメラ。
 * 割り当て一覧・バッジで「既定」として見せるのに使う(保存はしない)。決まっていない・一覧に無ければ null。
 */
data class DefaultApps(val dialer: AppRef? = null, val camera: AppRef? = null)

/** 名前の部分一致で絞り込む(大文字小文字を区別しない。前後の空白は無視)。空なら全件。 */
fun <T> filterByName(items: List<T>, query: String, name: (T) -> String): List<T> {
    val q = query.trim()
    if (q.isEmpty()) return items
    return items.filter { name(it).contains(q, ignoreCase = true) }
}

/**
 * アプリ名の並び順(§6)。先頭の文字で 英字 → #(数字・記号・その他)→ 日本語(かな・漢字)の 3 群に分け、
 * 群の中は日本語ロケールの照合順(オーナー指定「A#あ」の順)。照合順だけだと 記号・数字 → 英字 → かな になるため群を先に比べる。
 */
fun <T> sortByName(items: List<T>, name: (T) -> String): List<T> {
    val collator = Collator.getInstance(Locale.JAPANESE)
    return items.sortedWith(compareBy<T> { nameGroup(name(it)) }.thenComparator { a, b -> collator.compare(name(a), name(b)) })
}

/**
 * 名前の群(並びの順。§6)。割り当て一覧の小見出し・アプリ一覧のページの帯にも使う(画面の表記は A–Z・#・あ)。
 */
enum class NameGroup { LATIN, OTHER, JAPANESE }

/** LATIN = 英字(全角も含む)、OTHER = #(数字・記号・空・ほかの文字体系)、JAPANESE = 日本語(ひらがな・カタカナ・漢字)。 */
fun nameGroup(name: String): NameGroup {
    val c = name.trimStart().firstOrNull() ?: return NameGroup.OTHER
    return when (Character.UnicodeScript.of(c.code)) {
        Character.UnicodeScript.LATIN -> NameGroup.LATIN
        Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN -> NameGroup.JAPANESE
        else -> NameGroup.OTHER
    }
}
