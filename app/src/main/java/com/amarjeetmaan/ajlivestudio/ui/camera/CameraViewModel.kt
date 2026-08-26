package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
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

    /** Exposes the underlying streamer so the screen can render SourcePreview(streamer). */
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

    // --- Audio (Phase 4) ------------------------------------------------

    fun toggleMic() {
        val audio = audioController ?: return
        val newMuted = !uiState.isMicMuted
        audio.setMicMuted(newMuted)
        uiState = uiState.copy(isMicMuted = newMuted)
    }

    /**
     * UI-level gain (0-200%). Note: this does NOT multiply the actual
     * signal yet — true gain requires hooking StreamPack's audio pipeline
     * with a custom processor, which isn't wired up in this phase. Stored
     * here so the control + value persist and are ready for that hook.
     */
    fun setMicGain(percent: Int) {
        uiState = uiState.copy(micGainPercent = percent.coerceIn(0, 200))
    }

    /**
     * UI-level music track volume. Same status as mic gain: stored and
     * ready, but there's no music track actually mixed into the outgoing
     * audio yet — that needs the same StreamPack audio-pipeline hook
     * flagged since Phase 4. See README.
     */
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

    fun onScreenSharePermissionResult(granted: Boolean) {
        uiState = uiState.copy(screenSharePermissionGranted = granted)
        // screenShareWiredToStream intentionally stays false — see
        // ScreenShareController's doc comment on why the StreamPack
        // video-source wiring isn't done yet.
    }

    private fun resolveBitrateBps(preset: BitratePreset, widthHint: Int): Int {
        val kbps = preset.kbps ?: if (widthHint >= 1920) 5000 else 3000 // Auto default
        return kbps * 1000
    }

    override fun onCleared() {
        super.onCleared()
        // Always un-mute the system mic on teardown so a mute here never
        // leaks into other apps after AJ Live Studio closes.
        audioController?.setMicMuted(false)
        val streamEngine = engine ?: return
        // Fire-and-forget release; ViewModel scope is already cancelled here,
        // so use a detached scope for the final cleanup call.
        kotlinx.coroutines.GlobalScope.launch {
            runCatching { streamEngine.release() }
        }
    }
}
