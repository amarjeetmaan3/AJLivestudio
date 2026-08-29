package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.projection.MediaProjection
import android.util.Size
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.setConfig
import io.github.thibaultbee.streampack.core.streamers.single.videoMediaProjectionSingleStreamer
import com.amarjeetmaan.ajlivestudio.screenshare.ScreenShareService

class StreamEngine(private val context: Context) {
    var streamer: SingleStreamer? = null
        private set
    
    private var audioConfig: AudioConfig? = null
    private var videoConfig: VideoConfig? = null

    fun initialize(config: EngineVideoConfig) {
        audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )
        videoConfig = VideoConfig(
            startBitrate = config.bitrateBps,
            resolution = Size(config.width, config.height),
            fps = config.fps
        )
    }

    /**
     * Streams by screen-recording the app's own UI (camera preview + the
     * Compose overlay layer, exactly as displayed) via StreamPack's
     * MediaProjection video source. This is why the camera preview MUST
     * render through a TextureView-backed surface (PreviewView in
     * COMPATIBLE mode) — SurfaceView content is invisible to screen
     * capture and would show up as black to the viewer.
     *
     * mediaProjection must come from ScreenShareController.getMediaProjection(),
     * called AFTER ScreenShareService (foreground service, type
     * "mediaProjection") is already running — that ordering is an Android
     * 14+ platform requirement.
     *
     * Built against StreamPack 3.2.0's confirmed Dokka API
     * (videoMediaProjectionSingleStreamer, in
     * io.github.thibaultbee.streampack.core.streamers.single) — not guessed.
     */
    suspend fun goLive(rtmpUrl: String, mediaProjection: MediaProjection) {
        context.startForegroundService(Intent(context, ScreenShareService::class.java))

        val newStreamer = videoMediaProjectionSingleStreamer(
            context = context,
            mediaProjection = mediaProjection
        )
        audioConfig?.let { a -> videoConfig?.let { v -> newStreamer.setConfig(a, v) } }

        streamer = newStreamer
        streamer?.startStream(rtmpUrl)
    }

    suspend fun stopLive() {
        runCatching { streamer?.stopStream() }
        runCatching { streamer?.release() }
        streamer = null
        context.stopService(Intent(context, ScreenShareService::class.java))
    }
}

data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int,
)
