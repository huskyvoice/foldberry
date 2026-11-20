package com.foldberry.launcher.ui

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager

/*
 * 端末のライト・マナーの切り替え(下の行 §4.3 と音声操作 §5.5 で共用。2026-11-19 に QuickDock.kt から移した)。
 * どちらも権限不要(カメラの権限・おやすみモードへのアクセスは使わない。§10)。
 */

/** フラッシュのある背面カメラの ID。無ければ null。 */
internal fun torchCameraId(manager: CameraManager): String? = try {
    manager.cameraIdList.firstOrNull { id ->
        val c = manager.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
            c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    }
} catch (e: CameraAccessException) {
    null
}

/** ライトをつける/消す。フラッシュが無い・カメラアプリが使っているときは false。 */
internal fun setTorch(context: Context, on: Boolean): Boolean {
    val manager = context.getSystemService(CameraManager::class.java)
    val id = torchCameraId(manager) ?: return false
    return try {
        manager.setTorchMode(id, on)
        true
    } catch (e: CameraAccessException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }
}

/** 着信音が通常か(マナー・サイレントでなければ true)。 */
internal fun isRingerNormal(context: Context): Boolean =
    context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL

/**
 * マナー(バイブ)にする/通常へ戻す(サイレントへは切り替えない。2026-11-17 オーナー決定)。
 * おやすみモード中などで変えられないと SecurityException になるので false を返す。
 */
internal fun setVibrate(context: Context, vibrate: Boolean): Boolean {
    val audio = context.getSystemService(AudioManager::class.java)
    val next = if (vibrate) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
    return try {
        audio.ringerMode = next
        audio.ringerMode == next
    } catch (e: SecurityException) {
        false
    }
}

/** 下の行のマナー: 通常⇔バイブの 2 段。サイレント中に押したら通常へ戻す。 */
internal fun toggleVibrate(context: Context): Boolean = setVibrate(context, isRingerNormal(context))
