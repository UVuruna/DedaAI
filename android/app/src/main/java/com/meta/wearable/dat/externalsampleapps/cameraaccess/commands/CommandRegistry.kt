package com.meta.wearable.dat.externalsampleapps.cameraaccess.commands

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.meta.wearable.dat.externalsampleapps.cameraaccess.settings.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * The one registry of Deda's voice commands (owner's order 2026-08-21:
 * implement what a sideload can, with calls and SMS first). Everything a
 * command needs lives in one entry here — declaration for the Live setup
 * message, the permissions it stands on, and its line in the prompt
 * addendum — so a new command is a new entry, never a copied block (Law 8).
 *
 * The confirmation rule is enforced in the PROMPT, not in code: the model
 * must read a message back and hear a clear yes before it may call
 * send_sms. The tool call itself is the commit.
 */
object CommandRegistry {

    /** Dangerous permissions the call/SMS commands stand on. */
    val COMMAND_PERMISSIONS = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
    )

    fun permissionsGranted(context: Context): Boolean =
        COMMAND_PERMISSIONS.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    /** Commands are ON when the user enabled them in Settings AND granted the permissions. */
    fun enabled(context: Context): Boolean =
        SettingsManager.voiceCommandsEnabled && permissionsGranted(context)

    /**
     * Function declarations for the Live setup message. end_conversation is
     * always there; the command tools only when enabled — an undeclared tool
     * cannot be hallucinated into a call.
     */
    fun toolDeclarations(context: Context): JSONArray {
        val tools = JSONArray()
        tools.put(JSONObject().apply {
            put("name", "end_conversation")
            put(
                "description",
                "End the current voice conversation immediately. Call this " +
                    "the moment the user says the farewell phrase (Cao Deda / " +
                    "Ciao Deda / Chao Deda in any language) or clearly asks " +
                    "to stop talking.",
            )
        })
        if (!enabled(context)) return tools

        tools.put(
            declare(
                "make_phone_call",
                "Place a regular phone call to a contact from the user's " +
                    "address book. Call audio goes through the glasses. After " +
                    "this succeeds, say one short sentence that you are " +
                    "calling, then the assistant hands over to the call.",
                JSONObject().apply {
                    put("contact", param("string",
                        "The contact name exactly as the user said it."))
                    put("choice_number", param("integer",
                        "Only after ask_user: the 1-based number of the option the user chose."))
                },
                required = JSONArray().put("contact"),
            )
        )
        tools.put(
            declare(
                "send_sms",
                "Send an SMS text message to a contact from the user's " +
                    "address book. ONLY call this after you have read the " +
                    "message text back to the user and they clearly confirmed " +
                    "sending it.",
                JSONObject().apply {
                    put("contact", param("string",
                        "The contact name exactly as the user said it."))
                    put("message", param("string", "The exact message text to send."))
                    put("choice_number", param("integer",
                        "Only after ask_user: the 1-based number of the option the user chose."))
                },
                required = JSONArray().put("contact").put("message"),
            )
        )
        tools.put(
            declare(
                "set_alarm_or_timer",
                "Set an alarm at a clock time, or a countdown timer. Uses the " +
                    "phone's own clock app.",
                JSONObject().apply {
                    put("hour", param("integer", "Alarm hour 0-23. Omit for a timer."))
                    put("minute", param("integer", "Alarm minute 0-59. Omit for a timer."))
                    put("timer_seconds", param("integer",
                        "Timer length in seconds. Omit for an alarm."))
                    put("label", param("string", "Short label, in the user's language."))
                },
            )
        )
        tools.put(
            declare(
                "navigate_to",
                "Start turn-by-turn navigation to a destination in the phone's " +
                    "maps app. Spoken guidance plays through the glasses. After " +
                    "this succeeds, say one short sentence that navigation is " +
                    "starting, then the assistant hands over to it.",
                JSONObject().apply {
                    put("destination", param("string",
                        "The destination exactly as the user said it."))
                },
                required = JSONArray().put("destination"),
            )
        )
        return tools
    }

    /**
     * Appended to the system prompt so the model knows what it can DO now —
     * the base prompt keeps the standing rule that anything beyond the
     * declared tools is honestly refused.
     */
    fun promptAddendum(context: Context): String {
        if (!enabled(context)) return ""
        return """

YOUR TOOLS FOR ACTING
- make_phone_call: when asked to call someone. If the tool answers with
  status ask_user, read the numbered options aloud and call it again with
  choice_number once the user picks. If it answers not_found, say so.
- send_sms: when asked to send a message. First collect the text, read it
  back, and ask for confirmation in the user's language. Only after a clear
  yes call the tool. Never send without the read-back.
- set_alarm_or_timer and navigate_to: use directly when asked.
- Every tool answers with a status. On error or no_permission, tell the
  user plainly in one sentence; never pretend an action happened.
"""
    }

    private fun declare(
        name: String,
        description: String,
        properties: JSONObject,
        required: JSONArray? = null,
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("description", description)
        put("parameters", JSONObject().apply {
            put("type", "object")
            put("properties", properties)
            required?.let { put("required", it) }
        })
    }

    private fun param(type: String, description: String): JSONObject =
        JSONObject().apply {
            put("type", type)
            put("description", description)
        }
}
