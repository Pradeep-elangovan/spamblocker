@file:OptIn(ExperimentalMaterial3Api::class)

package com.pradeep.spamblocker.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pradeep.spamblocker.domain.PatternType

val REQUIRED_PERMISSIONS = listOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.POST_NOTIFICATIONS,
    Manifest.permission.ANSWER_PHONE_CALLS,
    Manifest.permission.READ_CALL_LOG,
)

fun relativeTime(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

fun PatternType.title() = when (this) {
    PatternType.STARTS_WITH -> "Starts with"
    PatternType.ENDS_WITH -> "Ends with"
    PatternType.CONTAINS -> "Contains"
}

/** Onboarding: becomes the call-screening app and gets the permissions it needs. Hidden once everything is set. */
@Composable
fun SetupCard(tick: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    val roleHeld = remember(tick) { roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) }
    val missing = remember(tick) {
        REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onChanged() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }

    if (roleHeld && missing.isEmpty()) return

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
            if (!roleHeld) {
                Text("Blocking only works once this app is your \"Caller ID & spam\" app.")
                Button(onClick = {
                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                }) { Text("Set as call screening app") }
            }
            if (missing.isNotEmpty()) {
                Text("Permissions still needed: contacts (so saved numbers are never blocked), notifications, hang-up and call log (to learn how calls ended).")
                Button(onClick = { permissionLauncher.launch(missing.toTypedArray()) }) { Text("Grant permissions") }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun NumberDialog(title: String, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onAdd(text); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PatternDialog(onDismiss: () -> Unit, onAdd: (PatternType, String, String) -> Unit) {
    var type by remember { mutableStateOf(PatternType.STARTS_WITH) }
    var pattern by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    val digits = pattern.count { it.isDigit() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add pattern rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PatternType.entries.forEach {
                        FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.title()) })
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Digits, e.g. 8056") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (optional)") },
                    singleLine = true,
                )
                if (digits in 1..2) {
                    Text(
                        "Very broad: this will match a huge number of callers.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = digits > 0, onClick = { onAdd(type, pattern, label); onDismiss() }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
