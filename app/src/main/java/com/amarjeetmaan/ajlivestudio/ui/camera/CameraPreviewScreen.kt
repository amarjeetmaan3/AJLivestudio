package com.amarjeetmaan.ajlivestudio.ui.camera

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbAuto
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import com.amarjeetmaan.ajlivestudio.ui.theme.CrimsonBright
import com.amarjeetmaan.ajlivestudio.ui.theme.GoldPrimary
import com.amarjeetmaan.ajlivestudio.ui.theme.LiveGreen
import com.amarjeetmaan.ajlivestudio.ui.theme.NavyDeep
import kotlin.math.roundToInt

@Composable
fun CameraPreviewScreen(
    setupState: StudioSetupState,
    rtmpConfig: RtmpConfig,
    onBack: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel()
) {

    val context = LocalContext.current
    val activity = context as? Activity

    val uiState =
        viewModel.uiState

    val overlayItems =
        overlayViewModel.items.toList()

    var showWbMenu by remember {
        mutableStateOf(false)
    }

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

    /*
     * ------------------------------------------------------------
     * HARD ORIENTATION LOCK
     * ------------------------------------------------------------
     *
     * Auto-rotate does not control this screen.
     */
    DisposableEffect(
        activity,
        setupState.orientation
    ) {

        val previousOrientation =
            activity?.requestedOrientation
                ?: ActivityInfo
                    .SCREEN_ORIENTATION_UNSPECIFIED

        val previousUiFlags =
            @Suppress("DEPRECATION")
            activity?.window
                ?.decorView
                ?.systemUiVisibility
                ?: 0

        activity?.actionBar?.hide()

        activity?.window?.addFlags(
            WindowManager.LayoutParams
                .FLAG_KEEP_SCREEN_ON
        )

        @Suppress("DEPRECATION")
        activity?.window?.decorView
            ?.systemUiVisibility =
            ViewSystemUi.FULLSCREEN_FLAGS

        activity?.requestedOrientation =
            when (
                setupState.orientation
            ) {

                StreamOrientation.LANDSCAPE ->
                    ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE

                StreamOrientation.PORTRAIT ->
                    ActivityInfo
                        .SCREEN_ORIENTATION_PORTRAIT
            }

        onDispose {

            activity?.requestedOrientation =
                previousOrientation

            @Suppress("DEPRECATION")
            activity?.window?.decorView
                ?.systemUiVisibility =
                previousUiFlags

            activity?.window?.clearFlags(
                WindowManager.LayoutParams
                    .FLAG_KEEP_SCREEN_ON
            )

            activity?.actionBar?.show()
        }
    }

    /*
     * Initialize StreamPack using exactly the selected
     * orientation and resolution.
     */
    LaunchedEffect(
        setupState
    ) {
        viewModel.initialize(
            context,
            setupState
        )
    }

    /*
     * Update native broadcast overlay.
     */
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
                context = context,
                items = overlayItems,
                containerWidthPx =
                    previewWidthPx,
                containerHeightPx =
                    previewHeightPx
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        /*
         * FULL SCREEN CAMERA PREVIEW
         */
        AndroidView(
            modifier =
                Modifier.fillMaxSize(),

            factory = { ctx ->

                TextureView(ctx).apply {

                    isOpaque = true

                    surfaceTextureListener =
                        object :
                            TextureView
                                .SurfaceTextureListener {

                            override fun
                                onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
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
                                    surfaceTexture: SurfaceTexture,
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
                                    surfaceTexture: SurfaceTexture
                                ): Boolean {

                                viewModel.stopPreview()

                                previewWidthPx = 0
                                previewHeightPx = 0

                                return true
                            }

                            override fun
                                onSurfaceTextureUpdated(
                                    surfaceTexture: SurfaceTexture
                                ) {
                                // Nothing required.
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

        /*
         * Compose overlay editing layer.
         */
        OverlayLayer(
            items = overlayItems,
            editable = true,
            webReloadTick =
                overlayViewModel.webReloadTick,
            onTransform = {
                    id,
                    x,
                    y,
                    scale ->

                overlayViewModel.updateTransform(
                    id,
                    x,
                    y,
                    scale
                )
            }
        )

        /*
         * LIVE STATUS BAR
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    NavyDeep.copy(
                        alpha = 0.55f
                    )
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                TextButton(
                    onClick = onBack,
                    enabled =
                        uiState.streamState !=
                                StreamState.LIVE
                ) {

                    Text(
                        "← Setup",
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    val (
                        dotColor,
                        label
                    ) =
                        when (
                            uiState.streamState
                        ) {

                            StreamState.IDLE ->
                                Color.Gray to
                                        "Not live"

                            StreamState.CONNECTING ->
                                GoldPrimary to
                                        "Connecting…"

                            StreamState.LIVE ->
                                LiveGreen to
                                        "LIVE"

                            StreamState.ERROR ->
                                CrimsonBright to
                                        "Error"
                        }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                dotColor
                            )
                    )

                    Spacer(
                        modifier =
                            Modifier.width(6.dp)
                    )

                    Text(
                        label,
                        color = Color.White,
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )

                    Spacer(
                        modifier =
                            Modifier.width(10.dp)
                    )

                    Text(
                        "${setupState.resolution.label} · " +
                                "${setupState.frameRate.value}fps",
                        color =
                            Color.White.copy(
                                alpha = 0.7f
                            ),
                        style =
                            MaterialTheme
                                .typography
                                .labelSmall
                    )
                }
            }
        }

        /*
         * ERROR
         */
        uiState.errorMessage?.let { message ->

            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
                    .background(
                        CrimsonBright.copy(
                            alpha = 0.85f
                        )
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 6.dp
                    )
            )
        }

        /*
         * BOTTOM CONTROLS
         */
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    NavyDeep.copy(
                        alpha = 0.8f
                    )
                )
                .padding(
                    bottom = 16.dp,
                    top = 10.dp
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    "EV",
                    color = Color.White,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )

                Slider(
                    value =
                        uiState.exposureIndex
                            .toFloat(),

                    onValueChange = {
                        viewModel.setExposure(
                            it.roundToInt()
                        )
                    },

                    valueRange =
                        uiState.exposureMin
                            .toFloat()..(
                            if (
                                uiState.exposureMax >
                                uiState.exposureMin
                            ) {
                                uiState.exposureMax
                                    .toFloat()
                            } else {
                                uiState.exposureMin
                                    .toFloat() + 1f
                            }
                        ),

                    colors =
                        SliderDefaults.colors(
                            thumbColor =
                                GoldPrimary,
                            activeTrackColor =
                                GoldPrimary
                        ),

                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(
                                horizontal = 8.dp
                            )
                )

                Text(
                    "${uiState.exposureIndex}",
                    color = Color.White,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 20.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    "Zoom",
                    color = Color.White,
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall
                )

                Slider(
                    value =
                        uiState.zoomRatio,

                    onValueChange = {
                        viewModel.setZoom(it)
                    },

                    valueRange =
                        uiState.minZoomRatio..(
                            if (
                                uiState.maxZoomRatio >
                                uiState.minZoomRatio
                            ) {
                                uiState.maxZoomRatio
                            } else {
                                uiState.minZoomRatio + 1f
                            }
                        ),

                    colors =
                        SliderDefaults.colors(
                            thumbColor =
                                GoldPrimary,
                            activeTrackColor =
                                GoldPrimary
                        ),

                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(
                                horizontal = 8.dp
                            )
                )
            }

            Spacer(
                modifier =
                    Modifier.height(4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp
                    ),
                horizontalArrangement =
                    Arrangement.SpaceEvenly,
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                ControlIcon(
                    icon =
                        Icons.Filled.Cameraswitch,
                    label = "Flip",
                    enabled =
                        uiState.streamState !=
                                StreamState.LIVE,
                    onClick = {
                        viewModel.flip()
                    }
                )

                ControlIcon(
                    icon =
                        if (
                            uiState.isTorchOn
                        ) {
                            Icons.Filled.FlashOn
                        } else {
                            Icons.Filled.FlashOff
                        },
                    label = "Torch",
                    tint =
                        if (
                            uiState.isTorchOn
                        ) {
                            GoldPrimary
                        } else {
                            Color.White
                        },
                    enabled =
                        uiState.isTorchAvailable,
                    onClick = {
                        viewModel.toggleTorch()
                    }
                )

                Box {

                    ControlIcon(
                        icon =
                            Icons.Filled.WbAuto,
                        label =
                            uiState
                                .whiteBalance
                                .label,
                        onClick = {
                            showWbMenu = true
                        }
                    )

                    DropdownMenu(
                        expanded =
                            showWbMenu,
                        onDismissRequest = {
                            showWbMenu = false
                        }
                    ) {

                        WhiteBalancePreset
                            .entries
                            .forEach { preset ->

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            preset.label
                                        )
                                    },
                                    onClick = {

                                        viewModel
                                            .setWhiteBalance(
                                                preset
                                            )

                                        showWbMenu =
                                            false
                                    }
                                )
                            }
                    }
                }

                ControlIcon(
                    icon =
                        if (
                            uiState.isMicMuted
                        ) {
                            Icons.Filled.MicOff
                        } else {
                            Icons.Filled.Mic
                        },
                    label =
                        if (
                            uiState.isMicMuted
                        ) {
                            "Muted"
                        } else {
                            "Mic"
                        },
                    tint =
                        if (
                            uiState.isMicMuted
                        ) {
                            CrimsonBright
                        } else {
                            Color.White
                        },
                    onClick = {
                        viewModel.toggleMic(
                            context
                        )
                    }
                )

                ControlIcon(
                    icon =
                        Icons.Filled.Layers,
                    label = "Overlays",
                    onClick = {
                        showOverlayPanel = true
                    }
                )

                ControlIcon(
                    icon =
                        Icons.Filled.Tune,
                    label = "Audio",
                    onClick = {
                        showAudioMixer = true
                    }
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )

            Button(
                onClick = {

                    if (
                        uiState.streamState ==
                        StreamState.LIVE
                    ) {
                        showStopLiveDialog = true
                    } else {
                        viewModel.goLive(
                            rtmpConfig.fullUrl()
                        )
                    }
                },

                enabled =
                    uiState.cameraReady &&
                            uiState.streamState !=
                            StreamState.CONNECTING,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 24.dp
                        )
                        .height(52.dp),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (
                                uiState.streamState ==
                                StreamState.LIVE
                            ) {
                                CrimsonBright
                            } else {
                                GoldPrimary
                            }
                    ),

                shape =
                    RoundedCornerShape(12.dp)
            ) {

                Text(
                    when (
                        uiState.streamState
                    ) {

                        StreamState.LIVE ->
                            "STOP LIVE"

                        StreamState.CONNECTING ->
                            "CONNECTING…"

                        else ->
                            "GO LIVE"
                    },

                    fontWeight =
                        androidx.compose.ui.text.font
                            .FontWeight.Bold
                )
            }
        }
    }

    /*
     * STOP LIVE DIALOG
     */
    if (showStopLiveDialog) {

        AlertDialog(

            onDismissRequest = {
                showStopLiveDialog = false
            },

            title = {
                Text(
                    "Stop live stream?"
                )
            },

            text = {
                Text(
                    "Are you sure you want to stop " +
                            "the current YouTube live stream?"
                )
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        showStopLiveDialog = false

                        viewModel.stopLive()
                    }
                ) {

                    Text(
                        "STOP LIVE",
                        color = CrimsonBright
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showStopLiveDialog = false
                    }
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showOverlayPanel) {

        OverlayPanel(
            viewModel = overlayViewModel,
            onDismiss = {
                showOverlayPanel = false
            }
        )
    }

    if (showAudioMixer) {

        AudioMixerSheet(
            uiState = uiState,

            onGainChange = {
                viewModel.setMicGain(it)
            },

            onMusicVolumeChange = {
                viewModel.setMusicVolume(it)
            },

            onConnectBluetooth = {
                viewModel.connectBluetoothMic()
            },

            onDismiss = {
                showAudioMixer = false
            }
        )
    }
}

private object ViewSystemUi {

    @Suppress("DEPRECATION")
    const val FULLSCREEN_FLAGS =
        View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
}

@Composable
private fun ControlIcon(
    icon:
        androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    enabled: Boolean = true,
    onClick: () -> Unit
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {

            Icon(
                imageVector = icon,
                contentDescription = label,
                tint =
                    if (enabled) {
                        tint
                    } else {
                        Color.Gray
                    }
            )
        }

        Text(
            label,
            color =
                if (enabled) {
                    Color.White
                } else {
                    Color.Gray
                },
            style =
                MaterialTheme
                    .typography
                    .labelSmall
        )
    }
}
