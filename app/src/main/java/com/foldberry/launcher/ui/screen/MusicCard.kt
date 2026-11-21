package com.foldberry.launcher.ui.screen

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.data.IconStyle
import com.foldberry.launcher.ui.LineIcon
import com.foldberry.launcher.ui.LocalAssignedIconFilter
import com.foldberry.launcher.ui.LocalAssignedIconLook
import com.foldberry.launcher.ui.MediaInfo
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.SheetFieldColor
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.openNotificationAccessSettings
import com.foldberry.launcher.ui.playLastMedia
import com.foldberry.launcher.ui.rememberAssignedIcon
import com.foldberry.launcher.ui.rememberMediaInfo
import kotlinx.coroutines.delay

/**
 * 音楽のタイル(T1。D26。2026-11-19 オーナー決定。モック docs/mock/tiles-mock.html)。再生中のメディアを出して操作する。
 * - [wide] = true(横の T1・T2 の段の下半分): アート・曲名とアーティスト・進み具合・前へ/再生/次へ を 1 列に。
 * - [wide] = false(縦の 2 マス): 上にアート・曲名・アーティスト、下に 前へ/再生/次へ(進み具合は省く)。
 * 何も再生していなければ最後に再生したアプリと「再生」(メディアの再生キーを送る)。通知へのアクセスが未許可なら案内(タップで許可画面)。
 * アート・曲名の部分のタップは再生中のアプリの画面を開く(セッションの sessionActivity。無ければアプリを起動)。
 */
@Composable
fun MusicCard(wide: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as FoldBerryApp
    val media by rememberMediaInfo()
    val catalogState by app.catalog.state.collectAsState()
    fun entryOf(pkg: String?): AppEntry? = pkg?.let { p -> (catalogState as? CatalogState.Loaded)?.apps?.firstOrNull { it.ref.packageName == p } }

    Box(modifier.widgetCard()) {
        when (val m = media) {
            MediaInfo.NotAllowed -> Row(
                Modifier.fillMaxSize().clickable { openNotificationAccessSettings(context) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIcon(FoldBerryIcons.Line.Music, Color(0x99FFFFFF), 22.dp)
                Spacer(Modifier.width(10.dp))
                BasicText(stringResource(R.string.music_not_allowed), style = TextStyle(color = Color(0x99FFFFFF), fontSize = 12.5.sp))
            }
            is MediaInfo.Idle -> {
                val last = entryOf(m.lastPackage)
                MusicLayout(
                    wide = wide,
                    art = null,
                    appEntry = last,
                    title = stringResource(R.string.music_idle),
                    subtitle = last?.let { stringResource(R.string.music_last, it.label) }.orEmpty(),
                    playing = false,
                    progress = null,
                    onOpen = { last?.let { app.catalog.launch(it.ref) } },
                    onPrev = null,
                    onPlayPause = { playLastMedia(context) },
                    onNext = null,
                )
            }
            is MediaInfo.Active -> {
                val entry = entryOf(m.packageName)
                // 再生中は 1 秒ごとに進み具合を描き直す(横だけ。縦は進み具合を出さない)。
                var tick by remember { mutableLongStateOf(0L) }
                if (wide && m.playing) LaunchedEffect(m) { while (true) { delay(1000); tick++ } }
                val progress = if (wide && m.durationMs > 0) { tick.let { m.positionNow().toFloat() / m.durationMs } } else null
                val resources = LocalResources.current
                MusicLayout(
                    wide = wide,
                    art = m.art,
                    appEntry = entry,
                    title = m.title.ifEmpty { resources.getString(R.string.music_untitled) },
                    subtitle = listOfNotNull(m.artist.takeIf { it.isNotEmpty() }, entry?.label).joinToString(" ・ "),
                    playing = m.playing,
                    progress = progress,
                    onOpen = {
                        val opened = m.controller.sessionActivity?.let { NotificationCounter.sendFromHome(context, it) } == true
                        if (!opened && (entry == null || !app.catalog.launch(entry.ref))) {
                            Toast.makeText(context, resources.getString(R.string.launch_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPrev = { m.controller.transportControls.skipToPrevious() },
                    onPlayPause = { if (m.playing) m.controller.transportControls.pause() else m.controller.transportControls.play() },
                    onNext = { m.controller.transportControls.skipToNext() },
                )
            }
        }
    }
}

@Composable
private fun MusicLayout(
    wide: Boolean,
    art: ImageBitmap?,
    appEntry: AppEntry?,
    title: String,
    subtitle: String,
    playing: Boolean,
    progress: Float?,
    onOpen: () -> Unit,
    onPrev: (() -> Unit)?,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val artSize = if (wide) 72.dp else 44.dp
    @Composable
    fun Info(modifier: Modifier) = Row(modifier.clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
        Artwork(art, appEntry, artSize)
        Spacer(Modifier.width(if (wide) 12.dp else 10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = if (wide) 15.sp else 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                BasicText(subtitle, style = TextStyle(color = Color(0xA6FFFFFF), fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x1FFFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(SheetAccent))
                }
            }
        }
    }
    @Composable
    fun Controls(modifier: Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        ControlButton(FoldBerryIcons.Media.Prev, stringResource(R.string.music_prev), onPrev)
        PlayButton(playing, if (wide) 40.dp else 34.dp, onPlayPause)
        ControlButton(FoldBerryIcons.Media.Next, stringResource(R.string.music_next), onNext)
    }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Info(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Controls(Modifier.width(140.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Info(Modifier.fillMaxWidth())
            Controls(Modifier.fillMaxWidth())
        }
    }
}

/**
 * アート。無ければアプリのアイコン、それも無ければ音符。
 * アプリのアイコンは割り当てアイコンの出し方(標準/白黒/絵記号)に従う(2026-11-21 オーナー指定。§5.2)。アートはそのまま。
 */
@Composable
private fun Artwork(art: ImageBitmap?, appEntry: AppEntry?, size: Dp) {
    val app = LocalContext.current.applicationContext as FoldBerryApp
    val shape = RoundedCornerShape(if (size > 50.dp) 8.dp else 6.dp)
    // 絵記号の絵は周りに余白を持って描かれている(AppIcon.kt の PICTOGRAM_INSET)ので、アプリのアイコン用の余白は足さない。
    val pictogram = LocalAssignedIconLook.current.style == IconStyle.PICTOGRAM
    val filter = LocalAssignedIconFilter.current
    Box(Modifier.size(size).clip(shape).background(SheetFieldColor, shape), contentAlignment = Alignment.Center) {
        val icon = rememberAssignedIcon(app.catalog, appEntry?.ref)
        when {
            art != null -> Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            icon != null -> Image(
                icon, contentDescription = null, colorFilter = filter,
                modifier = Modifier.fillMaxSize().then(if (pictogram) Modifier else Modifier.padding(size / 8)),
            )
            else -> LineIcon(FoldBerryIcons.Line.Music, Color(0x99FFFFFF), size / 2)
        }
        // アートがあるときは右下にアプリのアイコンを小さく重ねる(どのアプリで鳴っているか)。
        // 絵記号は白い線なので、明るいアートの上でも見えるよう暗い丸を敷く。
        if (art != null && icon != null) {
            Image(
                icon, contentDescription = null, colorFilter = filter,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(size / 3.2f)
                    .then(if (pictogram) Modifier.background(Color(0xB3000000), CircleShape) else Modifier),
            )
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick!!) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (enabled) Color(0xE6FFFFFF) else Color(0x33FFFFFF)))
    }
}

@Composable
private fun PlayButton(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val description = stringResource(if (playing) R.string.music_pause else R.string.music_play)
    Box(
        Modifier.size(size).clip(CircleShape).background(SheetAccent).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            if (playing) FoldBerryIcons.Media.Pause else FoldBerryIcons.Media.Play, contentDescription = null,
            modifier = Modifier.size(size * 0.5f), colorFilter = ColorFilter.tint(Color(0xFF06141B)),
        )
    }
}
