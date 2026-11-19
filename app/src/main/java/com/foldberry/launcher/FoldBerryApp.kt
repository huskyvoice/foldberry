package com.foldberry.launcher

import android.app.Application
import android.util.Log
import com.foldberry.launcher.data.AppCatalog
import com.foldberry.launcher.data.AssignmentStore
import com.foldberry.launcher.data.CatalogState
import com.foldberry.launcher.data.reconcile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * プロセス全体で 1 つだけ持つもの(割り当ての保存とアプリ一覧)。
 * カバー/メインは同じ Activity・同じプロセスなので、ここを共有すれば片方の変更がもう片方に出る(§7)。
 */
class FoldBerryApp : Application() {
    /** 画面の開閉・回転で途中で止まってほしくない処理(設定の書出し・読込みなど)に使う。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var store: AssignmentStore
        private set
    lateinit var catalog: AppCatalog
        private set

    override fun onCreate() {
        super.onCreate()
        store = AssignmentStore(filesDir, appScope)
        catalog = AppCatalog(this, appScope)

        // 一覧を読み直すたびに割り当てと突き合わせる。止まっている間のアンインストールにも追従する(§5.2)。
        appScope.launch {
            catalog.state.collect { state ->
                if (state !is CatalogState.Loaded) return@collect
                store.update { current ->
                    val result = reconcile(current, state.apps.map { it.ref }) ?: return@update current
                    if (result.removed + result.remapped > 0) {
                        Log.i("FoldBerry", "割り当てを突き合わせました: 解除 ${result.removed} 件 / 付け替え ${result.remapped} 件")
                    }
                    result.assignments
                }
            }
        }
    }
}
