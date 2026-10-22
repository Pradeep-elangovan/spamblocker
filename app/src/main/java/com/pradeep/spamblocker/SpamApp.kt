package com.pradeep.spamblocker

import android.app.Application
import com.pradeep.spamblocker.data.AppDatabase
import com.pradeep.spamblocker.data.SettingsStore
import com.pradeep.spamblocker.data.SpamRepository
import com.pradeep.spamblocker.domain.NumberParser
import com.pradeep.spamblocker.domain.Reason
import com.pradeep.spamblocker.service.Notifier
import kotlin.concurrent.thread

/** The call most recently seen by the screening service; lets the notification listener tie a label to it. */
data class LastCall(
    val id: Long,
    val e164: String?,
    val timestamp: Long,
    val reason: Reason,
    val blocked: Boolean,
)

class SpamApp : Application() {
    @Volatile var lastCall: LastCall? = null

    lateinit var repository: SpamRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SpamRepository(AppDatabase.get(this).dao(), SettingsStore(this))
        Notifier.ensureChannel(this)
        // Load libphonenumber's data off the main thread so the first screened call isn't slow.
        thread(name = "number-warmup") { NumberParser.warmUp() }
    }
}
