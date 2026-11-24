package com.foldberry.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.ui.icons.FoldBerryIcons

/* シート(本体部の中で下から出すパネル)の共通の色。 */
/**
 * シートの枠(SheetFrame)が IME・ナビバーの分だけ下を空けるか。既定は空ける。
 * メイン縦の上半分(D25。PortraitTop)は画面の下端に接せず IME も掛からないので false にする。
 */
internal val LocalSheetWindowInsets = staticCompositionLocalOf { true }

internal val SheetColor = Color(0xFF0C0D10)
internal val SheetFieldColor = Color(0xFF232731)
internal val SheetAccent = Color(0xFF3FB6E8)
internal val SheetText = Color(0xFFE8E9EC)
internal val SheetTextDim = Color(0xFF8B8F98)
internal val SheetDanger = Color(0xFFE5776B)

/* 案 C(BlackBerry 10 風。2026-11-17 オーナー決定。docs/mock/settings-mock.html・lists-mock.html)の線と帯の色。 */
/** 行と行の区切り線(アプリ一覧の格子も。見本の 0x0F では実機で薄いため 0x14 に。2026-11-23 オーナー指定)。 */
internal val CLine = Color(0x14FFFFFF)
/** 枠線(背景の見本・暗さの区切り)。 */
internal val CFrame = Color(0x1AFFFFFF)
/** 選択中の薄い背景。 */
internal val CSelected = SheetAccent.copy(alpha = 0x1F / 255f)
private val CBarBrush = Brush.verticalGradient(listOf(Color(0xFF1C1E23), Color(0xFF121317)))
private val CBarIcon = Color(0xFFCFD2D8)
private val CBarDivider = Color(0x10FFFFFF)
private val CheckOff = Color(0xFF6A6F78)
/** アクセントの青の上に置く文字の色(件数の札・選択の印)。 */
internal val OnAccent = Color(0xFF06141B)

/**
 * 本体部の中で下から出すシートの枠(割り当てシート §7・設定シート §8・長押しメニューで共通)。
 * 上は案 C の帯(左に ‹、題、右に任意の文字)。外側を暗く覆い、外側のタップで閉じる。端末の「戻る」は HomeScreen の
 * BackHandler が HomeController.back() で受ける(ここでも持つと 1 回の戻るで二重に閉じ得るため持たない)。
 * ‹ は onBack を 1 回呼ぶだけ(端末の戻ると同じ関数を渡す)。ナビゲーションバーの下に潜らず、文字入力中は IME の上に持ち上がる。
 *
 * @param onBack 帯の ‹(既定は閉じる)。設定は settings.back(確認 → 一覧 → トップ → 閉じる)
 * @param trailing 帯の右に出す文字(割り当て一覧の件数・割り当てシートの今のアプリ名)。null なら出さない
 * @param bottomInset 置き場所の下端がウィンドウ下端(ナビゲーション領域)に接しているか。メイン縦向きの一覧(上半分。
 *   下端はヒンジ)のように接していなければナビバーの分を空けない(空けるとシートが浮き、下に暗幕の帯が残る)
 * @param bounce 開閉を弾むせり上がりにする(設定・割り当てシート。表示ギミック案 4)。false なら素直なスライド
 */
@Composable
fun SheetFrame(
    title: String,
    onDismiss: () -> Unit,
    onBack: () -> Unit = onDismiss,
    trailing: String? = null,
    heightFraction: Float = 0.88f,
    bottomInset: Boolean = true,
    bounce: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        // IME とナビバーは重なるので union で大きい方を取る(manifest の adjustResize と組み合わせ)。
        // メイン縦の上半分(D25)は画面の下端に接しておらず、IME も下半分にしか掛からないので空けない。
        val insets = when {
            !LocalSheetWindowInsets.current -> WindowInsets(0, 0, 0, 0)
            bottomInset -> WindowInsets.ime.union(WindowInsets.navigationBars)
            else -> WindowInsets.ime
        }
        Box(Modifier.fillMaxSize().windowInsetsPadding(insets)) {
            // AnimatedPanel の中なら下から滑り出る(暗幕はフェードだけ)。
            PanelSlide(Modifier.align(Alignment.BottomCenter), bounce = bounce) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .background(SheetColor)
                        // 案 C は角を丸めず、上端に細い線を引く。
                        .drawBehind { drawLine(CFrame, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                        // シートの余白をタップしても下の暗幕(=閉じる)に届かないようにする。
                        .pointerInput(Unit) {},
                ) {
                    CBar(title = title, onBack = onBack, trailing = trailing)
                    content()
                }
            }
        }
    }
}

/**
 * 案 C の帯(題の帯)。左に ‹(onBack があれば)、題、右に文字(trailing)と ×(onClose があれば)。
 * ボタンと題の間には縦の区切り線。下端は黒線+薄い白線で、帯を浮かせる。
 */
@Composable
internal fun CBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: String? = null,
    onClose: (() -> Unit)? = null,
    height: Dp = 56.dp,
    titleSize: Float = 19f,
    /** 題の代わりに置く中身(アプリ一覧の検索欄。§6)。null なら title を文字で出す。 */
    titleContent: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(CBarBrush)
            // 下端の黒線+薄い白線は帯の高さの内側に描く(外に足すと一覧の見出しが 48dp を超え、MainHomePanel のマス割りとずれる)。
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(Color.Black, Offset(0f, size.height - w * 1.5f), Offset(size.width, size.height - w * 1.5f), w)
                drawLine(Color(0x0DFFFFFF), Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            BarButton(FoldBerryIcons.Line.Back, height, onBack)
            Box(Modifier.width(1.dp).fillMaxHeight().background(CBarDivider))
        }
        if (titleContent != null) {
            Box(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) { titleContent() }
        } else {
            BasicText(
                title,
                Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
                style = TextStyle(color = SheetText, fontSize = titleSize.sp, letterSpacing = 0.02.em),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            // 幅の上限を付ける(長いアプリ名で題=対象の表示 §7 が押し出されないように)。
            BasicText(
                trailing,
                Modifier.padding(end = if (onClose != null) 12.dp else 14.dp).widthIn(max = 150.dp),
                style = TextStyle(color = SheetAccent, fontSize = 14.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClose != null) {
            Box(Modifier.width(1.dp).fillMaxHeight().background(CBarDivider))
            BarButton(FoldBerryIcons.Line.Close, height, onClose)
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        LineIcon(icon, CBarIcon, 22.dp)
    }
}

/** 線画アイコン(FoldBerryIcons.Line)を色付きで描く。 */
@Composable
internal fun LineIcon(icon: ImageVector, tint: Color, size: Dp = 20.dp) {
    Image(icon, contentDescription = null, modifier = Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/**
 * 件数の札(フォルダの中の数・Hub の種類ごとの件数で共通。2026-11-18 オーナー決定「フォルダと Hub は統一」)。
 * 大きさは置き場所ごとに [fontSize] で変え、色・形・文字の太さはここで 1 つに決める。
 * @param ring アイコンに重ねるときの縁取りの色(下地と同じ色にして、アイコンとの境目を作る)。null なら付けない
 */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier, fontSize: TextUnit = 11.sp, ring: Color? = null) {
    val shape = RoundedCornerShape(50)
    BasicText(
        if (count > 99) "99+" else count.toString(),
        modifier
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
            .background(SheetAccent, shape)
            .widthIn(min = 16.dp)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        style = TextStyle(color = OnAccent, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
    )
}

/**
 * 点線の角丸の枠(既定のアプリの印。§6・§8。v0.6.2)。自分で割り当てた枠の実線・塗りと見分ける。
 * @param radius 角の丸み(置く印の shape と合わせる)
 */
internal fun Modifier.dashedOutline(color: Color, radius: Dp): Modifier = drawBehind {
    val w = 1.2.dp.toPx()
    drawRoundRect(
        color,
        topLeft = Offset(w / 2, w / 2),
        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = Stroke(width = w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

/** 下端に区切り線を引く(案 C のリストの行)。 */
internal fun Modifier.bottomLine(color: Color = CLine): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

/** 案 C の小見出し: アクセント色・字間広め・下に細いアクセントの線。右に補足(件数など)を出せる。 */
@Composable
internal fun CSectionTitle(text: String, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp)
            .bottomLine(SheetAccent.copy(alpha = 0.33f))
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text, Modifier.weight(1f),
            style = TextStyle(color = SheetAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em),
        )
        if (trailing != null) BasicText(trailing, style = TextStyle(color = SheetTextDim, fontSize = 12.sp))
    }
}

/**
 * 案 C のリストの行: 左に線画アイコン、題と説明、右に値・チェック・› などを置く。下に区切り線。
 * @param onClick null なら押せない行
 */
@Composable
internal fun CRow(
    icon: ImageVector?,
    title: String,
    description: String? = null,
    onClick: (() -> Unit)?,
    titleColor: Color = SheetText,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .bottomLine()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) LineIcon(icon, if (titleColor == SheetText) Color(0xFF9AA0AA) else titleColor)
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            BasicText(title, style = TextStyle(color = titleColor, fontSize = 16.sp))
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(description, style = TextStyle(color = SheetTextDim, fontSize = 12.5.sp, lineHeight = 17.sp))
            }
        }
        trailing()
    }
}

/** 行の右に出す今の値(アクセント色)。 */
@Composable
internal fun CValue(text: String) {
    BasicText(text, style = TextStyle(color = SheetAccent, fontSize = 14.sp), maxLines = 1)
}

/** 行の右の ›。 */
@Composable
internal fun CChevron() = LineIcon(FoldBerryIcons.Line.Chevron, Color(0xFF6A6F78))

/** 四角いチェックボックス(案 C のオン/オフ)。押すのは行全体で受ける。 */
@Composable
internal fun CCheck(checked: Boolean) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier.size(22.dp)
            .then(if (checked) Modifier.background(SheetAccent, shape) else Modifier.border(2.dp, CheckOff, shape)),
    ) {
        if (checked) CheckMark(OnAccent, Modifier.fillMaxSize())
    }
}

/** 丸いチェック(割り当てシートの今のアプリ)。 */
@Composable
internal fun CRoundCheck() {
    Box(Modifier.size(22.dp).background(SheetAccent, RoundedCornerShape(50))) { CheckMark(OnAccent, Modifier.fillMaxSize()) }
}

@Composable
private fun CheckMark(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * 0.26f, size.height * 0.52f)
            lineTo(size.width * 0.43f, size.height * 0.69f)
            lineTo(size.width * 0.75f, size.height * 0.33f)
        }
        drawPath(p, color, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 文字のボタン(確認の「すべて解除」「やめる」など)。案 C は角の小さい枠つき。 */
@Composable
internal fun SheetButton(text: String, color: Color, onClick: () -> Unit) {
    BasicText(
        text,
        Modifier
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        style = TextStyle(color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
internal fun SheetMessage(text: String) {
    BasicText(text, Modifier.fillMaxWidth().padding(24.dp), style = TextStyle(color = SheetTextDim, fontSize = 14.sp))
}
