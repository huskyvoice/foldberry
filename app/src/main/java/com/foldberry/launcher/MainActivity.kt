package com.foldberry.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.foldberry.launcher.ui.AppLanguage
import com.foldberry.launcher.ui.HomeScreen
import com.foldberry.launcher.ui.LOG_TAG
import com.foldberry.launcher.ui.StatusPermissionEvents
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {
    /** ホームジェスチャー(=終話。§5.3)の通知。取りこぼしても害は無いので、溜めずに 1 件だけ持つ。 */
    private val homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** ステータス行の Bluetooth・電波の許可(§4.1 / §10)。結果が出たら登録をやり直させる。断られたら該当項目は出さない。 */
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        StatusPermissionEvents.changed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初回起動なら日本語にする(既定=日本語。以後は OS での選択を優先)。OS がこのあと作り直すので、この回は描かずに待つ。
        if (AppLanguage.ensureDefault(this)) return
        // 開閉で作り直されているかを実機で確かめるためのログ(manifest の configChanges の効き具合の確認)。
        Log.i(LOG_TAG, "onCreate ${describe(resources.configuration)}")
        enableEdgeToEdge()
        hideStatusBar()
        setContent { HomeScreen(homeEvents) }
        askStatusPermissionsOnce()
    }

    // singleTask なので、ホームジェスチャー・ホームボタンは作り直さずここに届く。初回起動の intent は来ない。
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 開閉でも届くか(届くと読込みの確認待ちまで消える)を実機で確かめるためのログ。
        Log.i(LOG_TAG, "onNewIntent action=${intent.action} categories=${intent.categories}")
        homeEvents.tryEmit(Unit)
    }

    // 他アプリから戻った・開閉した・フォーカスが戻ったときにシステムがバーを出し直すことがあるので掛け直す。
    override fun onResume() {
        super.onResume()
        hideStatusBar()
        // アプリ情報・通知へのアクセスの画面で許可を変えて戻った場合に読み直す。
        StatusPermissionEvents.changed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(LOG_TAG, "onConfigurationChanged ${describe(newConfig)}")
        hideStatusBar()
    }

    /**
     * システムのステータスバーだけを隠し、自前のステータス行を出す(仕様書 §4.1 / D14)。
     * 上端からのスワイプで一時的に表示され、通知パネルも引き出せる。
     * ナビゲーションバーは隠さない(3 ボタンナビの端末で操作できなくなるため)。
     */
    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).run {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    /**
     * 初回だけ Bluetooth・電話の許可をまとめて求める(§10。2026-11-17 改訂)。毎回聞くとホームに戻るたびに邪魔なので
     * 聞いたことを覚える。あとから変えるときは設定シートの「Bluetooth・電波の表示」からアプリ情報を開く。
     */
    private fun askStatusPermissionsOnce() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED, false)) return
        prefs.edit { putBoolean(KEY_ASKED, true) }
        val missing = STATUS_PERMISSIONS.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionRequest.launch(missing.toTypedArray())
    }

    private fun describe(c: Configuration): String =
        "w=${c.screenWidthDp}dp h=${c.screenHeightDp}dp sw=${c.smallestScreenWidthDp}dp orientation=${c.orientation}"

    private companion object {
        const val PREFS = "foldberry"
        const val KEY_ASKED = "status_permissions_asked"
        val STATUS_PERMISSIONS = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.READ_PHONE_STATE)
    }
}
