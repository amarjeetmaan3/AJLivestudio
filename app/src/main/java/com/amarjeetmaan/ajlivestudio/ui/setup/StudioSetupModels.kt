package com.amarjeetmaan.ajlivestudio.ui.setup

enum class Resolution(val label: String, val width: Int, val height: Int) {
    HD_720("720p", 1280, 720),
    FULL_HD_1080("1080p", 1920, 1080),
}

enum class FrameRate(val label: String, val value: Int) {
    FPS_24("24", 24),
    FPS_30("30", 30),
    FPS_60("60", 60),
}

enum class BitratePreset(val label: String, val kbps: Int?) {
    AUTO("Auto", null),
    LOW("Low", 1500),
    MEDIUM("Medium", 3000),
    HIGH("High", 6000),
}

enum class StreamOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape"),
}

data class StudioSetupState(
    val resolution: Resolution = Resolution.FULL_HD_1080,
    val frameRate: FrameRate = FrameRate.FPS_30,
    val bitrate: BitratePreset = BitratePreset.AUTO,
    val orientation: StreamOrientation = StreamOrientation.PORTRAIT,
)
