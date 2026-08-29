package com.amarjeetmaan.ajlivestudio.screenshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import io.github.thibaultbee.streampack.services.MediaProjectionService

class ScreenShareService : MediaProjectionService() {
    override fun createNotification(): Notification {
        val channelId = "screen_share_channel"
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(channelId, "AJ Live", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AJ Live Studio")
            .setContentText("Screen Capture is active for Overlays")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}
