package com.amarjeetmaan.ajlivestudio.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

/**
 * Detects whether this device can expose front + back camera concurrently.
 *
 * Uses CameraManager.getConcurrentCameraIds() (API 30+), the official
 * Android API for this — not every phone supports concurrent streaming
 * even if it physically has two cameras, so this is a real hardware check,
 * not a guess. Devices below API 30, or where the OEM doesn't report a
 * concurrent front+back combo, correctly report "not supported" and the
 * feature stays disabled — matching the original spec's requirement to
 * detect capability and disable gracefully rather than assume support.
 */
class DualCameraCapability(private val context: Context) {

    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val concurrentGroups = runCatching { manager.concurrentCameraIds }.getOrNull() ?: return false

        return concurrentGroups.any { group ->
            val facings = group.mapNotNull { id ->
                runCatching {
                    manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                }.getOrNull()
            }
            facings.contains(CameraCharacteristics.LENS_FACING_FRONT) &&
                facings.contains(CameraCharacteristics.LENS_FACING_BACK)
        }
    }
}
