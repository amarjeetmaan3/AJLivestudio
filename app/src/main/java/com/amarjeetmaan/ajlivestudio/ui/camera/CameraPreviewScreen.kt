package com.amarjeetmaan.ajlivestudio.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbAuto
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.ui.live.RtmpConfig
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayLayer
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayPanel
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayViewModel
import com.amarjeetmaan.ajlivestudio.ui.scene.SceneBar
import com.amarjeetmaan.ajlivestudio.ui.scene.SceneViewModel
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import com.amarjeetmaan.ajlivestudio.screenshare.ScreenShareController
import com.amarjeetmaan.ajlivestudio.ui.layout.LayoutPickerMenu
import com.amarjeetmaan.ajlivestudio.ui.layout.LayoutViewModel
import com.amarjeetmaan.ajlivestudio.ui.layout.LayoutZonesOverlay
import com.amarjeetmaan.ajlivestudio.ui.theme.CrimsonBright
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.LiveGreen
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyDeep
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

/**
 * Full-screen preview + live control bar.
 * Preview uses StreamPack's XML PreviewView (io.github.thibaultbee.streampack.views.PreviewView)
 * wrapped via AndroidView, bound to the streamer with setVideoSourceProvider —
 * both confirmed against StreamPack's official docs/changelog.
 */
@Composable
fun CameraPreviewScreen(
    setupState: StudioSetupState,
    rtmpConfig: RtmpConfig,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel(),
    sceneViewModel: SceneViewModel = viewModel(),
    layoutViewModel: LayoutViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var showWbMenu by remember { mutableStateOf(false) }
    var showOverlayPanel by remember { mutableStateOf(false) }
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showAudioMixer by remember { mutableStateOf(false) }
    val screenShareController = remember { ScreenShareController(context) }
    val screenSharePermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onScreenSharePermissionResult(screenShareController.isResultGranted(result.resultCode))
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initialize(context, setupState)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        val streamer = viewModel.currentStreamer()
        AndroidView(
            factory = { ctx -> io.github.thibaultbee.streampack.views.PreviewView(ctx) },
            update = { previewView ->
                streamer?.let { previewView.setVideoSourceProvider(it) }
            },
            modifier = Modifier.fillMaxSize()
        )

        OverlayLayer(
            items = overlayViewModel.items,
            editable = true,
            webReloadTick = overlayViewModel.webReloadTick,
            onDrag = { id, x, y -> overlayViewModel.updatePosition(id, x, y) }
        )

        LayoutZonesOverlay(preset = layoutViewModel.preset)

        // Top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(NavyDeep.copy(alpha = 0.55f))
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack, enabled = uiState.streamState != StreamState.LIVE) {
                Text("← Setup", color = Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (dotColor, label) = when (uiState.streamState) {
                    StreamState.IDLE -> Color.Gray to "Not live"
                    StreamState.CONNECTING -> GoldPrimary to "Connecting…"
                    StreamState.LIVE -> LiveGreen to "LIVE"
                    StreamState.ERROR -> CrimsonBright to "Error"
                }
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                Spacer(modifier = Modifier.width(6.dp))
                Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "${setupState.resolution.label} · ${setupState.frameRate.value}fps",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        SceneBar(sceneViewModel = sceneViewModel, overlayViewModel = overlayViewModel)
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CrimsonBright.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Bottom control bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(NavyDeep.copy(alpha = 0.8f))
                .padding(bottom = 16.dp, top = 10.dp)
        ) {
            // Exposure slider
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("EV", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.exposureIndex.toFloat(),
                    onValueChange = { viewModel.setExposure(it.roundToInt()) },
                    valueRange = uiState.exposureMin.toFloat()..(if (uiState.exposureMax > uiState.exposureMin) uiState.exposureMax.toFloat() else uiState.exposureMin.toFloat() + 1f),
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("${uiState.exposureIndex}", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }

            // Zoom slider
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom", color = Color.White, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = uiState.zoomRatio,
                    onValueChange = { viewModel.setZoom(it) },
                    valueRange = uiState.minZoomRatio..(if (uiState.maxZoomRatio > uiState.minZoomRatio) uiState.maxZoomRatio else uiState.minZoomRatio + 1f),
                    colors = SliderDefaults.colors(thumbColor = GoldPrimary, activeTrackColor = GoldPrimary),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 1: camera-related controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(
                    icon = Icons.Filled.Cameraswitch,
                    label = "Flip",
                    enabled = uiState.streamState != StreamState.LIVE,
                    onClick = { viewModel.flip() }
                )
                ControlIcon(
                    icon = if (uiState.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    label = "Torch",
                    tint = if (uiState.isTorchOn) GoldPrimary else Color.White,
                    enabled = uiState.isTorchAvailable,
                    onClick = { viewModel.toggleTorch() }
                )
                Box {
                    ControlIcon(
                        icon = Icons.Filled.WbAuto,
                        label = uiState.whiteBalance.label,
                        onClick = { showWbMenu = true }
                    )
                    DropdownMenu(expanded = showWbMenu, onDismissRequest = { showWbMenu = false }) {
                        WhiteBalancePreset.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = { viewModel.setWhiteBalance(preset); showWbMenu = false }
                            )
                        }
                    }
                }
                Box {
                    ControlIcon(
                        icon = Icons.Filled.GridView,
                        label = "Layout",
                        onClick = { showLayoutMenu = true }
                    )
                    LayoutPickerMenu(
                        expanded = showLayoutMenu,
                        onDismiss = { showLayoutMenu = false },
                        onSelect = { layoutViewModel.setPreset(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: production controls (audio, overlays, screen)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlIcon(
                    icon = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (uiState.isMicMuted) "Muted" else "Mic",
                    tint = if (uiState.isMicMuted) CrimsonBright else Color.White,
                    onClick = { viewModel.toggleMic() }
                )
                ControlIcon(
                    icon = Icons.Filled.Layers,
                    label = "Overlays",
                    onClick = { showOverlayPanel = true }
                )
                ControlIcon(
                    icon = Icons.Filled.ScreenShare,
                    label = if (uiState.screenSharePermissionGranted) "Granted" else "Screen",
                    tint = if (uiState.screenSharePermissionGranted) GoldPrimary else Color.White,
                    onClick = { screenSharePermissionLauncher.launch(screenShareController.createCaptureIntent()) }
                )
                ControlIcon(
                    icon = Icons.Filled.Tune,
                    label = "Audio mixer",
                    onClick = { showAudioMixer = true }
                )
            }

            if (uiState.screenSharePermissionGranted && !uiState.screenShareWiredToStream) {
                Text(
                    "Screen capture permission granted — not yet wired into the broadcast (see README)",
                    color = GoldPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, start = 12.dp, end = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (uiState.streamState == StreamState.LIVE) {
                        viewModel.stopLive()
                    } else {
                        viewModel.goLive(rtmpConfig.fullUrl())
                    }
                },
                enabled = uiState.cameraReady && uiState.streamState != StreamState.CONNECTING,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.streamState == StreamState.LIVE) CrimsonBright else GoldPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    when (uiState.streamState) {
                        StreamState.LIVE -> "STOP LIVE"
                        StreamState.CONNECTING -> "CONNECTING…"
                        else -> "GO LIVE"
                    },
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }

            if (!uiState.dualCameraAvailable) {
                Text(
                    "Second camera not available on this device",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                )
            } else {
                Text(
                    if (uiState.dualCameraConcurrentSupported)
                        "Simultaneous front+back capture: supported on this device"
                    else
                        "Simultaneous front+back capture: not supported on this device (has both cameras, but not concurrently)",
                    color = if (uiState.dualCameraConcurrentSupported) LiveGreen else Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, start = 16.dp, end = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }

    if (showOverlayPanel) {
        OverlayPanel(
            viewModel = overlayViewModel,
            onDismiss = { showOverlayPanel = false }
        )
    }

    if (showAudioMixer) {
        AudioMixerSheet(
            uiState = uiState,
            onGainChange = { viewModel.setMicGain(it) },
            onMusicVolumeChange = { viewModel.setMusicVolume(it) },
            onConnectBluetooth = { viewModel.connectBluetoothMic() },
            onDismiss = { showAudioMixer = false }
        )
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = if (enabled) tint else Color.Gray)
        }
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}
