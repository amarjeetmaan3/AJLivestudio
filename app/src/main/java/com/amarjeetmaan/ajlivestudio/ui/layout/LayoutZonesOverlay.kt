package com.amarjeetmaan.ajlivestudio.ui.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary

private data class Zone(val offset: Offset, val size: Size)

/** Returns (cameraZone, screenZone?) as fractions of the full canvas (0..1). */
private fun zonesFor(preset: LayoutPreset): Pair<Zone, Zone?> = when (preset) {
    LayoutPreset.FULL_CAMERA ->
        Zone(Offset(0f, 0f), Size(1f, 1f)) to null
    LayoutPreset.SPLIT_50_50 ->
        Zone(Offset(0f, 0f), Size(1f, 0.5f)) to Zone(Offset(0f, 0.5f), Size(1f, 0.5f))
    LayoutPreset.SPLIT_70_30 ->
        Zone(Offset(0f, 0f), Size(1f, 0.7f)) to Zone(Offset(0f, 0.7f), Size(1f, 0.3f))
    LayoutPreset.SPLIT_30_70 ->
        Zone(Offset(0f, 0f), Size(1f, 0.3f)) to Zone(Offset(0f, 0.3f), Size(1f, 0.7f))
    LayoutPreset.PIP_TOP_LEFT ->
        Zone(Offset(0f, 0f), Size(1f, 1f)) to Zone(Offset(0.04f, 0.06f), Size(0.32f, 0.22f))
    LayoutPreset.PIP_TOP_RIGHT ->
        Zone(Offset(0f, 0f), Size(1f, 1f)) to Zone(Offset(0.64f, 0.06f), Size(0.32f, 0.22f))
    LayoutPreset.PIP_BOTTOM_LEFT ->
        Zone(Offset(0f, 0f), Size(1f, 1f)) to Zone(Offset(0.04f, 0.55f), Size(0.32f, 0.22f))
    LayoutPreset.PIP_BOTTOM_RIGHT ->
        Zone(Offset(0f, 0f), Size(1f, 1f)) to Zone(Offset(0.64f, 0.55f), Size(0.32f, 0.22f))
}

/**
 * Draws zone outlines: gold solid for Camera, dashed gray for the Screen
 * Share placeholder (since screen capture isn't wired into the broadcast
 * yet — see Phase 7 README). Purely a local design aid.
 */
@Composable
fun LayoutZonesOverlay(preset: LayoutPreset) {
    if (preset == LayoutPreset.FULL_CAMERA) return // nothing to draw, camera already fills the screen

    val (_, screenZone) = zonesFor(preset)

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            screenZone?.let { zone ->
                val topLeft = Offset(zone.offset.x * size.width, zone.offset.y * size.height)
                val zoneSize = Size(zone.size.width * size.width, zone.size.height * size.height)
                drawRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = topLeft,
                    size = zoneSize,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                )
            }
        }
        Text(
            text = "Dashed zone = Screen Share (design only, not yet in broadcast)",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
