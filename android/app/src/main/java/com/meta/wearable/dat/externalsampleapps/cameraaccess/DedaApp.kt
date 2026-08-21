package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.app.Application
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager

/**
 * The ONE place settings are initialised, because Android creates this app's
 * components in more ways than one (crash found 2026-08-21 on the emulator,
 * but nothing about it is emulator-specific):
 *
 *   FATAL EXCEPTION: Unable to create service GlassesButtonService:
 *   lateinit property prefs has not been initialized
 *     at SettingsManager.getDedaActivationMode
 *     at GlassesButtonService.buildNotification
 *     at GlassesButtonService.onCreate
 *
 * GlassesButtonService is START_STICKY: whenever the process dies — low
 * memory, any crash, the system reclaiming it — Android recreates the
 * SERVICE ALONE, with no MainActivity to have called SettingsManager.init
 * first. The service's very first act, building its notification, then read
 * an uninitialised lateinit and killed the fresh process: "DedaAI keeps
 * stopping", and Deda stays dead until the user opens the app by hand. The
 * same hole swallowed the notification's own Turn-off / Quit buttons after a
 * process death — the tap starts the service, the service crashes.
 *
 * An Application subclass is created before ANY component of the process
 * (activity, service or receiver), so initialising here closes the hole for
 * all of them at once, instead of every entry point remembering to do it.
 */
class DedaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
    }
}
