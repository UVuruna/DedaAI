package com.meta.wearable.dat.externalsampleapps.cameraaccess.commands

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.telecom.TelecomManager
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

    /**
     * The open disambiguation, tied to the QUESTION that produced it. A
     * choice_number means nothing on its own — it indexes the answer to one
     * particular question, so it must die with that question. A bare list
     * survived whole conversations and would dial option 2 of "which Marko"
     * when the user later said "call Ana, the second one".
     */
    private data class Pending(
        val query: String,
        val candidates: List<ContactResolver.Candidate>,
    )

    @Volatile private var pending: Pending? = null

    /** Resource law: no disambiguation outlives the conversation that opened it. */
    fun clearPending() {
        pending = null
    }

    private fun norm(name: String) = name.trim().lowercase()

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
        // NOT startActivity: Deda works with the phone in a pocket, and
        // Android 10+ refuses an activity start from an app with no visible
        // window — silently, so the model would announce a call that never
        // happened. TelecomManager hands the call to the system, which raises
        // its own in-call UI with system privileges; a missing CALL_PHONE
        // raises SecurityException, which the caller turns into no_permission.
        context.getSystemService(TelecomManager::class.java).placeCall(
            Uri.fromParts("tel", candidate.number, null), null)
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
        return startAside(context, intent,
            if (timerSeconds > 0) "timer set" else "alarm set", handoff = false)
    }

    // ---- navigation ----------------------------------------------------------

    private fun navigateTo(context: Context, args: JSONObject): JSONObject {
        val destination = args.optString("destination").trim()
        if (destination.isEmpty()) return status("error", "no destination given")
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=" + Uri.encode(destination)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startAside(context, intent,
            "navigation to $destination is starting; after your one short " +
                "sentence the assistant hands over to the guidance",
            handoff = true)
    }

    /**
     * Starts an activity for a command and tells the truth about it.
     *
     * With the phone in a pocket the app has no visible window, and Android
     * 10+ then refuses the start WITHOUT throwing — so a bare startActivity
     * let the model announce an alarm or a route that never happened. There
     * is no API to ask "was I allowed?", so the answer says plainly that the
     * user must confirm it happened, and no hand-over runs when we could not
     * even deliver the intent (no app to handle it).
     */
    private fun startAside(
        context: Context,
        intent: Intent,
        okDetail: String,
        handoff: Boolean,
    ): JSONObject {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "no app handles ${intent.action}: $e")
            return status("error", "no app on this phone can do that")
        }
        if (handoff) scheduleHandoff("activity handed over")
        return status("ok", okDetail)
    }

    // ---- shared --------------------------------------------------------------

    /**
     * The chosen candidate: either the single match for the spoken name, or
     * — after an ask_user round-trip about THAT SAME name — the option the
     * user picked by number. A choice_number that does not belong to the
     * open question is ignored, and the name is resolved afresh.
     */
    private fun pickCandidate(
        context: Context,
        args: JSONObject,
    ): ContactResolver.Candidate? {
        val query = args.optString("contact")
        val open = pending
        val choice = args.optInt("choice_number", 0)
        if (choice > 0 && open != null && norm(open.query) == norm(query)) {
            return open.candidates.getOrNull(choice - 1)
        }
        val matches = ContactResolver.resolve(context, query)
        pending = if (matches.size > 1) Pending(query, matches) else null
        return matches.singleOrNull()
    }

    /** The not-found / pick-one / bad-choice answer for the model to speak from. */
    private fun candidatesAnswer(context: Context, args: JSONObject): JSONObject {
        val contact = args.optString("contact")
        val open = pending
        if (args.optInt("choice_number", 0) > 0 && open != null &&
            norm(open.query) == norm(contact)
        ) {
            return status("invalid_choice",
                "that number is not on the list — read the options again")
        }
        if (open == null || norm(open.query) != norm(contact)) {
            return status("not_found", "no contact matching \"$contact\"")
        }
        val options = open.candidates.mapIndexed { i, c ->
            "${i + 1}: ${c.displayName}${if (c.label.isBlank()) "" else " (${c.label})"}"
        }.joinToString("; ")
        return status("ask_user",
            "several matches — read the options aloud and call the tool again " +
                "with the SAME contact name plus choice_number: $options")
    }

    private fun scheduleHandoff(why: String) {
        val gen = DedaState.talkGeneration
        handler.postDelayed({
            // Only step aside from the very conversation that asked for it:
            // a new "Hej Deda" inside these seconds must not be killed by the
            // previous one's handoff (the race DedaController already guards
            // with its own generation counter).
            if (DedaState.mode.value == DedaMode.TALKING &&
                DedaState.talkGeneration == gen
            ) {
                Log.d(TAG, "handoff ($why) — Deda off, audio route released")
                DedaState.set(DedaMode.OFF)
            }
        }, HANDOFF_DELAY_MS)
    }

    private fun status(status: String, detail: String): JSONObject =
        JSONObject().put("status", status).put("detail", detail)
}
