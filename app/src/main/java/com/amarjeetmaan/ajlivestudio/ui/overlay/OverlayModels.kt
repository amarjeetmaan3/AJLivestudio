package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.net.Uri
import java.util.UUID

enum class OverlayType { LOGO, TEXT, LOWER_THIRD, WEB }

data class OverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val type: OverlayType,
    val imageUri: Uri? = null,        // LOGO
    val text: String = "",            // TEXT / LOWER_THIRD title
    val subtitle: String = "",        // LOWER_THIRD subtitle
    val webUrl: String = "",          // WEB
    val webWidthDp: Int = 260,        // WEB
    val webHeightDp: Int = 140,       // WEB
    val webCornerRadiusDp: Int = 8,   // WEB
    val webBordered: Boolean = true,  // WEB
    val xPercent: Float = 0.5f,       // 0..1 of preview width, center anchor
    val yPercent: Float = 0.15f,      // 0..1 of preview height
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val visible: Boolean = true,
)
