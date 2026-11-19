package com.foldberry.launcher.ui

import android.app.StatusBarManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.foldberry.launcher.R

private const val TAG = "FoldBerry/NotifPanel"

/**
 * 通知パネルを開く操作(開けなければトーストで知らせる)。ステータス行のタップ・下スワイプと、
 * 時計の横の通知のタップで共有する(§4.1 / §4.2。2026-11-18)。
 */
@Composable
fun rememberOpenNotificationPanel(): () -> Unit {
    val context = LocalContext.current
    val failed = stringResource(R.string.status_open_notifications_failed)
    return remember(context, failed) {
        { if (!expandNotificationPanel(context)) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show() }
    }
}

/**
 * システムの通知パネルを開く(仕様書 §4.1 / U5。2026-11-18 追加)。開けたら true。
 *
 * ステータスバーを「スワイプで一時表示」で隠しているので、上端からのスワイプでは 2 回目でやっと開く。
 * 公開 API が無いため StatusBarManager の非公開メソッド expandNotificationsPanel をリフレクションで呼ぶ。
 * 権限 EXPAND_STATUS_BAR は通常権限(インストール時に許可。ダイアログは出ない §10)。
 * 端末や OS 版によって塞がれていることがあるので、失敗はログに残して false を返す(呼び出し側がトーストで知らせる)。
 */
fun expandNotificationPanel(context: Context): Boolean {
    val manager = context.getSystemService(StatusBarManager::class.java)
    if (manager == null) {
        Log.w(TAG, "StatusBarManager を取得できません")
        return false
    }
    return try {
        manager.javaClass.getMethod("expandNotificationsPanel").invoke(manager)
        true
    } catch (e: ReflectiveOperationException) {
        Log.w(TAG, "通知パネルを開けません(非公開メソッドを呼べない)", e)
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "通知パネルを開けません(権限)", e)
        false
    }
}
