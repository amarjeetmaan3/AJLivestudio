package com.amarjeetmaan.ajlivestudio.ui.camera

import com.amarjeetmaan.ajlivestudio.audio.AudioRoute

enum class WhiteBalancePreset(val label: String, val awbMode: Int) {
    // Values match android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_*
    AUTO("Auto", 1),
    DAYLIGHT("Daylight", 5),
    CLOUDY("Cloudy", 6),
    FLUORESCENT("Fluorescent", 3),
    INCANDESCENT("Incandescent", 2),
}

enum class StreamState {
    IDLE, CONNECTING, LIVE, ERROR
}

data class CameraUiState(
    val cameraReady: Boolean = false,
    val isTorchOn: Boolean = false,
    val isTorchAvailable: Boolean = false,
    val isFrontCamera: Boolean = false,
    val dualCameraAvailable: Boolean = false,
    val dualCameraConcurrentSupported: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val whiteBalance: WhiteBalancePreset = WhiteBalancePreset.AUTO,
    val streamState: StreamState = StreamState.IDLE,
    val errorMessage: String? = null,
    // Audio (Phase 4)
    val isMicMuted: Boolean = false,
    val micGainPercent: Int = 100,
    val musicVolumePercent: Int = 0,
    val audioRoute: AudioRoute = AudioRoute.UNKNOWN,
    val audioLevel: Float = 0f, // 0..1, see AudioLevelMonitor note in README
    // Screen Share (Phase 7)
    val screenSharePermissionGranted: Boolean = false,
    val screenShareWiredToStream: Boolean = false, // always false until Phase 7b
)
