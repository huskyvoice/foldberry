package com.foldberry.launcher.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.HourFormat
import com.foldberry.launcher.data.HubItem
import com.foldberry.launcher.data.HubItems
import com.foldberry.launcher.data.HubPreview
import com.foldberry.launcher.data.NotificationCounter
import com.foldberry.launcher.data.NotificationKind
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.screen.rememberClockLanguage
import com.foldberry.launcher.ui.screen.rememberNow

private val CardShape = RoundedCornerShape(6.dp)
private val Accent = SheetAccent
private val Divider = Color(0x12FFFFFF)
private val KindColumnWidth = 46.dp
private val RowHeight = 44.dp
private val RowIconSize = 28.dp

/** 行を消すのに払う距離(行幅に対する割合)。 */
private const val DismissFraction = 0.35f

/**
 * Hub(§4.6。BB10 の BlackBerry Hub に倣う)。メインの一覧の枠の 4×2 マスに、設定でカレンダーの代わりに出す。
 * 左=種類の列(全・不在着信・メール・SMS・その他と件数。押すと絞り込み)、右=通知の一覧(新しい順。1 件 2 行)。
 * 件数と一覧は時計の横の件数と同じ通知から作る(NotificationCounter)。行のタップで通知を開く(contentIntent)。
 * 絞り込みは開閉で「全」に戻る。回転では作り直されないので残る(カレンダーの月と同じ扱い)。
 *
 * カバーのタイル部(§4.3.1)にも同じものを出す(v0.7.3)。アプリ名・アイコンはアプリ一覧からパッケージ名で引く。
 *
 * @param picking キーの選択待ち中か。その間は通知を開かない(割り当て先を選んでいる最中の誤操作を防ぐ)
 * @param headerModifier 見出しの行に付ける受け口(下払いでカレンダーへフリップ。PanelFlip)。未許可の案内では案内全体に付ける
 */
@Composable
fun HubPanel(
    preview: HubPreview,
    hourFormat: HourFormat,
    picking: Boolean,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val app = context.applicationContext as FoldBerryApp
    val now by rememberNow()
    val catalogState by app.catalog.state.collectAsState()
    // 通知はパッケージ名しか持たないので、パッケージで引く(起動画面が複数あれば最初の 1 つ)。
    val appsByPackage = remember(catalogState) {
        (catalogState as? CatalogState.Loaded)?.apps?.groupBy { it.ref.packageName }?.mapValues { it.value.first() }.orEmpty()
    }
    val hubItems by NotificationCounter.items.collectAsState()
    var filter by remember { mutableStateOf<NotificationKind?>(null) }
    val language = rememberClockLanguage()
    // 時刻の 12/24 は大時計と同じ設定に従う(端末に従うときは描くたびに読む。軽い)。
    val is24 = hourFormat.is24(DateFormat.is24HourFormat(context))

    fun open(item: HubItem) {
        if (picking) return
        if (NotificationCounter.open(context, item.key)) return
        // 開く手段の無い通知・取り消された通知は、そのアプリを起動する(通知パネルで押したときより一歩手前の動き)。
        val entry = appsByPackage[item.packageName]
        if (entry == null || !app.catalog.launch(entry.ref)) {
            Toast.makeText(context, resources.getString(R.string.hub_open_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // 通知を消す(行の横払い・見出しの「すべて消去」。§4.6。2026-11-19)。選択待ち中は何もしない(開くと同じ)。
    fun dismiss(keys: List<String>): Boolean {
        if (picking) return false
        val ok = NotificationCounter.dismiss(keys)
        if (!ok) Toast.makeText(context, resources.getString(R.string.hub_dismiss_failed), Toast.LENGTH_SHORT).show()
        return ok
    }

    Row(
        modifier
            .clip(CardShape)
            .background(Color(0x9E12141A), CardShape)
            .border(1.dp, Color(0x14FFFFFF), CardShape),
    ) {
        val all = hubItems
        if (all == null) {
            // 未許可・未接続: 案内だけ出し、押すと許可画面(§10)。
            Message(
                stringResource(R.string.hub_not_allowed),
                // 見出しが無いので、案内全体で下払いを受ける(カレンダーへ戻れるように)。
                Modifier.fillMaxSize().then(headerModifier).clickable { openNotificationAccessSettings(context) },
            )
            return@Row
        }
        val counts = HubItems.counts(all)
        KindColumn(
            selected = filter,
            countOf = { kind -> if (kind == null) counts.total else counts.count(kind) },
            onSelect = { filter = it },
        )
        Box(Modifier.fillMaxHeight().width(1.dp).background(Divider))
        val shown = HubItems.filter(all, filter)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(30.dp).then(headerModifier).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Hub", style = TextStyle(color = Color(0xEEFFFFFF), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
                if (shown.isNotEmpty()) {
                    BasicText(
                        shown.size.toString(),
                        Modifier.padding(start = 6.dp),
                        style = TextStyle(color = Accent, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
                Spacer(Modifier.weight(1f))
                filter?.let { BasicText(stringResource(kindNameRes(it)), style = TextStyle(color = Color(0x99FFFFFF), fontSize = 12.sp)) }
                // すべて消去: 見えている通知(絞り込み中はその種類だけ)を確認なしで消す(2026-11-19 オーナー決定)。
                // 見出しの行の下払い(フリップ)は外の枠で判定するので、タップだけのボタンとは取り合わない(カレンダーの ‹ › と同じ)。
                if (shown.isNotEmpty()) {
                    BasicText(
                        stringResource(R.string.hub_clear_all),
                        Modifier
                            .padding(start = 8.dp)
                            .clickable(enabled = !picking, role = Role.Button) { dismiss(shown.map { it.key }) }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        style = TextStyle(color = Accent, fontSize = 12.sp),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
            if (shown.isEmpty()) {
                Message(stringResource(R.string.hub_empty), Modifier.fillMaxSize())
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    // key は通知の key: 新しい通知が先頭に入っても、行が入れ替わってちらつかない。
                    items(shown, key = { it.key }) { item ->
                        val entry = appsByPackage[item.packageName]
                        HubRow(
                            item = item,
                            preview = preview,
                            appLabel = entry?.label ?: item.packageName,
                            entry = entry,
                            time = HubItems.timeLabel(item.postTime, now, is24, language),
                            onClick = { open(item) },
                            onDismiss = if (picking) null else ({ dismiss(listOf(item.key)) }),
                        )
                    }
                }
            }
        }
    }
}

/** 左の種類の列。null=すべて。件数は 0 なら出さない。押すとその種類に絞る(もう一度押すと全に戻す)。 */
@Composable
private fun KindColumn(selected: NotificationKind?, countOf: (NotificationKind?) -> Int, onSelect: (NotificationKind?) -> Unit) {
    Column(Modifier.width(KindColumnWidth).fillMaxHeight().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (listOf<NotificationKind?>(null) + NotificationKind.entries).forEach { kind ->
            val on = kind == selected
            val count = countOf(kind)
            val label = if (kind == null) stringResource(R.string.hub_kind_all) else stringResource(kindNameRes(kind))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(if (on) SheetAccent.copy(alpha = 0x1A / 255f) else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(if (on) null else kind) }
                    .semantics {
                        this.selected = on
                        contentDescription = if (count > 0) "$label $count" else label
                    },
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (on) Accent else Color(0x8CFFFFFF)
                if (kind == null) {
                    BasicText(
                        stringResource(R.string.hub_kind_all_short),
                        style = TextStyle(color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                    )
                } else {
                    Image(kindIcon(kind), contentDescription = null, modifier = Modifier.size(17.dp), colorFilter = ColorFilter.tint(tint))
                }
                if (count > 0) {
                    CountBadge(count, Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 4.dp), fontSize = 9.5.sp)
                }
                // 選んでいる種類は右端に縦線(一覧との境目に寄せる)。
                if (on) Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(24.dp).background(Accent))
            }
        }
    }
}

/**
 * 通知 1 件。左=アプリのアイコン、1 行目=差出人(太字)+アプリ名、2 行目=本文、右上=時刻。
 * 左右どちらかへ払うと消す(§4.6。2026-11-19)。行は指に付いて動き、離したとき行幅の [DismissFraction] を越えていれば
 * 外へ滑らせて [onDismiss]、越えていなければばねで戻す。
 * @param onDismiss 消す処理(消す依頼を出せたら true)。null なら払いを受けない(キーの選択待ち中)
 */
@Composable
private fun HubRow(
    item: HubItem,
    preview: HubPreview,
    appLabel: String,
    entry: AppEntry?,
    time: String,
    onClick: () -> Unit,
    onDismiss: (() -> Boolean)?,
) {
    val app = LocalContext.current.applicationContext as FoldBerryApp
    val display = HubItems.display(item, preview, appLabel)
    val body = display.body ?: stringResource(if (preview == HubPreview.SENDER) R.string.hub_body_new else R.string.hub_body_hidden)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    // pointerInput は作り直さないので、最新の消す処理(選択待ちの出入りで変わる)をここから読む。
    val latestDismiss by rememberUpdatedState(onDismiss)
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.7f
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val x = offset.value
                        val dismissNow = latestDismiss
                        scope.launch {
                            if (dismissNow != null && abs(x) > width * DismissFraction) {
                                offset.animateTo(sign(x) * width, tween(150))
                                // 消えれば OS の通知の削除 → recount でこの行は一覧から外れる。
                                // 消せなかった・消えずに残った場合は、外に出したままにせず戻す。
                                if (dismissNow()) delay(1500)
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx ->
                    if (latestDismiss == null) return@detectHorizontalDragGestures
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + dx) }
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = rememberAppIcon(app.catalog, entry?.ref)
        Box(Modifier.size(RowIconSize), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(), colorFilter = LocalAssignedIconFilter.current)
            } else {
                // 一覧に無いアプリ(システムの通知など): 種類のアイコンで代える。
                Image(kindIcon(item.kind), contentDescription = null, modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color(0x99FFFFFF)))
            }
        }
        Column(Modifier.weight(1f).padding(start = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    display.title,
                    Modifier.weight(1f, fill = false),
                    style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                // アプリ名だけのときは 1 行目がアプリ名なので重ねない。
                if (preview != HubPreview.APP) {
                    BasicText(
                        appLabel,
                        Modifier.padding(start = 6.dp),
                        style = TextStyle(color = Color(0x61FFFFFF), fontSize = 10.5.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (body.isNotEmpty()) {
                BasicText(
                    body,
                    style = TextStyle(color = Color(0xB3FFFFFF), fontSize = 12.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BasicText(
            time,
            Modifier.align(Alignment.Top).padding(top = 7.dp, start = 8.dp),
            style = TextStyle(color = Color(0x6BFFFFFF), fontSize = 11.sp),
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Divider))
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        BasicText(text, style = TextStyle(color = Color(0x80FFFFFF), fontSize = 12.5.sp, textAlign = TextAlign.Center))
    }
}

private fun kindIcon(kind: NotificationKind): ImageVector = when (kind) {
    NotificationKind.MISSED_CALL -> FoldBerryIcons.MissedCall
    NotificationKind.EMAIL -> FoldBerryIcons.Mail
    NotificationKind.SMS -> FoldBerryIcons.Sms
    NotificationKind.OTHER -> FoldBerryIcons.Bell
}

private fun kindNameRes(kind: NotificationKind): Int = when (kind) {
    NotificationKind.MISSED_CALL -> R.string.hub_kind_missed_call
    NotificationKind.EMAIL -> R.string.hub_kind_email
    NotificationKind.SMS -> R.string.hub_kind_sms
    NotificationKind.OTHER -> R.string.hub_kind_other
}
