package com.foldberry.launcher.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.foldberry.launcher.R
import com.foldberry.launcher.ui.LocalAssignedIconFilter
import com.foldberry.launcher.ui.RingerStatus
import com.foldberry.launcher.ui.SheetAccent
import com.foldberry.launcher.ui.icons.FoldBerryIcons
import com.foldberry.launcher.ui.keyboard.pressFeedback
import com.foldberry.launcher.ui.keyboard.rememberPressState
import com.foldberry.launcher.ui.rememberRingerStatus

/** 下の行の高さ(§4.3。モック docs/mock/calendar-mock.html)。 */
val QuickDockHeight = 48.dp

private val IconOn = SheetAccent
private val IconOff = Color(0x99FFFFFF)
private val IconPlain = Color(0xEBFFFFFF)

/**
 * カバー画面の下の行(§4.3。BB10 のホーム下端の「封筒・家・点」に倣った並び。2026-11-17 オーナー決定)。
 * 中央=設定・家(カバー=今月へ、メイン=ホームに戻る)・ライト・マナー(通常⇔バイブ)・自動回転・QR コード読み取り、右=カメラの枠(アプリ割り当て。未割り当てなら端末のカメラ)。
 * 中央は詰めた 1 本の列に見せる: 家だけ大きく(20dp)、ほかは小さく(13dp)して下端をそろえる。オン=水色、オフ=薄い白。
 *
 * @param homeActive 家を白で出すか(カバーは当月表示中だけ白、他の月では薄い白)
 * @param cameraIcon カメラの枠に割り当てたアプリのアイコン(未割り当て・読込み前は null でカメラの絵)
 */
@Composable
fun QuickDock(
    homeActive: Boolean,
    homeDescription: String,
    onHome: () -> Unit,
    cameraAssigned: Boolean,
    cameraIcon: ImageBitmap?,
    onCameraTap: () -> Unit,
    onCameraLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val torch by rememberTorch()
    val ringer by rememberRingerStatus()
    val autoRotate by rememberAutoRotate()
    fun toast(id: Int) = Toast.makeText(context, resources.getString(id), Toast.LENGTH_SHORT).show()

    Row(
        modifier.fillMaxWidth().height(QuickDockHeight).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左は空ける(BB10 の Hub の位置。2026-11-17 オーナー指定で外した)。右のカメラと同じ幅で中央の列を真ん中に置く。
        Spacer(Modifier.width(44.dp))
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.Center) {
            QuickItem("quick-settings", FoldBerryIcons.Settings, stringResource(R.string.quick_settings), IconPlain) {
                if (!openSystemSettings(context)) toast(R.string.quick_settings_failed)
            }
            QuickItem("quick-home", FoldBerryIcons.Home, homeDescription, if (homeActive) Color.White else IconOff, big = true, onTap = onHome)
            QuickItem("quick-light", FoldBerryIcons.Flashlight, stringResource(R.string.quick_light), if (torch == true) IconOn else IconOff) {
                if (!toggleTorch(context, torch == true)) toast(R.string.quick_light_failed)
            }
            val vibrate = ringer == RingerStatus.VIBRATE
            QuickItem(
                "quick-ringer", if (vibrate) FoldBerryIcons.Vibrate else FoldBerryIcons.Bell, stringResource(R.string.quick_vibrate),
                if (vibrate) IconOn else IconPlain,
            ) {
                if (!toggleVibrate(context)) toast(R.string.quick_vibrate_failed)
            }
            QuickItem("quick-rotate", FoldBerryIcons.AutoRotate, stringResource(R.string.quick_rotate), if (autoRotate) IconOn else IconOff) {
                when {
                    !Settings.System.canWrite(context) -> {
                        openWriteSettingsPermission(context)
                        toast(R.string.quick_rotate_permission)
                    }
                    !setAutoRotate(context, !autoRotate) -> toast(R.string.quick_rotate_failed)
                }
            }
            QuickItem("quick-qr", FoldBerryIcons.QrScan, stringResource(R.string.quick_qr), IconPlain) {
                if (!openQrScanner(context)) toast(R.string.quick_qr_failed)
            }
        }
        CameraButton(cameraAssigned, cameraIcon, onCameraTap, onCameraLongPress)
    }
}

/** 列の 1 項目の幅。押せる範囲は見た目の間隔(モックの 27dp)より少し広く取る。 */
private val ItemWidth = 30.dp

@Composable
private fun QuickItem(key: String, icon: ImageVector, description: String, tint: Color, big: Boolean = false, onTap: () -> Unit) {
    val pressed = rememberPressState()
    Box(
        Modifier
            .width(ItemWidth)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .pressFeedback(pressed, key, onTap = onTap)
            .graphicsLayer { alpha = if (pressed.value) 0.6f else 1f },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 下端を家にそろえる(家の絵は viewport の下端近くまであるので 2dp 低く置く)。
        Image(
            icon, contentDescription = null,
            modifier = Modifier.padding(bottom = if (big) 14.dp else 16.dp).size(if (big) 20.dp else 13.dp),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

/** 右端のカメラの枠。割り当てアプリがあればそのアイコン(グレースケール設定に従う)、無ければカメラの絵。 */
@Composable
private fun CameraButton(assigned: Boolean, icon: ImageBitmap?, onTap: () -> Unit, onLongPress: () -> Unit) {
    val pressed = rememberPressState()
    val description = stringResource(R.string.dock_camera)
    Box(
        Modifier
            .size(44.dp)
            .semantics { contentDescription = description }
            .pressFeedback(pressed, "dock-camera", onTap = onTap, onLongPress = onLongPress)
            .graphicsLayer {
                val s = if (pressed.value) 0.92f else 1f
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        if (assigned && icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(30.dp).clip(RoundedCornerShape(22)), colorFilter = LocalAssignedIconFilter.current)
        } else {
            Image(FoldBerryIcons.Camera, contentDescription = null, modifier = Modifier.size(30.dp), colorFilter = ColorFilter.tint(Color(0xF2FFFFFF)))
        }
    }
}

/* ───── 状態の読取りと切替え(どれも失敗で落とさず false を返す) ───── */

/**
 * QR コードの読み取り画面を開く(2026-11-17 オーナー要望。旧「未定」の枠)。
 * Samsung のカメラが公開している QR 専用画面(SCAN_QR_CODE)を優先し、無い端末では静止画カメラを開く
 * (たいていのカメラアプリは QR を読める)。権限・追加の依存は使わない。開けたら true。
 */
private fun openQrScanner(context: Context): Boolean {
    val intents = listOf(
        Intent(SAMSUNG_SCAN_QR_CODE),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    )
    return intents.any { intent ->
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

/** Samsung のカメラの QR コード読み取り画面(com.sec.android.app.camera/.QrScannerActivity が受ける)。 */
private const val SAMSUNG_SCAN_QR_CODE = "com.sec.android.app.camera.action.SCAN_QR_CODE"

/** 端末の設定アプリを開く。 */
private fun openSystemSettings(context: Context): Boolean = try {
    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/** フラッシュのある背面カメラの ID。無ければ null。 */
private fun torchCameraId(manager: CameraManager): String? = try {
    manager.cameraIdList.firstOrNull { id ->
        val c = manager.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
} catch (e: CameraAccessException) {
    null
}

/**
 * ライトの点灯状態。null=フラッシュが無い/使えない。setTorchMode とその監視は権限不要
 * (カメラの権限は使わない。§10)。カメラアプリが使っている間は使えない状態になる。
 */
@Composable
private fun rememberTorch(): State<Boolean?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(CameraManager::class.java)
        val id = torchCameraId(manager)
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) state.value = enabled
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) state.value = false
            }
        }
        if (id != null) manager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { if (id != null) manager.unregisterTorchCallback(callback) }
    }
    return state
}

private fun toggleTorch(context: Context, on: Boolean): Boolean {
    val manager = context.getSystemService(CameraManager::class.java)
    val id = torchCameraId(manager) ?: return false
    return try {
        manager.setTorchMode(id, !on)
        true
    } catch (e: CameraAccessException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}

/**
 * マナー: 通常⇔バイブの 2 段(2026-11-17 オーナー決定。サイレントへは切り替えない)。サイレント中に押したら通常へ戻す。
 * おやすみモード中などで変えられないと SecurityException になるので false を返す(おやすみモードへのアクセスは使わない。§10)。
 */
private fun toggleVibrate(context: Context): Boolean {
    val audio = context.getSystemService(AudioManager::class.java)
    val next = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
    return try {
        audio.ringerMode = next
        audio.ringerMode == next
    } catch (e: SecurityException) {
        false
    }
}

/** 画面の自動回転のオン/オフ。読むのは権限不要、書くのは「システム設定の変更」の許可が要る(§10)。 */
@Composable
private fun rememberAutoRotate(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        fun read() = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { state.value = read() }
        }
        resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer)
        state.value = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}

/** 「システム設定の変更」の許可画面(このアプリの分)を開く。画面が無い端末では何もしない(案内のトーストだけ出る)。 */
private fun openWriteSettingsPermission(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: ActivityNotFoundException) {
    }
}

/** 自動回転を [on] にする。許可済みであること(canWrite)が前提。書けたら true。 */
private fun setAutoRotate(context: Context, on: Boolean): Boolean = try {
    Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
} catch (e: SecurityException) {
    false
} catch (e: IllegalArgumentException) {
    false
}
