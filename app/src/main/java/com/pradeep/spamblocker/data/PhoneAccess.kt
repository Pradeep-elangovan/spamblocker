package com.pradeep.spamblocker.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.pradeep.spamblocker.domain.NumberParser

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

object ContactsLookup {
    fun available(context: Context) = context.hasPermission(Manifest.permission.READ_CONTACTS)

    /** True when [number] belongs to a saved contact. PhoneLookup copes with +91 / 0 / bare formats itself. */
    fun isContact(context: Context, number: String): Boolean {
        if (!available(context)) return false
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null,
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }
}

object CallLogImporter {
    private fun outcomeOf(type: Int) = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "answered"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        CallLog.Calls.BLOCKED_TYPE -> "blocked"
        else -> ""
    }

    /**
     * Reads the phone's call log. Calls the app screened get their outcome and talk time filled in; calls it never
     * saw are added to the history. Returns how many rows were added or updated.
     */
    suspend fun sync(context: Context, repo: SpamRepository, limit: Int = 1000): Int {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return 0
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        var changed = 0
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            args, null,
        )?.use { c ->
            val iNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val iDuration = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val outcome = outcomeOf(c.getInt(iType))
                if (outcome.isEmpty()) continue // outgoing and other types
                val parsed = NumberParser.parse(c.getString(iNumber))
                val ts = c.getLong(iDate)
                val seconds = c.getInt(iDuration)
                val e164 = parsed.e164
                val existing = e164?.let { repo.findCallNear(it, ts) }
                if (existing != null) {
                    if (existing.outcome.isEmpty()) {
                        repo.setCallOutcome(existing.id, outcome, seconds)
                        changed++
                    }
                } else {
                    repo.logCall(
                        CallEntity(
                            e164 = e164, display = parsed.display, timestamp = ts, blocked = false,
                            reason = "IMPORTED", region = parsed.regions.joinToString(", "), source = "import",
                            outcome = outcome, durationSec = seconds,
                        ),
                    )
                    changed++
                }
            }
        }
        return changed
    }

    /** Numbers you have called yourself: a strong sign that a later call from them is not spam. */
    fun dialedNumbers(context: Context, limit: Int = 3000): Set<String> {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return emptySet()
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${CallLog.Calls.TYPE} = ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()))
        }
        val out = HashSet<String>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER), args, null)?.use { c ->
            val i = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            while (c.moveToNext()) NumberParser.parse(c.getString(i)).e164?.let { out += it }
        }
        return out
    }
}
