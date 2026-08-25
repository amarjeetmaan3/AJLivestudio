package com.amarjeetmaan.ajlivestudio.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyDeep
import kotlin.math.roundToInt

/**
 * Renders overlay items ON TOP OF the local preview so the operator can see
 * and position exactly what the overlay will look like.
 *
 * IMPORTANT — this layer is NOT yet baked into the outgoing RTMP stream.
 * Viewers of the actual broadcast will not see it. Wiring these overlays
 * into the encoded frames needs a custom StreamPack ISurfaceProcessorInternal
 * (available since StreamPack 3.x per the release notes) — that's real,
 * low-level OpenGL/EGL surface-compositing code that I did not write blind
 * without being able to compile-test it. This screen gets you the full
 * design/positioning workflow now; baking it into the broadcast is the
 * next concrete engineering step.
 */
@Composable
fun OverlayLayer(
    items: List<OverlayItem>,
    editable: Boolean,
    webReloadTick: Int = 0,
    onDrag: (id: String, xPercent: Float, yPercent: Float) -> Unit,
) {
    var containerSize by remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
    ) {
        items.filter { it.visible }.forEach { item ->
            val xPx = (item.xPercent * containerSize.width)
            val yPx = (item.yPercent * containerSize.height)

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                    .alpha(item.opacity)
                    .then(
                        if (editable) Modifier.pointerInput(item.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (containerSize.width > 0 && containerSize.height > 0) {
                                    val newX = item.xPercent + dragAmount.x / containerSize.width
                                    val newY = item.yPercent + dragAmount.y / containerSize.height
                                    onDrag(item.id, newX, newY)
                                }
                            }
                        } else Modifier
                    )
            ) {
                when (item.type) {
                    OverlayType.LOGO -> {
                        AsyncImage(
                            model = item.imageUri,
                            contentDescription = "Logo overlay",
                            modifier = Modifier.width((90 * item.scale).dp)
                        )
                    }
                    OverlayType.TEXT -> {
                        Text(
                            text = item.text,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NavyDeep.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    OverlayType.LOWER_THIRD -> {
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NavyDeep.copy(alpha = 0.85f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(GoldPrimary)
                                    .padding(bottom = 2.dp)
                            ) {
                                Text(
                                    text = item.text,
                                    color = NavyDeep,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    OverlayType.WEB -> {
                        WebOverlayView(item = item, reloadTick = webReloadTick)
                    }
                }
            }
        }
    }
}

/**
 * Renders a live web page (e.g. your Firebase control.html/overlay.html
 * scoreboard) inside the preview. Background is set transparent so pages
 * with a transparent <body> composite cleanly over the camera feed — pages
 * with an opaque background will simply show as an opaque card, which is
 * usually fine for a scoreboard box anyway.
 */
@Composable
private fun WebOverlayView(item: OverlayItem, reloadTick: Int) {
    val cornerShape = RoundedCornerShape((item.webCornerRadiusDp * item.scale).dp)
    Box(
        modifier = Modifier
            .width((item.webWidthDp * item.scale).dp)
            .height((item.webHeightDp * item.scale).dp)
            .clip(cornerShape)
            .then(
                if (item.webBordered)
                    Modifier.background(NavyDeep.copy(alpha = 0.2f))
                else Modifier
            )
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = android.webkit.WebViewClient()
                    if (item.webUrl.isNotBlank()) loadUrl(item.webUrl)
                }
            },
            update = { webView ->
                if (item.webUrl.isNotBlank()) webView.loadUrl(item.webUrl)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
