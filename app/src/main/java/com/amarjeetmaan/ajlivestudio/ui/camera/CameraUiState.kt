package com.amarjeetmaan.ajlivestudio.ui.camera

data class CameraUiState(
    val cameraReady: Boolean = false,
    val streamState: StreamState =
        StreamState.IDLE,
    val isFrontCamera: Boolean = false,
    val isTorchOn: Boolean = false,
    val isTorchAvailable: Boolean = false,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val zoomRatio: Float = 1f,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val exposureIndex: Int = 0,
    val whiteBalance: WhiteBalancePreset =
        WhiteBalancePreset.AUTO,
    val isMicMuted: Boolean = false,
    val micGainPercent: Int = 100,
    val musicVolumePercent: Int = 100,
    val audioRouteLabel: String = "Unknown",
    val errorMessage: String? = null
)

enum class StreamState {
    IDLE,
    CONNECTING,
    LIVE,
    ERROR
}

enum class WhiteBalancePreset(
    val label: String,
    val awbMode: Int
) {
    AUTO(
        "Auto",
        1
    ),

    INCANDESCENT(
        "Incandescent",
        2
    ),

    FLUORESCENT(
        "Fluorescent",
        3
    ),

    DAYLIGHT(
        "Daylight",
        5
    ),

    CLOUDY(
        "Cloudy",
        6
    )
}
