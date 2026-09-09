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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class StreamEngine(
    private val context: Context
) {

    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false

    private var configuredWidth: Int = 1280
    private var configuredHeight: Int = 720
    private var configuredFps: Int = 30
    private var configuredBitrate: Int = 3_000_000

    suspend fun initializeCamera(
        videoConfig: EngineVideoConfig,
        targetRotation: Int? = null
    ) {
        closeCurrentStreamer()

        configuredWidth = videoConfig.width
        configuredHeight = videoConfig.height
        configuredFps = videoConfig.fps
        configuredBitrate = videoConfig.bitrateBps

        val cameraId =
            defaultBackCameraId()
                ?: throw IllegalStateException(
                    "No back camera found"
                )

        currentCameraId = cameraId
        isFront = false

        val newStreamer =
            cameraSingleStreamer(
                context = context,
                cameraId = cameraId
            )

        /*
         * Rotation is optional.
         * It must NOT be allowed to break StreamPack
         * initialization.
         */
        targetRotation?.let { rotation ->
            runCatching {
                newStreamer.setTargetRotation(rotation)
            }
        }

        val audioConfig =
            AudioConfig(
                startBitrate = 128_000,
                sampleRate = 44_100,
                channelConfig = AudioFormat.CHANNEL_IN_MONO
            )

        val videoConfigForStream =
            VideoConfig(
                startBitrate = configuredBitrate,
                resolution =
                    Size(
                        configuredWidth,
                        configuredHeight
                    ),
                fps = configuredFps
            )

        /*
         * IMPORTANT:
         *
         * Do NOT wait for sourceFlow here.
         *
         * StreamPack creates/exposes the camera source when
         * startPreview() is called with a real Surface.
         *
         * Previously this function waited for sourceFlow
         * BEFORE the TextureView Surface existed.
         * That caused the artificial:
         *
         * "Stream is not initialized"
         *
         * error.
         */
        newStreamer.setConfig(
            audioConfig,
            videoConfigForStream
        )

        streamer = newStreamer
    }

    fun updateOverlay(
        bitmap: android.graphics.Bitmap?
    ) {
        // Overlay pipeline remains disabled here.
    }

    suspend fun startCameraPreview(
        surface: Surface
    ) {
        val s =
            streamer
                ?: throw IllegalStateException(
                    "Stream is not initialized"
                )

        /*
         * This is the point where the actual camera Surface
         * becomes available to StreamPack.
         */
        s.startPreview(surface)

        /*
         * Now wait for the real camera source.
         */
        val source =
            awaitCameraSource(
                timeoutMs = 10_000
            )

        if (source == null) {
            throw IllegalStateException(
                "Camera source did not become ready"
            )
        }
    }

    suspend fun stopCameraPreview() {
        runCatching {
            streamer?.stopPreview()
        }
    }

    suspend fun goLive(
        rtmpUrl: String
    ) {
        val s =
            streamer
                ?: throw IllegalStateException(
                    "Stream is not initialized"
                )

        if (rtmpUrl.isBlank()) {
            throw IllegalArgumentException(
                "RTMP URL is empty"
            )
        }

        /*
         * Preview must have successfully created the camera
         * source before RTMP streaming starts.
         */
        val source =
            awaitCameraSource(
                timeoutMs = 5_000
            )

        if (source == null) {
            throw IllegalStateException(
                "Camera source is not ready"
            )
        }

        s.startStream(rtmpUrl)
    }

    suspend fun stopLive() {
        runCatching {
            streamer?.stopStream()
        }
    }

    suspend fun flipCamera(): Boolean {
        val s =
            streamer
                ?: return isFront

        val nextId =
            if (isFront) {
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

            /*
             * Give StreamPack time to expose the new source.
             */
            awaitCameraSource(5_000)

            isFront
        } catch (_: Exception) {
            isFront
        }
    }

    fun muteAudio(
        muted: Boolean
    ) {
        try {
            val audioSettings =
                streamer
                    ?.javaClass
                    ?.getMethod(
                        "getAudioSettings"
                    )
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
        }
    }

    suspend fun awaitCameraSource(
        timeoutMs: Long = 10_000
    ): ICameraSource? {

        val s =
            streamer
                ?: return null

        return withTimeoutOrNull(
            timeoutMs
        ) {
            s.videoInput.sourceFlow
                .filterNotNull()
                .filterIsInstance<ICameraSource>()
                .first()
        }
    }

    suspend fun isTorchAvailableAsync(): Boolean {
        val source =
            awaitCameraSource(
                timeoutMs = 3_000
            )
                ?: return false

        return source.settings.flash.isAvailable
    }

    suspend fun setTorch(
        enabled: Boolean
    ) {
        val source =
            awaitCameraSource(
                timeoutMs = 3_000
            )
                ?: throw IllegalStateException(
                    "Camera source is not ready"
                )

        if (!source.settings.flash.isAvailable) {
            throw IllegalStateException(
                "Flashlight is not available on this camera"
            )
        }

        source.settings.flash.setIsEnable(
            enabled
        )
    }

    fun isFrontCamera(): Boolean {
        return isFront
    }

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

    fun close() {
        runBlocking(Dispatchers.Default) {
            release()
        }
    }

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
            context.getSystemService(
                Context.CAMERA_SERVICE
            ) as CameraManager

        return manager.cameraIdList
            .firstOrNull { id ->

                manager
                    .getCameraCharacteristics(id)
                    .get(
                        CameraCharacteristics.LENS_FACING
                    ) == facing
            }
    }
}

data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int
)
