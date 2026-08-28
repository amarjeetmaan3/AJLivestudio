package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.util.Size
import android.view.Surface
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.elements.sources.video.screen.ScreenSource
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.interfaces.startPreview
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.interfaces.stopPreview
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.setConfig
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class StreamEngine(private val context: Context) {

    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false
    
    // Screen Share Tracking
    private var screenShareIntent: Intent? = null
    private var screenSource: ScreenSource? = null

    suspend fun initialize(videoConfig: EngineVideoConfig, targetRotation: Int? = null) {
        val cameraId = defaultBackCameraId() ?: throw IllegalStateException("No camera found")
        currentCameraId = cameraId
        isFront = false

        // Base Streamer Initialization
        val newStreamer = cameraSingleStreamer(context = context, cameraId = cameraId)
        streamer = newStreamer

        val audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO,
        )
        val streamPackVideoConfig = VideoConfig(
            startBitrate = videoConfig.bitrateBps,
            resolution = Size(videoConfig.width, videoConfig.height),
            fps = videoConfig.fps,
        )
        newStreamer.setConfig(audioConfig, streamPackVideoConfig)
        
        targetRotation?.let { rotation ->
            runCatching { newStreamer.setTargetRotation(rotation) }
        }

        withTimeoutOrNull(5_000) {
            newStreamer.videoInput?.sourceFlow?.filterNotNull()?.first()
        }
    }

    suspend fun startCameraPreview(surface: Surface) {
        val s = streamer ?: return
        try {
            s.startPreview(surface)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun stopCameraPreview() {
        streamer?.stopPreview()
    }

    // --- Screen Share Implementation ---
    suspend fun startScreenShare(intent: Intent) {
        screenShareIntent = intent
        val s = streamer ?: throw IllegalStateException("Streamer not initialized")
        
        try {
            // सिस्टम से ScreenSource बनाएँ
            val newScreenSource = ScreenSource(context = context, mediaProjectionIntent = intent)
            screenSource = newScreenSource
            
            // SingleStreamer में हम एक साथ दो वीडियो नहीं भेज सकते (Mixer की जरूरत होती है)।
            // इसलिए हम स्क्रीन शेयर चालू होने पर कैमरे को हटाकर स्क्रीन सोर्स सेट कर रहे हैं।
            s.videoInput?.source = newScreenSource
            
        } catch (e: Exception) {
            e.printStackTrace()
            screenShareIntent = null
        }
    }

    suspend fun stopScreenShare() {
        val s = streamer
        try {
            // स्क्रीन शेयर बंद होने पर वापस कैमरा चालू करें
            val cameraId = if (isFront) defaultFrontCameraId() else defaultBackCameraId()
            cameraId?.let { id ->
                s?.setCameraId(id)
            }
            screenSource?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            screenSource = null
            screenShareIntent = null
        }
    }

    // -----------------------------------

    suspend fun goLive(rtmpUrl: String) {
        val s = streamer ?: throw IllegalStateException("Streamer not initialized")
        s.startStream(rtmpUrl)
    }

    suspend fun stopLive() {
        val s = streamer ?: return
        runCatching { s.stopStream() }
        runCatching { s.close() }
    }

    suspend fun release() {
        stopLive()
        streamer?.release()
        streamer = null
    }

    suspend fun flipCamera(): Boolean {
        val s = streamer ?: return isFront
        
        // अगर स्क्रीन शेयर चल रहा है तो फ्लिप काम नहीं करेगा
        if (screenSource != null) return isFront 

        val nextId = if (isFront) defaultBackCameraId() else defaultFrontCameraId()
        if (nextId == null) return isFront
        s.setCameraId(nextId)
        currentCameraId = nextId
        isFront = !isFront
        
        withTimeoutOrNull(5_000) {
            s.videoInput?.sourceFlow?.filterNotNull()?.first()
        }
        return isFront
    }

    fun isFrontCamera(): Boolean = isFront

    fun hasFrontAndBack(): Boolean =
        defaultFrontCameraId() != null && defaultBackCameraId() != null

    private fun cameraSource(): ICameraSource? =
        streamer?.videoInput?.sourceFlow?.value as? ICameraSource

    suspend fun setTorch(enabled: Boolean) {
        cameraSource()?.settings?.flash?.setIsEnable(enabled)
    }

    fun isTorchAvailable(): Boolean = cameraSource()?.settings?.flash?.isAvailable ?: false

    suspend fun setZoomRatio(ratio: Float) {
        cameraSource()?.settings?.zoom?.setZoomRatio(ratio)
    }

    fun zoomRange(): ClosedFloatingPointRange<Float> {
        val range = cameraSource()?.settings?.zoom?.availableRatioRange
        return if (range != null) range.lower..range.upper else 1f..1f
    }

    suspend fun setExposureCompensation(index: Int) {
        cameraSource()?.settings?.exposure?.setCompensation(index)
    }

    fun exposureRange(): IntRange {
        val range = cameraSource()?.settings?.exposure?.availableCompensationRange
        return if (range != null) range.lower..range.upper else 0..0
    }

    suspend fun setWhiteBalanceAutoMode(awbMode: Int) {
        cameraSource()?.settings?.whiteBalance?.setAutoMode(awbMode)
    }

    private fun defaultBackCameraId(): String? = findCameraId(CameraCharacteristics.LENS_FACING_BACK)
    private fun defaultFrontCameraId(): String? = findCameraId(CameraCharacteristics.LENS_FACING_FRONT)

    private fun findCameraId(facing: Int): String? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing
        }
    }
}

data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int,
)
