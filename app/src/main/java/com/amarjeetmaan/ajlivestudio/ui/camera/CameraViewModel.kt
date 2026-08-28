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
import com.amarjeetmaan.ajlivestudio.camera.DualCameraCapability
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

    fun initialize(context: Context, setupState: StudioSetupState) {
        val streamEngine = StreamEngine(context)
        engine = streamEngine

        val audio = AudioController(context)
        audioController = audio
        uiState = uiState.copy(
            isMicMuted = audio.isMicMuted(),
            audioRoute = audio.currentInputRoute(),
        )

        viewModelScope.launch {
            runCatching {
                streamEngine.initialize(
                    EngineVideoConfig(
                        width = setupState.resolution.width,
                        height = setupState.resolution.height,
                        fps = setupState.frameRate.value,
                        bitrateBps = resolveBitrateBps(setupState.bitrate, setupState.resolution.width),
                    )
                )
            }.onSuccess {
                uiState = uiState.copy(
                    cameraReady = true,
                    isTorchAvailable = streamEngine.isTorchAvailable(),
                    dualCameraAvailable = streamEngine.hasFrontAndBack(),
                    dualCameraConcurrentSupported = DualCameraCapability(context).isSupported(),
                    minZoomRatio = streamEngine.zoomRange().start,
                    maxZoomRatio = streamEngine.zoomRange().endInclusive,
                    zoomRatio = streamEngine.zoomRange().start,
                    exposureMin = streamEngine.exposureRange().first,
                    exposureMax = streamEngine.exposureRange().last,
                )
            }.onFailure { e ->
                uiState = uiState.copy(streamState = StreamState.ERROR, errorMessage = e.message)
            }
        }
    }

    fun startPreview(surface: Surface) {
        viewModelScope.launch {
            engine?.startCameraPreview(surface)
        }
    }

    fun stopPreview() {
        viewModelScope.launch {
            engine?.stopCameraPreview()
        }
    }

    fun currentStreamer() = engine?.streamer

    fun goLive(rtmpUrl: String) {
        val streamEngine = engine ?: return
        uiState = uiState.copy(streamState = StreamState.CONNECTING, errorMessage = null)
        viewModelScope.launch {
            runCatching { streamEngine.goLive(rtmpUrl) }
                .onSuccess { uiState = uiState.copy(streamState = StreamState.LIVE) }
                .onFailure { e ->
                    uiState = uiState.copy(streamState = StreamState.ERROR, errorMessage = e.message ?: "Connection failed")
                }
        }
    }

    fun stopLive() {
        val streamEngine = engine ?: return
        viewModelScope.launch {
            runCatching { streamEngine.stopLive() }
            uiState = uiState.copy(streamState = StreamState.IDLE)
        }
    }

    fun flip() {
        val streamEngine = engine ?: return
        viewModelScope.launch {
            val nowFront = streamEngine.flipCamera()
            uiState = uiState.copy(
                isFrontCamera = nowFront,
                isTorchOn = false,
                isTorchAvailable = streamEngine.isTorchAvailable(),
                minZoomRatio = streamEngine.zoomRange().start,
                maxZoomRatio = streamEngine.zoomRange().endInclusive,
                zoomRatio = streamEngine.zoomRange().start,
            )
        }
    }

    fun toggleTorch() {
        val streamEngine = engine ?: return
        if (!uiState.isTorchAvailable) return
        val newState = !uiState.isTorchOn
        viewModelScope.launch {
            runCatching { streamEngine.setTorch(newState) }
                .onSuccess { uiState = uiState.copy(isTorchOn = newState) }
        }
    }

    fun setZoom(ratio: Float) {
        val streamEngine = engine ?: return
        uiState = uiState.copy(zoomRatio = ratio)
        viewModelScope.launch { runCatching { streamEngine.setZoomRatio(ratio) } }
    }

    fun setExposure(index: Int) {
        val streamEngine = engine ?: return
        uiState = uiState.copy(exposureIndex = index)
        viewModelScope.launch { runCatching { streamEngine.setExposureCompensation(index) } }
    }

    fun setWhiteBalance(preset: WhiteBalancePreset) {
        val streamEngine = engine ?: return
        uiState = uiState.copy(whiteBalance = preset)
        viewModelScope.launch { runCatching { streamEngine.setWhiteBalanceAutoMode(preset.awbMode) } }
    }

    fun toggleMic() {
        val audio = audioController ?: return
        val newMuted = !uiState.isMicMuted
        audio.setMicMuted(newMuted)
        uiState = uiState.copy(isMicMuted = newMuted)
    }

    fun setMicGain(percent: Int) {
        uiState = uiState.copy(micGainPercent = percent.coerceIn(0, 200))
    }

    fun setMusicVolume(percent: Int) {
        uiState = uiState.copy(musicVolumePercent = percent.coerceIn(0, 100))
    }

    fun refreshAudioRoute() {
        val audio = audioController ?: return
        uiState = uiState.copy(audioRoute = audio.currentInputRoute())
    }

    fun connectBluetoothMic() {
        audioController?.startBluetoothScoIfAvailable()
        refreshAudioRoute()
    }

    // --- Screen Share Wiring Updated ---
    fun onScreenSharePermissionResult(granted: Boolean, data: Intent?) {
        uiState = uiState.copy(screenSharePermissionGranted = granted)
        
        if (granted && data != null) {
            viewModelScope.launch {
                runCatching { 
                    engine?.startScreenShare(data) 
                }.onSuccess {
                    uiState = uiState.copy(screenShareWiredToStream = true)
                }.onFailure { e ->
                    uiState = uiState.copy(errorMessage = "Screen share failed: ${e.message}")
                }
            }
        }
    }

    private fun resolveBitrateBps(preset: BitratePreset, widthHint: Int): Int {
        val kbps = preset.kbps ?: if (widthHint >= 1920) 5000 else 3000
        return kbps * 1000
    }

    override fun onCleared() {
        super.onCleared()
        audioController?.setMicMuted(false)
        val streamEngine = engine ?: return
        kotlinx.coroutines.GlobalScope.launch {
            runCatching { streamEngine.release() }
        }
    }
}
