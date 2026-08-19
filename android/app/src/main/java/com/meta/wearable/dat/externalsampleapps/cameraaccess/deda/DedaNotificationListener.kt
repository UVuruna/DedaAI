package com.meta.wearable.dat.externalsampleapps.cameraaccess.deda

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Empty notification listener. We never read a notification — the binding is
 * only there because Android gates MediaSessionManager.getActiveSessions()
 * behind it, and that is how Deda hands a single tap back to whichever app
 * was playing music before Deda took the buttons. Same trick every headset
 * button-remapper app uses.
 */
class DedaNotificationListener : NotificationListenerService() {
    companion object {
        fun componentName(context: Context) =
            ComponentName(context, DedaNotificationListener::class.java)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver, "enabled_notification_listeners"
            ) ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it) == componentName(context) }
        }
    }
}
