package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.util.Size
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.CameraSourceFactory
import io.github.thibaultbee.streampack.core.elements.sources.video.camera.ICameraSource
import io.github.thibaultbee.streampack.core.interfaces.setCameraId
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.setConfig

/**
 * Thin wrapper around StreamPack's SingleStreamer: owns the camera capture,
 * H.264 encode, and RTMP send pipeline in one place.
 *
 * Every method/property name here is verified against the official 3.2.0
 * KDoc (thibaultbee.github.io/StreamPack/streampack-core/...), not guessed —
 * several of these (setConfig, startStream(descriptor), setCameraId,
 * startPreview) are extension functions that live in
 * io.github.thibaultbee.streampack.core.interfaces and require an explicit
 * import even though they're called like members.
 */
class StreamEngine(private val context: Context) {

    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false

    suspend fun initialize(videoConfig: EngineVideoConfig) {
        val cameraId = defaultBackCameraId() ?: throw IllegalStateException("No camera found")
        currentCameraId = cameraId
        isFront = false

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
        // startPreview() requires a preview Surface already attached to the
        // streamer's outputs. Since the preview widget is a placeholder in
        // this build (see CameraPreviewScreen.kt note), there's no surface
        // to preview into yet — calling it would throw "Output preview not
        // found in outputs stream". Skipping it here does NOT affect actual
        // encoding/streaming, which reads frames from the camera source
        // directly, independent of whether a preview is attached.
    }

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

    // --- Camera controls -----------------------------------------------

    suspend fun flipCamera(): Boolean {
        val s = streamer ?: return isFront
        val nextId = if (isFront) defaultBackCameraId() else defaultFrontCameraId()
        if (nextId == null) return isFront // device has no second camera
        s.setCameraId(nextId)
        currentCameraId = nextId
        isFront = !isFront
        return isFront
    }

    fun isFrontCamera(): Boolean = isFront

    fun hasFrontAndBack(): Boolean =
        defaultFrontCameraId() != null && defaultBackCameraId() != null

    /** The active camera source, if the current video input is a camera. */
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

    /** autoMode values are android.hardware.camera2.CameraMetadata.CONTROL_AWB_MODE_* constants. */
    suspend fun setWhiteBalanceAutoMode(awbMode: Int) {
        cameraSource()?.settings?.whiteBalance?.setAutoMode(awbMode)
    }

    // --- Camera id lookup -------------------------------------------------

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
