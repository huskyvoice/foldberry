package com.foldberry.launcher.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.data.BackgroundPhoto
import com.foldberry.launcher.data.BackgroundPreset
import com.foldberry.launcher.data.BackgroundSetting
import com.foldberry.launcher.data.DateStyle
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.ui.StatusRow
import java.time.LocalDate

/**
 * 画面部(仕様書 §4)。背景(§4.4)+暗めのオーバーレイ+ステータス行(§4.1)+大時計(§4.2)+タイル部(§4.3)。
 * タイル部は、メイン画面(横)では 上=次の予定 2 件・下=音楽(T1・T2 の段。D26。2026-11-19 オーナー決定)+[mainDock](下の行)、
 * カバー画面では [coverArea](月のカレンダー+下の行)。(旧: タイル T1・T2 にアプリを割り当てて起動)
 *
 * @param height ステータス行を含む画面部の高さ
 * @param coverArea カバー画面のタイル部(今日を受け取る)。null なら次の予定と音楽を出す
 * @param mainDock メイン画面で音楽の下に置く下の行(null なら置かない)
 * @param statusIndicators ステータス行の右のアイコン群を出すか(メイン横向きでは右の枠の上端へ移すので false。2026-11-18)
 * @param clockNotifications 時計の日付の右端に通知数を出すか(設定の通知の知らせ方。2026-11-28)
 */
@Composable
fun ScreenPanel(
    height: Dp,
    statusHeight: Dp,
    background: BackgroundSetting,
    dateStyle: DateStyle,
    hourFormat: HourFormat,
    coverArea: (@Composable (today: LocalDate, modifier: Modifier) -> Unit)?,
    mainDock: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    statusIndicators: Boolean = true,
    clockNotifications: Boolean = true,
) {
    // 時計とカバーのカレンダーで「今」を共有する(日付の変わり目も同時に切り替わる)。
    val now by rememberNow()
    Box(modifier.fillMaxWidth().height(height)) {
        ScreenBackground(background, Modifier.fillMaxWidth().height(height))
        // 時計とタイルを読みやすくする暗い膜(§4.4)。
        Box(Modifier.fillMaxWidth().height(height).background(Color.Black.copy(alpha = background.overlay / 100f)))
        Column(Modifier.fillMaxWidth().height(height)) {
            // カバー(coverArea あり)には音楽のタイルが無いので、再生中をステータス行の音符で知らせる(2026-11-26)。
            StatusRow(Modifier.fillMaxWidth().height(statusHeight), indicators = statusIndicators, musicPlaying = coverArea != null)
            // 時計の高さは画面部に合わせて伸縮(メイン縦向きの低い画面部でも収まるように)。
            val clockHeight = ((height - statusHeight) * 0.3f).coerceIn(statusHeight * 1.6f, statusHeight * 4.6f)
            ClockBlock(now, clockHeight, dateStyle, hourFormat, showNotifications = clockNotifications)
            if (coverArea != null) {
                coverArea(now.toLocalDate(), Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    // 上に次の予定(見出し+2 件)、下に音楽(横長)。どちらも幅いっぱい(モック tiles-mock.html の横)。
                    Column(Modifier.weight(1f).fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
                        NextEventsCard(now, hourFormat, Modifier.fillMaxWidth().height(EventsCardHeight))
                        Spacer(Modifier.height(8.dp))
                        MusicCard(wide = true, modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                    mainDock?.invoke()
                }
            }
        }
    }
}

/** 横の次の予定の札の高さ(見出し+2 件。モックの 98dp)。 */
private val EventsCardHeight = 92.dp

/** 背景。写真が選ばれていても、ファイルが無い・読めないときは既定のプリセットで描く。メイン縦の上半分(PortraitTop)とも共有。 */
@Composable
internal fun ScreenBackground(setting: BackgroundSetting, modifier: Modifier) {
    if (setting.isPhoto) {
        val context = LocalContext.current
        val revision by BackgroundPhoto.revision.collectAsState()
        // 読み込み済みならそれを初期値にして、作り直し(開閉など)のたびにプリセットが一瞬見えるのを防ぐ。
        val photo by produceState(BackgroundPhoto.cached(revision), revision) { value = BackgroundPhoto.load(context) }
        val p = photo
        if (p != null) {
            Image(p, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
            return
        }
        PresetBackground(BackgroundPreset.DEFAULT, modifier)
    } else {
        PresetBackground(BackgroundPreset.resolve(setting.preset), modifier)
    }
}
