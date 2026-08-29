package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun OverlayLayer(
    items: List<OverlayItem>,
    editable: Boolean,
    webReloadTick: Int,
    onDrag: (String, Float, Float) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        items.forEach { item ->
            var offsetX by remember { mutableFloatStateOf(item.x) }
            var offsetY by remember { mutableFloatStateOf(item.y) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        if (editable) {
                            detectDragGestures(
                                onDragEnd = { onDrag(item.id, offsetX, offsetY) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            )
                        }
                    }
            ) {
                when (item.type) {
                    OverlayType.TEXT -> {
                        Text(
                            text = item.content,
                            color = Color.White,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    OverlayType.LOGO -> {
                        AsyncImage(
                            model = Uri.parse(item.content),
                            contentDescription = "Logo",
                            modifier = Modifier.size(100.dp)
                        )
                    }
                    OverlayType.LOWER_THIRD -> {
                        val parts = item.content.split("||")
                        Column(modifier = Modifier.background(Color.Blue.copy(alpha = 0.8f)).padding(12.dp)) {
                            Text(text = parts.getOrNull(0) ?: "", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text(text = parts.getOrNull(1) ?: "", color = Color.Yellow, fontSize = 16.sp)
                        }
                    }
                    OverlayType.WEB -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    loadUrl(item.content)
                                }
                            },
                            update = { webView ->
                                if (webReloadTick > 0) webView.reload()
                            },
                            modifier = Modifier.width(300.dp).height(200.dp)
                        )
                    }
                }
            }
        }
    }
}
