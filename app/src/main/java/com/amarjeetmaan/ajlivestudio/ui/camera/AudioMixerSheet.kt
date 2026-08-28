package com.amarjeetmaan.ajlivestudio.ui.camera

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioMixerSheet(
    uiState: CameraUiState,
    onGainChange: (Int) -> Unit,
    onMusicVolumeChange: (Int) -> Unit,
    onConnectBluetooth: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text("Audio Mixer", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Mic Gain: ${uiState.micGainPercent}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = uiState.micGainPercent.toFloat(),
                onValueChange = { onGainChange(it.toInt()) },
                valueRange = 0f..200f
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Music Volume: ${uiState.musicVolumePercent}%", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = uiState.musicVolumePercent.toFloat(),
                onValueChange = { onMusicVolumeChange(it.toInt()) },
                valueRange = 0f..100f
            )

            Spacer(modifier = Modifier.height(24.dp))
            
            // यहाँ हमने audioRoute की जगह audioRouteLabel कर दिया है
            Text("Current Route: ${uiState.audioRouteLabel}", color = MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onConnectBluetooth,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Connect Bluetooth Mic")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
