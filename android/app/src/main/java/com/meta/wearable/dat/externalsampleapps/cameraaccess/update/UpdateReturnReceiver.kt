package com.meta.wearable.dat.externalsampleapps.cameraaccess.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.MainActivity
import com.meta.wearable.dat.externalsampleapps.cameraaccess.deda.Greeter
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.ServiceNotifications

/**
 * The way back into the app after a successful self-update: installing the
 * new APK kills this very process, so no success callback can exist —
 * MY_PACKAGE_REPLACED is the only signal, and it must be declared in the
 * manifest (the process is dead when it fires; runtime registration cannot
 * work). Two carriers, the shape VibeCoder's updater proved: a best-effort
 * activity start (Android 12+ usually forbids it from the background) and
 * an always-posted notification as the reliable one.
 */
class UpdateReturnReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "UpdateReturnReceiver"
        private const val CHANNEL_ID = "deda_update"
        private const val NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Log.d(TAG, "package replaced — inviting the user back in")
        // Fresh process: nothing has initialized settings yet, and the
        // localized texts below resolve through them.
        SettingsManager.init(context)
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.d(TAG, "background activity start refused (normal): $e")
        }
        val texts = Greeter.Texts.forCurrentLanguage()
        ServiceNotifications.ensureChannel(context, CHANNEL_ID, "Deda", "Update notifications")
        val notification = ServiceNotifications.builder(
            context, CHANNEL_ID, "DedaAI", texts.updated)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }
}
