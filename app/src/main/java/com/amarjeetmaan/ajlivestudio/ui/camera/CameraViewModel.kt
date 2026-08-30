package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amarjeetmaan.ajlivestudio.audio.AudioController
import com.amarjeetmaan.ajlivestudio.streaming.EngineVideoConfig
import com.amarjeetmaan.ajlivestudio.streaming.OverlayRenderer
import com.amarjeetmaan.ajlivestudio.streaming.StreamEngine
import com.amarjeetmaan.ajlivestudio.ui.overlay.OverlayItem
import com.amarjeetmaan.ajlivestudio.ui.setup.BitratePreset
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
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
        engine?.close()
        engine = StreamEngine(context)
        audioController = AudioController(context)
        uiState = uiState.copy(isMicMuted = audioController?.isMicMuted() ?: false)

        val targetRotation = when (setupState.orientation) {
            com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation.LANDSCAPE -> Surface.ROTATION_90
            com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation.PORTRAIT -> Surface.ROTATION_0
        }

        viewModelScope.launch {
            runCatching {
                engine?.initializeCamera(
                    EngineVideoConfig(
                        width = setupState.resolution.width,
                        height = setupState.resolution.height,
                        fps = setupState.frameRate.value,
                        bitrateBps = resolveBitrateBps(
                            setupState.bitrate,
                            setupState.resolution.width
                        )
                    ),
                    targetRotation = targetRotation
                )
            }.onSuccess {
                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable = engine?.isTorchAvailable() ?: false,
                    errorMessage = null
                )
            }.onFailure { error ->
                uiState = uiState.copy(
                    cameraReady = false,
                    streamState = StreamState.ERROR,
                    errorMessage = error.message ?: "Camera initialization failed"
                )
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

        val bitmap = OverlayRenderer.render(
            context = context,
            items = items,
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            videoWidth = state.resolution.width,
            videoHeight = state.resolution.height
        )

        engine?.updateOverlay(bitmap)
    }

    fun startPreview(surface: Surface) {
        activePreviewSurface = surface
        viewModelScope.launch {
            runCatching { engine?.startCameraPreview(surface) }
        }
    }

    fun stopPreview() {
        activePreviewSurface = null
        viewModelScope.launch { engine?.stopCameraPreview() }
    }

    fun goLive(rtmpUrl: String) {
        uiState = uiState.copy(
            streamState = StreamState.CONNECTING,
            errorMessage = null
        )

        viewModelScope.launch {
            runCatching { engine?.goLive(rtmpUrl) }
                .onSuccess {
                    uiState = uiState.copy(streamState = StreamState.LIVE)
                }
                .onFailure { e ->
                    uiState = uiState.copy(
                        streamState = StreamState.ERROR,
                        errorMessage = e.message ?: "Unable to start live stream"
                    )
                }
        }
    }

    fun stopLive(context: Context) {
        viewModelScope.launch {
            runCatching { engine?.stopLive() }
            uiState = uiState.copy(streamState = StreamState.IDLE)
        }
    }

    fun flip() {
        if (uiState.streamState == StreamState.LIVE) return

        viewModelScope.launch {
            runCatching { engine?.flipCamera() ?: false }
                .onSuccess { nowFront ->
                    uiState = uiState.copy(
                        isFrontCamera = nowFront,
                        isTorchOn = false,
                        isTorchAvailable = engine?.isTorchAvailable() ?: false
                    )
                    activePreviewSurface?.let { engine?.startCameraPreview(it) }
                }
        }
    }

    fun toggleTorch() {
        if (!uiState.isTorchAvailable) return

        val newState = !uiState.isTorchOn
        viewModelScope.launch {
            runCatching { engine?.setTorch(newState) }
                .onSuccess {
                    uiState = uiState.copy(isTorchOn = newState)
                }
        }
    }

    fun toggleMic(context: Context) {
        val newMuted = !uiState.isMicMuted
        uiState = uiState.copy(isMicMuted = newMuted)
        audioController?.setMicMuted(newMuted)

        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.isMicrophoneMute = newMuted
            engine?.muteAudio(newMuted)
        } catch (_: Exception) {
        }
    }

    fun setZoom(ratio: Float) {
        uiState = uiState.copy(zoomRatio = ratio)
    }

    fun setExposure(index: Int) {
        uiState = uiState.copy(exposureIndex = index)
    }

    fun setMicGain(percent: Int) {
        uiState = uiState.copy(micGainPercent = percent.coerceIn(0, 200))
    }

    fun setMusicVolume(percent: Int) {
        uiState = uiState.copy(musicVolumePercent = percent.coerceIn(0, 100))
    }

    fun connectBluetoothMic() {
        audioController?.startBluetoothScoIfAvailable()
    }

    fun setWhiteBalance(preset: WhiteBalancePreset) {
        // Camera white-balance control is intentionally unchanged for now.
    }

    override fun onCleared() {
        engine?.close()
        engine = null
        audioController = null
        super.onCleared()
    }

    private fun resolveBitrateBps(preset: BitratePreset, widthHint: Int): Int {
        return (preset.kbps ?: if (widthHint >= 1920) 5000 else 3000) * 1000
    }
}
