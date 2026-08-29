package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.media.AudioFormat
import android.util.Size

import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.setAudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.setVideoConfig

/**
 * AJ Live Studio streaming engine.
 *
 * StreamPack 3.2.0 camera pipeline:
 *
 * Camera
 *   ↓
 * cameraSingleStreamer()
 *   ↓
 * VideoConfig / AudioConfig
 *   ↓
 * RTMP
 *   ↓
 * YouTube
 *
 * IMPORTANT:
 * The old ScreenStreamer implementation has intentionally
 * been removed because ScreenStreamer is not part of the
 * StreamPack 3.2.0 API.
 */
class StreamEngine(
    private val context: Context
) {

    var streamer: SingleStreamer? = null
        private set

    private var audioConfig: AudioConfig? = null
    private var videoConfig: VideoConfig? = null

    /**
     * Prepare the audio/video configuration.
     */
    fun initialize(config: EngineVideoConfig) {

        audioConfig = AudioConfig(
            startBitrate = 128_000,
            sampleRate = 44_100,
            channelConfig = AudioFormat.CHANNEL_IN_STEREO
        )

        videoConfig = VideoConfig(
            startBitrate = config.bitrateBps,
            resolution = Size(
                config.width,
                config.height
            ),
            fps = config.fps
        )
    }

    /**
     * Start camera live streaming.
     *
     * mediaProjectionIntent is retained in the method signature so
     * the existing CameraViewModel / UI does not need to change yet.
     *
     * It is intentionally NOT used here.
     *
     * The current streaming stage is camera → StreamPack → RTMP.
     * The overlay compositor will be connected to the video
     * processing surface in the next stage.
     */
    suspend fun goLive(
        rtmpUrl: String,
        mediaProjectionIntent: android.content.Intent? = null
    ) {

        // Always clean up an old streamer before creating a new one.
        runCatching {
            streamer?.stopStream()
        }

        runCatching {
            streamer?.close()
        }

        runCatching {
            streamer?.release()
        }

        streamer = null

        /*
         * StreamPack 3.2.0 official camera factory.
         *
         * This creates the complete camera streaming pipeline.
         */
        val newStreamer = cameraSingleStreamer(
            context = context
        )

        /*
         * Apply audio configuration.
         */
        audioConfig?.let { config ->
            newStreamer.setAudioConfig(config)
        }

        /*
         * Apply video configuration.
         */
        videoConfig?.let { config ->
            newStreamer.setVideoConfig(config)
        }

        streamer = newStreamer

        /*
         * Start RTMP/RTMPS stream.
         */
        newStreamer.startStream(rtmpUrl)
    }

    /**
     * Stop the current live stream and release StreamPack.
     */
    suspend fun stopLive() {

        val currentStreamer = streamer ?: return

        runCatching {
            currentStreamer.stopStream()
        }

        runCatching {
            currentStreamer.close()
        }

        runCatching {
            currentStreamer.release()
        }

        streamer = null
    }
}


/**
 * Video configuration used by AJ Live Studio.
 */
data class EngineVideoConfig(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateBps: Int
)
