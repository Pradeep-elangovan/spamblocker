package com.pradeep.spamblocker.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pradeep.spamblocker.R
import com.pradeep.spamblocker.data.hasPermission

object Notifier {
    const val CHANNEL_ID = "unknown_callers"
    const val ACTION_BLOCK = "com.pradeep.spamblocker.BLOCK"
    const val ACTION_SPAM = "com.pradeep.spamblocker.SPAM"
    const val EXTRA_E164 = "e164"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Unknown caller alerts", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Block or flag an unknown number while it is calling" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notificationId(e164: String) = e164.hashCode()

    /** Heads-up notification with Block / Spam buttons for an unknown caller that was let through. */
    @SuppressLint("MissingPermission")
    fun showUnknownCaller(context: Context, e164: String, display: String, region: String) {
        if (!context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val id = notificationId(e164)
        val text = if (region.isNotEmpty()) "Unknown caller · $region" else "Unknown caller"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_block)
            .setContentTitle(display)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .addAction(0, "Block", actionIntent(context, ACTION_BLOCK, e164, id))
            .addAction(0, "Spam", actionIntent(context, ACTION_SPAM, e164, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    private fun actionIntent(context: Context, action: String, e164: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_E164, e164)
            .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context, (action + e164).hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
