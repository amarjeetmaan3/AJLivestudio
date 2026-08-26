package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioFormat
import android.net.Uri
import android.util.Size
import android.view.SurfaceView
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer

/**
 * AJ Live Studio camera + RTMP streaming engine.
 *
 * Uses StreamPack SingleStreamer for:
 * - Camera capture
 * - H.264 video encoding
 * - AAC audio encoding
 * - RTMP streaming
 */
class StreamEngine(
    private val context: Context
) {

    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false

    suspend fun initialize(
        videoConfig: EngineVideoConfig
    ) {
        val cameraId = defaultBackCameraId()
            ?: throw IllegalStateException("No back camera found")

        currentCameraId = cameraId
        isFront = false

        val newStreamer = cameraSingleStreamer(
            context = context,
            cameraId = cameraId
        )

        streamer = newStreamer

        val audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        val streamPackVideoConfig = VideoConfig(
            startBitrate = videoConfig.bitrateBps,
            resolution = Size(
                videoConfig.width,
                videoConfig.height
            ),
            fps = videoConfig.fps
        )

        newStreamer.setAudioConfig(audioConfig)
        newStreamer.setVideoConfig(streamPackVideoConfig)
    }

    suspend fun startPreview(
        previewView: SurfaceView
    ) {
        val s = streamer
            ?: throw IllegalStateException("Streamer not initialized")

        s.startPreview(previewView)
    }

    suspend fun goLive(
        rtmpUrl: String
    ) {
        val s = streamer
            ?: throw IllegalStateException("Streamer not initialized")

        s.open(Uri.parse(rtmpUrl))
        s.startStream()
    }

    suspend fun stopLive() {
        val s = streamer ?: return

        runCatching {
            s.stopStream()
        }

        runCatching {
            s.close()
        }
    }

    suspend fun release() {
        stopLive()

        runCatching {
            streamer?.release()
        }

        streamer = null
    }

    // ---------------------------------------------------------------------
    // CAMERA
    // ---------------------------------------------------------------------

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

        runCatching {
            s.setCameraId(nextId)
        }.onFailure {
            return isFront
        }

        currentCameraId = nextId
        isFront = !isFront

        return isFront
    }

    fun isFrontCamera(): Boolean {
        return isFront
    }

    fun hasFrontAndBack(): Boolean {
        return defaultFrontCameraId() != null &&
                defaultBackCameraId() != null
    }

    // ---------------------------------------------------------------------
    // CAMERA CHARACTERISTICS
    // ---------------------------------------------------------------------

    private fun cameraCharacteristics(): CameraCharacteristics? {
        return runCatching {
            val manager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            manager.getCameraCharacteristics(currentCameraId)
        }.getOrNull()
    }

    // ---------------------------------------------------------------------
    // TORCH
    // ---------------------------------------------------------------------

    suspend fun setTorch(
        enabled: Boolean
    ) {
        runCatching {
            val manager =
                context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            manager.setTorchMode(
                currentCameraId,
                enabled
            )
        }
    }

    fun isTorchAvailable(): Boolean {
        return cameraCharacteristics()
            ?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    // ---------------------------------------------------------------------
    // ZOOM
    // ---------------------------------------------------------------------

    suspend fun setZoomRatio(
        ratio: Float
    ) {
        /*
         * StreamPack currently owns the active Camera2 capture session.
         *
         * The UI keeps this value so the control is ready for the
         * Camera2/StreamPack capture-request hook.
         */
    }

    fun zoomRange(): ClosedFloatingPointRange<Float> {
        val max =
            cameraCharacteristics()
                ?.get(
                    CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM
                )
                ?: 1f

        return 1f..max.coerceAtLeast(1f)
    }

    // ---------------------------------------------------------------------
    // EXPOSURE
    // ---------------------------------------------------------------------

    suspend fun setExposureCompensation(
        index: Int
    ) {
        /*
         * Reserved for Camera2 capture-request integration.
         */
    }

    fun exposureRange(): IntRange {
        val range =
            cameraCharacteristics()
                ?.get(
                    CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE
                )

        return if (range != null) {
            range.lower..range.upper
        } else {
            0..0
        }
    }

    // ---------------------------------------------------------------------
    // WHITE BALANCE
    // ---------------------------------------------------------------------

    suspend fun setWhiteBalanceAutoMode(
        awbMode: Int
    ) {
        /*
         * Reserved for StreamPack Camera2 capture-request integration.
         */
    }

    // ---------------------------------------------------------------------
    // CAMERA IDs
    // ---------------------------------------------------------------------

    private fun defaultBackCameraId(): String? {
        return findCameraId(
            CameraCharacteristics.LENS_FACING_BACK
        )
    }

    private fun defaultFrontCameraId(): String? {
        return findCameraId(
            CameraCharacteristics.LENS_FACING_FRONT
        )
    }

    private fun findCameraId(
        facing: Int
    ): String? {

        val manager =
            context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        return manager.cameraIdList.firstOrNull { id ->

            manager
                .getCameraCharacteristics(id)
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
