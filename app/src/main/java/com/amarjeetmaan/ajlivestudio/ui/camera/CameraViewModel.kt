package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.content.Intent
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amarjeetmaan.ajlivestudio.audio.AudioController
import com.amarjeetmaan.ajlivestudio.streaming.EngineVideoConfig
import com.amarjeetmaan.ajlivestudio.streaming.StreamEngine
import com.amarjeetmaan.ajlivestudio.ui.setup.BitratePreset
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {
    var uiState by mutableStateOf(CameraUiState())
        private set

    private var engine: StreamEngine? = null
    private var audioController: AudioController? = null
    private var activePreviewSurface: Surface? = null

    fun initialize(context: Context, setupState: StudioSetupState) {
        engine = StreamEngine(context)
        audioController = AudioController(context)
        uiState = uiState.copy(isMicMuted = audioController?.isMicMuted() ?: false)

        val targetRotation = when (setupState.orientation) {
            com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation.LANDSCAPE -> android.view.Surface.ROTATION_90
            com.amarjeetmaan.ajlivestudio.ui.setup.StreamOrientation.PORTRAIT -> android.view.Surface.ROTATION_0
        }

        viewModelScope.launch {
            runCatching {
                engine?.initialize(
                    EngineVideoConfig(
                        width = setupState.resolution.width,
                        height = setupState.resolution.height,
                        fps = setupState.frameRate.value,
                        bitrateBps = resolveBitrateBps(setupState.bitrate, setupState.resolution.width)
                    ),
                    targetRotation = targetRotation
                )
            }.onSuccess {
                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable = engine?.isTorchAvailable() ?: false,
                    minZoomRatio = engine?.zoomRange()?.start ?: 1f,
                    maxZoomRatio = engine?.zoomRange()?.endInclusive ?: 1f,
                    zoomRatio = engine?.zoomRange()?.start ?: 1f,
                    exposureMin = engine?.exposureRange()?.first ?: 0,
                    exposureMax = engine?.exposureRange()?.last ?: 0
                )
            }.onFailure { e -> uiState = uiState.copy(streamState = StreamState.ERROR, errorMessage = e.message) }
        }
    }

    fun startPreview(surface: Surface) {
        activePreviewSurface = surface
        viewModelScope.launch { engine?.startCameraPreview(surface) }
    }

    fun stopPreview() {
        activePreviewSurface = null
        viewModelScope.launch { engine?.stopCameraPreview() }
    }

    fun goLive(rtmpUrl: String) {
        uiState = uiState.copy(streamState = StreamState.CONNECTING, errorMessage = null)
        viewModelScope.launch {
            runCatching { engine?.goLive(rtmpUrl) }
                .onSuccess { uiState = uiState.copy(streamState = StreamState.LIVE) }
                .onFailure { e -> uiState = uiState.copy(streamState = StreamState.ERROR, errorMessage = e.message ?: "Connection failed") }
        }
    }

    fun stopLive() {
        viewModelScope.launch {
            runCatching { engine?.stopLive() }
            uiState = uiState.copy(streamState = StreamState.IDLE)
        }
    }

    fun flip() {
        viewModelScope.launch {
            runCatching { engine?.flipCamera() ?: false }
                .onSuccess { nowFront ->
                    uiState = uiState.copy(isFrontCamera = nowFront, isTorchOn = false, isTorchAvailable = engine?.isTorchAvailable() ?: false)
                    activePreviewSurface?.let { engine?.startCameraPreview(it) }
                }
        }
    }

    fun toggleTorch() {
        if (!uiState.isTorchAvailable) return
        val newState = !uiState.isTorchOn
        viewModelScope.launch {
            runCatching { engine?.setTorch(newState) }
                .onSuccess { uiState = uiState.copy(isTorchOn = newState) }
        }
    }

    fun setZoom(ratio: Float) {
        uiState = uiState.copy(zoomRatio = ratio)
        viewModelScope.launch { runCatching { engine?.setZoomRatio(ratio) } }
    }

    fun setExposure(index: Int) {
        uiState = uiState.copy(exposureIndex = index)
        viewModelScope.launch { runCatching { engine?.setExposureCompensation(index) } }
    }

    fun toggleMic() {
        audioController?.let {
            val newMuted = !uiState.isMicMuted
            it.setMicMuted(newMuted)
            uiState = uiState.copy(isMicMuted = newMuted)
        }
    }

    fun onScreenSharePermissionResult(granted: Boolean, data: Intent?) {
        uiState = uiState.copy(screenSharePermissionGranted = granted)
    }

    fun setMicGain(percent: Int) { uiState = uiState.copy(micGainPercent = percent.coerceIn(0, 200)) }
    fun setMusicVolume(percent: Int) { uiState = uiState.copy(musicVolumePercent = percent.coerceIn(0, 100)) }
    fun connectBluetoothMic() { audioController?.startBluetoothScoIfAvailable() }
    fun setWhiteBalance(preset: WhiteBalancePreset) {}

    private fun resolveBitrateBps(preset: BitratePreset, widthHint: Int): Int {
        return (preset.kbps ?: if (widthHint >= 1920) 5000 else 3000) * 1000
    }
}
