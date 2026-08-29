package com.amarjeetmaan.ajlivestudio.ui.overlay

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    onTransform: (String, Float, Float, Float) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        items.forEach { item ->
            var offsetX by remember { mutableFloatStateOf(item.x) }
            var offsetY by remember { mutableFloatStateOf(item.y) }
            var scale by remember { mutableFloatStateOf(item.scale) }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .pointerInput(Unit) {
                        if (editable) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.3f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                                onTransform(item.id, offsetX, offsetY, scale)
                            }
                        }
                    }
            ) {
                when (item.type) {
                    OverlayType.TEXT -> {
                        Text(
                            text = item.content,
                            color = Color.White,
                            fontSize = 22.sp,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f)).padding(12.dp)
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
                            update = { webView -> if (webReloadTick > 0) webView.reload() },
                            modifier = Modifier.width(300.dp).height(200.dp)
                        )
                    }
                }
            }
        }
    }
}                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale
                    )
                    .pointerInput(Unit) {
                        if (editable) {
                            // यह ड्रैग (Move) और ज़ूम (Resize) दोनों को एक साथ संभालेगा
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale *= zoom
                                scale = scale.coerceIn(0.3f, 5f) // बहुत छोटा या बहुत बड़ा होने से रोकेगा
                                
                                offsetX += pan.x
                                offsetY += pan.y
                                
                                onTransform(item.id, offsetX, offsetY, scale)
                            }
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
