package com.amarjeetmaan.ajlivestudio.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.NavySurface
import com.amarjeetmaan.ajlivestudio.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Audio mixer bottom sheet: mic gain, music track volume, input route.
 *
 * IMPORTANT: mic gain and music volume are UI state only right now,
 * same limitation flagged since Phase 4 — neither one multiplies/mixes
 * an actual audio signal yet. Mic mute IS real (system-level, Phase 4).
 * There is also no music track playback wired up at all in this phase —
 * this slider exists so the control is in place and ready once a real
 * audio-pipeline hook lets us mix in a music file.
 */
@Composable
fun AudioMixerSheet(
    uiState: CameraUiState,
    onGainChange: (Int) -> Unit,
    onMusicVolumeChange: (Int) -> Unit,
    onConnectBluetooth: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NavySurface) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Audio Mixer", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            MixerRow(
                label = "Mic",
                value = uiState.micGainPercent,
                valueRange = 0..200,
                onChange = onGainChange,
            )
            MixerRow(
                label = "Music",
                value = uiState.musicVolumePercent,
                valueRange = 0..100,
                onChange = onMusicVolumeChange,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Gain/volume controls are UI-only for now — see the README note from Phase 4 on why. Mic mute (the icon in the control bar) is fully real.",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Input: ${uiState.audioRoute.label}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onConnectBluetooth) {
                    Text("Use Bluetooth mic", color = GoldPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MixerRow(label: String, value: Int, valueRange: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("$value%", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
    }
}
