package com.meta.wearable.dat.externalsampleapps.cameraaccess.stream

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.util.ServiceNotifications

/**
 * Foreground service that keeps the camera streaming alive when the screen is locked
 * or the app is in the background.
 *
 * - Displays a persistent notification while streaming
 * - Acquires a partial wake lock to prevent CPU sleep
 * - Allows the streaming to continue when the app is backgrounded
 */
class StreamingService : Service() {

  companion object {
    private const val TAG = "StreamingService"
    private const val CHANNEL_ID = "streaming_channel"
    private const val CHANNEL_NAME = "Camera Streaming"
    private const val NOTIFICATION_ID = 1001
    private const val WAKELOCK_TAG = "VisionClaw::StreamingWakeLock"

    fun start(context: Context) {
      val intent =
          Intent(context, StreamingService::class.java).apply { `package` = context.packageName }
      context.startForegroundService(intent)
    }

    fun stop(context: Context) {
      val intent =
          Intent(context, StreamingService::class.java).apply { `package` = context.packageName }
      context.stopService(intent)
    }
  }

  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Service created")
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.d(TAG, "Service started")

    startForeground(
        NOTIFICATION_ID,
        createNotification(),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
    )

    acquireWakeLock()

    return START_STICKY
  }

  override fun onDestroy() {
    Log.d(TAG, "Service destroyed")
    releaseWakeLock()
    super.onDestroy()
  }

  private fun createNotificationChannel() {
    ServiceNotifications.ensureChannel(
        this, CHANNEL_ID, CHANNEL_NAME, "Notifications for active camera streaming")
  }

  private fun createNotification(): Notification =
      ServiceNotifications.builder(
              this,
              CHANNEL_ID,
              title = "Camera Streaming",
              text = "Streaming from your glasses...",
          )
          .build()

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
      wakeLock =
          powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
          }
      Log.d(TAG, "WakeLock acquired")
    }
  }

  private fun releaseWakeLock() {
    wakeLock?.let {
      if (it.isHeld) {
        it.release()
        Log.d(TAG, "WakeLock released")
      }
    }
    wakeLock = null
  }
}
