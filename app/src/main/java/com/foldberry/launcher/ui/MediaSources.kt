package com.foldberry.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.foldberry.launcher.data.NotificationCounter

/**
 * 音楽のタイル(T1。D26。2026-11-19)が出す再生中のメディア。
 * MediaSessionManager は「通知へのアクセス」を許可されたアプリにだけ他アプリの再生中のセッションを見せる(Hub と同じ許可。§10)。
 */
sealed interface MediaInfo {
    /** 通知へのアクセスが未許可(案内を出し、押すと許可画面)。 */
    data object NotAllowed : MediaInfo

    /** 再生できるセッションが無い。最後に再生したアプリ(覚えていれば)と「再生」を出す。 */
    data class Idle(val lastPackage: String?) : MediaInfo

    /**
     * セッションあり(再生中・一時停止中)。
     * @param positionMs [positionAtMs](SystemClock.elapsedRealtime)時点の再生位置。再生中なら速さ [speed] で進む
     */
    data class Active(
        val packageName: String,
        val title: String,
        val artist: String,
        val art: ImageBitmap?,
        val playing: Boolean,
        val positionMs: Long,
        val positionAtMs: Long,
        val speed: Float,
        val durationMs: Long,
        val controller: MediaController,
    ) : MediaInfo {
        /** 今の再生位置(ms)。長さが分からなければ 0。 */
        fun positionNow(): Long {
            if (durationMs <= 0) return 0
            val moved = if (playing) ((SystemClock.elapsedRealtime() - positionAtMs) * speed).toLong() else 0L
            return (positionMs + moved).coerceIn(0, durationMs)
        }
    }
}

/** 最後に再生していたアプリを覚えておく(何も再生していないときにアイコンと名前を出す)。 */
private const val PREFS = "foldberry"
private const val KEY_LAST_MEDIA = "media_last_package"

/** アートは表示の大きさ(最大 72dp)に十分な大きさに縮めて持つ(大きな Bitmap を描くたびに縮めない)。 */
private const val ART_MAX_PX = 256

@Composable
fun rememberMediaInfo(): State<MediaInfo> {
    val context = LocalContext.current
    val permissionTick by StatusPermissionEvents.tick.collectAsState()
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val state = remember { mutableStateOf<MediaInfo>(MediaInfo.Idle(prefs.getString(KEY_LAST_MEDIA, null))) }
    DisposableEffect(context, permissionTick) {
        if (!NotificationCounter.isAllowed(context)) {
            state.value = MediaInfo.NotAllowed
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        val component = NotificationCounter.component(context)
        val handler = Handler(Looper.getMainLooper())
        var current: MediaController? = null

        fun publish() {
            val c = current
            if (c == null) {
                state.value = MediaInfo.Idle(prefs.getString(KEY_LAST_MEDIA, null))
                return
            }
            val meta = c.metadata
            val pb = c.playbackState
            val playing = pb?.state == PlaybackState.STATE_PLAYING || pb?.state == PlaybackState.STATE_BUFFERING
            val art = (meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let(::shrink)
            state.value = MediaInfo.Active(
                packageName = c.packageName,
                title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty(),
                artist = (meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.trim().orEmpty(),
                art = art,
                playing = playing,
                positionMs = pb?.position ?: 0L,
                positionAtMs = pb?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
                speed = pb?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                controller = c,
            )
            prefs.edit { putString(KEY_LAST_MEDIA, c.packageName) }
        }

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
            override fun onSessionDestroyed() {
                current?.unregisterCallback(this)
                current = null
                publish()
            }
        }

        /** 再生中のセッションを優先し、無ければ先頭(一時停止中)を選ぶ。 */
        fun bind(list: List<MediaController>?) {
            val next = list.orEmpty().let { all ->
                all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
            }
            if (next?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(callback)
                current = next
                next?.registerCallback(callback, handler)
            }
            publish()
        }

        val listener = MediaSessionManager.OnActiveSessionsChangedListener { bind(it) }
        val ok = try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            bind(manager.getActiveSessions(component))
            true
        } catch (e: SecurityException) {
            // 許可を外された直後など。案内に戻す。
            Log.w(LOG_TAG, "再生中のメディアを読めません", e)
            state.value = MediaInfo.NotAllowed
            false
        }
        onDispose {
            if (ok) manager.removeOnActiveSessionsChangedListener(listener)
            current?.unregisterCallback(callback)
        }
    }
    return state
}

private fun shrink(b: Bitmap): ImageBitmap {
    val longest = maxOf(b.width, b.height)
    if (longest <= ART_MAX_PX) return b.asImageBitmap()
    val f = ART_MAX_PX.toFloat() / longest
    return b.scale((b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1)).asImageBitmap()
}

/**
 * セッションが無いときの「再生」: メディアの再生キーを送る。OS が最後に再生していたアプリに届け、続きから再生する
 * (再開に対応しないアプリでは何も起きない)。権限は要らない。
 */
fun playLastMedia(context: Context) = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)

/**
 * メディアキー([keyCode] は KeyEvent.KEYCODE_MEDIA_*)を押して離す。送り先は OS が選ぶ(再生中のセッション、無ければ最後に再生していたアプリ)。
 * トラックパッドの払い(§5.3)も使う。通知へのアクセスが未許可でも動くよう、MediaController ではなくこちらで送る。
 */
fun sendMediaKey(context: Context, keyCode: Int) {
    val audio = context.getSystemService(AudioManager::class.java) ?: return
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
}
