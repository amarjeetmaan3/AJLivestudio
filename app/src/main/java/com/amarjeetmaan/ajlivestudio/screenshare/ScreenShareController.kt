package com.amarjeetmaan.ajlivestudio.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

/**
 * Wraps the standard Android MediaProjection permission flow, confirmed
 * against StreamPack 3.2.0's real, published Dokka API
 * (thibaultbee.github.io/StreamPack/streampack-core) — not guessed.
 *
 * getMediaProjection() must only be called AFTER a foreground service of
 * type "mediaProjection" (ScreenShareService) is already running — that's
 * an Android 14+ requirement, not a StreamPack one.
 */
class ScreenShareController(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun createCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun isResultGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK

    /**
     * Turns the raw ActivityResult (resultCode + data Intent) from the
     * system's screen-capture permission dialog into a real MediaProjection
     * object that StreamPack's MediaProjectionVideoSourceFactory can use.
     */
    fun getMediaProjection(resultCode: Int, data: Intent): MediaProjection =
        projectionManager.getMediaProjection(resultCode, data)
}
