package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.media.projection.MediaProjection
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amarjeetmaan.ajlivestudio.audio.AudioController
import com.amarjeetmaan.ajlivestudio.streaming.EngineVideoConfig
import com.amarjeetmaan.ajlivestudio.streaming.StreamEngine
import com.amarjeetmaan.ajlivestudio.ui.setup.BitratePreset
import com.amarjeetmaan.ajlivestudio.ui.setup.StudioSetupState
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CameraViewModel : ViewModel() {
    var uiState by mutableStateOf(CameraUiState())
        private set

    private var engine: StreamEngine? = null
    private var audioController: AudioController? = null
    
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var cameraInfo: androidx.camera.core.CameraInfo? = null

    fun initialize(context: Context, setupState: StudioSetupState) {
        engine = StreamEngine(context).apply {
            initialize(
                EngineVideoConfig(
                    width = setupState.resolution.width,
                    height = setupState.resolution.height,
                    fps = setupState.frameRate.value,
                    bitrateBps = resolveBitrateBps(setupState.bitrate, setupState.resolution.width)
                )
            )
        }
        audioController = AudioController(context)
        uiState = uiState.copy(isMicMuted = audioController?.isMicMuted() ?: false)
    }

    suspend fun startCameraX(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraProvider = suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
        }
        bindCamera(lifecycleOwner, previewView)
    }

    private fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val cameraSelector = CameraSelector.Builder().requireLensFacing(currentLensFacing).build()
        
        try {
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
            cameraControl = camera.cameraControl
            cameraInfo = camera.cameraInfo
            uiState = uiState.copy(cameraReady = true, isTorchAvailable = cameraInfo?.hasFlashUnit() ?: false)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun flip(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCamera(lifecycleOwner, previewView)
    }

    fun toggleTorch() {
        if (!uiState.isTorchAvailable) return
        val newState = !uiState.isTorchOn
        cameraControl?.enableTorch(newState)
        uiState = uiState.copy(isTorchOn = newState)
    }

    fun goLive(rtmpUrl: String, mediaProjection: MediaProjection) {
        uiState = uiState.copy(streamState = StreamState.CONNECTING, errorMessage = null)
        viewModelScope.launch {
            runCatching { engine?.goLive(rtmpUrl, mediaProjection) }
                .onSuccess { uiState = uiState.copy(streamState = StreamState.LIVE) }
                .onFailure { e -> uiState = uiState.copy(streamState = StreamState.ERROR, errorMessage = e.message ?: "Failed") }
        }
    }

    fun stopLive() {
        viewModelScope.launch {
            engine?.stopLive()
            uiState = uiState.copy(streamState = StreamState.IDLE)
        }
    }

    fun toggleMic() {
        audioController?.let {
            val newMuted = !uiState.isMicMuted
            it.setMicMuted(newMuted)
            uiState = uiState.copy(isMicMuted = newMuted)
        }
    }

    private fun resolveBitrateBps(preset: BitratePreset, widthHint: Int): Int {
        return (preset.kbps ?: if (widthHint >= 1920) 5000 else 3000) * 1000
    }
    
    fun setMicGain(percent: Int) { uiState = uiState.copy(micGainPercent = percent) }
    fun setMusicVolume(percent: Int) { uiState = uiState.copy(musicVolumePercent = percent) }
    fun connectBluetoothMic() { audioController?.startBluetoothScoIfAvailable() }
    
    fun setExposure(index: Int) {}
    fun setZoom(ratio: Float) {}
    fun setWhiteBalance(preset: WhiteBalancePreset) {}
}
