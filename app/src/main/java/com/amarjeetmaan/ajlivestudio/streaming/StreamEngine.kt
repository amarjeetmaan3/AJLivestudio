package com.amarjeetmaan.ajlivestudio.streaming

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.util.Size

import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.setConfig

/**
 * AJ Live Studio Stream Engine
 *
 * Current stage:
 *
 * Camera
 *   ↓
 * StreamPack 3.2.0 SingleStreamer
 *   ↓
 * Video + Audio encoder
 *   ↓
 * RTMP
 *   ↓
 * YouTube
 *
 * IMPORTANT:
 * ScreenStreamer has intentionally been removed.
 *
 * StreamPack 3.2.0 does not expose the old:
 *
 * io.github.thibaultbee.streampack.streamers.ScreenStreamer
 *
 * API.
 *
 * Overlay compositing will be connected to the StreamPack
 * SurfaceProcessor in the next stage.
 */
class StreamEngine(
    private val context: Context
) {

    var streamer: SingleStreamer? = null
        private set

    private var audioConfig: AudioConfig? = null
    private var videoConfig: VideoConfig? = null

    /**
     * Configure the streamer.
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
     * Starts the camera RTMP stream.
     *
     * mediaProjectionIntent is kept in the method signature
     * temporarily so the existing CameraPreviewScreen does not
     * need to be changed yet.
     *
     * It is NOT used by the current camera streamer.
     */
    suspend fun goLive(
        rtmpUrl: String,
        mediaProjectionIntent: Intent? = null
    ) {

        // Stop and release any previous streamer.
        streamer?.let { oldStreamer ->

            runCatching {
                oldStreamer.stopStream()
            }

            runCatching {
                oldStreamer.close()
            }

            runCatching {
                oldStreamer.release()
            }
        }

        streamer = null

        /*
         * StreamPack 3.2.0 official camera streamer.
         */
        val newStreamer = cameraSingleStreamer(
            context = context
        )

        val audio = audioConfig
        val video = videoConfig

        /*
         * StreamPack 3.2.0 requires the combined
         * audio/video configuration through setConfig().
         */
        if (audio != null && video != null) {
            newStreamer.setConfig(
                audioConfig = audio,
                videoConfig = video
            )
        } else {
            throw IllegalStateException(
                "StreamEngine is not initialized. " +
                    "Call initialize() before goLive()."
            )
        }

        streamer = newStreamer

        /*
         * Open RTMP endpoint and start streaming.
         */
        newStreamer.startStream(rtmpUrl)
    }

    /**
     * Stops the current stream.
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
