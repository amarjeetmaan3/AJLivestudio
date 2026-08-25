package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class OverlayViewModel : ViewModel() {

    var items by mutableStateOf(listOf<OverlayItem>())
        private set

    fun addLogo(uri: Uri) {
        items = items + OverlayItem(
            type = OverlayType.LOGO,
            imageUri = uri,
            xPercent = 0.85f,
            yPercent = 0.08f,
            scale = 0.5f,
        )
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        items = items + OverlayItem(
            type = OverlayType.TEXT,
            text = text,
            xPercent = 0.5f,
            yPercent = 0.1f,
        )
    }

    fun addLowerThird(title: String, subtitle: String) {
        if (title.isBlank()) return
        items = items + OverlayItem(
            type = OverlayType.LOWER_THIRD,
            text = title,
            subtitle = subtitle,
            xPercent = 0.5f,
            yPercent = 0.88f,
        )
    }

    fun addWeb(url: String) {
        if (url.isBlank()) return
        val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        items = items + OverlayItem(
            type = OverlayType.WEB,
            webUrl = fullUrl,
            xPercent = 0.05f,
            yPercent = 0.75f,
        )
    }

    fun updateWebSize(id: String, widthDp: Int, heightDp: Int) {
        items = items.map {
            if (it.id == id) it.copy(webWidthDp = widthDp.coerceIn(80, 400), webHeightDp = heightDp.coerceIn(60, 400))
            else it
        }
    }

    fun updateWebCornerRadius(id: String, radiusDp: Int) {
        items = items.map { if (it.id == id) it.copy(webCornerRadiusDp = radiusDp.coerceIn(0, 40)) else it }
    }

    fun toggleWebBorder(id: String) {
        items = items.map { if (it.id == id) it.copy(webBordered = !it.webBordered) else it }
    }

    // Bumped whenever a WEB overlay should force-reload its WebView.
    var webReloadTick by mutableStateOf(0)
        private set

    fun reloadWebOverlays() {
        webReloadTick++
    }

    fun updatePosition(id: String, xPercent: Float, yPercent: Float) {
        items = items.map {
            if (it.id == id) it.copy(
                xPercent = xPercent.coerceIn(0f, 1f),
                yPercent = yPercent.coerceIn(0f, 1f),
            ) else it
        }
    }

    fun updateScale(id: String, scale: Float) {
        items = items.map { if (it.id == id) it.copy(scale = scale.coerceIn(0.2f, 3f)) else it }
    }

    fun updateOpacity(id: String, opacity: Float) {
        items = items.map { if (it.id == id) it.copy(opacity = opacity.coerceIn(0f, 1f)) else it }
    }

    fun toggleVisible(id: String) {
        items = items.map { if (it.id == id) it.copy(visible = !it.visible) else it }
    }

    fun remove(id: String) {
        items = items.filterNot { it.id == id }
    }
}
