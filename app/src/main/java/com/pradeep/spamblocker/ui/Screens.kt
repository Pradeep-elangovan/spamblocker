@file:OptIn(ExperimentalMaterial3Api::class)

package com.pradeep.spamblocker.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pradeep.spamblocker.data.CallEntity
import com.pradeep.spamblocker.data.hasPermission
import com.pradeep.spamblocker.domain.BlockAction
import com.pradeep.spamblocker.domain.IndiaRegions
import com.pradeep.spamblocker.domain.Reason

private val PAGE_PADDING = PaddingValues(16.dp)

// ------------------------------------------------------------------------------------------------
// Calls
// ------------------------------------------------------------------------------------------------

@Composable
fun CallsScreen(vm: MainViewModel, tick: Int, onChanged: () -> Unit) {
    val calls by vm.calls.collectAsStateWithLifecycle()
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val allowed by vm.allowed.collectAsStateWithLifecycle()
    val blockedNumbers = remember(blocked) { blocked.map { it.e164 }.toSet() }
    val allowedNumbers = remember(allowed) { allowed.map { it.e164 }.toSet() }

    LazyColumn(contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { SetupCard(tick, onChanged) }
        item { Text("Calls", style = MaterialTheme.typography.headlineMedium) }
        if (calls.isEmpty()) {
            item {
                Text(
                    "Nothing yet. Incoming calls will appear here once the app is your call screening app. " +
                        "You can also import older calls from Settings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(calls, key = { it.id }) { CallRow(it, it.e164 in blockedNumbers, it.e164 in allowedNumbers, vm) }
    }
}

@Composable
private fun CallRow(call: CallEntity, isBlocked: Boolean, isAllowed: Boolean, vm: MainViewModel) {
    val reasonText = when {
        call.reason == "IMPORTED" -> "From phone call log"
        else -> runCatching { Reason.valueOf(call.reason).label }.getOrDefault(call.reason)
    } + if (call.detail.isNotEmpty()) " · ${call.detail}" else ""

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(call.display, style = MaterialTheme.typography.titleMedium)
                    val where = if (call.region.isNotEmpty()) " · ${call.region}" else ""
                    Text(relativeTime(call.timestamp) + where, style = MaterialTheme.typography.bodySmall)
                }
                if (call.blocked) {
                    Text(
                        "Blocked (${call.action.lowercase()})",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(reasonText, style = MaterialTheme.typography.bodySmall)
            if (call.source == "screened") {
                Text(
                    "Carrier name: ${call.callerName.ifEmpty { "none" }} · Caller ID check: ${call.verification}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (call.outcome.isNotEmpty()) {
                    val talk = if (call.durationSec > 0) " · ${call.durationSec}s" else ""
                    Text(
                        "Outcome: ${call.outcome}$talk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (call.extras.isNotEmpty()) {
                    Text(
                        "Raw: ${call.extras}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val e164 = call.e164
            if (e164 != null) {
                // The buttons reflect the number's current state, so tapping one flips the row to the other.
                Row {
                    when {
                        // Blocked by you: the only sensible action is to undo it.
                        isBlocked -> TextButton(onClick = { vm.notSpam(e164) }) { Text("Not spam") }
                        // Marked not spam / allowed: flip it back to spam.
                        isAllowed -> TextButton(onClick = { vm.block(e164, true) }) { Text("Spam") }
                        // Not on any list yet.
                        else -> {
                            if (call.blocked) TextButton(onClick = { vm.notSpam(e164) }) { Text("Not spam") }
                            TextButton(onClick = { vm.block(e164, false) }) { Text("Block") }
                            TextButton(onClick = { vm.block(e164, true) }) { Text("Spam") }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Rules: blocklist, allowlist, patterns
// ------------------------------------------------------------------------------------------------

private enum class RuleDialog { BLOCK, ALLOW, PATTERN }

@Composable
fun RulesScreen(vm: MainViewModel) {
    val blocked by vm.blocked.collectAsStateWithLifecycle()
    val allowed by vm.allowed.collectAsStateWithLifecycle()
    val patterns by vm.patterns.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<RuleDialog?>(null) }

    when (dialog) {
        RuleDialog.BLOCK -> NumberDialog("Block a number", { dialog = null }) { vm.addBlocked(it) }
        RuleDialog.ALLOW -> NumberDialog("Always allow a number", { dialog = null }) { vm.addAllowed(it) }
        RuleDialog.PATTERN -> PatternDialog({ dialog = null }) { type, pattern, label -> vm.addPattern(type, pattern, label) }
        null -> Unit
    }

    LazyColumn(contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { SectionHeader("Blocklist", "Add") { dialog = RuleDialog.BLOCK } }
        if (blocked.isEmpty()) item { Hint("Numbers you flag appear here.") }
        items(blocked, key = { "b" + it.e164 }) { b ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.e164)
                    val sub = listOfNotNull(
                        if (b.spam) "spam" else null,
                        b.label.ifEmpty { null },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { vm.toggleBlockedAction(b.e164, b.action) }) {
                    Text(b.action.lowercase().replaceFirstChar { it.uppercase() })
                }
                TextButton(onClick = { vm.removeBlocked(b.e164) }) { Text("Remove") }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("Pattern rules", "Add") { dialog = RuleDialog.PATTERN } }
        if (patterns.isEmpty()) item { Hint("Block every number that starts or ends with digits you choose, e.g. starts with 8056.") }
        items(patterns, key = { "p" + it.id }) { p ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val type = runCatching { com.pradeep.spamblocker.domain.PatternType.valueOf(p.type).title() }.getOrDefault(p.type)
                    Text("$type ${p.pattern}")
                    val sub = listOfNotNull(p.label.ifEmpty { null }, "${p.hits} blocked").joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = p.enabled, onCheckedChange = { vm.setPatternEnabled(p.id, it) })
                TextButton(onClick = { vm.deletePattern(p.id) }) { Text("Remove") }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader("Allowlist", "Add") { dialog = RuleDialog.ALLOW } }
        if (allowed.isEmpty()) item { Hint("Numbers here are never blocked by any rule, like saved contacts.") }
        items(allowed, key = { "a" + it.e164 }) { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(a.e164, Modifier.weight(1f))
                TextButton(onClick = { vm.removeAllowed(a.e164) }) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ------------------------------------------------------------------------------------------------
// Regions
// ------------------------------------------------------------------------------------------------

@Composable
fun RegionsScreen(vm: MainViewModel) {
    val rules by vm.regions.collectAsStateWithLifecycle()
    val byName = remember(rules) { rules.associateBy { it.region } }

    LazyColumn(contentPadding = PAGE_PADDING, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Blocked regions", style = MaterialTheme.typography.headlineMedium)
                Hint(
                    "Applies only to numbers that aren't saved contacts or on your allowlist. Landlines are matched by " +
                        "STD code. Mobile numbers are matched by the circle the number series was issued in; because of " +
                        "number portability that is a good guess, not a guarantee.",
                )
            }
        }
        items(IndiaRegions.STATES, key = { it }) { state ->
            val rule = byName[state]
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(state, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = rule != null, onCheckedChange = { vm.setRegion(state, it, true, true) })
                }
                if (rule != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = rule.blockMobile,
                            onClick = { vm.setRegion(state, true, rule.blockLandline, !rule.blockMobile) },
                            label = { Text("Mobile") },
                        )
                        FilterChip(
                            selected = rule.blockLandline,
                            onClick = { vm.setRegion(state, true, !rule.blockLandline, rule.blockMobile) },
                            label = { Text("Landline") },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Settings
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: MainViewModel, tick: Int, onChanged: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        if (it != null) vm.export(it)
    }
    val datasetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        if (it != null) vm.exportDataset(it)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) vm.importFrom(it)
    }
    val callLogPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.syncCallLog() else vm.say("Call log permission was denied")
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(PAGE_PADDING),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        SetupCard(tick, onChanged)

        SwitchRow(
            "Block all unknown numbers",
            "Anything not in your contacts or allowlist is blocked, unless a rule above already decided.",
            s.blockAllUnknown,
        ) { v -> vm.update { it.copy(blockAllUnknown = v) } }

        HorizontalDivider()
        Text("When a call is blocked", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = s.action == BlockAction.REJECT,
                onClick = { vm.update { it.copy(action = BlockAction.REJECT) } },
                label = { Text("Reject") },
            )
            FilterChip(
                selected = s.action == BlockAction.SILENCE,
                onClick = { vm.update { it.copy(action = BlockAction.SILENCE) } },
                label = { Text("Silence") },
            )
        }
        Hint(
            "Reject ends the call before it rings; what the caller hears (busy tone, a \"declined\" message or voicemail) is " +
                "decided by your carrier, so test it with a friend. Silence keeps your phone quiet but the caller hears " +
                "normal ringing. You can override this per number in Rules.",
        )

        HorizontalDivider()
        Text("History and backup", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {
                if (context.hasPermission(Manifest.permission.READ_CALL_LOG)) vm.syncCallLog()
                else callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sync call log now") }
        OutlinedButton(onClick = { exportLauncher.launch("spamblocker-backup.json") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export rules to a file")
        }
        OutlinedButton(
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import rules from a file") }
        OutlinedButton(onClick = { vm.clearHistory() }, modifier = Modifier.fillMaxWidth()) { Text("Clear call history") }

        HorizontalDivider()
        Text("Learning data", style = MaterialTheme.typography.titleMedium)
        Hint(
            "The app records how each call ended (answered, missed, rejected, blocked) and how long you talked. Unanswered " +
                "unknown calls count as spam by default, so just tap Not spam on the ones that were wrong. Calls you " +
                "answered for 20 seconds or more count as legitimate.",
        )
        OutlinedButton(onClick = { datasetLauncher.launch("spamblocker-dataset.csv") }, modifier = Modifier.fillMaxWidth()) {
            Text("Export labelled history for analysis")
        }
        Hint("The export has no full numbers: only the first 6 digits plus region, time, call counts and outcome.")

        val version = remember {
            runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        }
        Hint("Installed version: $version")
    }
}
