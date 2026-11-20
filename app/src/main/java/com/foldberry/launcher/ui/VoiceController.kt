package com.foldberry.launcher.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.foldberry.launcher.R
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AppEntry
import com.foldberry.launcher.data.AppRef
import com.foldberry.launcher.data.AssignmentStore
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.ClockLanguage
import com.foldberry.launcher.data.VoiceCommand
import com.foldberry.launcher.data.VoiceCommands
import com.foldberry.launcher.data.VoiceContacts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 音声操作のカードの状態(§5.5)。null=閉じている。 */
sealed interface VoiceState {
    /** 聞いている。partial は途中まで聞き取った文字(出ない端末もある)。 */
    data class Listening(val partial: String) : VoiceState

    /** 電話の確認(取り消せない操作だけ確認する)。 */
    data class Confirm(val heard: String, val name: String, val number: String) : VoiceState

    /** 電話の言い回しで、連絡先の許可を求めている。 */
    data class NeedContacts(val heard: String, val name: String) : VoiceState

    /** キーの割り当ての上書きの確認(別のアプリが入っているときだけ。2026-11-20 オーナー決定)。 */
    data class ConfirmAssign(val heard: String, val letter: Char, val from: String, val to: String, val ref: AppRef) : VoiceState

    /** 実行した。2 秒で閉じる。args は message の %1$s… に入れる。 */
    data class Done(val heard: String, @param:StringRes val message: Int, val args: List<String> = emptyList()) : VoiceState

    /** 分からなかった・できなかった。 */
    data class Failed(val heard: String, val reason: VoiceFailure, val arg: String? = null) : VoiceState

    /** 端末内の音声認識が使えない。canDownload=この言語のモデルをダウンロードできる(カードに誰が・どこから・どの言語かを出す)。 */
    data class Unavailable(val canDownload: Boolean, val language: ClockLanguage) : VoiceState
}

enum class VoiceFailure { NOT_UNDERSTOOD, NOT_HEARD, APP_NOT_FOUND, CONTACT_NOT_FOUND, CONTACTS_DENIED, KEY_EMPTY, FAILED }

/** 音声操作から HomeController の画面を動かす口(設定を開く・カレンダー/Hub の切替。§5.5)。HomeController が入れる。 */
interface VoiceHost {
    fun openSettings(assignments: Boolean)

    /** hub=null は入れ替え。いまの構成(カバー/メイン)の枠を切り替え、切り替えた先が Hub なら true。 */
    fun switchPanel(hub: Boolean?): Boolean
}

/**
 * 音声操作(0 のキーの長押し。§5.5。D27)。端末内の音声認識だけを使い(声を外に出さない。2026-11-19 オーナー決定)、
 * 聞き取った文字は VoiceCommands の規則で読む。HomeController が持つ(開閉で作り直されない層。開閉・終話・戻るで中止)。
 */
@Stable
class VoiceController(
    private val context: Context,
    private val catalog: AppCatalog,
    private val store: AssignmentStore,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<VoiceState?>(null)
        private set

    /** 設定を開く・パネルの切替の先(HomeController の init で入れる)。 */
    var host: VoiceHost? = null

    private var recognizer: SpeechRecognizer? = null
    private var request: Intent? = null
    private var language = ClockLanguage.JA

    /** カードを出していて、キーボードを止めているか(実行した表示の 2 秒間はキーボードを普段どおり押せる)。 */
    val isOpen: Boolean get() = state != null && state !is VoiceState.Done

    fun start(language: ClockLanguage) {
        stopRecognizer()
        this.language = language
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            state = VoiceState.Unavailable(canDownload = false, language = this@VoiceController.language)
            return
        }
        val tag = if (language == ClockLanguage.EN) "en-US" else "ja-JP"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        val r = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (e: UnsupportedOperationException) {
            Log.w(LOG_TAG, "端末内の音声認識を作れません", e)
            state = VoiceState.Unavailable(canDownload = false, language = this@VoiceController.language)
            return
        }
        recognizer = r
        request = intent
        r.setRecognitionListener(Listener(r))
        state = VoiceState.Listening("")
        // この言語のモデルが端末にあるか確かめてから聞く(無ければダウンロードを出す)。
        r.checkRecognitionSupport(
            intent, context.mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    if (recognizer !== r) return
                    val lang = tag.substringBefore('-')
                    fun has(list: List<String>) = list.any { it.substringBefore('-').equals(lang, ignoreCase = true) }
                    Log.i(LOG_TAG, "音声認識の対応: installed=${support.installedOnDeviceLanguages} pending=${support.pendingOnDeviceLanguages}")
                    if (has(support.installedOnDeviceLanguages)) {
                        r.startListening(intent)
                    } else {
                        state = VoiceState.Unavailable(
                            canDownload = has(support.supportedOnDeviceLanguages) || has(support.pendingOnDeviceLanguages),
                            language = language,
                        )
                    }
                }

                override fun onError(error: Int) {
                    // 確かめられないときは聞いてみる(言語が無ければ聞く側のエラーで分かる)。
                    Log.w(LOG_TAG, "音声認識の対応を確かめられません: $error")
                    if (recognizer === r) r.startListening(intent)
                }
            },
        )
    }

    /** 中止(戻る・終話・開閉・キーボードや暗幕に触れた・アプリが裏に回った)。 */
    fun cancel() {
        stopRecognizer()
        state = null
    }

    fun retry() = start(language)

    /**
     * この言語の端末内モデルのダウンロードを頼み、カードは閉じる。この先は OS(ASI)の確認画面(大きさの表示・キャンセル)に任せる。
     * 押されたか・終わったかは知る手段がないので追わない(終わったら 0 のキーの長押しをやり直す旨を押す前の説明に書く。2026-11-20 オーナー決定)。
     */
    fun download() {
        val r = recognizer
        val intent = request
        if (r != null && intent != null) r.triggerModelDownload(intent)
        cancel()
    }

    /** 確認の「発信」(電話アプリを番号入りで開く)・「変更」(キーの割り当てを上書き)。 */
    fun confirm() {
        when (val s = state) {
            is VoiceState.Confirm ->
                if (openDialerWith(context, s.number)) done(s.heard, R.string.voice_done_dial, s.name)
                else state = VoiceState.Failed(s.heard, VoiceFailure.FAILED)
            is VoiceState.ConfirmAssign -> assignKey(s.heard, s.letter, s.ref, s.to)
            else -> Unit
        }
    }

    /** 連絡先の許可を求めた結果。 */
    fun onContactsPermission(granted: Boolean) {
        val s = state as? VoiceState.NeedContacts ?: return
        if (granted) resolveCall(s.heard, s.name) else state = VoiceState.Failed(s.heard, VoiceFailure.CONTACTS_DENIED)
    }

    private fun stopRecognizer() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        request = null
    }

    private fun done(heard: String, @StringRes message: Int, vararg args: String) {
        val d = VoiceState.Done(heard, message, args.toList())
        state = d
        // 結果を 2 秒見せて閉じる(その間に別の操作で状態が変わっていれば何もしない)。
        scope.launch {
            delay(DONE_SHOW_MS)
            if (state === d) cancel()
        }
    }

    private fun handle(heard: String) {
        if (heard.isBlank()) {
            state = VoiceState.Failed("", VoiceFailure.NOT_HEARD)
            return
        }
        fun fail() {
            state = VoiceState.Failed(heard, VoiceFailure.FAILED)
        }
        when (val cmd = VoiceCommands.parse(heard, language)) {
            is VoiceCommand.OpenApp -> {
                val apps = (catalog.state.value as? CatalogState.Loaded)?.apps.orEmpty()
                val index = VoiceCommands.matchApp(cmd.query, apps.map { it.label })
                when {
                    index == null -> state = VoiceState.Failed(heard, VoiceFailure.APP_NOT_FOUND, cmd.query)
                    catalog.launch(apps[index].ref) -> done(heard, R.string.voice_done_open, apps[index].label)
                    else -> fail()
                }
            }
            is VoiceCommand.Alarm ->
                if (setAlarm(context, cmd.hour, cmd.minute)) {
                    done(heard, R.string.voice_done_alarm, String.format(Locale.ROOT, "%d:%02d", cmd.hour, cmd.minute))
                } else fail()
            is VoiceCommand.Timer ->
                if (startTimer(context, cmd.seconds)) done(heard, R.string.voice_done_timer, VoiceCommands.durationText(cmd.seconds, language))
                else fail()
            is VoiceCommand.Call ->
                if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) resolveCall(heard, cmd.name)
                else state = VoiceState.NeedContacts(heard, cmd.name)
            is VoiceCommand.Light -> {
                // 「ライト」だけのときはつける(今の点灯状態はコールバックでしか分からないため。§5.5)。
                val on = cmd.on ?: true
                if (setTorch(context, on)) done(heard, if (on) R.string.voice_done_light_on else R.string.voice_done_light_off) else fail()
            }
            is VoiceCommand.Manner -> {
                val vibrate = cmd.on ?: isRingerNormal(context)
                if (setVibrate(context, vibrate)) done(heard, if (vibrate) R.string.voice_done_manner_on else R.string.voice_done_manner_off) else fail()
            }
            is VoiceCommand.AssignKey -> {
                val apps = loadedApps()
                val index = VoiceCommands.matchApp(cmd.app, apps.map { it.label })
                if (index == null) {
                    state = VoiceState.Failed(heard, VoiceFailure.APP_NOT_FOUND, cmd.app)
                    return
                }
                val app = apps[index]
                val current = store.state.value.keys[keyId(cmd.letter)]
                when {
                    current == null -> assignKey(heard, cmd.letter, app.ref, app.label)
                    current == app.ref -> done(heard, R.string.voice_done_key_same, cmd.letter.toString(), app.label)
                    else -> state = VoiceState.ConfirmAssign(heard, cmd.letter, labelOf(apps, current), app.label, app.ref)
                }
            }
            is VoiceCommand.ClearKey -> {
                val id = keyId(cmd.letter)
                val current = store.state.value.keys[id]
                if (current == null) {
                    state = VoiceState.Failed(heard, VoiceFailure.KEY_EMPTY, cmd.letter.toString())
                } else {
                    store.update { it.withKey(id, null) }
                    done(heard, R.string.voice_done_key_cleared, cmd.letter.toString(), labelOf(loadedApps(), current))
                }
            }
            is VoiceCommand.AskKey -> {
                val current = store.state.value.keys[keyId(cmd.letter)]
                if (current == null) done(heard, R.string.voice_done_key_empty, cmd.letter.toString())
                else done(heard, R.string.voice_done_key_is, cmd.letter.toString(), labelOf(loadedApps(), current))
            }
            is VoiceCommand.OpenSettings -> {
                val h = host ?: return fail()
                // カードを閉じてから開く(設定シートの上にカードを残さない)。
                cancel()
                h.openSettings(cmd.assignments)
            }
            is VoiceCommand.SwitchPanel -> {
                val h = host ?: return fail()
                done(heard, if (h.switchPanel(cmd.hub)) R.string.voice_done_panel_hub else R.string.voice_done_panel_calendar)
            }
            VoiceCommand.Unknown -> state = VoiceState.Failed(heard, VoiceFailure.NOT_UNDERSTOOD)
        }
    }

    private fun loadedApps(): List<AppEntry> = (catalog.state.value as? CatalogState.Loaded)?.apps.orEmpty()

    /** 割り当て済みのアプリの名前(一覧に無ければパッケージ名)。 */
    private fun labelOf(apps: List<AppEntry>, ref: AppRef): String = apps.firstOrNull { it.ref == ref }?.label ?: ref.packageName

    /** 英字キーの保存キー(KeyLayout の英字キーの id は大文字 1 文字)。 */
    private fun keyId(letter: Char): String = letter.toString()

    private fun assignKey(heard: String, letter: Char, ref: AppRef, label: String) {
        store.update { it.withKey(keyId(letter), ref) }
        done(heard, R.string.voice_done_key_assigned, letter.toString(), label)
    }

    private fun resolveCall(heard: String, name: String) {
        val before = state
        scope.launch {
            val match = withContext(Dispatchers.IO) { VoiceContacts.find(context, name) }
            // 調べている間に中止・別の操作になっていたら何もしない。
            if (state !== before) return@launch
            state = if (match == null) VoiceState.Failed(heard, VoiceFailure.CONTACT_NOT_FOUND, name)
            else VoiceState.Confirm(heard, match.name, match.number)
        }
    }

    private inner class Listener(private val r: SpeechRecognizer) : RecognitionListener {
        private fun current() = recognizer === r

        private fun first(results: Bundle?) = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

        override fun onPartialResults(partialResults: Bundle?) {
            if (current() && state is VoiceState.Listening) state = VoiceState.Listening(first(partialResults))
        }

        override fun onResults(results: Bundle?) {
            if (!current()) return
            val heard = first(results)
            stopRecognizer()
            handle(heard)
        }

        override fun onError(error: Int) {
            if (!current()) return
            Log.w(LOG_TAG, "音声認識のエラー: $error")
            stopRecognizer()
            state = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceState.Failed("", VoiceFailure.NOT_HEARD)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceState.Unavailable(canDownload = false, language = this@VoiceController.language)
                else -> VoiceState.Failed("", VoiceFailure.FAILED)
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private companion object {
        const val DONE_SHOW_MS = 2000L
    }
}
