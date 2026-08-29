package com.amarjeetmaan.ajlivestudio.ui.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amarjeetmaan.ajlivestudio.ui.live.RtmpConfig
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayLayer
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayPanel
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayViewModel
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import com.amarjeetmaan.ajlivestudio.ui.theme.CrimsonBright
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.LiveGreen
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyDeep
import io.github.thibaultbee.streampack.services.MediaProjectionUtils

@Composable
fun CameraPreviewScreen(
    setupState: StudioSetupState,
    rtmpConfig: RtmpConfig,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState

    var showOverlayPanel by remember { mutableStateOf(false) }
    var showAudioMixer by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    
    val screenSharePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenLive(context, result.resultCode, result.data!!, rtmpConfig.fullUrl())
        }
    }

    BackHandler(enabled = uiState.streamState == StreamState.LIVE) { showExitDialog = true }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Stop Live Stream?", color = Color.Black) },
            text = { Text("Are you sure you want to end the current live broadcast?", color = Color.DarkGray) },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; viewModel.stopLive(context); onBack() }) { 
                    Text("End Stream", color = CrimsonBright, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) 
                }
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

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initialize(context, setupState)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (uiState.cameraReady) {
            val previewRatio = if (isLandscape) 16f / 9f else 9f / 16f
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(previewRatio).background(Color.Black)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            android.view.SurfaceView(ctx).apply {
                                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: android.view.SurfaceHolder) { viewModel.startPreview(holder.surface) }
                                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {}
                                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) { viewModel.stopPreview() }
                                })
                            }
                        }
                    )
                }
            }
        }

        OverlayLayer(
            items = overlayViewModel.items,
            editable = true,
            webReloadTick = overlayViewModel.webReloadTick,
            onTransform = { id, x, y, scale -> overlayViewModel.updateTransform(id, x, y, scale) }
        )

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(NavyDeep.copy(alpha = 0.55f))) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, enabled = uiState.streamState != StreamState.LIVE) { Text("← Setup", color = Color.White) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = if (uiState.streamState == StreamState.LIVE) LiveGreen else Color.Gray
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (uiState.streamState == StreamState.LIVE) "LIVE" else "READY", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(NavyDeep.copy(alpha = 0.8f)).padding(bottom = 16.dp, top = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ControlIcon(icon = Icons.Filled.Cameraswitch, label = "Flip", enabled = true, onClick = { viewModel.flip() })
                ControlIcon(icon = if (uiState.isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff, label = "Torch", tint = if (uiState.isTorchOn) GoldPrimary else Color.White, enabled = uiState.isTorchAvailable, onClick = { viewModel.toggleTorch() })
                ControlIcon(icon = if (uiState.isMicMuted) Icons.Filled.MicOff else Icons.Filled.Mic, label = "Mic", tint = if (uiState.isMicMuted) CrimsonBright else Color.White, onClick = { viewModel.toggleMic(context) })
                ControlIcon(icon = Icons.Filled.Layers, label = "Overlays", onClick = { showOverlayPanel = true })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (uiState.streamState == StreamState.LIVE) {
                        showExitDialog = true
                    } else {
                        try {
                            screenSharePermissionLauncher.launch(MediaProjectionUtils.createScreenCaptureIntent(context))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                },
                enabled = uiState.cameraReady && uiState.streamState != StreamState.CONNECTING,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (uiState.streamState == StreamState.LIVE) CrimsonBright else GoldPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (uiState.streamState == StreamState.LIVE) "STOP LIVE" else "GO LIVE", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }

    if (showOverlayPanel) OverlayPanel(viewModel = overlayViewModel, onDismiss = { showOverlayPanel = false })
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label, tint = if (enabled) tint else Color.Gray) }
        Text(label, color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}
