package com.foldberry.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.flipped
import com.foldberry.launcher.keyboard.FunctionKey
import com.foldberry.launcher.keyboard.KeyLayout
import com.foldberry.launcher.keyboard.KeySpec
import com.foldberry.launcher.layout.BodyLayout
import com.foldberry.launcher.layout.computeBodyLayout
import com.foldberry.launcher.layout.computeKeyboardLayout
import androidx.compose.foundation.layout.Box
import com.foldberry.launcher.ui.keyboard.FunctionRow
import com.foldberry.launcher.ui.keyboard.LocalHapticsEnabled
import com.foldberry.launcher.ui.keyboard.KeyRows
import com.foldberry.launcher.ui.screen.CoverTileArea
import com.foldberry.launcher.ui.screen.QuickDock
import com.foldberry.launcher.ui.screen.ScreenPanel
import java.util.Locale

/** キーボード部の本体色(§4.4: キーボード部は常に本体色)。 */
private val BodyColor = Color(0xFF070708)

/** メイン縦の下半分で、キーの上に置く下の行の高さ(QuickDock 48dp+上下の余白。D25)。 */
private val KeyboardDockHeight = 52.dp

/**
 * BlackBerry 本体部(カバー構成の全面/メイン構成の片側。§3.2 / §3.3)。
 * 上から 画面部(背景・ステータス行・時計・タイル部。§4)/ 機能列 / 4 段のキー / 最下段の下の余白。
 *
 * @param touchesWindowBottom 本体の下端がウィンドウ下端(ナビゲーション領域)に接しているか。
 *   いまの構成(カバー・メイン横の左・メイン縦の下)では常に接している。接しない配置にしたときは
 *   ナビゲーション用の余白を取らない。
 * @param home 開いているパネルの状態(開閉で作り直されない HomeScreen が持つ)
 * @param cover カバー画面か。カバーはタイル部を月のカレンダー+下の行、メインは次の予定+音楽(D26)+下の行にする(§4.3)
 */
/**
 * 一覧の「キーに割り当て」で選択待ちのとき、押されたキー・枠に割り当てる(§6)。保存は即時(§7)。
 * 選択待ちの間にアンインストールされていたら割り当てない(AppCatalog.isListed 参照)。本体部とメインの自由枠(§4.5)で共用。
 */
internal fun assignPickedApp(context: Context, home: HomeController, keyId: String, keyName: String, entry: AppEntry) {
    val app = context.applicationContext as FoldBerryApp
    val text = if (app.catalog.isListed(entry.ref)) {
        app.store.update { it.withKey(keyId, entry.ref) }
        context.getString(R.string.key_assigned, keyName, entry.label)
    } else {
        context.getString(R.string.app_not_found, entry.label)
    }
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    home.pickingApp = null
}

/**
 * @param sheets シート類(HomeSheets)をこの中に出すか。メイン縦は上半分の画面に出すので false(D25)
 * @param keyboardOnly 画面部を出さず、下の行+機能列+キーだけにする(メイン縦の下半分。D25。2026-11-19)
 */
@Composable
fun BerryBody(
    modifier: Modifier,
    touchesWindowBottom: Boolean,
    home: HomeController,
    cover: Boolean,
    statusIndicators: Boolean = true,
    sheets: Boolean = true,
    keyboardOnly: Boolean = false,
) {
    val density = LocalDensity.current
    // ステータスバーは MainActivity で隠しているので上は空けない(自前のステータス行を出す。§4.1)。
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomMargin = if (touchesWindowBottom) maxOf(BodyLayout.MIN_BOTTOM_MARGIN_DP, navBottom.value) else 8f

    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    val settings = home.settings
    val picking = home.pickingApp

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    // 割り当てアプリを起動する(キー・機能列・カメラの枠。§5.2 / §4.3)。未割り当てなら何もしない。
    fun launch(ref: AppRef?) {
        if (ref != null && !app.catalog.launch(ref)) toast(resources.getString(R.string.launch_failed))
    }

    fun assignPicked(keyId: String, keyName: String, entry: AppEntry) = assignPickedApp(context, home, keyId, keyName, entry)

    CompositionLocalProvider(*assignedIconLocals(assignments.appearance), LocalHapticsEnabled provides assignments.feedback.haptics) {
        BoxWithConstraints(modifier.background(BodyColor)) {
            // 幅は与えられた領域いっぱいに使う(メイン縦向きの下半分でも左右を空けない。オーナー指定)。
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value
            val b = computeBodyLayout(widthDp, heightDp, bottomMargin)
            // メイン縦の下半分はキーボードだけ(D25)。画面部の分をキーに回す。
            val k = if (keyboardOnly) computeKeyboardLayout(heightDp, bottomMargin, KeyboardDockHeight.value) else null
            val functionRowHeight = k?.functionRow ?: b.functionRow
            val keyRowHeight = k?.keyRow ?: b.keyRow
            val total = k?.total ?: b.total
            // 設定・割り当てシートを開いている間は後ろの本体をぼかす(表示ギミック 5)。シートは同じ Box の上に重ねるのでぼけない。
            // シートを別の場所(メイン縦の上半分)に出すときは、こちらはシートの後ろではないのでぼかさない。
            val backdropBlur = rememberBackdropBlur(sheets && (home.editing != null || settings.showing))
            // 下の行(§4.3)。カバーはカレンダーの下、メインは次の予定・音楽の下に置く。家の動きだけが違う。
            val cameraRef = assignments.keys[KeyLayout.DOCK_CAMERA_ID]
            val cameraName = resources.getString(R.string.dock_camera_name)
            @Composable
            fun Dock(homeActive: Boolean, homeDescription: String, onHome: () -> Unit) = QuickDock(
                homeActive = homeActive,
                homeDescription = homeDescription,
                onHome = onHome,
                cameraAssigned = cameraRef != null,
                cameraIcon = rememberAssignedIcon(app.catalog, cameraRef),
                // カメラの枠: 選択待ちなら割り当て、割り当て済みなら起動、未割り当てなら端末のカメラ。
                onCameraTap = {
                    val pickingNow = picking
                    when {
                        pickingNow != null -> assignPicked(KeyLayout.DOCK_CAMERA_ID, cameraName, pickingNow)
                        cameraRef != null -> launch(cameraRef)
                        !openCamera(context) -> toast(resources.getString(R.string.camera_failed))
                    }
                },
                onCameraLongPress = {
                    val pickingNow = picking
                    if (pickingNow != null) assignPicked(KeyLayout.DOCK_CAMERA_ID, cameraName, pickingNow) else home.startEdit(EditTarget.DockCamera)
                },
            )
            Column(Modifier.fillMaxWidth().fillMaxHeight().graphicsLayer { applyBackdropBlur(backdropBlur) }) {
                // メイン縦: 下の行をキーの上に置く(親指の届く位置。家はホームに戻る=メインと同じ)。
                if (keyboardOnly) Box(Modifier.fillMaxWidth().height(KeyboardDockHeight), contentAlignment = Alignment.Center) {
                    Dock(home.mainCalendarMonth == null, stringResource(R.string.quick_home)) { home.goHome() }
                } else ScreenPanel(
                    height = b.panel.dp,
                    statusHeight = b.status.dp,
                    background = assignments.background,
                    dateStyle = assignments.clock.style,
                    hourFormat = assignments.clock.hours,
                    coverArea = if (!cover) {
                        null
                    } else {
                        { today, areaModifier ->
                            CoverTileArea(
                                today,
                                modifier = areaModifier,
                                dock = { isThisMonth, goThisMonth ->
                                    Dock(isThisMonth, stringResource(R.string.quick_this_month), goThisMonth)
                                },
                                // カバーのカレンダーの場所も見出しの下払いで Hub と入れ替わる(§4.3.1。v0.7.3。メインとは別の保存)。
                                kind = assignments.hub.cover,
                                onFlip = { app.store.update { it.copy(hub = it.hub.copy(coverPanel = it.hub.cover.flipped().id)) } },
                                hub = { header, hubModifier ->
                                    HubPanel(
                                        preview = assignments.hub.previewMode,
                                        hourFormat = assignments.clock.hours,
                                        picking = picking != null,
                                        modifier = hubModifier,
                                        headerModifier = header,
                                    )
                                },
                            )
                        }
                    },
                    // メインは次の予定・音楽の下に同じ下の行。家はホームに戻る(終話キーと同じ。カレンダーも当月へ。2026-11-17 オーナー決定)。
                    // 家の色はカバーと同じく、カレンダーが当月以外のときは薄い白。
                    mainDock = if (cover) null else {
                        { Dock(home.mainCalendarMonth == null, stringResource(R.string.quick_home)) { home.goHome() } }
                    },
                    statusIndicators = statusIndicators,
                )
                FunctionRow(
                    functionRowHeight.dp,
                    // 機能列 5 キー(§5.3)。送話・戻る・終話は、選択待ちなら割り当て、割り当て済みなら起動、
                    // 未割り当てなら固定機能(2026-11-17 オーナー決定)。
                    onKeyTap = { key ->
                        val id = key.assignId
                        val pickingNow = picking
                        val assigned = id?.let { assignments.keys[it] }
                        when {
                            id != null && pickingNow != null -> assignPicked(id, resources.functionKeyName(key), pickingNow)
                            assigned != null -> launch(assigned)
                            else -> when (key) {
                                FunctionKey.SEND -> if (!openDialer(context)) toast(resources.getString(R.string.dialer_failed))
                                FunctionKey.MENU -> home.openSettings()
                                FunctionKey.TRACKPAD -> home.trackpad()
                                FunctionKey.BACK -> home.back()
                                FunctionKey.END -> home.goHome()
                            }
                        }
                    },
                    onKeyLongPress = { key ->
                        val id = key.assignId
                        val pickingNow = picking
                        if (id != null) {
                            if (pickingNow != null) assignPicked(id, resources.functionKeyName(key), pickingNow) else home.startEdit(EditTarget.Function(key))
                        }
                    },
                )
                // Shift 面(D28): 左 Shift のタップで切り替え。2 面目の間は英字だけが押せ、もう一度左 Shift を押すまで 2 面目のまま。
                val shiftOn = home.shiftOn
                KeyRows(
                    keyRowHeight = keyRowHeight.dp,
                    fretHeight = b.fret.dp,
                    iconFor = { spec ->
                        val id = if (shiftOn && KeyLayout.isShiftable(spec)) KeyLayout.shiftId(spec) else spec.id
                        rememberAssignedIcon(app.catalog, assignments.keys[id])
                    },
                    onTap = { spec ->
                        when {
                            spec.id == KeyLayout.SHIFT_KEY_ID -> home.shiftOn = !home.shiftOn
                            home.shiftOn -> if (KeyLayout.isShiftable(spec)) {
                                val id = KeyLayout.shiftId(spec)
                                if (picking != null) assignPicked(id, resources.keyName(spec, shifted = true), picking) else launch(assignments.keys[id])
                            }
                            picking != null -> assignPicked(spec.id, resources.keyName(spec), picking)
                            else -> launch(assignments.keys[spec.id])
                        }
                    },
                    // 長押し: 割り当て編集(§7)。選択待ちの間はタップと同じく、そのキーに割り当てる。左 Shift は割り当てできないので何もしない。
                    onLongPress = { spec ->
                        when {
                            spec.id == KeyLayout.SHIFT_KEY_ID -> Unit
                            home.shiftOn -> if (KeyLayout.isShiftable(spec)) {
                                if (picking != null) assignPicked(KeyLayout.shiftId(spec), resources.keyName(spec, shifted = true), picking)
                                else home.startEdit(EditTarget.Key(spec, shifted = true))
                            }
                            picking != null -> assignPicked(spec.id, resources.keyName(spec), picking)
                            else -> home.startEdit(EditTarget.Key(spec))
                        }
                    },
                    highlightKey = if (shiftOn) KeyLayout.SHIFT_KEY_ID else null,
                    dimmed = { shiftOn && !KeyLayout.isShiftable(it) && it.id != KeyLayout.SHIFT_KEY_ID },
                )
                Spacer(Modifier.height(b.bottomMargin.dp))
            }
            // シート類(選択待ちの帯・割り当て/フォルダ・設定)。メイン縦は上半分の画面に出すので、ここでは出さない(D25)。
            if (sheets) HomeSheets(home, topInset = b.status.dp)
            else if (home.editing != null || settings.showing) {
                // メイン縦で上半分にシートが開いている間は、下半分のキーを押せないようにする(シートを開いたままアプリが起動したり、
                // トラックパッドの一覧がシートの下に開いたりしないように。カバー・メイン横はシートが本体の上に重なるので不要)。
                // 触れたら戻る 1 回(シートの外側タップで閉じるのと同じ)。選択待ちの帯はキーを押させたいので対象外。
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight().background(Color(0x66000000))
                        .clickable(interactionSource = null, indication = null) { home.back() },
                )
            }
            if (total > heightDp + 0.5f) {
                // 狭すぎて収まらない(§3.2 の下限 40dp を優先した結果)。
                Label(stringResource(R.string.height_short, fmt(total), fmt(heightDp)), Modifier.align(Alignment.BottomCenter), Color(0xFFF0A640))
            }
        }
    }
}

/** カメラの枠が未割り当てのとき: 端末のカメラ(静止画)を開く(§4.3。カメラの権限は使わない)。開けたら true。 */
private fun openCamera(context: Context): Boolean = try {
    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/** 送話キー: 端末の電話アプリのダイヤル画面を開く(§5.3。発信権限は使わない)。開けたら true。 */
private fun openDialer(context: Context): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

@Composable
internal fun Label(text: String, modifier: Modifier = Modifier, color: Color = Color(0x99FFFFFF)) {
    BasicText(text, modifier, style = TextStyle(color = color, fontSize = 12.sp))
}

internal fun fmt(v: Float): String = String.format(Locale.ROOT, "%.1f", v)
