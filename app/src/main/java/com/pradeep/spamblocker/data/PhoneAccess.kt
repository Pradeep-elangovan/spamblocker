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
    /** Imports recent incoming / missed / rejected calls into the in-app history so they can be flagged. */
    suspend fun import(context: Context, repo: SpamRepository, limit: Int = 300): Int {
        if (!context.hasPermission(Manifest.permission.READ_CALL_LOG)) return 0
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        var added = 0
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            args, null,
        )?.use { c ->
            val iNumber = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val iType = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            while (c.moveToNext()) {
                val type = c.getInt(iType)
                val incoming = type == CallLog.Calls.INCOMING_TYPE || type == CallLog.Calls.MISSED_TYPE ||
                    type == CallLog.Calls.REJECTED_TYPE || type == CallLog.Calls.BLOCKED_TYPE
                if (!incoming) continue
                val parsed = NumberParser.parse(c.getString(iNumber))
                val ts = c.getLong(iDate)
                val e164 = parsed.e164
                if (e164 != null && repo.hasCallNear(e164, ts)) continue
                repo.logCall(
                    CallEntity(
                        e164 = e164, display = parsed.display, timestamp = ts, blocked = false,
                        reason = "IMPORTED", region = parsed.regions.joinToString(", "), source = "import",
                    ),
                )
                added++
            }
        }
        return added
    }
}
