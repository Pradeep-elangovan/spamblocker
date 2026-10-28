package com.pradeep.spamblocker.data

import android.content.Context
import com.pradeep.spamblocker.domain.LabelInput
import com.pradeep.spamblocker.domain.Labeling
import com.pradeep.spamblocker.domain.NumberParser
import com.pradeep.spamblocker.domain.Reason
import java.util.Calendar

/**
 * Builds a labelled CSV of unknown callers for offline analysis. Numbers are masked to their first 6 digits;
 * saved contacts are left out. Features that need the full number (call counts, "did I ever dial this") are
 * computed here, on the phone, and only the result is written.
 */
object DatasetExporter {
    const val HEADER = "prefix6,region,is_mobile,hour,weekday,prior_calls,dialed_before,outcome,duration_sec,label,label_source\n"

    suspend fun buildCsv(context: Context, repo: SpamRepository): String {
        val calls = repo.allCallsAscending()
        val blocked = repo.blockedNumbers()
        val allowed = repo.allowedNumbers()
        val dialed = CallLogImporter.dialedNumbers(context)
        val contactCache = HashMap<String, Boolean>()
        val seen = HashMap<String, Int>()
        val cal = Calendar.getInstance()
        val sb = StringBuilder(HEADER)

        for (c in calls) {
            val e164 = c.e164
            val prior = if (e164 != null) seen[e164] ?: 0 else 0
            if (e164 != null) seen[e164] = prior + 1

            if (c.reason == Reason.CONTACT.name) continue
            if (e164 != null && contactCache.getOrPut(e164) { ContactsLookup.isContact(context, e164) }) continue

            val parsed = NumberParser.parse(e164)
            cal.timeInMillis = c.timestamp
            val outcome = c.outcome.ifEmpty { if (c.blocked) "blocked" else "unknown" }
            val label = Labeling.label(
                LabelInput(
                    inAllowlist = e164 != null && e164 in allowed,
                    inBlocklist = e164 != null && e164 in blocked,
                    outcome = outcome,
                    durationSec = c.durationSec,
                    appBlocked = c.blocked,
                    appReason = c.reason,
                ),
            )
            sb.append(parsed.national?.take(6).orEmpty()).append(',')
                .append('"').append(c.region.replace("\"", "\"\"")).append('"').append(',')
                .append(parsed.isMobile?.let { if (it) "1" else "0" } ?: "").append(',')
                .append(cal.get(Calendar.HOUR_OF_DAY)).append(',')
                .append(cal.get(Calendar.DAY_OF_WEEK)).append(',')
                .append(prior).append(',')
                .append(if (e164 != null && e164 in dialed) 1 else 0).append(',')
                .append(outcome).append(',')
                .append(c.durationSec).append(',')
                .append(label.label).append(',')
                .append(label.source.replace(',', ';'))
                .append('\n')
        }
        return sb.toString()
    }
}
