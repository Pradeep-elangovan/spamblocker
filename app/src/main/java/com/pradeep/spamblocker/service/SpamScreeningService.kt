package com.pradeep.spamblocker.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import com.pradeep.spamblocker.SpamApp
import com.pradeep.spamblocker.data.CallEntity
import com.pradeep.spamblocker.data.ContactsLookup
import com.pradeep.spamblocker.domain.BlockAction
import com.pradeep.spamblocker.domain.CallInfo
import com.pradeep.spamblocker.domain.Decision
import com.pradeep.spamblocker.domain.NumberParser
import com.pradeep.spamblocker.domain.Reason
import com.pradeep.spamblocker.domain.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SpamScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow)
            return
        }

        scope.launch {
            val app = application as SpamApp
            val parsed = NumberParser.parse(details.handle?.schemeSpecificPart)
            val callerName = details.callerDisplayName.orEmpty()
            val contactName = details.contactDisplayName.orEmpty()
            // Match against every name Android gives us; the carrier's spam label may sit in either.
            val matchName = listOf(callerName, contactName).filter { it.isNotBlank() }.distinct().joinToString(" | ")
            val extras = describe(details)
            val verification = verificationLabel(details.callerNumberVerificationStatus)

            // Android gives us a few seconds; anything slower than this is treated as "allow".
            val decision: Decision? = try {
                withTimeoutOrNull(3_500) {
                    val info = CallInfo(
                        e164 = parsed.e164,
                        national = parsed.national,
                        isContact = parsed.e164?.let { ContactsLookup.isContact(this@SpamScreeningService, it) } ?: false,
                        contactsAvailable = ContactsLookup.available(this@SpamScreeningService),
                        callerName = matchName.ifEmpty { null },
                        verificationFailed = details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED,
                        regions = parsed.regions,
                        isMobile = parsed.isMobile,
                    )
                    RuleEngine.decide(info, app.repository.buildRuleSet())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rule evaluation failed, allowing call", e)
                null
            }

            respondToCall(details, decision?.let(::toResponse) ?: allow)
            if (decision == null) return@launch

            // Bookkeeping happens after the call has been answered, so it can't slow the decision down.
            try {
                app.repository.logCall(
                    CallEntity(
                        e164 = parsed.e164,
                        display = parsed.display,
                        timestamp = System.currentTimeMillis(),
                        blocked = decision.blocked,
                        reason = decision.reason.name,
                        detail = decision.detail,
                        region = parsed.regions.joinToString(", "),
                        callerName = matchName,
                        verification = verification,
                        extras = extras,
                        action = if (decision.blocked) decision.action.name else "",
                    ),
                )
                decision.patternId?.let { app.repository.bumpPattern(it) }
                if (!decision.blocked && decision.reason == Reason.DEFAULT_ALLOW && parsed.e164 != null) {
                    Notifier.showUnknownCaller(
                        this@SpamScreeningService, parsed.e164, parsed.display, parsed.regions.joinToString(", "),
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log call", e)
            }
        }
    }

    private fun toResponse(d: Decision): CallResponse {
        val b = CallResponse.Builder()
        if (!d.blocked) return b.build()
        return when (d.action) {
            BlockAction.REJECT -> b
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(true)
                .setSkipNotification(true)
                .build()
            BlockAction.SILENCE -> b.setSilenceCall(true).build()
        }
    }

    @Suppress("DEPRECATION")
    private fun describe(d: Call.Details): String {
        val parts = mutableListOf("presentation=${d.callerDisplayNamePresentation}", "props=${d.callProperties}")
        fun bundle(label: String, b: android.os.Bundle?) {
            if (b != null && !b.isEmpty) parts += "$label={" + b.keySet().joinToString { k -> "$k=${b.get(k)}" } + "}"
        }
        bundle("extras", d.extras)
        bundle("intentExtras", d.intentExtras)
        return parts.joinToString(" ")
    }

    private fun verificationLabel(status: Int) = when (status) {
        Connection.VERIFICATION_STATUS_PASSED -> "passed"
        Connection.VERIFICATION_STATUS_FAILED -> "failed"
        Connection.VERIFICATION_STATUS_NOT_VERIFIED -> "not verified"
        else -> "unknown"
    }

    private companion object {
        const val TAG = "SpamScreeningService"
    }
}
