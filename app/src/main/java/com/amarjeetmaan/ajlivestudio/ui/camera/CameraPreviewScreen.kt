package com.amarjeetmaan.ajlivestudio.ui.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.ui.live.RtmpConfig
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayLayer
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayPanel
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayViewModel
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import com.amarjeetmaan.ajlivestudio.screenshare.ScreenShareController
import com.amarjeetmaan.ajlivestudio.ui.theme.*

@Composable
fun CameraPreviewScreen(
    setupState: StudioSetupState,
    rtmpConfig: RtmpConfig,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState = viewModel.uiState

    var showOverlayPanel by remember { mutableStateOf(false) }
    var showAudioMixer by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    
    // CameraX Preview View.
    // IMPORTANT: must be COMPATIBLE (TextureView-backed), not the default
    // PERFORMANCE mode (SurfaceView-backed) — SurfaceView content is
    // invisible to MediaProjection screen capture and shows up as black
    // to the viewer, since we broadcast by screen-recording this UI.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val screenShareController = remember { ScreenShareController(context) }
    val screenSharePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (screenShareController.isResultGranted(result.resultCode) && result.data != null) {
            // Permission Granted -> START LIVE STREAM!
            val mediaProjection = screenShareController.getMediaProjection(result.resultCode, result.data!!)
            viewModel.goLive(rtmpConfig.fullUrl(), mediaProjection)
        }
    }

    BackHandler(enabled = uiState.streamState == StreamState.LIVE) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Stop Live Stream?", color = Color.Black) },
            text = { Text("Are you sure you want to end the broadcast?", color = Color.DarkGray) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; viewModel.stopLive(); onBack() }) { Text("End Stream", color = CrimsonBright) }
            },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancel", color = Color.Gray) } },
            containerColor = Color.White
        )
    }

    val isLandscape = setupState.orientation == com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation.LANDSCAPE
    DisposableEffect(isLandscape) {
        val activity = context as? Activity
        activity?.requestedOrientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context, setupState)
        viewModel.startCameraX(context, lifecycleOwner, previewView)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        // 1. CAMERA BACKGROUND (NATIVE)
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // 2. OVERLAYS (DRAG/ZOOM)
        OverlayLayer(
            items = overlayViewModel.items,
            editable = true,
            webReloadTick = overlayViewModel.webReloadTick,
            onTransform = { id, x, y, scale -> overlayViewModel.updateTransform(id, x, y, scale) }
        )

        // 3. UI CONTROLS
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(NavyDeep.copy(alpha = 0.55f))) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, enabled = uiState.streamState != StreamState.LIVE) { Text("← Setup", color = Color.White) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = if(uiState.streamState == StreamState.LIVE) LiveGreen else Color.Gray
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if(uiState.streamState == StreamState.LIVE) "LIVE" else "READY", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        uiState.errorMessage?.let { msg ->
            Text(msg, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp).background(CrimsonBright).padding(8.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(NavyDeep.copy(alpha = 0.8f)).padding(bottom = 16.dp, top = 10.dp)) {
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ControlIcon(icon = Icons.Filled.Cameraswitch, label = "Flip", enabled = uiState.streamState != StreamState.LIVE) { viewModel.flip(lifecycleOwner, previewView) }
                ControlIcon(icon = if (uiState.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, label = "Torch", tint = if (uiState.isTorchOn) GoldPrimary else Color.White, enabled = uiState.isTorchAvailable) { viewModel.toggleTorch() }
                ControlIcon(icon = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic, label = "Mic", tint = if (uiState.isMicMuted) CrimsonBright else Color.White) { viewModel.toggleMic() }
                ControlIcon(icon = Icons.Filled.Layers, label = "Overlays") { showOverlayPanel = true }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (uiState.streamState == StreamState.LIVE) {
                        showExitDialog = true
                    } else {
                        // THIS TRIGGERS THE SCREEN CAPTURE AND STARTS THE LIVE STREAM
                        screenSharePermissionLauncher.launch(screenShareController.createCaptureIntent())
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.streamState == StreamState.LIVE) CrimsonBright else GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (uiState.streamState == StreamState.LIVE) "STOP LIVE" else "GO LIVE", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    if (showOverlayPanel) OverlayPanel(viewModel = overlayViewModel, onDismiss = { showOverlayPanel = false })
    if (showAudioMixer) AudioMixerSheet(uiState = uiState, onGainChange = { viewModel.setMicGain(it) }, onMusicVolumeChange = { viewModel.setMusicVolume(it) }, onConnectBluetooth = { viewModel.connectBluetoothMic() }, onDismiss = { showAudioMixer = false })
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label, tint = if (enabled) tint else Color.Gray) }
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}
