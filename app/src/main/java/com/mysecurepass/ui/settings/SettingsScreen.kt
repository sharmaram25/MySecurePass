package com.mysecurepass.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import com.mysecurepass.ui.components.OptionRow
import com.mysecurepass.ui.components.IconTile
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.data.ThemeMode
import com.mysecurepass.data.VaultState
import com.mysecurepass.ui.canUseStrongBiometrics
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.ScreenTitle
import com.mysecurepass.ui.components.SecretField
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.ToggleRow
import com.mysecurepass.ui.components.fieldColors
import com.mysecurepass.ui.vault.enableBiometric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Dialog { NONE, CHANGE_PASSWORD, IMPORT_PASSWORD, CSV_WARNING, CSV_EXPORT, SELF_DESTRUCT, WIPE }

@Composable
fun SettingsScreen(onAccessLog: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val message = LocalMessenger.current
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val vaultState by c.vault.state.collectAsStateWithLifecycle()
    val bioEnabled by c.vault.biometricEnabled.collectAsStateWithLifecycle()
    val unlocked = vaultState is VaultState.Unlocked
    val hasVault = vaultState !is VaultState.Empty

    var dialog by remember { mutableStateOf(Dialog.NONE) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var autofillOn by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        autofillOn = context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = c.vault.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }
            }.onSuccess { c.vault.recordBackupExported(); message("Encrypted backup saved") }.onFailure { message("Export failed") }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = c.vault.exportCsv()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
            }.onSuccess { message("CSV saved. Delete it as soon as you've imported it elsewhere.") }.onFailure { message("Export failed") }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
            }
            if (bytes == null || bytes.size > 50 * 1024 * 1024) message("Couldn't read that file")
            else { pendingImport = bytes; dialog = Dialog.IMPORT_PASSWORD }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
                c.vault.importCsv(text)
            }.onSuccess { (added, skipped) ->
                val skippedNote = if (skipped > 0) " · skipped $skipped" else ""
                message("Imported $added logins$skippedNote. Now delete the CSV file.")
            }.onFailure { message(it.message ?: "Import failed") }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            ScreenTitle("Settings")
            val lockedHint = "Unlock your vault first"
            val bioAvailable = context.canUseStrongBiometrics()

            // ---- Unlock & lock ----
            SectionLabel("Unlock & lock")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow(
                    "Biometric unlock", bioEnabled && hasVault,
                    onChange = { on ->
                        if (on) enableBiometric(context, onResult = { ok -> message(if (ok) "Biometric unlock enabled" else "Couldn't enable biometrics") })
                        else { c.vault.disableBiometric(); message("Biometric unlock off") }
                    },
                    subtitle = when {
                        !hasVault -> "Create a vault first"
                        !bioAvailable -> "No strong biometric enrolled on this phone"
                        else -> "Fingerprint or face, sealed in secure hardware"
                    },
                    enabled = hasVault && bioAvailable && (unlocked || bioEnabled),
                    icon = Icons.Outlined.Fingerprint,
                )
                Hairline()
                IconOptionRow(
                    Icons.Outlined.Schedule, "Auto-lock", "After you leave the app",
                    listOf(0 to "Instantly", 30 to "30s", 60 to "1 min", 300 to "5 min", 900 to "15 min"), settings.autoLockSeconds,
                ) { v -> c.settings.update { it.copy(autoLockSeconds = v) } }
                Hairline()
                ToggleRow("Lock when screen turns off", settings.lockOnScreenOff, { v -> c.settings.update { it.copy(lockOnScreenOff = v) } }, icon = Icons.Outlined.ScreenLockPortrait)
                if (hasVault) {
                    Hairline()
                    ToggleRow(
                        "Erase after ${com.mysecurepass.data.AppSettings.SELF_DESTRUCT_ATTEMPTS} failed attempts", settings.selfDestruct,
                        { v -> if (v) dialog = Dialog.SELF_DESTRUCT else c.settings.update { it.copy(selfDestruct = false) } },
                        "For a stolen phone. Keep a backup.", icon = Icons.Outlined.DeleteForever,
                    )
                }
            }

            // ---- Privacy ----
            SectionLabel("Privacy")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                ToggleRow(
                    "Block screenshots", settings.blockScreenshots, { v -> c.settings.update { it.copy(blockScreenshots = v) } },
                    "Also hides content in recent apps", icon = Icons.Outlined.VisibilityOff,
                )
                Hairline()
                IconOptionRow(
                    Icons.Outlined.ContentPaste, "Clear clipboard", "After copying a secret",
                    listOf(15 to "15s", 30 to "30s", 60 to "1 min", 120 to "2 min", 0 to "Never"), settings.clipboardClearSeconds,
                ) { v -> c.settings.update { it.copy(clipboardClearSeconds = v) } }
                Hairline()
                NavRow(Icons.Outlined.History, "Access log", "Unlocks, failed attempts, sensitive actions", onClick = onAccessLog)
            }

            // ---- Autofill ----
            SectionLabel("Autofill")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NavRow(
                    Icons.Outlined.PhonelinkLock, "Autofill service",
                    if (autofillOn) "Fills logins in apps and browsers" else "Off. Tap to choose MySecurePass",
                    trailing = if (autofillOn) "On" else null,
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
                    },
                )
            }

            // ---- Backup & data ----
            if (hasVault) {
                SectionLabel("Backup & data")
                Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    NavRow(
                        Icons.Outlined.Backup, "Back up now",
                        when {
                            !unlocked -> lockedHint
                            settings.lastBackupAt == 0L -> "Never backed up"
                            else -> "Last backup ${com.mysecurepass.ui.security.ago(settings.lastBackupAt)}"
                        },
                        enabled = unlocked,
                        warn = unlocked && (settings.lastBackupAt == 0L || System.currentTimeMillis() - settings.lastBackupAt > 30L * 86_400_000),
                        onClick = { exportLauncher.launch("mysecurepass-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.msp") },
                    )
                    Hairline()
                    NavRow(
                        Icons.Outlined.SettingsBackupRestore, "Restore from backup", if (unlocked) "Merge a .msp file into this vault" else lockedHint,
                        enabled = unlocked, onClick = { importLauncher.launch(arrayOf("*/*")) },
                    )
                    Hairline()
                    NavRow(
                        Icons.Outlined.FileDownload, "Import from CSV", if (unlocked) "Chrome, Firefox, Bitwarden, 1Password, KeePassXC" else lockedHint,
                        enabled = unlocked, onClick = { dialog = Dialog.CSV_WARNING },
                    )
                    Hairline()
                    NavRow(
                        Icons.Outlined.FileUpload, "Export as CSV", if (unlocked) "Unencrypted, for moving to another app" else lockedHint,
                        enabled = unlocked, onClick = { dialog = Dialog.CSV_EXPORT },
                    )
                }

                SectionLabel("Master password")
                Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    NavRow(
                        Icons.Outlined.Password, "Change master password", if (unlocked) "Re-encrypts your vault key" else lockedHint,
                        enabled = unlocked, onClick = { dialog = Dialog.CHANGE_PASSWORD },
                    )
                    Hairline()
                    NavRow(Icons.Outlined.DeleteOutline, "Delete vault", "Permanently erase everything on this phone", destructive = true, onClick = { dialog = Dialog.WIPE })
                }
            }

            // ---- Appearance ----
            SectionLabel("Appearance")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                IconOptionRow(Icons.Outlined.Contrast, "Theme", null, listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), settings.theme) { v ->
                    c.settings.update { it.copy(theme = v) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Hairline()
                    ToggleRow("Wallpaper colours", settings.dynamicColor, { v -> c.settings.update { it.copy(dynamicColor = v) } }, "Material You dynamic colour", icon = Icons.Outlined.Palette)
                }
                Hairline()
                ToggleRow("Haptic feedback", settings.haptics, { v -> c.settings.update { it.copy(haptics = v) } }, icon = Icons.Outlined.Vibration)
                Hairline()
                ToggleRow("Remember generator options", settings.rememberGenerator, { v -> c.settings.update { it.copy(rememberGenerator = v) } }, "Options only, never passwords", icon = Icons.Outlined.Tune)
            }

            // ---- About ----
            SectionLabel("About")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NavRow(Icons.Outlined.CloudOff, "Offline by design", "No internet permission, accounts, ads or analytics", onClick = null)
                Hairline()
                NavRow(Icons.Outlined.School, "Show introduction again", null, onClick = { c.settings.update { it.copy(onboarded = false) } })
                Hairline()
                NavRow(
                    Icons.Outlined.Info, "Version ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"}",
                    "EFF wordlist (CC BY 3.0) · zxcvbn (MIT) · Argon2 (CC0) · ZXing (Apache 2.0) · JetBrains Mono (OFL)", onClick = null,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        Dialog.CHANGE_PASSWORD -> ChangePasswordDialog(onDismiss = { dialog = Dialog.NONE })
        Dialog.IMPORT_PASSWORD -> PasswordPromptDialog(
            title = "Backup password", body = "Enter the master password that was used when this backup was made.",
            onDismiss = { dialog = Dialog.NONE; pendingImport = null },
            onConfirm = { pw ->
                val bytes = pendingImport ?: return@PasswordPromptDialog
                dialog = Dialog.NONE; pendingImport = null
                scope.launch {
                    runCatching { c.vault.importBackup(bytes, pw) }
                        .onSuccess { message("Restored $it logins") }
                        .onFailure { message(if (it is com.mysecurepass.core.vault.WrongPasswordException) "Wrong backup password" else "Not a valid MySecurePass backup") }
                }
            },
        )
        Dialog.CSV_WARNING -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text("Import from CSV") },
            text = { Text("CSV exports are unencrypted. After importing, delete the CSV file from your phone and any cloud drive.") },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE; csvLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) { Text("Choose file") } },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text("Cancel") } },
        )
        Dialog.CSV_EXPORT -> PasswordPromptDialog(
            title = "Export unencrypted CSV",
            body = "The file will contain every login in plain text. Anyone who gets it can read your passwords. Confirm your master password to continue.",
            onDismiss = { dialog = Dialog.NONE },
            onConfirm = { pw ->
                dialog = Dialog.NONE
                scope.launch {
                    if (c.vault.verifyPassword(pw)) {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        csvExportLauncher.launch("mysecurepass-export-$date.csv")
                    } else message("Wrong password")
                }
            },
        )
        Dialog.SELF_DESTRUCT -> AlertDialog(
            onDismissRequest = { dialog = Dialog.NONE },
            title = { Text("Erase after failed attempts?") },
            text = {
                Text(
                    "After ${com.mysecurepass.data.AppSettings.SELF_DESTRUCT_ATTEMPTS} wrong master passwords in a row, the vault and its keys are destroyed permanently. " +
                        "Only turn this on if you keep an up-to-date encrypted backup. A child playing with your phone could trigger it.",
                )
            },
            confirmButton = { TextButton(onClick = { dialog = Dialog.NONE; c.settings.update { it.copy(selfDestruct = true) } }) { Text("Turn on", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = Dialog.NONE }) { Text("Cancel") } },
        )
        Dialog.WIPE -> WipeDialog(onDismiss = { dialog = Dialog.NONE }, onConfirm = {
            dialog = Dialog.NONE
            scope.launch { c.vault.wipe(); message("Vault deleted") }
        })
        Dialog.NONE -> Unit
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    destructive: Boolean = false,
    warn: Boolean = false,
    trailing: String? = null,
    onClick: (() -> Unit)?,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val accent = when {
        destructive -> MaterialTheme.colorScheme.error
        warn -> com.mysecurepass.ui.theme.Msp.extra.fair
        else -> color
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = accent)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (warn) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun <T> IconOptionRow(icon: ImageVector, title: String, subtitle: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.padding(top = 12.dp)) { IconTile(icon) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) { OptionRow(title, subtitle, options, selected, onSelect) }
    }
}

@Composable
private fun PasswordPromptDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                SecretField(pw, { pw = it }, "Password", monospace = false)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw.toCharArray()); pw = "" }, enabled = pw.isNotEmpty()) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val message = LocalMessenger.current
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var score by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(new) { score = if (new.isEmpty()) 0 else c.analyzer.analyze(new).score }
    val ok = current.isNotEmpty() && score >= 3 && new.length >= 10 && new == confirm && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Change master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecretField(current, { current = it; error = null }, "Current password", monospace = false, isError = error != null, supporting = error)
                SecretField(new, { new = it }, "New password", monospace = false,
                    supporting = if (new.isNotEmpty() && (score < 3 || new.length < 10)) "Too easy to guess" else null, isError = new.isNotEmpty() && score < 3)
                SecretField(confirm, { confirm = it }, "Confirm new password", monospace = false,
                    isError = confirm.isNotEmpty() && confirm != new, supporting = if (confirm.isNotEmpty() && confirm != new) "Doesn't match" else null)
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                busy = true
                val cur = current.toCharArray(); val nw = new.toCharArray()
                scope.launch {
                    val success = c.vault.changePassword(cur, nw)
                    busy = false
                    if (success) { message("Master password changed"); onDismiss() } else error = "Wrong password"
                }
            }) { Text(if (busy) "Working…" else "Change") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun WipeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete vault?") },
        text = {
            Column {
                Text("Every login in this vault will be erased and the encryption keys destroyed. Without a backup this cannot be undone.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    typed, { typed = it }, label = { Text("Type DELETE to confirm") }, singleLine = true,
                    shape = MaterialTheme.shapes.medium, colors = fieldColors(), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = typed == "DELETE") { Text("Delete", color = if (typed == "DELETE") MaterialTheme.colorScheme.error else Color.Unspecified) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

