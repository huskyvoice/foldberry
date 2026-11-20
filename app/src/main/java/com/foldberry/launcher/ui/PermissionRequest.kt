package com.foldberry.launcher.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private const val PREFS = "foldberry"

/**
 * 実行時の許可を 1 つ求める(カレンダー §4.3・音声操作のマイクと連絡先 §5.5 で共通。2026-11-19 にカレンダーから切り出した)。
 * 断られたあと、もう確認を出せない(2 回断られた)ときはアプリ情報の画面を開く。結果は [onResult] で返す。
 *
 * @param deniedKey 一度でも断られたかを覚える SharedPreferences(foldberry)のキー。許可ごとに別にする
 */
@Composable
internal fun rememberPermissionRequest(permission: String, deniedKey: String, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = remember(context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // 断られたことがあり(記録あり)、今回も確認が出せなかった(rationale なし)ときだけアプリ情報を開く。
        // 初めての確認を外側タップ・戻るで閉じただけのときは開かない(rationale は false だが記録が無い。2026-11-19 refuter の指摘)。
        val deniedBefore = prefs.getBoolean(deniedKey, false)
        val rationale = activity?.shouldShowRequestPermissionRationale(permission) == true
        if (!granted && rationale) prefs.edit().putBoolean(deniedKey, true).apply()
        if (!granted && deniedBefore && !rationale && activity != null) {
            // 「今後表示しない」相当で確認が出ずに断られた: 自分で許可できるようアプリ情報を開く。
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: ActivityNotFoundException) {
                Log.w(LOG_TAG, "アプリ情報を開けません", e)
            }
        }
        callback(granted)
    }
    return { launcher.launch(permission) }
}
