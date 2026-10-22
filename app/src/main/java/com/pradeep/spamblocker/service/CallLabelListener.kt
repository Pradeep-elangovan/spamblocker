package com.pradeep.spamblocker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.pradeep.spamblocker.LastCall
import com.pradeep.spamblocker.SpamApp
import com.pradeep.spamblocker.domain.BlockAction
import com.pradeep.spamblocker.domain.Reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Experimental. Android does not give the screening service the spam label your dialer shows (for example
 * "Spam Alert From Jio"), but the dialer's incoming-call notification carries the same text. When the
 * "Block network-flagged spam" toggle is on, a ringing call whose notification contains one of your keywords
 * is hung up and the number is added to the blocklist, so next time it is blocked at the first ring.
 */
class CallLabelListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var handledCallId = -1L

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.category != Notification.CATEGORY_CALL) return
        val ex = n.extras
        val text = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_BIG_TEXT)
            .mapNotNull { ex.getCharSequence(it)?.toString()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" | ")
        if (text.isEmpty()) return
        val pkg = sbn.packageName
        scope.launch {
            try {
                handle(pkg, text)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle call notification", e)
            }
        }
    }

    private suspend fun handle(pkg: String, text: String) {
        val app = application as SpamApp
        val repo = app.repository

        // The screening service and the dialer race; wait briefly for the call to be logged.
        var last: LastCall? = fresh(app)
        var tries = 0
        while (last == null && tries < 20) {
            delay(150)
            last = fresh(app)
            tries++
        }
        val call = last ?: return
        if (call.id == handledCallId) return

        repo.appendCallNote(call.id, "notification[$pkg]: $text")

        val settings = repo.settingsNow()
        if (!settings.blockNetworkSpam) return
        val lower = text.lowercase()
        val hit = settings.networkKeywords.split(',').map { it.trim().lowercase() }.firstOrNull { it.isNotEmpty() && lower.contains(it) }
            ?: return

        // Never touch saved contacts / allowlisted numbers, calls already blocked, or answered calls.
        if (call.reason == Reason.CONTACT || call.reason == Reason.ALLOWLIST || call.blocked) return
        if (CallControl.isInCall(this)) return

        handledCallId = call.id
        call.e164?.let { repo.block(it, spam = true, label = "Network spam alert") }
        repo.markCallBlocked(call.id, Reason.NETWORK_SPAM.name, "Dialer label matched \"$hit\"", settings.action.name)
        if (settings.action == BlockAction.REJECT) CallControl.hangUp(this)
    }

    private fun fresh(app: SpamApp): LastCall? =
        app.lastCall?.takeIf { System.currentTimeMillis() - it.timestamp < 30_000 }

    private companion object {
        const val TAG = "CallLabelListener"
    }
}
