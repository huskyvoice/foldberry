package com.foldberry.launcher.ui.icons

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.data.Pictogram
import com.foldberry.launcher.keyboard.FunctionKey
import com.foldberry.launcher.keyboard.KeyGlyph

/*
 * 自作アイコン。パスはデザイン確定モック docs/mock/cover-mock.html の SVG と同じ。
 * BlackBerry のロゴ(点の集合マーク)・既存素材は使わない(仕様書 §5.3 / §13)。
 * すべて白で描き、使う側で ColorFilter.tint により色を付ける。
 */
object FoldBerryIcons {

    private class Spec(val d: String, val alpha: Float = 1f)

    /** Bluetooth のルーン(通常・接続中で共通)。 */
    private const val BLUETOOTH_RUNE = "M7 7l10 10-5 5V2l5 5L7 17"

    /** Bluetooth 接続中の左右の点(半径 1.6。線の太さより一回り大きくして、小さく出しても見えるようにする)。 */
    private const val BLUETOOTH_DOTS = "M2.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z M18.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z"
    /*
     * 同じ意味のアイコンは 1 つの形にそろえる(2026-11-18 オーナー決定「同じアイコンを複数の場所で使っていて絵が違うなら統一」)。
     * 形は設定シートの線画(Line)に合わせ、描き方(塗り・線・線幅)だけを置き場所ごとに変える。
     */
    /** 振動: 端末の輪郭と左右 2 本ずつの振動線(下の行・ステータス行・設定で共通)。 */
    private val VIBRATE_PATHS = arrayOf(
        "M10 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
        "M4 9v6M20 9v6M2 11v2M22 11v2",
    )
    /** ベル: 本体と半円の舌(下の行の着信音・Hub・時計の横・ステータス行のサイレント・設定で共通)。 */
    private val BELL_PATHS = arrayOf("M6 16V11a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")
    private val SILENT_PATHS = arrayOf(*BELL_PATHS, "M3 3l18 18")
    /** アラーム: 文字盤(時計の線画 Line.Clock と同じ針)と左右の小さなベル(ステータス行。D26。2026-11-19)。 */
    private val ALARM_PATHS = arrayOf("M20 13a8 8 0 1 1-16 0a8 8 0 1 1 16 0z", "M12 9v4l3 2", "M3.5 6.5l3-3", "M20.5 6.5l-3-3")
    /* ステータス行の継続表示(§4.1。2026-11-25。docs/mock/ongoing-mock.html の 接続中 B・進行中 C・常駐 C)。 */
    /** 接続中: 丸い頭の鍵。 */
    private val ONGOING_CONNECTION_PATHS = arrayOf("M11.5 15.5a4 4 0 1 1-8 0a4 4 0 1 1 8 0z", "M10.4 12.6L19 4", "M15.5 7.5l2.5 2.5", "M17.5 5.5l2 2")
    /** 進行中: 一部が欠けた円と再生の三角。 */
    private val ONGOING_ACTIVITY_PATHS = arrayOf("M12 4a8 8 0 1 1-8 8", "M10 9v6l5-3z")
    /** その他の常駐: ピン。 */
    private val ONGOING_SERVICE_PATHS = arrayOf("M9 3h6l-1 6 3 3v2H7v-2l3-3z", "M12 14v7")
    /** 音楽: 八分音符(音楽のタイルの案内。D26)。 */
    private val MUSIC_PATHS = arrayOf("M9 18V5l11-2v13", "M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z", "M20 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    private const val CHARGING_PATH = "M13.5 1.5L4.5 13.5h6l-1.5 9 9-12h-6z"

    private fun stroke(name: String, viewport: Float, width: Float, vararg paths: String, viewportH: Float = viewport) =
        build(name, viewport, viewportH) { b ->
            paths.forEach { d ->
                b.addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }

    private fun fill(name: String, viewport: Float, vararg specs: Spec, viewportH: Float = viewport) =
        build(name, viewport, viewportH) { b ->
            specs.forEach { s -> b.addPath(pathData = addPathNodes(s.d), fill = SolidColor(Color.White), fillAlpha = s.alpha) }
        }

    private inline fun build(name: String, w: Float, h: Float, block: (ImageVector.Builder) -> Unit): ImageVector {
        val b = ImageVector.Builder(name = name, defaultWidth = w.dp, defaultHeight = h.dp, viewportWidth = w, viewportHeight = h)
        block(b)
        return b.build()
    }

    /** マークの紫(アプリアイコン ic_launcher_foreground.xml の右下キーと同じ色)。満充電の稲妻でも使う(§4.1)。 */
    val MarkPurple = Color(0xFF9C27B0)

    /**
     * マーク: アプリアイコンと同じ角丸キー 2×2(右下だけ紫)を右に 10° 傾けた形(U3 改訂 2026-11-16。モックのマーク D)。
     * ImageVector の group は skew を持てないため、skewX(-10°) を掛けた座標をパスに焼き込んである
     * (角丸はベジエ近似。ic_launcher_foreground.xml の 108 枠の座標から 30 を引いて 48 枠にしたもの)。
     * 紫を残すため 2 色で描いており、使う側で tint を掛けないこと。
     */
    val Mark: ImageVector = build("mark", 48f, 48f) { b ->
        val white = SolidColor(Color.White)
        listOf(
            "M9.17,6L25.17,6C26.28,6 27.02,6.9 26.82,8L24.71,20C24.51,21.1 23.46,22 22.35,22L6.35,22C5.25,22 4.51,21.1 4.71,20L6.82,8C7.02,6.9 8.07,6 9.17,6Z",
            "M31.17,6L45.17,6C46.28,6 47.02,6.9 46.82,8L44.71,20C44.51,21.1 43.46,22 42.35,22L28.35,22C27.25,22 26.51,21.1 26.71,20L28.82,8C29.02,6.9 30.07,6 31.17,6Z",
            "M5.65,26L21.65,26C22.75,26 23.49,26.9 23.29,28L21.18,40C20.98,41.1 19.93,42 18.83,42L2.83,42C1.72,42 0.98,41.1 1.18,40L3.29,28C3.49,26.9 4.54,26 5.65,26Z",
        ).forEach { d -> b.addPath(pathData = addPathNodes(d), fill = white, fillAlpha = 0.88f) }
        b.addPath(
            pathData = addPathNodes(
                "M27.65,26L41.65,26C42.75,26 43.49,26.9 43.29,28L41.18,40C40.98,41.1 39.93,42 38.83,42L24.83,42C23.72,42 22.98,41.1 23.18,40L25.29,28C25.49,26.9 26.54,26 27.65,26Z",
            ),
            fill = SolidColor(MarkPurple),
        )
    }

    val Wifi: ImageVector = fill(
        "wifi", 24f,
        Spec("M12 17.5l-3.2-3.9a5 5 0 0 1 6.4 0z"),
        Spec("M12 6.5a11 11 0 0 1 7.6 3l-1.9 2.3a8 8 0 0 0-11.4 0L4.4 9.5A11 11 0 0 1 12 6.5z"),
        Spec("M12 1a16.5 16.5 0 0 1 11.3 4.4l-1.9 2.3A13.5 13.5 0 0 0 2.6 7.7L0.7 5.4A16.5 16.5 0 0 1 12 1z"),
        viewportH = 18f,
    )

    /** マナーモード(バイブ): 端末の輪郭と左右の振動線。クイックドックのマナーで使う(ステータス行は Status.Vibrate)。 */
    val Vibrate: ImageVector = stroke("vibrate", 24f, 1.8f, *VIBRATE_PATHS)

    /**
     * ステータス行(§4.1)用: 描かれている部分ぎりぎりまで viewport を詰めた版(2026-11-18 オーナー指摘「大きさが揃っていない」)。
     * 高さ 11dp で出すと、描画部が電波バー・電池と同じ 11dp、線の太さが 1.3dp にそろう(docs/mock/status-mock.html)。
     * 値は「中身の外接矩形+線の太さの半分」で、線の太さ w は 1.3 × 中身の高さ / (11 - 1.3) から求めた。
     * Bluetooth(ルーン風の線画)・接続中(ルーンの左右に点。Android 標準の「接続中」と同じ形)・バイブ・サイレント(斜線入りのベル)・充電中(稲妻は電池の中に描くので Path で持つ)は
     * ステータス行でしか使わないので、この版だけを持つ(24 の版は 2026-11-18 に削除。バイブの 24 の版はクイックドックが使う)。
     * 接続中は塗りの点にも線の太さの半分の余白を足しているので、左右に約 0.65dp の余白が残る(モックと同じ)。
     */
    object Status {
        val Bluetooth: ImageVector = cropped("st_bluetooth", 5.66f, 0.66f, 12.68f, 22.68f) { b ->
            b.strokePath(BLUETOOTH_RUNE, 2.68f)
        }
        val BluetoothConnected: ImageVector = cropped("st_bluetooth_connected", 1.06f, 0.66f, 21.88f, 22.68f) { b ->
            b.strokePath(BLUETOOTH_RUNE, 2.68f)
            b.addPath(pathData = addPathNodes(BLUETOOTH_DOTS), fill = SolidColor(Color.White))
        }
        val Vibrate: ImageVector = cropped("st_vibrate", 0.93f, 2.93f, 22.14f, 18.14f) { b ->
            VIBRATE_PATHS.forEach { b.strokePath(it, 2.14f) }
        }
        val Silent: ImageVector = cropped("st_silent", 1.73f, 1.73f, 20.55f, 21.55f) { b ->
            SILENT_PATHS.forEach { b.strokePath(it, 2.55f) }
        }
        /** 次のアラームの設定あり(D26)。中身は x 3.5〜20.5・y 3.5〜21(高さ 17.5)→ 線 1.3 × 17.5 / 9.7 = 2.345。 */
        val Alarm: ImageVector = cropped("st_alarm", 2.33f, 2.33f, 19.35f, 19.85f) { b ->
            ALARM_PATHS.forEach { b.strokePath(it, 2.345f) }
        }
        /** 継続表示の接続中(§4.1)。中身は x 3.5〜19.5・y 4〜19.5(高さ 15.5)→ 線 1.3 × 15.5 / 9.7 = 2.077。 */
        val OngoingConnection: ImageVector = cropped("st_ongoing_connection", 2.46f, 2.96f, 18.08f, 17.58f) { b ->
            ONGOING_CONNECTION_PATHS.forEach { b.strokePath(it, 2.077f) }
        }
        /** 継続表示の進行中(§4.1)。中身は x・y とも 4〜20(高さ 16)→ 線 1.3 × 16 / 9.7 = 2.144。 */
        val OngoingActivity: ImageVector = cropped("st_ongoing_activity", 2.93f, 2.93f, 18.14f, 18.14f) { b ->
            ONGOING_ACTIVITY_PATHS.forEach { b.strokePath(it, 2.144f) }
        }
        /** 継続表示のその他の常駐(§4.1)。中身は x 7〜17・y 3〜21(高さ 18)→ 線 1.3 × 18 / 9.7 = 2.412。 */
        val OngoingService: ImageVector = cropped("st_ongoing_service", 5.79f, 1.79f, 12.41f, 20.41f) { b ->
            ONGOING_SERVICE_PATHS.forEach { b.strokePath(it, 2.412f) }
        }
        /**
         * 充電中の稲妻の輪郭。原点を左上へ詰めた 13.5×21 の枠(縦横比 [CHARGING_ASPECT])。
         * 電池の中に重ねて描くので ImageVector ではなく Path で渡す(まわりを抜くのに輪郭が要る。2026-11-18 オーナー要望)。
         */
        fun chargingBolt(): Path = addPathNodes(CHARGING_PATH).toPath().apply { translate(Offset(-4.5f, -1.5f)) }

        /** 稲妻の幅 / 高さ。 */
        const val CHARGING_ASPECT = 13.5f / 21f

        /** viewport の左上 (x, y) を原点へずらして w×h に詰める(ImageVector は viewport の原点を持てないので group で動かす)。 */
        private inline fun cropped(name: String, x: Float, y: Float, w: Float, h: Float, block: (ImageVector.Builder) -> Unit): ImageVector {
            val b = ImageVector.Builder(name = name, defaultWidth = w.dp, defaultHeight = h.dp, viewportWidth = w, viewportHeight = h)
            b.addGroup(translationX = -x, translationY = -y)
            block(b)
            b.clearGroup()
            return b.build()
        }

        private fun ImageVector.Builder.strokePath(d: String, width: Float) = addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(Color.White),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    /* 時計の横の種類別の通知(§4.2)。2026-11-17 追加分で、ここで描いた線画。 */

    /** 通知(その他)。 */
    val Bell: ImageVector = stroke("bell", 24f, 1.8f, *BELL_PATHS)

    /** 不在着信: 受話器と、右上の折れた矢印。 */
    val MissedCall: ImageVector = stroke(
        "missed_call", 24f, 1.8f,
        "M5 5h3l1.5 4-2 1.5a11 11 0 0 0 5 5l1.5-2 4 1.5v3a2 2 0 0 1-2 2A16 16 0 0 1 3 7a2 2 0 0 1 2-2z",
        "M14 2.5l3 3 4.5-4.5", "M14 2.5v3h3",
    )

    /** メール: 封筒。 */
    val Mail: ImageVector = stroke(
        "mail", 24f, 1.8f,
        "M4 5.5h16a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-11a1 1 0 0 1 1-1z",
        "M3.5 6.5l8.5 6.5 8.5-6.5",
    )

    /** SMS: 3 つの点が入った吹き出し。 */
    val Sms: ImageVector = stroke(
        "sms", 24f, 1.8f,
        "M4 4h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H10l-4 3.5V16H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z",
        "M8 10h.01", "M12 10h.01", "M16 10h.01",
    )

    /* カバー画面の下の行(§4.3)。パスは docs/mock/calendar-mock.html と同じ。2026-11-17 追加分。 */

    /**
     * 端末の設定: 歯車。モックのパスは 24 の枠いっぱい(約 1〜23)で、隣の懐中電灯(縦 2.5〜22)より大きく見えるため、
     * 0.85 倍に縮めて高さを懐中電灯にそろえる(2026-11-17 オーナー指定)。下端は懐中電灯と同じ 22 に置き、
     * 線は縮む分だけ太くして見た目の太さ(1.6)を保つ。
     */
    val Settings: ImageVector = build("settings", 24f, 24f) { b ->
        val scale = 0.85f
        b.addGroup(name = "shrink", pivotX = 12f, pivotY = 23f, scaleX = scale, scaleY = scale, translationY = -1f)
        SettingsPaths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f / scale,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        b.clearGroup()
    }

    private val SettingsPaths get() = listOf(
        "M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )

    /** QR コード読み取り: 四隅の枠と中の小さな四角。 */
    val QrScan: ImageVector = stroke(
        "qr_scan", 24f, 1.8f,
        "M3 8V4.5A1.5 1.5 0 0 1 4.5 3H8", "M16 3h3.5A1.5 1.5 0 0 1 21 4.5V8", "M21 16v3.5a1.5 1.5 0 0 1-1.5 1.5H16", "M8 21H4.5A1.5 1.5 0 0 1 3 19.5V16",
        "M7.5 7.5h3.5v3.5H7.5z", "M13 7.5h3.5v3.5H13z", "M7.5 13h3.5v3.5H7.5z", "M13.5 13.5h1", "M16 16h.5", "M13.5 16.5h.5",
    )

    /** 今月へ戻る: 塗りの家。形は設定シートの Line.Home と同じ(外形からドアを削った 1 本のパス。2026-11-18 に BB10 風の軒付きの家から統一)。 */
    val Home: ImageVector = fill("home", 24f, Spec("M4 11l8-7 8 7v9h-6v-5h-4v5H4z"))

    /** 塗りのフォルダ: フォルダの中のアプリのバッジの印(§6。2026-11-18 追加)。 */
    val FolderMark: ImageVector = fill("folder_mark", 24f, Spec("M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"))

    /** 塗りの Shift: Shift 面の割り当てのバッジ・キートップの印(D28。docs/mock/shift-mock.html。2026-11-22 追加)。 */
    val ShiftMark: ImageVector = fill("shift_mark", 24f, Spec("M12 3l8.5 9H15.5v8h-7v-8H3.5z"))

    /** ライト: 懐中電灯。 */
    val Flashlight: ImageVector = stroke(
        "flashlight", 24f, 1.8f,
        "M8 2.5h8v4l-2 3V21a1 1 0 0 1-1 1h-2a1 1 0 0 1-1-1V9.5l-2-3z", "M8 6.5h8", "M12 13v2.5",
    )

    /** 自動回転: 端末を回転の矢印で囲む。 */
    val AutoRotate: ImageVector = stroke(
        "auto_rotate", 24f, 1.6f,
        "M10 8h4a.8.8 0 0 1 .8.8v6.4a.8.8 0 0 1-.8.8h-4a.8.8 0 0 1-.8-.8V8.8A.8.8 0 0 1 10 8z",
        "M3.5 12a8.5 8.5 0 0 1 14.5-6", "M18.5 2.5V6.5h-4", "M20.5 12a8.5 8.5 0 0 1-14.5 6", "M5.5 21.5v-4h4",
    )

    /** カメラ。 */
    val Camera: ImageVector = stroke(
        "camera", 24f, 1.7f,
        "M4 7.5h3.5L9.5 5h5l2 2.5H20a1 1 0 0 1 1 1V19a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V8.5a1 1 0 0 1 1-1z",
        "M12 9.5a4 4 0 1 1 0 8 4 4 0 0 1 0-8z",
    )

    private val Send = stroke("send", 28f, 2.2f, "M6 11v4.5q0 1.5 1.5 1.5h13q1.5 0 1.5-1.5V11")
    private val End = stroke("end", 28f, 2.2f, "M6 17.5v-3q0-3.5 8-3.5t8 3.5v3", "M5 17.5h5M18 17.5h5")
    private val Back = stroke("back", 28f, 2.2f, "M10 8l-4 4 4 4", "M6 12h11a4.5 4.5 0 0 1 0 9h-4")

    private val Backspace = stroke("backspace", 24f, 1.8f, "M8.5 6H20v12H8.5L3 12z", "M11 9.5l5 5M16 9.5l-5 5")
    private val Enter = stroke("enter", 24f, 1.9f, "M19 5v7H6", "M9.5 8.5L6 12l3.5 3.5")
    private val Shift = stroke("shift", 24f, 1.8f, "M12 4l7.5 8H15v7H9v-7H4.5z")
    private val Mic = stroke(
        "mic", 24f, 1.8f,
        "M12 3.5a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0v-5a3 3 0 0 1 3-3z",
        "M6 11.5a6 6 0 0 0 12 0M12 17.5V21",
    )
    private val Space = stroke("space", 40f, 2.2f, "M3 3v5h34V3", viewportH = 12f)
    private val Speaker = stroke(
        "speaker", 24f, 2f,
        "M4 9.5h3.5L12 6v12l-4.5-3.5H4z",
        "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11",
    )

    /** 音楽のタイルの操作ボタン(塗り。D26。2026-11-19)。 */
    object Media {
        val Play: ImageVector = fill("media_play", 24f, Spec("M8 5v14l11-7z"))
        val Pause: ImageVector = fill("media_pause", 24f, Spec("M6.5 5h4v14h-4zM13.5 5h4v14h-4z"))
        val Prev: ImageVector = fill("media_prev", 24f, Spec("M6 5h2.2v14H6zM19 5v14L9.5 12z"))
        val Next: ImageVector = fill("media_next", 24f, Spec("M15.8 5H18v14h-2.2zM5 5v14l9.5-7z"))
    }

    /**
     * 設定・一覧(案 C。2026-11-17 オーナー決定)の細い線画。パスは比較モック docs/mock/settings-mock.html・lists-mock.html と同じ。
     * キーの刻印(Back・Home など)とは太さと形が違うので別にまとめる。
     */
    object Line {
        private fun line(name: String, vararg paths: String) = stroke("line_$name", 24f, 1.7f, *paths)

        val Back = line("back", "M15 6l-6 6 6 6")
        val Close = line("close", "M6 6l12 12M18 6L6 18")
        val Chevron = line("chevron", "M9 6l6 6-6 6")
        val Search = line("search", "M17 11a6 6 0 1 1-12 0a6 6 0 1 1 12 0z", "M20 20l-4.5-4.5")
        val Trash = line("trash", "M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12")
        val Image = line("image", "M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z", "M11 10a2 2 0 1 1-4 0a2 2 0 1 1 4 0z", "M21 16l-5-5-9 9")
        val Globe = line("globe", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18")
        val Clock = line("clock", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 7v5l3 2")
        val Music = line("music", *MUSIC_PATHS)
        val Drop = line("drop", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z")
        val Keyboard = line("keyboard", "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M7 10h.01M11 10h.01M15 10h.01M8 14h8")
        val Vibrate = line("vibrate", *VIBRATE_PATHS)
        val Bell = line("bell", *BELL_PATHS)
        val Signal = line("signal", "M5 18v-3M10 18v-6M15 18v-9M20 18V5")
        val Home = line("home", "M4 11l8-7 8 7v9H4z", "M10 20v-5h4v5")
        val Upload = line("upload", "M12 15V4M7 9l5-5 5 5", "M5 15v4h14v-4")
        val Download = line("download", "M12 4v11M7 10l5 5 5-5", "M5 15v4h14v-4")
        /** アプリ情報: 丸に i(長押しメニュー §6。2026-11-18 追加)。 */
        val Info = line("info", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0z", "M12 11v5.5", "M12 7.8h.01")
        /** カレンダー: メインの枠の部品の選択(設定 §8。2026-11-18 追加)。 */
        val Calendar = line("calendar", "M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M4 10h16", "M8 3v4M16 3v4")
        /** フォルダ: 自由枠のフォルダを作る・解除する(§4.5 / §7。2026-11-18 追加)。 */
        val Folder = line("folder", "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
        /** プラス: 空きの自由枠・カメラの枠の印(§4.3 / §4.5。2026-11-18 にフォントの「+」から置き換え)。 */
        val Plus = line("plus", "M12 5v14M5 12h14")
        /** 目: Hub の本文の出し方(設定 §8。2026-11-18 追加)。 */
        val Eye = line("eye", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    }

    /**
     * 割り当てアイコンの代わりの絵記号(§5.2。2026-11-20。docs/mock/appicon-mock.html の SVG と同じ形。土台なし・白)。
     * アプリのロゴは描かない。モックの circle・rect は同じ形のパスに直して持つ。
     */
    object Pictograms {
        private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

        private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
            "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}" +
                "a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

        private fun pict(name: String, vararg paths: String) = stroke("pict_$name", 24f, 1.8f, *paths)

        private val Mail = pict("mail", rect(3f, 5.5f, 18f, 13f, 2f), "M3.5 7l8.5 6.5L20.5 7")
        private val Chat = pict("chat", "M4 6.5Q4 4.5 6 4.5h12q2 0 2 2v8q0 2-2 2h-7l-4.5 3.5v-3.5H6q-2 0-2-2z")
        /** SNS: 人が 2 人(docs/mock/sns-mock.html の A。2026-11-21)。 */
        private val Sns = pict(
            "sns", circle(9f, 8.5f, 3f), "M3.5 19.5q0-5.5 5.5-5.5t5.5 5.5", "M14.5 5.6a2.6 2.6 0 1 1 1.5 4.9", "M17 13.6q3.5.6 3.5 5",
        )
        private val Browser = pict("browser", circle(12f, 12f, 8.5f), "M3.5 12h17", "M12 3.5q-4 4-4 8.5t4 8.5q4-4 4-8.5t-4-8.5")
        private val Map = pict("map", "M12 21s-6.5-6.2-6.5-11a6.5 6.5 0 0 1 13 0c0 4.8-6.5 11-6.5 11z", circle(12f, 10f, 2.3f))
        private val Photo = pict("photo", rect(3.5f, 5f, 17f, 14f, 2f), "M4 17l5-5 3.5 3.5 2.5-2.5 4.5 4.5", circle(15.5f, 9f, 1.5f))
        private val Camera = pict(
            "camera", "M4 8.5q0-1.5 1.5-1.5h2.5l1.5-2h5l1.5 2h2.5q1.5 0 1.5 1.5v9q0 1.5-1.5 1.5h-13Q4 19 4 17.5z", circle(12f, 12.5f, 3.5f),
        )
        private val Phone = pict("phone", "M6.5 4h3l1.5 4-2 1.5q1.2 3.3 5.5 5.5l1.5-2 4 1.5v3q0 2-2 2C11 19.5 4.5 13 4.5 6q0-2 2-2z")
        private val Clock = pict("clock", circle(12f, 12f, 8.5f), "M12 7v5l3.5 2")
        private val Calendar = pict("calendar", rect(4f, 5.5f, 16f, 14f, 2f), "M4 10h16M8.5 3.5v4M15.5 3.5v4")
        private val Calculator = pict(
            "calculator", rect(5.5f, 3.5f, 13f, 17f, 2f), "M8.5 7h7",
            "M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01M8.5 15h.01M12 15h.01M15.5 15h.01M8.5 18h.01M12 18h.01M15.5 18h.01",
        )
        private val Note = pict("note", "M5.5 5q0-1.5 1.5-1.5h7.5l4 4V19q0 1.5-1.5 1.5H7q-1.5 0-1.5-1.5z", "M14 3.5V8h4.5M8.5 12h7M8.5 15.5h5")
        private val Video = pict("video", rect(3f, 5.5f, 18f, 13f, 3f), "M10.5 9.2v5.6l4.6-2.8z")
        private val Music = pict("music", "M9.5 17.5V5.5l10-2v12", circle(7f, 17.5f, 2.5f), circle(17f, 15.5f, 2.5f))
        private val Files = pict("files", "M3.5 7q0-1.5 1.5-1.5h4.5l2 2H19q1.5 0 1.5 1.5v8.5q0 1.5-1.5 1.5H5q-1.5 0-1.5-1.5z")
        private val Settings = pict(
            "settings", circle(12f, 12f, 3f), circle(12f, 12f, 6.2f),
            "M12 3.5v2.3M12 18.2v2.3M3.5 12h2.3M18.2 12h2.3M6 6l1.6 1.6M16.4 16.4L18 18M6 18l1.6-1.6M16.4 7.6L18 6",
        )
        private val Wallet = pict("wallet", "M4 7.5q0-2 2-2h11.5v3", rect(4f, 8.5f, 16f, 11f, 2f), "M20 12h-3.5a2 2 0 0 0 0 4H20")
        private val Shopping = pict("shopping", "M5.5 8h13l-1 11.5H6.5z", "M9 10V7a3 3 0 0 1 6 0v3")
        private val Generic = pict(
            "generic", rect(5f, 5f, 5.5f, 5.5f, 1.5f), rect(13.5f, 5f, 5.5f, 5.5f, 1.5f),
            rect(5f, 13.5f, 5.5f, 5.5f, 1.5f), rect(13.5f, 13.5f, 5.5f, 5.5f, 1.5f),
        )

        fun of(p: Pictogram): ImageVector = when (p) {
            Pictogram.MAIL -> Mail
            Pictogram.CHAT -> Chat
            Pictogram.SNS -> Sns
            Pictogram.BROWSER -> Browser
            Pictogram.MAP -> Map
            Pictogram.PHOTO -> Photo
            Pictogram.CAMERA -> Camera
            Pictogram.PHONE -> Phone
            Pictogram.CLOCK -> Clock
            Pictogram.CALENDAR -> Calendar
            Pictogram.CALCULATOR -> Calculator
            Pictogram.NOTE -> Note
            Pictogram.VIDEO -> Video
            Pictogram.MUSIC -> Music
            Pictogram.FILES -> Files
            Pictogram.SETTINGS -> Settings
            Pictogram.WALLET -> Wallet
            Pictogram.SHOPPING -> Shopping
            Pictogram.GENERIC -> Generic
        }
    }

    fun glyph(g: KeyGlyph): ImageVector = when (g) {
        KeyGlyph.BACKSPACE -> Backspace
        KeyGlyph.ENTER -> Enter
        KeyGlyph.SHIFT -> Shift
        KeyGlyph.MIC -> Mic
        KeyGlyph.SPACE -> Space
        KeyGlyph.SPEAKER -> Speaker
    }

    /** トラックパッドは図形として描くのでアイコンは無い(null)。 */
    fun function(k: FunctionKey): ImageVector? = when (k) {
        FunctionKey.SEND -> Send
        FunctionKey.MENU -> Mark
        FunctionKey.TRACKPAD -> null
        FunctionKey.BACK -> Back
        FunctionKey.END -> End
    }
}
