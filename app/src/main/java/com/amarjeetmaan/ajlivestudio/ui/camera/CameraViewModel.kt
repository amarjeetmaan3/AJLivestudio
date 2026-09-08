package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amarjeetmaan.ajlivestudio.audio.AudioController
import com.amarjeetmaan.ajlivestudio.streaming.EngineVideoConfig
import com.amarjeetmaan.ajlivestudio.streaming.StreamEngine
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayItem
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayRenderer
import com.amarjeetmaan.ajlivestudio.ui.setup.BitratePreset
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {

    var uiState by mutableStateOf(CameraUiState())
        private set

    private var engine: StreamEngine? = null
    private var audioController: AudioController? = null
    private var activePreviewSurface: Surface? = null
    private var currentSetupState: StudioSetupState? = null

    fun initialize(context: Context, setupState: StudioSetupState) {
        if (engine != null && currentSetupState == setupState) return

        currentSetupState = setupState

        val oldEngine = engine
        engine = null

        viewModelScope.launch {
            runCatching {
                oldEngine?.release()
            }

            val appContext = context.applicationContext
            val newEngine = StreamEngine(appContext)

            audioController?.let {
                runCatching { it.release() }
            }
            audioController = AudioController(appContext)

            uiState = uiState.copy(
                cameraReady = false,
                streamState = StreamState.IDLE,
                isTorchOn = false,
                isTorchAvailable = false,
                isMicMuted = audioController?.isMicMuted() ?: false,
                errorMessage = null
            )

            engine = newEngine

            val width: Int
            val height: Int

            /*
             * IMPORTANT:
             * Portrait must use portrait dimensions.
             * Landscape must use landscape dimensions.
             *
             * The setup resolution enum is always stored as:
             * 720p  = 1280x720
             * 1080p = 1920x1080
             */
            if (setupState.orientation == StreamOrientation.PORTRAIT) {
                width = minOf(
                    setupState.resolution.width,
                    setupState.resolution.height
                )
                height = maxOf(
                    setupState.resolution.width,
                    setupState.resolution.height
                )
            } else {
                width = maxOf(
                    setupState.resolution.width,
                    setupState.resolution.height
                )
                height = minOf(
                    setupState.resolution.width,
                    setupState.resolution.height
                )
            }

            val targetRotation =
                if (setupState.orientation == StreamOrientation.LANDSCAPE) {
                    Surface.ROTATION_90
                } else {
                    Surface.ROTATION_0
                }

            runCatching {
                newEngine.initializeCamera(
                    videoConfig = EngineVideoConfig(
                        width = width,
                        height = height,
                        fps = setupState.frameRate.value,
                        bitrateBps = resolveBitrateBps(
                            setupState.bitrate,
                            width,
                            height
                        )
                    ),
                    targetRotation = targetRotation
                )

                activePreviewSurface?.let { surface ->
                    newEngine.startCameraPreview(surface)
                }

                newEngine.awaitCameraSource()

                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable =
                        newEngine.isTorchAvailableAsync(),
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    cameraReady = false,
                    streamState = StreamState.ERROR,
                    errorMessage =
                        error.message
                            ?: "Camera initialization failed"
                )
            }
        }
    }

    fun startPreview(surface: Surface) {
        activePreviewSurface = surface

        val currentEngine = engine ?: return

        viewModelScope.launch {
            runCatching {
                currentEngine.startCameraPreview(surface)
                currentEngine.awaitCameraSource()
            }.onSuccess {
                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable =
                        currentEngine.isTorchAvailableAsync(),
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    cameraReady = false,
                    streamState = StreamState.ERROR,
                    errorMessage =
                        error.message
                            ?: "Unable to start camera preview"
                )
            }
        }
    }

    fun stopPreview(surface: Surface? = activePreviewSurface) {
        if (surface == null || surface === activePreviewSurface) {
            activePreviewSurface = null
        }

        viewModelScope.launch {
            runCatching {
                engine?.stopCameraPreview()
            }
        }
    }

    fun updateOverlayBitmap(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int
    ) {
        val state = currentSetupState ?: return

        val videoWidth =
            if (state.orientation == StreamOrientation.LANDSCAPE) {
                maxOf(
                    state.resolution.width,
                    state.resolution.height
                )
            } else {
                minOf(
                    state.resolution.width,
                    state.resolution.height
                )
            }

        val videoHeight =
            if (state.orientation == StreamOrientation.LANDSCAPE) {
                minOf(
                    state.resolution.width,
                    state.resolution.height
                )
            } else {
                maxOf(
                    state.resolution.width,
                    state.resolution.height
                )
            }

        val bitmap: Bitmap? =
            OverlayRenderer.render(
                context = context,
                items = items,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                videoWidth = videoWidth,
                videoHeight = videoHeight
            )

        engine?.updateOverlay(bitmap)
    }

    fun goLive(rtmpUrl: String) {
        if (!uiState.cameraReady) {
            uiState = uiState.copy(
                streamState = StreamState.ERROR,
                errorMessage = "Camera/Stream is not initialized"
            )
            return
        }

        if (rtmpUrl.isBlank()) {
            uiState = uiState.copy(
                streamState = StreamState.ERROR,
                errorMessage = "RTMP URL is empty"
            )
            return
        }

        uiState = uiState.copy(
            streamState = StreamState.CONNECTING,
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching {
                val currentEngine =
                    engine
                        ?: throw IllegalStateException(
                            "Stream is not initialized"
                        )

                currentEngine.goLive(rtmpUrl)
            }.onSuccess {
                uiState = uiState.copy(
                    streamState = StreamState.LIVE,
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    streamState = StreamState.ERROR,
                    errorMessage =
                        error.message
                            ?: "Unable to start live stream"
                )
            }
        }
    }

    fun stopLive() {
        viewModelScope.launch {
            runCatching {
                engine?.stopLive()
            }.onSuccess {
                uiState = uiState.copy(
                    streamState = StreamState.IDLE,
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    streamState = StreamState.ERROR,
                    errorMessage =
                        error.message
                            ?: "Unable to stop live stream"
                )
            }
        }
    }

    fun flip() {
        if (uiState.streamState == StreamState.LIVE) return

        val currentEngine = engine ?: return

        viewModelScope.launch {
            runCatching {
                currentEngine.flipCamera()
            }.onSuccess { nowFront ->

                uiState = uiState.copy(
                    isFrontCamera = nowFront,
                    isTorchOn = false,
                    isTorchAvailable =
                        currentEngine.isTorchAvailableAsync(),
                    errorMessage = null
                )

                activePreviewSurface?.let {
                    currentEngine.startCameraPreview(it)
                }
            }.onFailure { error ->
                uiState = uiState.copy(
                    errorMessage =
                        error.message
                            ?: "Unable to switch camera"
                )
            }
        }
    }

    fun toggleTorch() {
        val currentEngine = engine ?: return

        viewModelScope.launch {
            val available =
                runCatching {
                    currentEngine.isTorchAvailableAsync()
                }.getOrDefault(false)

            if (!available) {
                uiState = uiState.copy(
                    isTorchAvailable = false,
                    isTorchOn = false,
                    errorMessage =
                        "Flashlight is not available on this camera"
                )
                return@launch
            }

            val newState = !uiState.isTorchOn

            runCatching {
                currentEngine.setTorch(newState)
            }.onSuccess {
                uiState = uiState.copy(
                    isTorchAvailable = true,
                    isTorchOn = newState,
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    isTorchOn = false,
                    errorMessage =
                        error.message
                            ?: "Unable to change flashlight"
                )
            }
        }
    }

    fun toggleMic(context: Context) {
        val newMuted = !uiState.isMicMuted

        uiState = uiState.copy(
            isMicMuted = newMuted
        )

        audioController?.setMicMuted(newMuted)

        runCatching {
            val audioManager =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as android.media.AudioManager

            audioManager.isMicrophoneMute = newMuted

            engine?.muteAudio(newMuted)
        }
    }

    fun setZoom(ratio: Float) {
        uiState = uiState.copy(
            zoomRatio = ratio
        )
    }

    fun setExposure(index: Int) {
        uiState = uiState.copy(
            exposureIndex = index
        )
    }

    fun setMicGain(percent: Int) {
        uiState = uiState.copy(
            micGainPercent =
                percent.coerceIn(0, 200)
        )
    }

    fun setMusicVolume(percent: Int) {
        uiState = uiState.copy(
            musicVolumePercent =
                percent.coerceIn(0, 100)
        )
    }

    fun connectBluetoothMic() {
        audioController?.startBluetoothScoIfAvailable()
    }

    fun setWhiteBalance(
        preset: WhiteBalancePreset
    ) {
        // Kept for UI compatibility.
    }

    override fun onCleared() {
        engine?.close()
        engine = null
        audioController = null
        activePreviewSurface = null
        super.onCleared()
    }

    private fun resolveBitrateBps(
        preset: BitratePreset,
        width: Int,
        height: Int
    ): Int {
        val selected =
            preset.kbps
                ?: when {
                    width >= 1920 || height >= 1080 ->
                        5_000

                    width >= 1280 || height >= 720 ->
                        3_000

                    else ->
                        1_500
                }

        return selected.coerceIn(
            800,
            8_000
        ) * 1_000
    }
}
