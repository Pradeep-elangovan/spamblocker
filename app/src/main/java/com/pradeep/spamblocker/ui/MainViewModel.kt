package com.pradeep.spamblocker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pradeep.spamblocker.SpamApp
import com.pradeep.spamblocker.data.CallLogImporter
import com.pradeep.spamblocker.data.Settings
import com.pradeep.spamblocker.domain.NumberParser
import com.pradeep.spamblocker.domain.PatternType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as SpamApp).repository

    private fun <T> Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val calls = repo.calls.state(emptyList())
    val blocked = repo.blocked.state(emptyList())
    val allowed = repo.allowed.state(emptyList())
    val patterns = repo.patterns.state(emptyList())
    val regions = repo.regions.state(emptyList())
    val settings = repo.settings.state(Settings())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun say(text: String) {
        _messages.tryEmit(text)
    }

    private fun io(block: suspend CoroutineScope.() -> Unit) {
        viewModelScope.launch(Dispatchers.IO, block = block)
    }

    // ---- numbers ------------------------------------------------------------------------------

    fun block(e164: String, spam: Boolean) = io {
        repo.block(e164, spam)
        say(if (spam) "Marked as spam and blocked" else "Blocked")
    }

    fun notSpam(e164: String) = io {
        repo.notSpam(e164)
        say("Marked as not spam")
    }

    fun addBlocked(input: String) = io {
        val parsed = NumberParser.parse(input)
        val e164 = parsed.e164
        if (e164 == null) say("That doesn't look like a valid number") else {
            repo.block(e164, spam = false)
            say("Blocked ${parsed.display}")
        }
    }

    fun addAllowed(input: String) = io {
        val parsed = NumberParser.parse(input)
        val e164 = parsed.e164
        if (e164 == null) say("That doesn't look like a valid number") else {
            repo.allow(e164)
            say("Allowed ${parsed.display}")
        }
    }

    fun removeBlocked(e164: String) = io { repo.removeBlocked(e164) }
    fun removeAllowed(e164: String) = io { repo.removeAllowed(e164) }
    fun toggleBlockedAction(e164: String, current: String) = io { repo.toggleBlockedAction(e164, current) }

    // ---- patterns & regions ---------------------------------------------------------------------

    fun addPattern(type: PatternType, pattern: String, label: String) = io { repo.addPattern(type, pattern, label) }
    fun setPatternEnabled(id: Long, enabled: Boolean) = io { repo.setPatternEnabled(id, enabled) }
    fun deletePattern(id: Long) = io { repo.deletePattern(id) }

    fun setRegion(region: String, blocked: Boolean, landline: Boolean, mobile: Boolean) = io {
        repo.setRegion(region, blocked, landline, mobile)
    }

    // ---- settings, history, backup ---------------------------------------------------------------

    fun update(transform: (Settings) -> Settings) = io { repo.updateSettings(transform) }

    fun importCallLog() = io {
        val added = CallLogImporter.import(getApplication(), repo)
        say(if (added == 0) "No new calls to import" else "Imported $added calls")
    }

    fun clearHistory() = io { repo.clearCalls() }

    fun export(uri: Uri) = io {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(repo.exportJson().toByteArray())
            }
            say("Backup saved")
        } catch (e: Exception) {
            say("Couldn't save the backup")
        }
    }

    fun importFrom(uri: Uri) = io {
        try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() } ?: ""
            say("Imported ${repo.importJson(text)} items")
        } catch (e: Exception) {
            say("Couldn't read that file")
        }
    }
}
