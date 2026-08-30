package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ONE pipeline, camera straight to encoder:
 *
 *   Camera -> OverlayCompositor (GPU/GLES, bakes overlay bitmap in)
 *          -> [preview surface]   (phone screen)
 *          -> [encoder surface]   (MediaCodec, via StreamPack) -> RTMP -> YouTube
 *
 * There is exactly one SingleStreamer (`streamer`), used for local
 * preview AND for the actual broadcast. No MediaProjection, no screen
 * capture, no second streamer anywhere in this class.
 */
class StreamEngine(private val context: Context) {
    var streamer: SingleStreamer? = null
        private set

    private var currentCameraId: String = ""
    private var isFront: Boolean = false

    suspend fun initializeCamera(videoConfig: EngineVideoConfig, targetRotation: Int? = null) {
        val cameraId = defaultBackCameraId() ?: throw IllegalStateException("No camera found")
        currentCameraId = cameraId
        isFront = false

        val newStreamer = cameraSingleStreamer(
            context = context,
            cameraId = cameraId,
            
        )
        targetRotation?.let { runCatching { newStreamer.setTargetRotation(it) } }

        val audioConfig = AudioConfig(startBitrate = 128_000, sampleRate = 44_100, channelConfig = AudioFormat.CHANNEL_IN_STEREO)
        val streamPackVideoConfig = VideoConfig(startBitrate = videoConfig.bitrateBps, resolution = Size(videoConfig.width, videoConfig.height), fps = videoConfig.fps)
        newStreamer.setConfig(audioConfig, streamPackVideoConfig)

        streamer = newStreamer
        withTimeoutOrNull(5_000) { newStreamer.videoInput?.sourceFlow?.filterNotNull()?.first() }
    }

    /** Pushes the latest pre-rendered overlay bitmap into the compositor. */
    fun updateOverlay(bitmap: Bitmap?) {
        OverlayCompositor.Factory.instance?.setOverlayBitmap(bitmap)
    }

    suspend fun startCameraPreview(surface: Surface) {
        try { streamer?.startPreview(surface) } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun stopCameraPreview() { streamer?.stopPreview() }

    /** Direct camera -> RTMP. No popup, no MediaProjection, same streamer as preview. */
    suspend fun goLive(rtmpUrl: String) {
        streamer?.startStream(rtmpUrl)
    }

    suspend fun stopLive() {
        runCatching { streamer?.stopStream() }
    }

    suspend fun flipCamera(): Boolean {
        val s = streamer ?: return isFront
        val nextId = if (isFront) defaultBackCameraId() else defaultFrontCameraId()
        if (nextId == null) return isFront

        runCatching { s.setCameraId(nextId) }
        currentCameraId = nextId
        isFront = !isFront
        withTimeoutOrNull(5_000) { s.videoInput?.sourceFlow?.filterNotNull()?.first() }
        return isFront
    }

    fun muteAudio(muted: Boolean) {
        try {
            val audioSettings = streamer?.javaClass?.getMethod("getAudioSettings")?.invoke(streamer)
            audioSettings?.javaClass?.getMethod("setMuted", Boolean::class.javaPrimitiveType)?.invoke(audioSettings, muted)
        } catch (e: Exception) { }
    }

    fun isFrontCamera(): Boolean = isFront
    fun isTorchAvailable(): Boolean = cameraSource()?.settings?.flash?.isAvailable ?: false
    suspend fun setTorch(enabled: Boolean) { cameraSource()?.settings?.flash?.setIsEnable(enabled) }
    private fun cameraSource(): ICameraSource? = streamer?.videoInput?.sourceFlow?.value as? ICameraSource
    private fun defaultBackCameraId(): String? = findCameraId(CameraCharacteristics.LENS_FACING_BACK)
    private fun defaultFrontCameraId(): String? = findCameraId(CameraCharacteristics.LENS_FACING_FRONT)
    private fun findCameraId(facing: Int): String? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return manager.cameraIdList.firstOrNull { id -> manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == facing }
    }
}

data class EngineVideoConfig(val width: Int, val height: Int, val fps: Int, val bitrateBps: Int)
