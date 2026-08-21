package com.meta.wearable.dat.externalsampleapps.cameraaccess.commands

import android.content.Context
import android.provider.ContactsContract

/**
 * Spoken name → phone numbers, read-only. Disambiguation is a CONVERSATION,
 * not a screen: when several contacts (or several numbers of one contact)
 * match, the caller hands the numbered list back to the model, the model
 * reads it aloud, and the user answers with a number.
 */
object ContactResolver {

    data class Candidate(val displayName: String, val number: String, val label: String)

    private const val MAX_CANDIDATES = 5

    /**
     * All numbers whose contact display-name contains [spokenName]
     * (case-insensitive), best-guess ordered: exact name match first, then
     * primary numbers. Duplicate numbers collapse to one entry.
     */
    fun resolve(context: Context, spokenName: String): List<Candidate> {
        val name = spokenName.trim()
        if (name.isEmpty()) return emptyList()
        val out = mutableListOf<Candidate>()
        val seenNumbers = mutableSetOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { cursor ->
            val iName = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val iNumber = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.NUMBER)
            val iType = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.TYPE)
            val iLabel = cursor.getColumnIndexOrThrow(
                ContactsContract.CommonDataKinds.Phone.LABEL)
            while (cursor.moveToNext() && out.size < MAX_CANDIDATES * 2) {
                val number = cursor.getString(iNumber)?.trim() ?: continue
                val normalized = number.filter { it.isDigit() || it == '+' }
                if (normalized.isEmpty() || !seenNumbers.add(normalized)) continue
                out.add(
                    Candidate(
                        displayName = cursor.getString(iName) ?: name,
                        number = number,
                        label = typeLabel(cursor.getInt(iType), cursor.getString(iLabel)),
                    )
                )
            }
        }
        // Exact full-name matches outrank substring ones ("Ana" before "Anastasija").
        return out.sortedBy { if (it.displayName.equals(name, ignoreCase = true)) 0 else 1 }
            .take(MAX_CANDIDATES)
    }

    private fun typeLabel(type: Int, custom: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> custom ?: ""
        else -> ""
    }
}
