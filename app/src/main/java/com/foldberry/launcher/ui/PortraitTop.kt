package com.foldberry.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.layout.BodyLayout
import com.foldberry.launcher.ui.keyboard.LocalHapticsEnabled
import com.foldberry.launcher.ui.screen.ClockLine
import com.foldberry.launcher.ui.screen.ScreenBackground
import com.foldberry.launcher.ui.screen.rememberNow

/** メイン縦の上半分の 1 段の時計の高さ(モック portrait-mock.html の案 A)。 */
private val PortraitClockHeight = 64.dp

/**
 * メイン縦の上半分=画面(D25。2026-11-19 オーナー決定。モック docs/mock/portrait-mock.html の案 A)。
 * 上から ステータス行(画面の上端)/ 1 段の時計 / カレンダー(または Hub)4×2+自由枠 8+音楽(D26。MainHomePanel)。
 * 背景は画面部と同じ壁紙+暗い膜。アプリ一覧・フォルダはステータス行の下に重ね(ステータス行は見えたまま)、
 * シート類(選択待ちの帯・割り当て/フォルダ・設定)もこの上半分に出す。端末のキーボードは下半分(キー)を覆うだけになる。
 * 下半分は BerryBody(keyboardOnly)。
 */
@Composable
internal fun PortraitTop(home: HomeController, modifier: Modifier) {
    val app = LocalContext.current.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    val now by rememberNow()
    val status = BodyLayout.STATUS_DP.dp
    val iconFilter = if (assignments.appearance.grayscaleIcons) AssignedIconFilter else null
    CompositionLocalProvider(
        LocalAssignedIconFilter provides iconFilter,
        LocalHapticsEnabled provides assignments.feedback.haptics,
        // 上半分のシートは IME・ナビバーの分を空けない(どちらも下半分にしか掛からない)。
        LocalSheetWindowInsets provides false,
    ) {
        Box(modifier) {
            // シートを開いている間は後ろ(画面と重ねた一覧)をぼかす(本体部のシートと同じ表示ギミック 5)。
            val backdropBlur = rememberBackdropBlur(home.editing != null || home.settings.showing)
            Box(Modifier.fillMaxSize().graphicsLayer { applyBackdropBlur(backdropBlur) }) {
                ScreenBackground(assignments.background, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = assignments.background.overlay / 100f)))
                Column(Modifier.fillMaxSize()) {
                    // ステータス行が画面の上端に来るので、上端からの下払い 1 回で通知パネルが開く(§4.1)。
                    StatusRow(Modifier.fillMaxWidth().height(status))
                    ClockLine(now, PortraitClockHeight, assignments.clock.style, assignments.clock.hours)
                    MainHomePanel(home, bottomInset = false, modifier = Modifier.fillMaxWidth().weight(1f), portraitScreen = true)
                }
                // トラックパッドの一覧・自由枠のフォルダはステータス行の下に重ねる(下からスライド+フェード。途中の触れは受け止める)。
                Box(Modifier.fillMaxSize().padding(top = status)) {
                    AnimatedPanel(if (home.mainListOpen) Unit else null) {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                        PanelSlide(Modifier.fillMaxSize()) {
                            AppListPanel(
                                home, Modifier.fillMaxSize().pointerInput(Unit) {},
                                bottomInset = false, onClose = { home.mainListOpen = false },
                            )
                        }
                    }
                    AnimatedPanel(home.openFolder) { index ->
                        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                        PanelSlide(Modifier.fillMaxSize()) {
                            FolderPanel(home, index, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            HomeSheets(home, topInset = status)
            // 音声操作のカード(§5.5)。メイン縦は上半分全体を暗くして中央に出す(下半分のキーボードの暗幕は BerryBody)。
            VoicePanel(home.voice, Modifier.fillMaxSize())
        }
    }
}
