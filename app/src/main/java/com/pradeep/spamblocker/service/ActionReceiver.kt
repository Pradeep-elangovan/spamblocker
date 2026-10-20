package com.pradeep.spamblocker.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import com.pradeep.spamblocker.SpamApp
import com.pradeep.spamblocker.data.hasPermission
import com.pradeep.spamblocker.domain.BlockAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Block / Spam buttons on the incoming-call notification. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val e164 = intent.getStringExtra(Notifier.EXTRA_E164) ?: return
        val notificationId = intent.getIntExtra(Notifier.EXTRA_NOTIFICATION_ID, Notifier.notificationId(e164))
        val spam = intent.action == Notifier.ACTION_SPAM
        val app = context.applicationContext as SpamApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.block(e164, spam)
                // Hang up the ringing call only in Reject mode; in Silence mode the block applies from the next call.
                if (app.repository.settingsNow().action == BlockAction.REJECT) hangUp(context)
            } finally {
                NotificationManagerCompat.from(context).cancel(notificationId)
                pending.finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun hangUp(context: Context) {
        if (!context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            context.getSystemService(TelecomManager::class.java).endCall()
        } catch (e: SecurityException) {
            // ignore
        }
    }
}
