package com.meta.wearable.dat.externalsampleapps.cameraaccess.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.meta.wearable.dat.externalsampleapps.cameraaccess.MainActivity
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R

/**
 * The one place that knows what a foreground-service notification looks like
 * in this app. StreamingService and GlassesButtonService used to each carry a
 * copy of this skeleton, and the copies had already drifted apart (pregled 1).
 */
object ServiceNotifications {

    fun ensureChannel(context: Context, id: String, name: String, description: String) {
        val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW).apply {
            this.description = description
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Ongoing, low-priority, opens MainActivity on tap. The caller may add
     * actions before calling build().
     */
    fun builder(
        context: Context,
        channelId: String,
        title: String,
        text: String,
    ): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
    }
}
