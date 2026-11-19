package com.foldberry.launcher.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.CalendarEvents
import com.foldberry.launcher.ui.rememberCalendarEvents
import com.foldberry.launcher.data.CalendarDay
import com.foldberry.launcher.data.JapaneseHolidays
import com.foldberry.launcher.data.MainPanelKind
import com.foldberry.launcher.data.MonthCalendar
import com.foldberry.launcher.ui.LineIcon
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val CardShape = RoundedCornerShape(6.dp)
private val DayColor = Color(0xE0FFFFFF)
private val HolidayColor = Color(0xFFFF8A8A)
private val SaturdayColor = Color(0xFF8AB8FF)
private val TodayLine = SheetAccent

/** 見出し・曜日・余白の高さ(行の高さの計算に使う)。 */
private val HeaderHeight = 22.dp
private val WeekdayHeight = 15.dp
private val CardPadding = 6.dp
// カバーは行の高さを 26dp まで取り、文字は 22dp の行の大きさに据え置いて行間を広げる(2026-11-17 オーナー要望)。
private val MaxRow = 26.dp
private val MaxTextRow = 22.dp
private val MinRow = 14.dp

/**
 * カバー画面のタイル部(§4.3。2026-11-17 オーナー決定。モック docs/mock/calendar-mock.html)。
 * 月のカレンダーを、下の行(QuickDock)の上の残りに上下中央で置く。
 *
 * @param today 今日(時計と同じ更新。日付が変わると当月表示に戻る)
 */
@Composable
fun CoverTileArea(
    today: LocalDate,
    dock: @Composable (isThisMonth: Boolean, goThisMonth: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    kind: MainPanelKind = MainPanelKind.CALENDAR,
    onFlip: () -> Unit = {},
    hub: @Composable (header: Modifier, modifier: Modifier) -> Unit = { _, _ -> },
) {
    val thisMonth = YearMonth.from(today)
    var shown by rememberShownMonth(today)
    // カレンダーの場所は、見出しを下へ払うとフリップして Hub と入れ替わる(§4.3.1。v0.7.3。メインとは別に覚える)。
    Column(modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
        FlipPanel(kind, onFlip, Modifier.weight(1f).fillMaxWidth()) { face, header ->
            when (face) {
                MainPanelKind.CALENDAR -> FittedMonthCalendar(shown, today, { shown = it }, Modifier.fillMaxSize(), headerModifier = header)
                MainPanelKind.HUB -> hub(header, Modifier.fillMaxSize().padding(bottom = 8.dp))
            }
        }
        // Hub のときは月を動かせないので、家は当月表示の扱い(白)のまま。
        dock(kind == MainPanelKind.HUB || shown == thisMonth) { shown = thisMonth }
    }
}

/** 表示中の月。今日の月が変わったら(月をまたいだら)当月に戻す。開閉で作り直されたときも当月から。 */
@Composable
internal fun rememberShownMonth(today: LocalDate): MutableState<YearMonth> {
    val thisMonth = YearMonth.from(today)
    return remember(thisMonth) { mutableStateOf(thisMonth) }
}

/**
 * 月のカレンダーを [modifier] の枠に収めて上下中央に置く。カバーのタイル部とメインの一覧の枠(§4.5)で共用。
 * 行の高さ: 6 週ぶんが枠に収まるよう、[maxRow] を上限に縮める(余りは上下に振り分けて中央に寄せる)。
 * 日付の文字は行の高さに合わせるが、[maxTextRow] の行より大きくはしない(それ以上は行間として空ける)。
 */
@Composable
internal fun FittedMonthCalendar(
    month: YearMonth,
    today: LocalDate,
    onMonth: (YearMonth) -> Unit,
    modifier: Modifier,
    maxRow: Dp = MaxRow,
    maxTextRow: Dp = MaxTextRow,
    /** 見出しの行(‹ 月名 ›)に付ける受け口(下払いのフリップ。PanelFlip)。 */
    headerModifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val rowHeight = ((maxHeight - HeaderHeight - WeekdayHeight - CardPadding) / 6).coerceIn(MinRow, maxRow)
        MonthCalendarCard(
            month = month,
            today = today,
            rowHeight = rowHeight,
            textRow = rowHeight.coerceAtMost(maxTextRow),
            onPrev = { onMonth(month.minusMonths(1)) },
            onNext = { onMonth(month.plusMonths(1)) },
            headerModifier = headerModifier,
        )
    }
}

@Composable
private fun MonthCalendarCard(
    month: YearMonth,
    today: LocalDate,
    rowHeight: Dp,
    textRow: Dp,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val language = rememberClockLanguage()
    // 文字の大きさは dp から決める(時計と同じく、端末の文字サイズ設定で崩れないように)。
    val daySize = with(density) { (textRow * 0.64f).toSp() }
    val smallSize = with(density) { 11.5.dp.toSp() }
    val titleSize = with(density) { 15.dp.toSp() }
    val cells = remember(month) { MonthCalendar.cells(month.year, month.monthValue) }
    // 予定のある日の点(D26)。表示中の 6 週分(前後の月のはみ出しも含む)を読む。未許可なら点なし。
    val events by rememberCalendarEvents(cells.first().date, cells.last().date.plusDays(1))
    val eventDays = remember(events, cells) { events?.let { CalendarEvents.days(it, cells.first().date, cells.last().date) }.orEmpty() }
    val slide = rememberMonthSlide(onPrev, onNext)
    // 見出しの月名と日付は指の向きに動かす。端に近づくほど薄く。
    val moving = Modifier.graphicsLayer {
        translationX = slide.offset.value
        alpha = 1f - slide.progress() * 0.7f
    }
    // 曜日と ‹ › は日付と逆向きに動かす(2026-11-17 オーナー要望。旧: 止めたまま)。日付が左へ出るときは右へ出て、
    // 次の月では日付と反対側(左)から入る。‹ › は端にあるので小さく動かして薄くするだけ(押せる位置から大きく外さない)。
    val counter = Modifier.graphicsLayer {
        translationX = -slide.offset.value
        alpha = 1f - slide.progress() * 0.8f
    }
    val counterArrow = Modifier.graphicsLayer {
        translationX = -slide.offset.value * 0.25f
        alpha = 1f - slide.progress()
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0x9E12141A), CardShape)
            .border(1.dp, Color(0x14FFFFFF), CardShape)
            .clip(CardShape)
            .onSizeChanged { slide.width = it.width.toFloat() }
            .then(slide.gesture)
            .padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(HeaderHeight).then(headerModifier), verticalAlignment = Alignment.CenterVertically) {
            NavArrow(FoldBerryIcons.Line.Back, stringResource(R.string.calendar_prev), counterArrow) { slide.go(-1) }
            Box(Modifier.weight(1f).then(moving), contentAlignment = Alignment.Center) {
                BasicText(MonthCalendar.title(month.year, month.monthValue, language), style = TextStyle(color = Color(0xEBFFFFFF), fontSize = titleSize))
            }
            NavArrow(FoldBerryIcons.Line.Chevron, stringResource(R.string.calendar_next), counterArrow) { slide.go(1) }
        }
        Row(Modifier.fillMaxWidth().height(WeekdayHeight).then(counter), verticalAlignment = Alignment.CenterVertically) {
            MonthCalendar.weekdayLabels(language).forEachIndexed { i, label ->
                val color = when (i) {
                    0 -> HolidayColor
                    6 -> SaturdayColor
                    else -> Color(0x80FFFFFF)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    BasicText(label, style = TextStyle(color = color, fontSize = smallSize, fontWeight = FontWeight.Medium))
                }
            }
        }
        Column(moving) {
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                    week.forEach { day -> DayCell(day, today, daySize, day.date in eventDays, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

/**
 * 月送りの動き(2026-11-17 オーナー要望)。左右のスワイプでは日付が指に付いて動き、離したときにしきい値を超えていれば
 * そのまま押し出して次の月(左へ払う)/前の月(右へ払う)を反対側から滑り込ませる。超えなければ元の位置へ戻す。
 * ‹ › も同じ動きで送る。1 回で 1 か月だけ動かし、動いている間の操作は受け付けない(取りこぼしで 2 か月飛ばないように)。
 */
@Stable
private class MonthSlide(private val scope: CoroutineScope) {
    val offset = Animatable(0f)
    var width = 0f
    var onPrev: () -> Unit = {}
    var onNext: () -> Unit = {}
    private var busy = false

    /** 動きの進み具合(0=止まっている、1=幅いっぱいずれた)。薄くする量に使う。 */
    fun progress(): Float = (abs(offset.value) / width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    /** [dir] = 1 で次の月、-1 で前の月。 */
    fun go(dir: Int) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val w = width.coerceAtLeast(1f)
                offset.animateTo(-dir * w, tween(OUT_MS, easing = FastOutLinearInEasing))
                if (dir > 0) onNext() else onPrev()
                // 月の切り替えと同じフレームで反対側へ置き、滑り込ませる。
                offset.snapTo(dir * w)
                offset.animateTo(0f, tween(IN_MS, easing = LinearOutSlowInEasing))
            } finally {
                busy = false
            }
        }
    }

    val gesture: Modifier = Modifier.pointerInput(Unit) {
        val threshold = SwipeThreshold.toPx()
        detectHorizontalDragGestures(
            onDragEnd = {
                val x = offset.value
                when {
                    busy -> Unit
                    x <= -threshold -> go(1)
                    x >= threshold -> go(-1)
                    else -> scope.launch { offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                }
            },
            onDragCancel = { if (!busy) scope.launch { offset.animateTo(0f) } },
        ) { change, dx ->
            change.consume()
            if (!busy) scope.launch { offset.snapTo(offset.value + dx) }
        }
    }

    companion object {
        const val OUT_MS = 140
        const val IN_MS = 220
    }
}

@Composable
private fun rememberMonthSlide(onPrev: () -> Unit, onNext: () -> Unit): MonthSlide {
    val scope = rememberCoroutineScope()
    val slide = remember { MonthSlide(scope) }
    // pointerInput は作り直されないので、最新の月送りの処理を毎回入れ直す(PressFeedback と同じ考え方)。
    slide.onPrev = onPrev
    slide.onNext = onNext
    return slide
}

private val SwipeThreshold = 40.dp

/**
 * 日付 1 マス。日・祝=赤、土=青。前後の月と過ぎた日は薄く、今日は太字と水色の下線(丸で囲まない)。
 * 予定のある日は数字の下に黄色の点(今日は下線のさらに下。D26。2026-11-19 オーナー決定)。薄い日は点も同じだけ薄く。
 */
@Composable
private fun DayCell(day: CalendarDay, today: LocalDate, fontSize: TextUnit, hasEvent: Boolean, modifier: Modifier) {
    val date = day.date
    val isToday = date == today
    val base = when {
        date.dayOfWeek == DayOfWeek.SUNDAY || JapaneseHolidays.isHoliday(date) -> HolidayColor
        date.dayOfWeek == DayOfWeek.SATURDAY -> SaturdayColor
        isToday -> Color.White
        else -> DayColor
    }
    val dim = !day.inMonth || (!isToday && date.isBefore(today))
    val color = if (dim) base.copy(alpha = base.alpha * 0.3f) else base
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(
            date.dayOfMonth.toString(),
            modifier = Modifier.drawBehind {
                if (isToday) {
                    // 文字の下 4dp に 2dp の下線(文字幅より左右 3dp 長く)。
                    val y = size.height + 2.dp.toPx()
                    drawRoundRect(
                        TodayLine,
                        topLeft = Offset(-3.dp.toPx(), y),
                        size = Size(size.width + 6.dp.toPx(), 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx()),
                    )
                }
                if (hasEvent) {
                    val r = 2.dp.toPx()
                    val y = size.height + (if (isToday) 7.dp else 2.dp).toPx() + r
                    drawCircle(if (dim) EventDot.copy(alpha = 0.4f) else EventDot, r, Offset(size.width / 2f, y))
                }
            },
            style = TextStyle(color = color, fontSize = fontSize, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Light),
        )
    }
}

/** 予定のある日の点の色。 */
private val EventDot = Color(0xFFF0C24A)

@Composable
private fun NavArrow(icon: ImageVector, description: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .width(40.dp)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 設定の帯の ‹ と同じ線画アイコン(2026-11-18 にフォントの「‹ ›」から置き換え。アイコンの系統をそろえる)。
        LineIcon(icon, Color(0x80FFFFFF), 16.dp)
    }
}
