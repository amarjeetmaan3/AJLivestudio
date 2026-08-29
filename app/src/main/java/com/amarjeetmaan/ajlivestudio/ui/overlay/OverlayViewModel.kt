package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel

data class OverlayItem(
    val id: String,
    val type: OverlayType,
    val content: String, // Text, URL, or URI string for Logo
    var x: Float = 0f,
    var y: Float = 0f
)

enum class OverlayType {
    TEXT, LOGO, LOWER_THIRD, WEB
}

class OverlayViewModel : ViewModel() {
    val items = mutableStateListOf<OverlayItem>()
    var webReloadTick = 0

    fun addText(text: String) {
        if (text.isNotBlank()) {
            items.add(OverlayItem(id = System.currentTimeMillis().toString(), type = OverlayType.TEXT, content = text))
        }
    }

    fun addLogo(uri: Uri) {
        items.add(OverlayItem(id = System.currentTimeMillis().toString(), type = OverlayType.LOGO, content = uri.toString()))
    }

    fun addLowerThird(name: String, title: String) {
        items.add(OverlayItem(id = System.currentTimeMillis().toString(), type = OverlayType.LOWER_THIRD, content = "$name||$title"))
    }

    fun addWeb(url: String) {
        if (url.isNotBlank()) {
            items.add(OverlayItem(id = System.currentTimeMillis().toString(), type = OverlayType.WEB, content = url))
        }
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
    }

    fun updatePosition(id: String, x: Float, y: Float) {
        val index = items.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = items[index]
            items[index] = item.copy(x = x, y = y)
        }
    }
}
