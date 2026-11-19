package com.foldberry.launcher.data

import android.util.Log
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val TAG = "FoldBerry"

/**
 * 割り当ての端末内保存(仕様書 §9)。アプリ内部の files/assignments.json に JSON で置く。
 * 変更は即時に保存する(§7)。カバー/メインは同じインスタンスを共有する。
 */
class AssignmentStore(dir: File, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(dir, FILE_NAME))
    private val brokenFile = File(dir, "$FILE_NAME.broken")
    private val writeLock = Mutex()

    /** 起動時に読んだファイルの中身(load が入れる。_state より上で初期化すること。CLAUDE.md の init 順の落とし穴)。 */
    private var loadedText: String? = null

    // ファイルは数 KB なので起動時に同期で読む。非同期にすると、読み終える前の操作が読込み結果で上書きされ得る。
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Assignments> = _state.asStateFlow()

    init {
        // 古いファイルの廃止した枠(タイル 3・4 枚目、自由枠 M9〜M12)を捨てたときだけ、ファイルも 1 回書き直して消す。
        // ほかの整形(壊れた任意項目を外した等)では書き直さない(手で直す機会を残す。変更時の保存までは元のまま)。
        val text = loadedText
        if (text != null && AssignmentsCodec.hasRetiredEntries(text)) {
            scope.launch(Dispatchers.IO) { writeLock.withLock { write(_state.value) } }
        }
    }

    /** 変更して即保存する。書込みは I/O スレッドで直列に行い、常にその時点の最新を書く(順序の逆転で古い内容が残らない)。 */
    fun update(transform: (Assignments) -> Assignments) {
        val before = _state.value
        val after = _state.updateAndGet(transform)
        if (after == before) return
        scope.launch(Dispatchers.IO) {
            writeLock.withLock { write(_state.value) }
        }
    }

    private fun load(): Assignments {
        if (!file.baseFile.exists()) return Assignments.EMPTY
        val text = try {
            String(file.readFully(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "割り当てファイルを読めません", e)
            backupBroken()
            return Assignments.EMPTY
        }
        return when (val decoded = AssignmentsCodec.decode(text)) {
            is AssignmentsCodec.Decoded.Ok -> decoded.value.also { loadedText = text }
            is AssignmentsCodec.Decoded.Invalid -> {
                // 黙って空で上書きすると手で直す機会も失うので、元のファイルを退避してから空で始める。
                Log.e(TAG, "割り当てファイルが不正なため ${brokenFile.name} に退避して空で始めます: ${decoded.reason}")
                backupBroken()
                Assignments.EMPTY
            }
        }
    }

    /** 読めなかった元のファイルを退避する。空で始めた後の最初の保存で、元の内容が上書きされて失われないように。 */
    private fun backupBroken() {
        runCatching { file.baseFile.copyTo(brokenFile, overwrite = true) }
            .onFailure { Log.e(TAG, "退避に失敗しました", it) }
    }

    private fun write(a: Assignments) {
        // AtomicFile: 書込み途中で落ちても、前の内容が残る。
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e(TAG, "割り当てファイルを書けません", e)
            return
        }
        try {
            out.write(AssignmentsCodec.encode(a).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "割り当ての保存に失敗しました", e)
        }
    }

    companion object {
        const val FILE_NAME = "assignments.json"
    }
}
