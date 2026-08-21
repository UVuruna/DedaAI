package com.meta.wearable.dat.externalsampleapps.cameraaccess.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * "New version → offer update → install in-app" (owner's order, 2026-08-21):
 * every future change — Companion, send/call commands, new UI — must reach
 * users without them ever hunting for an APK again. Modeled on VibeCoder's
 * check-and-offer flow, adapted to sideloaded Android.
 *
 * HOW IT LEARNS ABOUT A NEW VERSION — no GitHub API, no rate limits: it
 * fetches the same stable /releases/latest/download/ URL family the install
 * funnel already uses. publish.py uploads `deda-version.json` next to
 * `deda.apk`; the app compares that json's versionCode with its own.
 * Releases published before this mechanism have no json — the fetch 404s
 * and is treated as "no information", never as an error.
 *
 * RESOURCE LAW (CLAUDE.md): no polling daemon. A check runs only when the
 * user opens the app (throttled to one per 6 h) or taps the button in
 * Settings; the download is a one-shot coroutine; nothing stays resident.
 *
 * THE INSTALL stays inside the app: the APK downloads to the app's own
 * cache and is handed to Android's package installer via FileProvider
 * (Obtainium/F-Droid pattern — REQUEST_INSTALL_PACKAGES + a one-time
 * "allow installs from this app" system grant on first use).
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val BASE = "https://github.com/UVuruna/DedaAI/releases/latest/download"
    private const val VERSION_URL = "$BASE/deda-version.json"
    private const val APK_URL = "$BASE/deda.apk"
    private const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    data class Latest(val versionCode: Int, val versionName: String, val notes: String)

    /** A strictly newer published version, or null. HomeScreen shows the banner off this. */
    private val _available = MutableStateFlow<Latest?>(null)
    val available: StateFlow<Latest?> = _available

    /** True while the APK download runs — the install button disables itself. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS) // the APK itself is ~17 MB
            .build()
    }

    /** Called from MainActivity.onCreate — the ONLY automatic trigger (resource law). */
    fun checkOnAppOpen() {
        val now = System.currentTimeMillis()
        if (now - SettingsManager.updateLastCheckAt < AUTO_CHECK_INTERVAL_MS) return
        SettingsManager.updateLastCheckAt = now
        scope.launch {
            try {
                _available.value = fetchNewer()
            } catch (e: Exception) {
                Log.d(TAG, "auto check failed quietly: $e") // auto = silent by design
            }
        }
    }

    /**
     * The Settings button. [onDone] runs on Main with (foundNewer, failed) —
     * the caller owns the per-language wording of both outcomes.
     */
    fun checkNow(onDone: (foundNewer: Boolean, failed: Boolean) -> Unit) {
        scope.launch {
            var found = false
            var failed = false
            try {
                _available.value = fetchNewer()
                found = _available.value != null
            } catch (e: Exception) {
                Log.w(TAG, "manual check failed: $e")
                failed = true
            }
            withContext(Dispatchers.Main) { onDone(found, failed) }
        }
    }

    /** Null when up to date OR when no version json is published yet (pre-0.1.2 releases). */
    private fun fetchNewer(): Latest? {
        client.newCall(Request.Builder().url(VERSION_URL).build()).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("version fetch HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: throw IOException("empty version body"))
            val latest = Latest(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                notes = json.optString("notes", ""),
            )
            return if (latest.versionCode > BuildConfig.VERSION_CODE) latest else null
        }
    }

    /**
     * Downloads the APK and opens Android's installer over it. The user
     * confirms (or declines) in the system dialog; nothing retries on its own.
     */
    fun downloadAndInstall(context: Context, onError: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        val app = context.applicationContext
        scope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "deda.apk")
                client.newCall(Request.Builder().url(APK_URL).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("apk fetch HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("empty apk body")
                    apk.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                val uri = FileProvider.getUriForFile(
                    app, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                Log.w(TAG, "download/install failed: $e")
                withContext(Dispatchers.Main) { onError() }
            } finally {
                _busy.value = false
            }
        }
    }
}
