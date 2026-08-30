package com.amarjeetmaan.ajlivestudio.ui.camera

import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbAuto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.math.roundToInt

@Composable
fun CameraPreviewScreen(
    setupState: StudioSetupState,
    rtmpConfig: RtmpConfig,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel()
) {
    val context =
        LocalContext.current

    val uiState =
        viewModel.uiState

    val overlayItems =
        overlayViewModel.items.toList()

    var showOverlayPanel by remember {
        mutableStateOf(false)
    }

    var showAudioMixer by remember {
        mutableStateOf(false)
    }

    var showStopLiveDialog by remember {
        mutableStateOf(false)
    }

    var previewWidthPx by remember {
        mutableStateOf(0)
    }

    var previewHeightPx by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(setupState) {
        viewModel.initialize(
            context,
            setupState
        )
    }

    LaunchedEffect(
        overlayItems,
        previewWidthPx,
        previewHeightPx,
        uiState.cameraReady
    ) {
        if (
            previewWidthPx > 0 &&
            previewHeightPx > 0 &&
            uiState.cameraReady
        ) {
            viewModel.updateOverlayBitmap(
                context =
                    context,
                items =
                    overlayItems,
                containerWidthPx =
                    previewWidthPx,
                containerHeightPx =
                    previewHeightPx
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black
                )
    ) {

        AndroidView(
            modifier =
                Modifier
                    .fillMaxSize(),

            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener =
                        object :
                            TextureView.SurfaceTextureListener {

                            override fun
                                onSurfaceTextureAvailable(
                                    surfaceTexture:
                                        android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    previewWidthPx =
                                        width

                                    previewHeightPx =
                                        height

                                    viewModel.startPreview(
                                        Surface(
                                            surfaceTexture
                                        )
                                    )
                                }

                            override fun
                                onSurfaceTextureSizeChanged(
                                    surfaceTexture:
                                        android.graphics.SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    previewWidthPx =
                                        width

                                    previewHeightPx =
                                        height
                                }

                            override fun
                                onSurfaceTextureDestroyed(
                                    surfaceTexture:
                                        android.graphics.SurfaceTexture
                                ): Boolean {
                                    viewModel.stopPreview()
                                    return true
                                }

                            override fun
                                onSurfaceTextureUpdated(
                                    surfaceTexture:
                                        android.graphics.SurfaceTexture
                                ) {
                            }
                        }
                }
            },

            update = { textureView ->
                previewWidthPx =
                    textureView.width

                previewHeightPx =
                    textureView.height
            }
        )

        OverlayLayer(
            items =
                overlayItems,
            modifier =
                Modifier.fillMaxSize()
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(
                        Alignment.BottomCenter
                    )
                    .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            if (
                uiState.errorMessage !=
                    null
            ) {
                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),
                    shape =
                        RoundedCornerShape(
                            12.dp
                        ),
                    color =
                        Color.Black.copy(
                            alpha = 0.75f
                        )
                ) {
                    Text(
                        text =
                            uiState.errorMessage
                                ?: "",
                        color =
                            Color.White,
                        modifier =
                            Modifier.padding(
                                12.dp
                            )
                    )
                }
            }

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                IconButton(
                    onClick = {
                        viewModel.flip()
                    },
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.Black.copy(
                                    alpha = 0.65f
                                )
                            )
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Cameraswitch,
                        contentDescription =
                            "Flip camera",
                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.toggleTorch()
                    },
                    enabled =
                        uiState
                            .isTorchAvailable,
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.Black.copy(
                                    alpha = 0.65f
                                )
                            )
                ) {
                    Icon(
                        imageVector =
                            if (
                                uiState.isTorchOn
                            ) {
                                Icons.Default.FlashOn
                            } else {
                                Icons.Default.FlashOff
                            },
                        contentDescription =
                            "Flashlight",
                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick = {
                        viewModel.toggleMic(
                            context
                        )
                    },
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.Black.copy(
                                    alpha = 0.65f
                                )
                            )
                ) {
                    Icon(
                        imageVector =
                            if (
                                uiState.isMicMuted
                            ) {
                                Icons.Default.MicOff
                            } else {
                                Icons.Default.Mic
                            },
                        contentDescription =
                            "Microphone",
                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick = {
                        showOverlayPanel =
                            true
                    },
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.Black.copy(
                                    alpha = 0.65f
                                )
                            )
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Layers,
                        contentDescription =
                            "Overlays",
                        tint =
                            Color.White
                    )
                }

                IconButton(
                    onClick = {
                        showAudioMixer =
                            true
                    },
                    modifier =
                        Modifier
                            .clip(
                                CircleShape
                            )
                            .background(
                                Color.Black.copy(
                                    alpha = 0.65f
                                )
                            )
                ) {
                    Icon(
                        imageVector =
                            Icons.Default.Tune,
                        contentDescription =
                            "Audio",
                        tint =
                            Color.White
                    )
                }
            }

            if (
                uiState.streamState ==
                    StreamState.LIVE
            ) {
                Button(
                    onClick = {
                        showStopLiveDialog =
                            true
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        "STOP LIVE"
                    )
                }
            } else {
                Button(
                    onClick = {
                        viewModel.goLive(
                            rtmpConfig.rtmpUrl
                        )
                    },
                    enabled =
                        uiState.cameraReady &&
                            uiState.streamState !=
                                StreamState.CONNECTING,
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (
                            uiState.streamState ==
                                StreamState.CONNECTING
                        ) {
                            "CONNECTING..."
                        } else {
                            "GO LIVE"
                        }
                    )
                }
            }
        }

        if (showOverlayPanel) {
            OverlayPanel(
                viewModel =
                    overlayViewModel,
                onDismiss = {
                    showOverlayPanel =
                        false
                }
            )
        }

        if (showAudioMixer) {
            // Keep the existing audio mixer
            // integration in the project.
            showAudioMixer = false
        }
    }

    if (showStopLiveDialog) {
        AlertDialog(
            onDismissRequest = {
                showStopLiveDialog =
                    false
            },
            title = {
                Text(
                    "Stop Live Stream?"
                )
            },
            text = {
                Text(
                    "Are you sure you want to stop the live stream?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showStopLiveDialog =
                            false

                        viewModel.stopLive()
                    }
                ) {
                    Text(
                        "STOP"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStopLiveDialog =
                            false
                    }
                ) {
                    Text(
                        "CANCEL"
                    )
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreview()
        }
    }
}
