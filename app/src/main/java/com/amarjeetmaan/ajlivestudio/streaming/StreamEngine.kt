package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * DIRECT CAMERA STREAM TEST
 *
 * Camera -> StreamPack -> Encoder -> RTMP/RTMPS -> YouTube
 *
 * OverlayCompositor is intentionally NOT used here.
 * This file is for isolating the YouTube/encoder pipeline.
 */
class StreamEngine(private val context: Context) {

    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false

    /**
     * Initialize the normal StreamPack camera pipeline.
     *
     * IMPORTANT:
     * No SurfaceProcessorFactory is attached here.
     */
    suspend fun initializeCamera(
        videoConfig: EngineVideoConfig,
        targetRotation: Int? = null
    ) {
        closeCurrentStreamer()

        val cameraId = defaultBackCameraId()
            ?: throw IllegalStateException("No back camera found")

        currentCameraId = cameraId
        isFront = false

        /*
         * DIRECT CAMERA PIPELINE
         *
         * Do NOT pass surfaceProcessorFactory.
         *
         * This is the same basic architecture that worked
         * before the custom overlay compositor was introduced.
         */
        val newStreamer = cameraSingleStreamer(
            context = context,
            cameraId = cameraId
        )

        targetRotation?.let {
            newStreamer.setTargetRotation(it)
        }

        val audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        val videoStreamConfig = VideoConfig(
            startBitrate = videoConfig.bitrateBps,
            resolution = android.util.Size(
                videoConfig.width,
                videoConfig.height
            ),
            fps = videoConfig.fps
        )

        newStreamer.setConfig(
            audioConfig,
            videoStreamConfig
        )

        streamer = newStreamer

        /*
         * Wait until the actual camera source is available.
         */
        awaitCameraSource()
    }

    /**
     * Overlay is intentionally ignored in this diagnostic build.
     *
     * Keeping this function means existing ViewModel/UI code
     * can continue calling updateOverlay() without compilation errors.
     */
    fun updateOverlay(bitmap: android.graphics.Bitmap?) {
        // Intentionally disabled for direct-camera diagnostic test.
    }

    /**
     * Start normal camera preview.
     */
    suspend fun startCameraPreview(surface: Surface) {
        val s = streamer
            ?: throw IllegalStateException("Streamer is not initialized")

        s.startPreview(surface)

        awaitCameraSource()
    }

    /**
     * Stop camera preview.
     */
    suspend fun stopCameraPreview() {
        runCatching {
            streamer?.stopPreview()
        }
    }

    /**
     * Start RTMP/RTMPS stream.
     *
     * This sends the direct camera output to StreamPack encoder
     * without passing through OverlayCompositor.
     */
    suspend fun goLive(rtmpUrl: String) {
        val s = streamer
            ?: throw IllegalStateException("Streamer is not initialized")

        if (rtmpUrl.isBlank()) {
            throw IllegalArgumentException("RTMP URL is empty")
        }

        s.startStream(rtmpUrl)
    }

    /**
     * Stop live stream.
     */
    suspend fun stopLive() {
        runCatching {
            streamer?.stopStream()
        }
    }

    /**
     * Switch between rear and front camera.
     */
    suspend fun flipCamera(): Boolean {

        val s = streamer ?: return isFront

        val nextId = if (isFront) {
            defaultBackCameraId()
        } else {
            defaultFrontCameraId()
        }

        if (nextId == null) {
            return isFront
        }

        return try {

            s.setCameraId(nextId)

            currentCameraId = nextId
            isFront = !isFront

            awaitCameraSource()

            isFront

        } catch (_: Exception) {
            isFront
        }
    }

    /**
     * Mute/unmute audio when supported by the StreamPack version/device.
     */
    fun muteAudio(muted: Boolean) {

        try {

            val audioSettings = streamer
                ?.javaClass
                ?.getMethod("getAudioSettings")
                ?.invoke(streamer)

            audioSettings
                ?.javaClass
                ?.getMethod(
                    "setMuted",
                    Boolean::class.javaPrimitiveType
                )
                ?.invoke(
                    audioSettings,
                    muted
                )

        } catch (_: Exception) {
            /*
             * Audio mute is optional across StreamPack/device
             * combinations. Do not break video streaming if this
             * feature is unavailable.
             */
        }
    }

    /**
     * Get the actual StreamPack camera source.
     */
    suspend fun awaitCameraSource(
        timeoutMs: Long = 5_000
    ): ICameraSource? {

        val s = streamer ?: return null

        return withTimeoutOrNull(timeoutMs) {

            s.videoInput.sourceFlow
                .filterNotNull()
                .filterIsInstance<ICameraSource>()
                .first()
        }
    }

    /**
     * Check whether torch/flash is available.
     */
    suspend fun isTorchAvailableAsync(): Boolean {

        val source = awaitCameraSource()
            ?: return false

        return source.settings.flash.isAvailable
    }

    /**
     * Enable/disable flashlight.
     */
    suspend fun setTorch(enabled: Boolean) {

        val source = awaitCameraSource()
            ?: throw IllegalStateException(
                "Camera source is not ready"
            )

        if (!source.settings.flash.isAvailable) {
            throw IllegalStateException(
                "Flashlight is not available on this camera"
            )
        }

        source.settings.flash.setIsEnable(enabled)
    }

    /**
     * Current camera state.
     */
    fun isFrontCamera(): Boolean {
        return isFront
    }

    /**
     * Completely release current StreamPack instance.
     */
    private suspend fun closeCurrentStreamer() {

        runCatching {
            streamer?.stopPreview()
        }

        runCatching {
            streamer?.stopStream()
        }

        runCatching {
            streamer?.release()
        }

        streamer = null
    }

    /**
     * Release StreamPack.
     */
    suspend fun release() {

        runCatching {
            streamer?.stopPreview()
        }

        runCatching {
            streamer?.stopStream()
        }

        runCatching {
            streamer?.release()
        }

        streamer = null
    }

    /**
     * Synchronous cleanup helper.
     */
    fun close() {

        runBlocking(Dispatchers.Default) {
            release()
        }
    }

    /**
     * Find rear camera.
     */
    private fun defaultBackCameraId(): String? {
        return findCameraId(
            CameraCharacteristics.LENS_FACING_BACK
        )
    }

    /**
     * Find front camera.
     */
    private fun defaultFrontCameraId(): String? {
        return findCameraId(
            CameraCharacteristics.LENS_FACING_FRONT
        )
    }

    /**
     * Find camera ID by lens facing.
     */
    private fun findCameraId(facing: Int): String? {

        val manager =
            context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager

        return manager.cameraIdList.firstOrNull { id ->

            manager
                .getCameraCharacteristics(id)
                .get(
                    CameraCharacteristics.LENS_FACING
                ) == facing
        }
    }
}

/**
 * Video configuration used by StreamEngine.
 */
data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int
)
