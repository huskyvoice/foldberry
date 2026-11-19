package com.foldberry.launcher.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.time.LocalDate

private const val TAG = "FoldBerry"

/**
 * 設定ファイルの書出し/読込み(仕様書 §8 / §9)。保存先・読込み元は Android 標準のファイル画面で選んだ URI
 * (権限不要。§10)。読み書きは I/O スレッドで行う。
 */
object SettingsTransfer {
    /** これより大きいファイルは設定ファイルではないとみなして読まない(割り当て 39 件の JSON は数 KB)。 */
    const val MAX_BYTES = 1_000_000

    fun defaultFileName(today: LocalDate = LocalDate.now()): String =
        "foldberry-settings-%04d%02d%02d.json".format(today.year, today.monthValue, today.dayOfMonth)

    suspend fun write(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            // 既存ファイルへの上書きでも末尾が残らないよう "wt"(切り詰め)。対応しない保存先は "w" で開き直す。
            val out = try {
                context.contentResolver.openOutputStream(uri, "wt")
            } catch (e: FileNotFoundException) {
                context.contentResolver.openOutputStream(uri, "w")
            } catch (e: IllegalArgumentException) {
                context.contentResolver.openOutputStream(uri, "w")
            }
            if (out == null) return@withContext false
            out.use { it.write(bytes) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "設定の書出しに失敗しました", e)
            false
        }
    }

    sealed interface ReadResult {
        data class Ok(val text: String) : ReadResult
        data object TooLarge : ReadResult
        data object Failed : ReadResult
    }

    suspend fun read(context: Context, uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) }
                ?: return@withContext ReadResult.Failed
            if (bytes.size > MAX_BYTES) ReadResult.TooLarge else ReadResult.Ok(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "設定の読込みに失敗しました", e)
            ReadResult.Failed
        }
    }
}
