package com.foldberry.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "FoldBerry"

/**
 * 背景の写真(仕様書 §4.4)。選んだ画像は縮小してアプリ内にコピーし、元画像が消えても背景が消えないようにする。
 * 写真の選択は Android 標準の写真選択画面(ストレージ権限なし。§10)。
 */
object BackgroundPhoto {
    private const val FILE_NAME = "background.jpg"

    /** 長辺の上限(px)。メイン画面の長辺 2448px の画面部でも粗くならず、メモリも食い過ぎない大きさ。 */
    private const val MAX_SIDE = 2048

    private val _revision = MutableStateFlow(0L)

    /** 写真を差し替えるたびに増える。表示側はこれを鍵に読み直す。 */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /**
     * 読み込んだ写真と、そのときの revision。開閉のたびに画面部が作り直されても読み直さずに済ませ、
     * 読み終えるまで既定のプリセットが一瞬見える(ちらつく)のを防ぐ。
     */
    @Volatile
    private var cache: Pair<Long, ImageBitmap>? = null

    /** 今の revision の写真を既に読んでいれば返す(表示の初期値に使う)。 */
    fun cached(revision: Long): ImageBitmap? = cache?.takeIf { it.first == revision }?.second

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    /** 写真を縮小コピーする。失敗したら false(今の写真は変えない)。 */
    suspend fun importFrom(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val dest = file(context)
        val tmp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longSide = max(info.size.width, info.size.height)
                if (longSide > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longSide
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
                // compress できるよう、ハードウェアビットマップではなく通常のメモリに展開する。
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            tmp.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) error("JPEG に変換できません")
            }
            // 書き終えてから差し替える(途中で落ちても前の写真が残る)。
            if (!tmp.renameTo(dest)) error("写真を保存できません")
            // 縮小済みの画像をそのまま覚えておき、背景を写真に切り替えた直後のちらつきも防ぐ。
            val revision = System.currentTimeMillis()
            cache = revision to bitmap.asImageBitmap()
            _revision.value = revision
            true
        } catch (e: Exception) {
            Log.e(TAG, "背景の写真を読み込めません", e)
            tmp.delete()
            false
        }
    }

    /** 保存済みの写真を読む。無い・読めないときは null(表示側は既定のプリセットにする)。 */
    suspend fun load(context: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        val revision = _revision.value
        cached(revision)?.let { return@withContext it }
        val f = file(context)
        if (!f.isFile) return@withContext null
        try {
            BitmapFactory.decodeFile(f.path)?.asImageBitmap()?.also { cache = revision to it }
        } catch (e: Exception) {
            Log.e(TAG, "背景の写真を表示できません", e)
            null
        }
    }
}
