package com.meta.wearable.dat.externalsampleapps.cameraaccess.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.telephony.SmsManager
import android.util.Log
import com.meta.wearable.dat.externalsampleapps.cameraaccess.deda.DedaMode
import com.meta.wearable.dat.externalsampleapps.cameraaccess.deda.DedaState
import org.json.JSONObject

/**
 * Executes the registry's commands. Every branch answers with a status
 * JSON the model can speak from — never a silent failure (honesty rule).
 *
 * HANDOFF commands (a call, navigation) end with Deda stepping aside: the
 * phone call or the maps guidance needs the audio route Deda is holding, so
 * after a short beat (long enough for the model's one-sentence confirmation)
 * Deda goes OFF, releasing SCO and audio focus (resource law — the state
 * that needed them is over). The user wakes Deda again afterwards.
 */
object CommandExecutor {
    private const val TAG = "CommandExecutor"
    private const val HANDOFF_DELAY_MS = 3500L

    private val handler = Handler(Looper.getMainLooper())

    /** Pending disambiguation from the last ask_user answer, per tool. */
    private var lastCandidates: List<ContactResolver.Candidate> = emptyList()

    fun execute(context: Context, name: String, args: JSONObject): JSONObject {
        if (!CommandRegistry.enabled(context)) {
            return status("no_permission",
                "voice commands are disabled or their permissions are not granted; " +
                    "the user can enable them in Settings")
        }
        return try {
            when (name) {
                "make_phone_call" -> makeCall(context, args)
                "send_sms" -> sendSms(context, args)
                "set_alarm_or_timer" -> setAlarmOrTimer(context, args)
                "navigate_to" -> navigateTo(context, args)
                else -> status("error", "unknown tool $name")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "$name refused: $e")
            status("no_permission", "Android refused the permission")
        } catch (e: Exception) {
            Log.w(TAG, "$name failed: $e")
            status("error", e.message ?: "failed")
        }
    }

    // ---- calls ---------------------------------------------------------------

    private fun makeCall(context: Context, args: JSONObject): JSONObject {
        val candidate = pickCandidate(context, args) ?: return candidatesAnswer(context, args)
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${candidate.number}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        scheduleHandoff("call placed")
        return status("ok", "calling ${candidate.displayName} (${candidate.label}); " +
            "after your one short sentence the assistant hands over to the call")
    }

    // ---- SMS -----------------------------------------------------------------

    private fun sendSms(context: Context, args: JSONObject): JSONObject {
        val message = args.optString("message").trim()
        if (message.isEmpty()) return status("error", "empty message text")
        val candidate = pickCandidate(context, args) ?: return candidatesAnswer(context, args)
        val sms = context.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(message)
        if (parts.size == 1) {
            sms.sendTextMessage(candidate.number, null, message, null, null)
        } else {
            sms.sendMultipartTextMessage(candidate.number, null, parts, null, null)
        }
        Log.d(TAG, "sms sent to ${candidate.displayName} (${parts.size} part(s))")
        return status("sent", "message sent to ${candidate.displayName}")
    }

    // ---- alarm / timer -------------------------------------------------------

    private fun setAlarmOrTimer(context: Context, args: JSONObject): JSONObject {
        val label = args.optString("label")
        val timerSeconds = args.optInt("timer_seconds", -1)
        val intent = if (timerSeconds > 0) {
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, timerSeconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        } else {
            val hour = args.optInt("hour", -1)
            val minute = args.optInt("minute", 0)
            if (hour !in 0..23) return status("error", "no valid time given")
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return status("ok", if (timerSeconds > 0) "timer set" else "alarm set")
    }

    // ---- navigation ----------------------------------------------------------

    private fun navigateTo(context: Context, args: JSONObject): JSONObject {
        val destination = args.optString("destination").trim()
        if (destination.isEmpty()) return status("error", "no destination given")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + Uri.encode(destination)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        scheduleHandoff("navigation started")
        return status("ok", "navigation to $destination is starting; after your one " +
            "short sentence the assistant hands over to the guidance")
    }

    // ---- shared --------------------------------------------------------------

    /**
     * The chosen candidate: either the single match for the spoken name, or
     * — after an ask_user round-trip — the option the user picked by number.
     */
    private fun pickCandidate(
        context: Context,
        args: JSONObject,
    ): ContactResolver.Candidate? {
        val choice = args.optInt("choice_number", 0)
        if (choice in 1..lastCandidates.size) return lastCandidates[choice - 1]
        val matches = ContactResolver.resolve(context, args.optString("contact"))
        lastCandidates = matches
        return matches.singleOrNull()
    }

    /** The not-found / pick-one answer for the model to speak from. */
    private fun candidatesAnswer(context: Context, args: JSONObject): JSONObject {
        val contact = args.optString("contact")
        if (lastCandidates.isEmpty()) {
            return status("not_found", "no contact matching \"$contact\"")
        }
        val options = lastCandidates.mapIndexed { i, c ->
            "${i + 1}: ${c.displayName}${if (c.label.isBlank()) "" else " (${c.label})"}"
        }.joinToString("; ")
        return status("ask_user",
            "several matches — read the options aloud and call the tool again " +
                "with choice_number: $options")
    }

    private fun scheduleHandoff(why: String) {
        handler.postDelayed({
            if (DedaState.mode.value == DedaMode.TALKING) {
                Log.d(TAG, "handoff ($why) — Deda off, audio route released")
                DedaState.set(DedaMode.OFF)
            }
        }, HANDOFF_DELAY_MS)
    }

    private fun status(status: String, detail: String): JSONObject =
        JSONObject().put("status", status).put("detail", detail)
}
