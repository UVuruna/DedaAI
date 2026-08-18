package com.meta.wearable.dat.externalsampleapps.cameraaccess.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

/**
 * Routes the microphone (and, unavoidably, the answer) through the glasses'
 * Bluetooth headset link. Without this Android records from the phone's own
 * mic even while the glasses are streaming video — measured 2026-08-18:
 * input BUILTIN_MIC, output A2DP. With it, the phone can stay in a pocket.
 * The price is the headset profile's narrow-band audio in both directions.
 *
 * Reference-counted because two components hold the route in turn: the
 * standby wake-word listener and the live session's audio capture. The SCO
 * link is set up once and torn down only when the last holder releases —
 * re-negotiating it between STANDBY and TALKING would cost a ~1 s audio gap.
 */
object HeadsetRoute {
    private const val TAG = "HeadsetRoute"

    private var holds = 0
    private var routed = false

    private val audio: AudioManager
        get() = SettingsManager.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Returns true when the glasses (SCO) mic is now the active input. Falls
     * back silently to the phone mic when the setting is off or no headset is
     * connected — the phone-camera mode.
     */
    @Synchronized
    fun acquire(): Boolean {
        holds++
        if (holds > 1) return routed
        if (!SettingsManager.glassesMicEnabled) return false
        val headset = audio.availableCommunicationDevices
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        if (headset == null) {
            Log.d(TAG, "No Bluetooth headset available — using phone mic")
            return false
        }
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        routed = audio.setCommunicationDevice(headset)
        Log.d(TAG, "Headset mic route to '${headset.productName}': $routed")
        return routed
    }

    @Synchronized
    fun release() {
        if (holds == 0) return
        holds--
        if (holds > 0) return
        if (routed) audio.clearCommunicationDevice()
        audio.mode = AudioManager.MODE_NORMAL
        routed = false
        Log.d(TAG, "Headset mic route released")
    }
}
