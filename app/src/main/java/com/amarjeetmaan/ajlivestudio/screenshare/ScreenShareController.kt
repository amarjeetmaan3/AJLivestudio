package com.amarjeetmaan.ajlivestudio.screenshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager

/**
 * Wraps the standard Android MediaProjection permission flow.
 *
 * This part is solid, verified, standard Android API (not StreamPack-
 * specific) — createScreenCaptureIntent() + the system "Start recording
 * or casting?" dialog.
 *
 * What's NOT wired up yet: feeding the granted MediaProjection into
 * StreamPack's encoder as a video source. StreamPack does support this
 * (its README references a screen-recorder service + demos/screenrecorder
 * sample), but different README snapshots I found named the base service
 * class differently across versions (MediaProjectionService vs
 * DefaultScreenRecorderService vs ScreenRecorderRtmpLiveService) — enough
 * inconsistency that guessing which one matches whatever 3.1.2 actually
 * ships would be exactly the kind of blind-typed code that silently fails
 * to compile or, worse, compiles against the wrong class and crashes at
 * runtime. That wiring is the concrete next step once confirmed against
 * the actual resolved dependency (check demos/screenrecorder in
 * github.com/ThibaultBee/StreamPack for the version Gradle resolves).
 */
class ScreenShareController(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    fun createCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun isResultGranted(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK
}
