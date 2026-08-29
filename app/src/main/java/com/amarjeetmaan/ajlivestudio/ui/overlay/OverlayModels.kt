package com.amarjeetmaan.ajlivestudio.ui.overlay

enum class OverlayType {
    TEXT, LOGO, LOWER_THIRD, WEB
}

data class OverlayItem(
    val id: String,
    val type: OverlayType,
    val content: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f
)
