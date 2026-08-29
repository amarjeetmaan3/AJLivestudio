package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.util.Size
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.setConfig
import io.github.thibaultbee.streampack.streamers.ScreenStreamer
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

    suspend fun goLive(rtmpUrl: String, mediaProjectionIntent: Intent) {
        // Start foreground service for Screen Capture
        val serviceIntent = Intent(context, ScreenShareService::class.java)
        context.startForegroundService(serviceIntent)
        
        // Using the correct ScreenStreamer class for StreamPack 3.2.0
        val newStreamer = ScreenStreamer(context, mediaProjectionIntent)
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
