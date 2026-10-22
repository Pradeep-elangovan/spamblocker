package com.pradeep.spamblocker.service

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.telecom.TelecomManager
import com.pradeep.spamblocker.data.hasPermission

object CallControl {
    /** True once a call has been answered, so we never hang up on a conversation. */
    fun isInCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION
    }

    @Suppress("DEPRECATION")
    fun hangUp(context: Context) {
        if (!context.hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) return
        try {
            context.getSystemService(TelecomManager::class.java).endCall()
        } catch (e: SecurityException) {
            // ignore
        }
    }
}
