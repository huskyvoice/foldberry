package com.foldberry.launcher.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.foldberry.launcher.layout.BodyLayout
import com.foldberry.launcher.layout.Hinge
import com.foldberry.launcher.layout.HomeLayout
import com.foldberry.launcher.layout.PxRect
import com.foldberry.launcher.layout.Split
import com.foldberry.launcher.layout.decideHomeLayout
import kotlinx.coroutines.flow.Flow

const val LOG_TAG = "FoldBerry"

/**
 * ホームの最上位。ヒンジ情報でカバー/メイン構成を出し分ける(§3.1)。
 * ルートはウィンドウ全面(edge-to-edge)なので、ルート座標 = ヒンジ情報のウィンドウ座標。
 */
@Composable
fun HomeScreen(homeEvents: Flow<Unit>) {
    val activity = LocalActivity.current ?: return
    val layoutInfoFlow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    // 初期値 null = ヒンジ情報がまだ届いていない。
    val layoutInfo: WindowLayoutInfo? by layoutInfoFlow.collectAsState(initial = null)
    // 開いているパネル(設定・割り当て・一覧など)は、開閉で作り直されないここで持つ(HomeController の説明参照)。
    val home = rememberHomeController()

    // ホームジェスチャー(FoldBerry 表示中)は終話と同じ(§5.3)。MainActivity.onNewIntent から届く。
    LaunchedEffect(homeEvents) { homeEvents.collect { home.goHome() } }
    // 端末の戻る = 戻るキー。何も開いていなくても常に受けて何もしない(§5.3)。
    // システムに任せると MainActivity が finish し、OS がホームを作り直して再表示のように見えるため(v0.6.1 で実機確認)。
    BackHandler { home.back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val info = layoutInfo
        // 届く前・初回レイアウト前は描かない。間違った構成が一瞬出るのを防ぐ。
        if (info == null || constraints.maxWidth <= 0 || constraints.maxHeight <= 0) return@BoxWithConstraints

        val hinge = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let {
            Hinge(
                bounds = PxRect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom),
                isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
            )
        }
        val layout = decideHomeLayout(constraints.maxWidth, constraints.maxHeight, hinge)

        LaunchedEffect(layout) {
            // 実機での判定確認用(scripts/fb.sh logs)。
            Log.i(LOG_TAG, "layout=$layout window=${constraints.maxWidth}x${constraints.maxHeight}px hinge=$hinge")
        }
        // カバー⇔メインの切り替えでホーム初期状態に戻す(§3.1)。回転(メイン内の分割の向き)では閉じない。
        val isCover = layout is HomeLayout.Cover
        SideEffect { home.isCover = isCover }
        LaunchedEffect(isCover) { home.onLayoutChanged() }

        when (layout) {
            HomeLayout.Cover -> Box(Modifier.fillMaxSize()) {
                BerryBody(Modifier.fillMaxSize(), touchesWindowBottom = true, home = home, cover = true)
                // トラックパッドで全画面の一覧を重ねる(§5.3)。下の本体に触れが抜けないよう受け止める。
                // 開閉は下からスライド+フェード(表示ギミック 2026-11-17)。
                AnimatedPanel(if (home.overlayOpen) Unit else null) {
                    // 滑り込む途中は上側がまだ覆われていないので、全面で触れを受け止めて下のタイルに抜けないようにする。
                    Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                    PanelSlide(Modifier.fillMaxSize()) {
                        AppListPanel(
                            home, Modifier.fillMaxSize().pointerInput(Unit) {},
                            bottomInset = true, onClose = { home.overlayOpen = false },
                        )
                    }
                }
            }
            // メイン縦: 上半分=画面(ステータス行・時計・カレンダー/Hub・自由枠・音楽。一覧・フォルダ・シートもここ)、
            // 下半分=キーボード(下の行+機能列+キー)。D25(2026-11-19 オーナー決定。モック portrait-mock.html の案 A)。
            is HomeLayout.Main if layout.split == Split.TopBottom -> {
                Pane(layout.appList) {
                    PortraitTop(home, Modifier.fillMaxSize())
                }
                Pane(layout.body) {
                    BerryBody(
                        Modifier.fillMaxSize(), touchesWindowBottom = layout.body.bottom == constraints.maxHeight, home = home, cover = false,
                        sheets = false, keyboardOnly = true,
                    )
                }
            }
            is HomeLayout.Main -> {
                val landscape = layout.split == Split.LeftRight
                Pane(layout.body) {
                    BerryBody(
                        Modifier.fillMaxSize(), touchesWindowBottom = layout.body.bottom == constraints.maxHeight, home = home, cover = false,
                        // 横向きはステータス行のアイコン群を右の枠の上端へ移す(下の帯。2026-11-18 オーナー決定)。
                        statusIndicators = !landscape,
                    )
                }
                Pane(layout.appList) {
                    val listBottom = layout.appList.bottom == constraints.maxHeight
                    // 一覧を重ねている間は下の帯+カレンダー+自由枠をぼかす(一覧の下地が半透明のため)。
                    val listBlur = rememberBackdropBlur(home.mainListOpen)
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().graphicsLayer { applyBackdropBlur(listBlur) }) {
                            // 横向きは右の枠の上端にもステータス行の帯を置き、本体側(ロゴ)と合わせて画面全体で 1 本に見せる。
                            // 帯はヒンジで分かれるので折り目をまたがない(§3.3)。右上でもタップ・下スワイプで通知パネルが開く(§4.1)。
                            // 帯は枠の外(上)に置く: 重ねるとカレンダーの見出しと「›」にかぶる。帯の分だけ枠が低くなり、
                            // 実機の横は 4×4 になるので、自由枠は縦横とも 8 個にした(2026-11-18 オーナー決定)。
                            if (landscape) {
                                StatusRow(
                                    Modifier.fillMaxWidth().height(BodyLayout.STATUS_DP.dp).background(ListBackground),
                                    logo = false,
                                )
                            }
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                // 普段はカレンダー+自由枠。トラックパッドでアプリ一覧を重ねる(§4.5。2026-11-17 オーナー決定)。
                                MainHomePanel(home, bottomInset = listBottom, modifier = Modifier.fillMaxSize())
                            }
                        }
                        // 自由枠のフォルダを開いたとき(§4.5。v0.7.1)。暗幕は帯も含めて右の枠全体に掛け、
                        // カードは帯の下の枠の中央に出す(2026-11-24 オーナー決定)。下から滑り出す。
                        AnimatedPanel(home.openFolder) { index ->
                            // 滑り込む途中のまだ覆われていない場所の触れを受け止める(下の自由枠に抜けないように)。
                            Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                            PanelSlide(Modifier.fillMaxSize()) {
                                FolderPanel(
                                    home, index, Modifier.fillMaxSize(),
                                    topInset = if (landscape) BodyLayout.STATUS_DP.dp else 0.dp,
                                )
                            }
                        }
                        // アプリ一覧は帯も含めて右の枠全体に重ねる(2026-11-24 オーナー決定)。
                        // 重ね方はカバーの重ね表示と同じ(下からスライド+フェード、途中の触れは受け止める)。
                        AnimatedPanel(if (home.mainListOpen) Unit else null) {
                            Box(Modifier.fillMaxSize().pointerInput(Unit) {})
                            PanelSlide(Modifier.fillMaxSize()) {
                                AppListPanel(
                                    home, Modifier.fillMaxSize().pointerInput(Unit) {},
                                    bottomInset = listBottom, onClose = { home.mainListOpen = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ウィンドウ座標(px)の矩形にそのまま置く。 */
@Composable
private fun Pane(rect: PxRect, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(rect.left, rect.top) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() }),
    ) { content() }
}
