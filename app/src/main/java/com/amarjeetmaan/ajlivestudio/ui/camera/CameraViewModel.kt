package com.amarjeetmaan.ajlivestudio.ui.camera

import android.content.Context
import android.view.SurfaceView
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

    fun initialize(
        context: Context,
        setupState: StudioSetupState
    ) {

        val streamEngine = StreamEngine(context)

        engine = streamEngine

        val audio = AudioController(context)

        audioController = audio

        uiState = uiState.copy(
            isMicMuted = audio.isMicMuted(),
            audioRoute = audio.currentInputRoute()
        )

        viewModelScope.launch {

            runCatching {

                streamEngine.initialize(
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

            }.onSuccess {

                val zoomRange = streamEngine.zoomRange()
                val exposureRange = streamEngine.exposureRange()

                uiState = uiState.copy(
                    cameraReady = true,

                    isTorchAvailable =
                        streamEngine.isTorchAvailable(),

                    dualCameraAvailable =
                        streamEngine.hasFrontAndBack(),

                    dualCameraConcurrentSupported =
                        DualCameraCapability(context).isSupported(),

                    minZoomRatio =
                        zoomRange.start,

                    maxZoomRatio =
                        zoomRange.endInclusive,

                    zoomRatio =
                        zoomRange.start,

                    exposureMin =
                        exposureRange.first,

                    exposureMax =
                        exposureRange.last
                )

            }.onFailure { e ->

                uiState = uiState.copy(
                    streamState = StreamState.ERROR,
                    errorMessage = e.message
                )
            }
        }
    }

    suspend fun attachPreview(
        previewView: SurfaceView
    ) {
        engine?.startPreview(previewView)
    }

    fun goLive(
        rtmpUrl: String
    ) {

        val streamEngine = engine ?: return

        uiState = uiState.copy(
            streamState = StreamState.CONNECTING,
            errorMessage = null
        )

        viewModelScope.launch {

            runCatching {
                streamEngine.goLive(rtmpUrl)
            }.onSuccess {

                uiState = uiState.copy(
                    streamState = StreamState.LIVE
                )

            }.onFailure { e ->

                uiState = uiState.copy(
                    streamState = StreamState.ERROR,
                    errorMessage =
                        e.message ?: "Connection failed"
                )
            }
        }
    }

    fun stopLive() {

        val streamEngine = engine ?: return

        viewModelScope.launch {

            runCatching {
                streamEngine.stopLive()
            }

            uiState = uiState.copy(
                streamState = StreamState.IDLE
            )
        }
    }

    fun flip() {

        val streamEngine = engine ?: return

        viewModelScope.launch {

            val nowFront =
                streamEngine.flipCamera()

            val zoomRange =
                streamEngine.zoomRange()

            uiState = uiState.copy(
                isFrontCamera = nowFront,

                isTorchOn = false,

                isTorchAvailable =
                    streamEngine.isTorchAvailable(),

                minZoomRatio =
                    zoomRange.start,

                maxZoomRatio =
                    zoomRange.endInclusive,

                zoomRatio =
                    zoomRange.start
            )
        }
    }

    fun toggleTorch() {

        val streamEngine = engine ?: return

        if (!uiState.isTorchAvailable) {
            return
        }

        val newState =
            !uiState.isTorchOn

        viewModelScope.launch {

            runCatching {
                streamEngine.setTorch(newState)
            }.onSuccess {

                uiState = uiState.copy(
                    isTorchOn = newState
                )
            }
        }
    }

    fun setZoom(
        ratio: Float
    ) {

        val streamEngine = engine ?: return

        val safeRatio =
            ratio.coerceIn(
                uiState.minZoomRatio,
                uiState.maxZoomRatio
            )

        uiState =
            uiState.copy(
                zoomRatio = safeRatio
            )

        viewModelScope.launch {
            runCatching {
                streamEngine.setZoomRatio(safeRatio)
            }
        }
    }

    fun setExposure(
        index: Int
    ) {

        val streamEngine = engine ?: return

        val safeIndex =
            index.coerceIn(
                uiState.exposureMin,
                uiState.exposureMax
            )

        uiState =
            uiState.copy(
                exposureIndex = safeIndex
            )

        viewModelScope.launch {

            runCatching {
                streamEngine.setExposureCompensation(
                    safeIndex
                )
            }
        }
    }

    fun setWhiteBalance(
        preset: WhiteBalancePreset
    ) {

        val streamEngine = engine ?: return

        uiState =
            uiState.copy(
                whiteBalance = preset
            )

        viewModelScope.launch {

            runCatching {
                streamEngine.setWhiteBalanceAutoMode(
                    preset.awbMode
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // AUDIO
    // ---------------------------------------------------------------------

    fun toggleMic() {

        val audio =
            audioController ?: return

        val newMuted =
            !uiState.isMicMuted

        audio.setMicMuted(newMuted)

        uiState =
            uiState.copy(
                isMicMuted = newMuted
            )
    }

    fun setMicGain(
        percent: Int
    ) {

        uiState =
            uiState.copy(
                micGainPercent =
                    percent.coerceIn(0, 200)
            )
    }

    fun setMusicVolume(
        percent: Int
    ) {

        uiState =
            uiState.copy(
                musicVolumePercent =
                    percent.coerceIn(0, 100)
            )
    }

    fun refreshAudioRoute() {

        val audio =
            audioController ?: return

        uiState =
            uiState.copy(
                audioRoute =
                    audio.currentInputRoute()
            )
    }

    fun connectBluetoothMic() {

        audioController?.startBluetoothScoIfAvailable()

        refreshAudioRoute()
    }

    // ---------------------------------------------------------------------
    // SCREEN SHARE
    // ---------------------------------------------------------------------

    fun onScreenSharePermissionResult(
        granted: Boolean
    ) {

        uiState =
            uiState.copy(
                screenSharePermissionGranted =
                    granted
            )
    }

    // ---------------------------------------------------------------------
    // BITRATE
    // ---------------------------------------------------------------------

    private fun resolveBitrateBps(
        preset: BitratePreset,
        widthHint: Int
    ): Int {

        val kbps =
            preset.kbps
                ?: if (widthHint >= 1920) {
                    5000
                } else {
                    3000
                }

        return kbps * 1000
    }

    override fun onCleared() {

        audioController?.setMicMuted(false)

        val streamEngine =
            engine

        kotlinx.coroutines.GlobalScope.launch {

            runCatching {
                streamEngine?.release()
            }
        }

        super.onCleared()
    }
}
