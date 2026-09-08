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

    fun initialize(
        context: Context,
        setupState: StudioSetupState
    ) {
        if (engine != null && currentSetupState == setupState) {
            return
        }

        val oldEngine = engine

        engine = null
        currentSetupState = setupState

        viewModelScope.launch {

            runCatching {
                oldEngine?.release()
            }

            val newEngine =
                StreamEngine(
                    context.applicationContext
                )

            engine = newEngine

            audioController =
                AudioController(
                    context.applicationContext
                )

            uiState = uiState.copy(
                isMicMuted =
                    audioController?.isMicMuted()
                        ?: false,
                cameraReady = false,
                isTorchAvailable = false,
                isTorchOn = false,
                streamState = StreamState.IDLE,
                errorMessage = null
            )

            val targetRotation =
                when (setupState.orientation) {

                    StreamOrientation.LANDSCAPE ->
                        Surface.ROTATION_90

                    StreamOrientation.PORTRAIT ->
                        Surface.ROTATION_0
                }

            val safeResolution =
                resolveSafeResolution(
                    setupState
                )

            runCatching {

                newEngine.initializeCamera(

                    EngineVideoConfig(
                        width = safeResolution.first,
                        height = safeResolution.second,
                        fps = setupState.frameRate.value,
                        bitrateBps =
                            resolveBitrateBps(
                                setupState.bitrate,
                                safeResolution.first,
                                safeResolution.second
                            ),
                        maxWidth = safeResolution.first,
                        maxHeight = safeResolution.second
                    ),

                    targetRotation = targetRotation
                )

                activePreviewSurface?.let { surface ->
                    newEngine.startCameraPreview(surface)
                }

                newEngine.awaitCameraSource()

                val torchAvailable =
                    newEngine.isTorchAvailableAsync()

                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable = torchAvailable,
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

        val currentEngine = engine
            ?: return

        viewModelScope.launch {

            runCatching {

                currentEngine.startCameraPreview(
                    surface
                )

                currentEngine.awaitCameraSource()

            }.onSuccess {

                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable =
                        currentEngine
                            .isTorchAvailableAsync(),
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

    fun stopPreview(
        surface: Surface? = activePreviewSurface
    ) {

        if (
            surface == null ||
            surface === activePreviewSurface
        ) {
            activePreviewSurface = null
        }

        viewModelScope.launch {

            runCatching {
                engine?.stopCameraPreview()
            }

            runCatching {
                surface?.release()
            }
        }
    }

    fun updateOverlayBitmap(
        context: Context,
        items: List<OverlayItem>,
        containerWidthPx: Int,
        containerHeightPx: Int
    ) {

        val state =
            currentSetupState
                ?: return

        val safeResolution =
            resolveSafeResolution(state)

        val bitmap: Bitmap? =
            OverlayRenderer.render(
                context = context,
                items = items,
                containerWidthPx = containerWidthPx,
                containerHeightPx = containerHeightPx,
                videoWidth = safeResolution.first,
                videoHeight = safeResolution.second
            )

        engine?.updateOverlay(bitmap)
    }

    fun goLive(rtmpUrl: String) {

        if (!uiState.cameraReady) {
            return
        }

        uiState = uiState.copy(
            streamState = StreamState.CONNECTING,
            errorMessage = null
        )

        viewModelScope.launch {

            runCatching {

                engine?.goLive(rtmpUrl)
                    ?: throw IllegalStateException(
                        "Camera streamer is not ready"
                    )

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

        if (
            uiState.streamState ==
            StreamState.LIVE
        ) {
            return
        }

        val currentEngine =
            engine ?: return

        viewModelScope.launch {

            runCatching {

                currentEngine.flipCamera()

            }.onSuccess { nowFront ->

                uiState = uiState.copy(
                    isFrontCamera = nowFront,
                    isTorchOn = false,
                    isTorchAvailable =
                        currentEngine
                            .isTorchAvailableAsync(),
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

        val currentEngine =
            engine ?: return

        viewModelScope.launch {

            val available =
                runCatching {
                    currentEngine
                        .isTorchAvailableAsync()
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

            val newState =
                !uiState.isTorchOn

            runCatching {

                currentEngine.setTorch(
                    newState
                )

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

        val newMuted =
            !uiState.isMicMuted

        uiState =
            uiState.copy(
                isMicMuted = newMuted
            )

        audioController?.setMicMuted(
            newMuted
        )

        runCatching {

            val audioManager =
                context.getSystemService(
                    Context.AUDIO_SERVICE
                ) as android.media.AudioManager

            audioManager.isMicrophoneMute =
                newMuted

            engine?.muteAudio(
                newMuted
            )
        }
    }

    fun setZoom(ratio: Float) {

        uiState =
            uiState.copy(
                zoomRatio = ratio
            )
    }

    fun setExposure(index: Int) {

        uiState =
            uiState.copy(
                exposureIndex = index
            )
    }

    fun setMicGain(percent: Int) {

        uiState =
            uiState.copy(
                micGainPercent =
                    percent.coerceIn(0, 200)
            )
    }

    fun setMusicVolume(percent: Int) {

        uiState =
            uiState.copy(
                musicVolumePercent =
                    percent.coerceIn(0, 100)
            )
    }

    fun connectBluetoothMic() {

        audioController
            ?.startBluetoothScoIfAvailable()
    }

    fun setWhiteBalance(
        preset: WhiteBalancePreset
    ) {
        // Existing camera white-balance control.
    }

    override fun onCleared() {

        engine?.close()

        engine = null
        audioController = null
        activePreviewSurface = null

        super.onCleared()
    }

    /**
     * Prevents a 720p setup from ever being converted
     * to 1080p by an inconsistent setup object.
     *
     * 720p maximum:
     * 1280 x 720
     *
     * 1080p maximum:
     * 1920 x 1080
     */
    private fun resolveSafeResolution(
        state: StudioSetupState
    ): Pair<Int, Int> {

        val originalWidth =
            state.resolution.width

        val originalHeight =
            state.resolution.height

        val label =
            state.resolution.label
                .lowercase()

        val is720 =
            label.contains("720")

        val is1080 =
            label.contains("1080")

        if (is720) {

            return limitResolution(
                originalWidth,
                originalHeight,
                1280,
                720
            )
        }

        if (is1080) {

            return limitResolution(
                originalWidth,
                originalHeight,
                1920,
                1080
            )
        }

        return Pair(
            originalWidth.coerceAtMost(1920),
            originalHeight.coerceAtMost(1080)
        )
    }

    private fun limitResolution(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {

        if (
            width <= maxWidth &&
            height <= maxHeight
        ) {
            return Pair(
                width,
                height
            )
        }

        val widthScale =
            maxWidth.toFloat() /
                    width.toFloat()

        val heightScale =
            maxHeight.toFloat() /
                    height.toFloat()

        val scale =
            minOf(
                widthScale,
                heightScale
            )

        var newWidth =
            (width * scale)
                .toInt()

        var newHeight =
            (height * scale)
                .toInt()

        newWidth =
            (newWidth / 2) * 2

        newHeight =
            (newHeight / 2) * 2

        return Pair(
            newWidth.coerceAtLeast(2),
            newHeight.coerceAtLeast(2)
        )
    }

    private fun resolveBitrateBps(
        preset: BitratePreset,
        width: Int,
        height: Int
    ): Int {

        val defaultBitrate =
            when {

                width >= 1920 ||
                        height >= 1080 ->
                    5_000

                width >= 1280 ||
                        height >= 720 ->
                    3_000

                width >= 854 ||
                        height >= 480 ->
                    2_000

                else ->
                    1_200
            }

        return (
            preset.kbps
                ?: defaultBitrate
            )
            .coerceAtLeast(500)
            .times(1_000)
    }
}
