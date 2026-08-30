package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.util.Size
import android.view.Surface
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Camera -> OverlayCompositor -> StreamPack encoder -> RTMP.
 *
 * Screen capture / MediaProjection is intentionally not used.
 */
class StreamEngine(private val context: Context) {
    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false
    private var overlayFactory: OverlayCompositor.Factory? = null

    suspend fun initializeCamera(videoConfig: EngineVideoConfig, targetRotation: Int? = null) {
        closeCurrentStreamer()

        val cameraId = defaultBackCameraId()
            ?: throw IllegalStateException("No back camera found")

        currentCameraId = cameraId
        isFront = false

        val factory = OverlayCompositor.Factory()
        overlayFactory = factory

        val newStreamer = cameraSingleStreamer(
            context = context,
            cameraId = cameraId,
            surfaceProcessorFactory = factory
        )

        targetRotation?.let { newStreamer.setTargetRotation(it) }

        val audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        val streamPackVideoConfig = VideoConfig(
            startBitrate = videoConfig.bitrateBps,
            resolution = Size(videoConfig.width, videoConfig.height),
            fps = videoConfig.fps
        )

        newStreamer.setConfig(audioConfig, streamPackVideoConfig)
        streamer = newStreamer

        // Force resolution of the camera source before camera controls are queried.
        awaitCameraSource()
    }

    fun updateOverlay(bitmap: android.graphics.Bitmap?) {
        overlayFactory?.setOverlayBitmap(bitmap)
    }

    suspend fun startCameraPreview(surface: Surface) {
        val s = streamer ?: throw IllegalStateException("Streamer is not initialized")
        s.startPreview(surface)
        awaitCameraSource()
    }

    suspend fun stopCameraPreview() {
        runCatching { streamer?.stopPreview() }
    }

    suspend fun goLive(rtmpUrl: String) {
        val s = streamer ?: throw IllegalStateException("Streamer is not initialized")
        s.startStream(rtmpUrl)
    }

    suspend fun stopLive() {
        runCatching { streamer?.stopStream() }
    }

    suspend fun flipCamera(): Boolean {
        val s = streamer ?: return isFront
        val nextId = if (isFront) defaultBackCameraId() else defaultFrontCameraId()
            ?: return isFront

        runCatching { s.setCameraId(nextId) }
            .getOrElse { return isFront }

        currentCameraId = nextId
        isFront = !isFront
        awaitCameraSource()
        return isFront
    }

    fun muteAudio(muted: Boolean) {
        try {
            val audioSettings = streamer?.javaClass
                ?.getMethod("getAudioSettings")
                ?.invoke(streamer)

            audioSettings?.javaClass
                ?.getMethod("setMuted", Boolean::class.javaPrimitiveType)
                ?.invoke(audioSettings, muted)
        } catch (_: Exception) {
            // StreamPack audio muting is optional on some versions/devices.
        }
    }

    suspend fun awaitCameraSource(timeoutMs: Long = 5_000): ICameraSource? {
        val s = streamer ?: return null
        return withTimeoutOrNull(timeoutMs) {
            s.videoInput.sourceFlow.filterNotNull().first()
        }
    }

    suspend fun isTorchAvailableAsync(): Boolean {
        val source = awaitCameraSource() ?: return false
        return source.settings.flash.isAvailable
    }

    suspend fun setTorch(enabled: Boolean) {
        val source = awaitCameraSource()
            ?: throw IllegalStateException("Camera source is not ready")

        if (!source.settings.flash.isAvailable) {
            throw IllegalStateException("Flashlight is not available on this camera")
        }

        source.settings.flash.setIsEnable(enabled)
    }

    fun isFrontCamera(): Boolean = isFront

    private suspend fun closeCurrentStreamer() {
        runCatching { streamer?.stopPreview() }
        runCatching { streamer?.stopStream() }
        runCatching { streamer?.release() }
        streamer = null
        overlayFactory = null
    }

    suspend fun release() {
        runCatching { streamer?.stopPreview() }
        runCatching { streamer?.stopStream() }
        runCatching { streamer?.release() }
        streamer = null
        overlayFactory = null
    }

    fun close() {
        runBlocking(Dispatchers.Default) {
            release()
        }
    }

    private fun defaultBackCameraId(): String? =
        findCameraId(CameraCharacteristics.LENS_FACING_BACK)

    private fun defaultFrontCameraId(): String? =
        findCameraId(CameraCharacteristics.LENS_FACING_FRONT)

    private fun findCameraId(facing: Int): String? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == facing
        }
    }
}

data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int
)
