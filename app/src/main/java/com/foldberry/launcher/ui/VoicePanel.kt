package com.foldberry.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.foldberry.launcher.ui.screen.rememberClockLanguage
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldberry.launcher.FoldBerryApp
import com.foldberry.launcher.R
import com.foldberry.launcher.data.ClockLanguage
import com.foldberry.launcher.data.VoiceLanguage
import com.foldberry.launcher.keyboard.KeyGlyph
import com.foldberry.launcher.ui.icons.FoldBerryIcons

/** 画面部・キーボードに掛ける暗幕(モック voice-mock.html の案 C)。 */
internal val VoiceDim = Color(0x8C000000)

private val VoiceDone = Color(0xFF6FD39A)

/**
 * 音声操作のカード(§5.5。D27。モック docs/mock/voice-mock.html の案 C)。画面部だけを暗くして中央にカードを出す。
 * 暗幕に触れたら中止(実行した表示のときは閉じるだけ)。置き場所はカバー・メイン横=本体の画面部、メイン縦=上半分。
 */
@Composable
internal fun VoicePanel(voice: VoiceController, modifier: Modifier) {
    // 電話の言い回しで連絡先の許可が要るときは、ここから確認を出す(許可の確認はアクティビティの結果で受けるため画面側に置く)。
    val requestContacts = rememberPermissionRequest(Manifest.permission.READ_CONTACTS, deniedKey = "contacts_permission_denied") {
        voice.onContactsPermission(it)
    }
    val state = voice.state
    LaunchedEffect(state) { if (state is VoiceState.NeedContacts) requestContacts() }

    Box(modifier) {
        AnimatedPanel(state) { s ->
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().background(VoiceDim).clickable(interactionSource = null, indication = null) { voice.cancel() })
                VoiceCard(s, voice, Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun VoiceCard(s: VoiceState, voice: VoiceController, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .padding(horizontal = 28.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .background(Color(0xFF15171B), shape)
            .border(1.dp, CFrame, shape)
            // カードの中の触れは暗幕に渡さない(押しても中止にしない)。
            .clickable(interactionSource = null, indication = null) {}
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Image(
                FoldBerryIcons.glyph(KeyGlyph.MIC), contentDescription = null,
                modifier = Modifier.size(18.dp), colorFilter = ColorFilter.tint(SheetAccent),
            )
            Spacer(Modifier.width(10.dp))
            if (s is VoiceState.Listening) Wave()
            else BasicText(stringResource(R.string.voice_title), style = TextStyle(color = SheetAccent, fontSize = 12.sp))
        }
        Heard(s)
        when (s) {
            is VoiceState.Listening -> Buttons { SheetButton(stringResource(R.string.voice_cancel), SheetText) { voice.cancel() } }
            is VoiceState.NeedContacts -> {
                Hint(stringResource(R.string.voice_contacts_needed))
                Buttons { SheetButton(stringResource(R.string.voice_cancel), SheetText) { voice.cancel() } }
            }
            is VoiceState.Confirm -> {
                Column(Modifier.fillMaxWidth()) {
                    BasicText(stringResource(R.string.voice_confirm_call, s.name), style = TextStyle(color = SheetText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    BasicText(s.number, style = TextStyle(color = SheetTextDim, fontSize = 12.sp))
                }
                Buttons {
                    SheetButton(stringResource(R.string.voice_cancel), SheetText) { voice.cancel() }
                    SheetButton(stringResource(R.string.voice_dial), SheetAccent) { voice.confirm() }
                }
            }
            is VoiceState.ConfirmAssign -> {
                Column(Modifier.fillMaxWidth()) {
                    BasicText(stringResource(R.string.voice_confirm_assign, s.letter.toString()), style = TextStyle(color = SheetText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    Spacer(Modifier.height(2.dp))
                    BasicText("${s.from} → ${s.to}", style = TextStyle(color = SheetTextDim, fontSize = 12.sp))
                }
                Buttons {
                    SheetButton(stringResource(R.string.voice_cancel), SheetText) { voice.cancel() }
                    SheetButton(stringResource(R.string.voice_change), SheetAccent) { voice.confirm() }
                }
            }
            is VoiceState.Done -> BasicText(
                "✓ " + (if (s.args.isEmpty()) stringResource(s.message) else stringResource(s.message, *s.args.toTypedArray())),
                Modifier.fillMaxWidth(),
                style = TextStyle(color = VoiceDone, fontSize = 14.sp, textAlign = TextAlign.Center),
            )
            is VoiceState.Failed -> {
                Hint(failureText(s))
                Buttons {
                    SheetButton(stringResource(R.string.voice_close), SheetText) { voice.cancel() }
                    SheetButton(stringResource(R.string.voice_retry), SheetAccent) { voice.retry() }
                }
            }
            is VoiceState.Unavailable -> {
                if (s.canDownload) {
                    // 押す前に、誰が・どこから・どの言語を・どこへ保存するかを読めるようにする(2026-11-19 オーナー指摘)。
                    // 説明が長くカバーの画面部に収まらないときは、この部分だけ縦にスクロールする。
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(
                            stringResource(R.string.voice_model_missing, stringResource(s.language.voiceLabelRes())),
                            style = TextStyle(color = SheetText, fontSize = 14.sp),
                        )
                        Hint(stringResource(R.string.voice_model_explain))
                        Hint(stringResource(R.string.voice_model_retry_hint))
                    }
                } else {
                    Hint(stringResource(R.string.voice_unavailable))
                }
                Buttons {
                    SheetButton(stringResource(R.string.voice_close), SheetText) { voice.cancel() }
                    if (s.canDownload) SheetButton(stringResource(R.string.voice_download), SheetAccent) { voice.download() }
                }
            }
        }
    }
}

/** 聞き取った文字。聞いている間は途中の文字(まだ無ければ「話してください」)を薄く出す。 */
@Composable
private fun Heard(s: VoiceState) {
    val (text, dim) = when (s) {
        is VoiceState.Listening -> (s.partial.ifBlank { stringResource(R.string.voice_speak_now) } + "…") to true
        is VoiceState.Confirm -> stringResource(R.string.voice_quoted, s.heard) to false
        is VoiceState.ConfirmAssign -> stringResource(R.string.voice_quoted, s.heard) to false
        is VoiceState.NeedContacts -> stringResource(R.string.voice_quoted, s.heard) to false
        is VoiceState.Done -> (if (s.heard.isBlank()) "" else stringResource(R.string.voice_quoted, s.heard)) to false
        is VoiceState.Failed -> (if (s.heard.isBlank()) "" else stringResource(R.string.voice_quoted, s.heard)) to false
        is VoiceState.Unavailable -> "" to false
    }
    if (text.isEmpty()) return
    BasicText(
        text, Modifier.fillMaxWidth(),
        style = TextStyle(color = if (dim) SheetTextDim else Color.White, fontSize = 16.sp, textAlign = TextAlign.Center),
    )
}

/** 音声モデルの言語の名前(「日本語(ja-JP)」など。どの言語を落とすか分かるように)。 */
private fun ClockLanguage.voiceLabelRes(): Int = if (this == ClockLanguage.EN) R.string.voice_model_en else R.string.voice_model_ja

@Composable
private fun failureText(s: VoiceState.Failed): String = when (s.reason) {
    VoiceFailure.NOT_UNDERSTOOD -> stringResource(R.string.voice_not_understood) + " " + stringResource(R.string.voice_examples)
    VoiceFailure.NOT_HEARD -> stringResource(R.string.voice_not_heard) + " " + stringResource(R.string.voice_examples)
    VoiceFailure.APP_NOT_FOUND -> stringResource(R.string.voice_app_not_found, s.arg.orEmpty())
    VoiceFailure.CONTACT_NOT_FOUND -> stringResource(R.string.voice_contact_not_found, s.arg.orEmpty())
    VoiceFailure.CONTACTS_DENIED -> stringResource(R.string.voice_contacts_denied)
    VoiceFailure.KEY_EMPTY -> stringResource(R.string.voice_key_empty, s.arg.orEmpty())
    VoiceFailure.FAILED -> stringResource(R.string.voice_failed)
}

@Composable
private fun Hint(text: String) {
    BasicText(text, Modifier.fillMaxWidth(), style = TextStyle(color = SheetTextDim, fontSize = 12.sp, lineHeight = 18.sp))
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { content() }
}

/** 聞いている間の波形(5 本の棒が順に伸び縮みする)。 */
@Composable
private fun Wave() {
    val t = rememberInfiniteTransition(label = "voice-wave")
    Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            val h by t.animateFloat(
                initialValue = 5f, targetValue = 20f,
                // ずらすのは最初だけ(tween の delay だと毎回止まって波に見えない)。
                animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 150)),
                label = "bar$i",
            )
            Box(Modifier.width(3.dp).height(h.dp).background(SheetAccent, RoundedCornerShape(2.dp)))
        }
    }
}

/**
 * 0 のキーの長押しで音声操作を始める関数(§5.5)。マイクの許可が無ければ先に求め、許されたら始める。
 * 認識の言語は設定の「音声操作の言語」(既定は画面の言語)。
 */
@Composable
internal fun rememberVoiceStart(voice: VoiceController): () -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext as FoldBerryApp
    val assignments by app.store.state.collectAsState()
    // 話す言葉は設定(§8「音声操作の言語」)。画面の言語に合わせるときは時計と同じ言語。認識と言い回しの規則は同じ言語を使う。
    val displayLanguage = rememberClockLanguage()
    val language by rememberUpdatedState(
        when (assignments.voice.voiceLanguage) {
            VoiceLanguage.DISPLAY -> displayLanguage
            VoiceLanguage.JA -> ClockLanguage.JA
            VoiceLanguage.EN -> ClockLanguage.EN
        },
    )
    val request = rememberPermissionRequest(Manifest.permission.RECORD_AUDIO, deniedKey = "mic_permission_denied") { granted ->
        if (granted) voice.start(language)
    }
    return {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) voice.start(language)
        else request()
    }
}

/**
 * 音声操作の間、キーボードの中央に出す「触れると中止」(§5.5)。触れは受けない(下のキーに通す)。
 * 中止の振り分けはキーの側(BerryBody の voiceKeyPress: 0 のキーは何もしない、ほかのキー・機能列は中止。2026-11-19 オーナー指定)。
 * 暗くするのもキーの側(KeyRows の dimmed・機能列の透明度)。暗幕を掛けると青くした 0 のキーまで暗くなるため。
 * 「触れると中止」は暗くしたキーボードの中央に出す(最下段の余白はナビバーの高さそのもので、下端には置く隙間が無い。2026-11-19 実機)。
 */
@Composable
internal fun VoiceKeyboardHint(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { VoiceCancelLabel() }
}

/** 「触れると中止」の札。目立つようアクセントの青(2026-11-19 オーナー指定)。キーボードとメイン横の右の枠で共通。 */
@Composable
private fun VoiceCancelLabel() {
    val shape = RoundedCornerShape(12.dp)
    BasicText(
        stringResource(R.string.voice_keyboard_locked),
        Modifier.background(Color(0xE615171B), shape).border(1.dp, SheetAccent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        style = TextStyle(color = SheetAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    )
}

/**
 * メイン横の右の枠(一覧の枠)に掛ける幕(§5.5。2026-11-19 オーナー指定)。音声操作の間に右の枠に触れて
 * 割り当てシートやフォルダが開き、カードと重なって分かりにくくならないよう、暗くして触れたら中止にする。
 * ステータス行も幕の下に入る(聞いている間は通知パネルを開けない)。
 */
@Composable
internal fun VoiceSideCurtain(voice: VoiceController, modifier: Modifier) {
    Box(modifier) {
        AnimatedPanel(if (voice.isOpen) Unit else null) {
            Box(
                Modifier.fillMaxSize().background(VoiceDim).clickable(interactionSource = null, indication = null) { voice.cancel() },
                contentAlignment = Alignment.Center,
            ) { VoiceCancelLabel() }
        }
    }
}
