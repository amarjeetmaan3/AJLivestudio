package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.content.Intent

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

    private var currentLensFacing =
        CameraSelector.LENS_FACING_BACK

    private var cameraControl:
        androidx.camera.core.CameraControl? = null

    private var cameraInfo:
        androidx.camera.core.CameraInfo? = null


    /**
     * Initializes the streaming engine and audio controller.
     */
    fun initialize(
        context: Context,
        setupState: StudioSetupState
    ) {

        engine = StreamEngine(context).apply {

            initialize(
                EngineVideoConfig(
                    width = setupState.resolution.width,

                    height = setupState.resolution.height,

                    fps = setupState.frameRate.value,

                    bitrateBps = resolveBitrateBps(
                        setupState.bitrate,
                        setupState.resolution.width
                    )
                )
            )
        }

        audioController = AudioController(context)

        uiState = uiState.copy(
            isMicMuted =
                audioController?.isMicMuted() ?: false
        )
    }


    /**
     * Starts the CameraX preview.
     */
    suspend fun startCameraX(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {

        cameraProvider =
            suspendCoroutine { continuation ->

                val future =
                    ProcessCameraProvider.getInstance(context)

                future.addListener(

                    {
                        continuation.resume(
                            future.get()
                        )
                    },

                    ContextCompat.getMainExecutor(context)
                )
            }

        bindCamera(
            lifecycleOwner,
            previewView
        )
    }


    /**
     * Bind CameraX preview.
     */
    private fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {

        val provider =
            cameraProvider ?: return

        provider.unbindAll()

        val preview =
            Preview.Builder()
                .build()
                .also {

                    it.setSurfaceProvider(
                        previewView.surfaceProvider
                    )
                }

        val cameraSelector =
            CameraSelector.Builder()
                .requireLensFacing(
                    currentLensFacing
                )
                .build()

        try {

            val camera =
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )

            cameraControl =
                camera.cameraControl

            cameraInfo =
                camera.cameraInfo

            uiState =
                uiState.copy(
                    cameraReady = true,

                    isTorchAvailable =
                        cameraInfo?.hasFlashUnit()
                            ?: false
                )

        } catch (e: Exception) {

            e.printStackTrace()

            uiState =
                uiState.copy(
                    cameraReady = false,

                    errorMessage =
                        e.message
                            ?: "Unable to start camera"
                )
        }
    }


    /**
     * Switch between front and back camera.
     */
    fun flip(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView
    ) {

        currentLensFacing =
            if (
                currentLensFacing ==
                CameraSelector.LENS_FACING_BACK
            ) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }

        bindCamera(
            lifecycleOwner,
            previewView
        )
    }


    /**
     * Toggle camera flashlight.
     */
    fun toggleTorch() {

        if (!uiState.isTorchAvailable) {
            return
        }

        val newState =
            !uiState.isTorchOn

        cameraControl?.enableTorch(
            newState
        )

        uiState =
            uiState.copy(
                isTorchOn = newState
            )
    }


    /**
     * Starts the RTMP live stream.
     *
     * The Intent is retained for compatibility with the
     * existing screen-share permission flow.
     *
     * The current StreamEngine uses the camera streamer,
     * not MediaProjection.
     */
    fun goLive(
        rtmpUrl: String,
        mediaProjectionIntent: Intent
    ) {

        if (rtmpUrl.isBlank()) {

            uiState =
                uiState.copy(
                    streamState = StreamState.ERROR,
                    errorMessage =
                        "RTMP URL is empty"
                )

            return
        }

        uiState =
            uiState.copy(
                streamState =
                    StreamState.CONNECTING,

                errorMessage = null
            )

        viewModelScope.launch {

            runCatching {

                engine?.goLive(
                    rtmpUrl = rtmpUrl,
                    mediaProjectionIntent =
                        mediaProjectionIntent
                )
                    ?: throw IllegalStateException(
                        "Stream engine is not initialized"
                    )
            }

                .onSuccess {

                    uiState =
                        uiState.copy(
                            streamState =
                                StreamState.LIVE,

                            errorMessage = null
                        )
                }

                .onFailure { error ->

                    error.printStackTrace()

                    uiState =
                        uiState.copy(
                            streamState =
                                StreamState.ERROR,

                            errorMessage =
                                error.message
                                    ?: "Failed to start live stream"
                        )
                }
        }
    }


    /**
     * Stops live streaming.
     */
    fun stopLive() {

        viewModelScope.launch {

            runCatching {
                engine?.stopLive()
            }

            uiState =
                uiState.copy(
                    streamState =
                        StreamState.IDLE
                )
        }
    }


    /**
     * Toggle microphone mute.
     */
    fun toggleMic() {

        audioController?.let { controller ->

            val newMuted =
                !uiState.isMicMuted

            controller.setMicMuted(
                newMuted
            )

            uiState =
                uiState.copy(
                    isMicMuted = newMuted
                )
        }
    }


    /**
     * Resolve bitrate from selected preset.
     */
    private fun resolveBitrateBps(
        preset: BitratePreset,
        widthHint: Int
    ): Int {

        return (
            preset.kbps
                ?: if (widthHint >= 1920) {
                    5000
                } else {
                    3000
                }
            ) * 1000
    }


    fun setMicGain(
        percent: Int
    ) {

        uiState =
            uiState.copy(
                micGainPercent =
                    percent
            )
    }


    fun setMusicVolume(
        percent: Int
    ) {

        uiState =
            uiState.copy(
                musicVolumePercent =
                    percent
            )
    }


    fun connectBluetoothMic() {

        audioController
            ?.startBluetoothScoIfAvailable()
    }


    fun setExposure(
        index: Int
    ) {
        // Camera exposure implementation can be
        // connected to CameraX cameraControl later.
    }


    fun setZoom(
        ratio: Float
    ) {
        cameraControl?.setZoomRatio(
            ratio.coerceAtLeast(1f)
        )
    }


    fun setWhiteBalance(
        preset: WhiteBalancePreset
    ) {
        // Reserved for CameraX extension implementation.
    }


    override fun onCleared() {

        super.onCleared()

        cameraProvider?.unbindAll()

        audioController = null
    }
}
