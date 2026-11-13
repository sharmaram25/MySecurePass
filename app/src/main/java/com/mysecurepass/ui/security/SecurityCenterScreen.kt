package com.mysecurepass.ui.security

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.vault.HealthReport
import com.mysecurepass.core.vault.active
import com.mysecurepass.data.VaultState
import com.mysecurepass.security.DeviceSecurity
import com.mysecurepass.security.HardwareLevel
import com.mysecurepass.ui.canUseStrongBiometrics
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.OptionRow
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.ScoreRing
import com.mysecurepass.ui.components.ScreenTitle
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.Status
import com.mysecurepass.ui.components.StatusRow
import com.mysecurepass.ui.components.ToggleRow
import com.mysecurepass.ui.components.statusColor
import com.mysecurepass.ui.vault.computeHealth
import com.mysecurepass.ui.vault.enableBiometric
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Formats a duration since [ms] as "today", "3 days ago", etc. */
fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - ms) / 86_400_000L
    return when {
        d <= 0 -> "today"
        d == 1L -> "yesterday"
        d < 30 -> "$d days ago"
        d < 365 -> "${d / 30} months ago"
        else -> "over a year ago"
    }
}

private enum class P { ENCRYPTION, KEYS, NETWORK, SCREEN_LOCK, INTEGRITY, USB, SCREENSHOTS, AUTOLOCK, CLIPBOARD, BACKUP, BIOMETRIC, AUTOFILL }

private data class Protection(val id: P, val icon: ImageVector, val title: String, val summary: String, val status: Status, val weight: Int)

@Composable
fun SecurityCenterScreen(
    onOpenVault: () -> Unit,
    onHealth: () -> Unit,
    onChecker: () -> Unit,
    onSelfTest: () -> Unit,
    onLog: () -> Unit,
    onHowItWorks: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val vaultState by c.vault.state.collectAsStateWithLifecycle()
    val bio by c.vault.biometricEnabled.collectAsStateWithLifecycle()
    val hardware by c.hardwareLevel.collectAsStateWithLifecycle()
    val selfTest by c.lastSelfTest.collectAsStateWithLifecycle()
    val log by c.securityLog.entries.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<P?>(null) }

    // Device facts can change while we're in the background (screen lock, USB debugging, autofill).
    var refresh by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val screenLock = remember(refresh) { DeviceSecurity.hasScreenLock(context) }
    val noNetwork = remember(refresh) { DeviceSecurity.hasNoNetworkAccess(context) }
    val rooted = remember { DeviceSecurity.likelyRooted() }
    val adb = remember(refresh) { DeviceSecurity.usbDebuggingOn(context) }
    val autofill = remember(refresh) { DeviceSecurity.autofillEnabled(context) }

    val entries = (vaultState as? VaultState.Unlocked)?.entries
    val hasVault = vaultState !is VaultState.Empty
    val health by produceState<HealthReport?>(null, entries) { value = entries?.let { computeHealth(c, it) } }
    val hasItems = entries?.active()?.isNotEmpty() == true

    val backupStatus = when {
        settings.lastBackupAt == 0L -> if (hasItems) Status.WARN else Status.INFO
        System.currentTimeMillis() - settings.lastBackupAt > 30L * 86_400_000 -> Status.WARN
        else -> Status.GOOD
    }
    val hwStatus = when (hardware) { HardwareLevel.STRONGBOX, HardwareLevel.TEE -> Status.GOOD; HardwareLevel.SOFTWARE -> Status.WARN; else -> Status.INFO }

    val protections = listOf(
        Protection(P.ENCRYPTION, Icons.Outlined.EnhancedEncryption, "Vault encryption", "AES‑256‑GCM · Argon2id · unique salt", Status.GOOD, 0),
        Protection(P.KEYS, Icons.Outlined.Memory, "Key storage", hardware?.label ?: "Checking…", hwStatus, 2),
        Protection(P.NETWORK, Icons.Outlined.CloudOff, "Network access", if (noNetwork) "None, verified" else "Internet permission found", if (noNetwork) Status.GOOD else Status.BAD, 3),
        Protection(P.SCREEN_LOCK, Icons.Outlined.ScreenLockPortrait, "Phone screen lock", if (screenLock) "On" else "Not set", if (screenLock) Status.GOOD else Status.BAD, 3),
        Protection(P.INTEGRITY, Icons.Outlined.AdminPanelSettings, "Device integrity", if (rooted) "Signs of root found" else "No signs of tampering", if (rooted) Status.BAD else Status.GOOD, 3),
        Protection(P.USB, Icons.Outlined.BugReport, "USB debugging", if (adb) "On" else "Off", if (adb) Status.WARN else Status.GOOD, 1),
        Protection(P.SCREENSHOTS, Icons.Outlined.VisibilityOff, "Screenshot protection", if (settings.blockScreenshots) "On" else "Off", if (settings.blockScreenshots) Status.GOOD else Status.WARN, 2),
        Protection(
            P.AUTOLOCK, Icons.Outlined.Schedule, "Auto-lock", autoLockLabel(settings.autoLockSeconds),
            if (settings.autoLockSeconds <= 300) Status.GOOD else Status.WARN, 2,
        ),
        Protection(
            P.CLIPBOARD, Icons.Outlined.ContentPaste, "Clipboard clearing",
            if (settings.clipboardClearSeconds > 0) "After ${settings.clipboardClearSeconds}s" else "Never", if (settings.clipboardClearSeconds > 0) Status.GOOD else Status.WARN, 1,
        ),
        Protection(
            P.BACKUP, Icons.Outlined.Backup, "Encrypted backup",
            if (settings.lastBackupAt == 0L) "Never backed up" else "Last ${ago(settings.lastBackupAt)}", backupStatus, 2,
        ),
        Protection(P.BIOMETRIC, Icons.Outlined.Fingerprint, "Biometric unlock", if (bio) "On" else "Off", if (bio) Status.GOOD else Status.INFO, 0),
        Protection(P.AUTOFILL, Icons.Outlined.PhonelinkLock, "Autofill", if (autofill) "On" else "Off", if (autofill) Status.GOOD else Status.INFO, 0),
    )
    val scored = protections.filter { it.weight > 0 && it.status != Status.INFO }
    val protectionScore = scored.sumOf { it.weight * when (it.status) { Status.GOOD -> 1.0; Status.WARN -> 0.5; else -> 0.0 } } / scored.sumOf { it.weight }.toDouble() * 100
    val score = health?.takeIf { it.total > 0 }?.let { (protectionScore * 0.6 + it.score * 0.4).roundToInt() } ?: protectionScore.roundToInt()
    val attention = protections.filter { it.status == Status.WARN || it.status == Status.BAD }
    val active = protections.filter { it.status == Status.GOOD }
    val optional = protections.filter { it.status == Status.INFO }
    val overall = when { score >= 90 -> Status.GOOD; score >= 70 -> Status.WARN; else -> Status.BAD }
    val failures = remember(log) { c.securityLog.failuresSinceLastUnlock() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            ScreenTitle("Security")

            // ---- Hero ----
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreRing(score, statusColor(overall), size = 104.dp, label = "SCORE")
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (overall) { Status.GOOD -> "You're well protected"; Status.WARN -> "Almost there"; else -> "Needs your attention" },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (attention.isEmpty()) "All ${active.size} protections are active." else "${attention.size} ${if (attention.size == 1) "item" else "items"} to review.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val st = selfTest
                    Icon(Icons.Outlined.TaskAlt, null, tint = when { st == null -> MaterialTheme.colorScheme.onSurfaceVariant; st.passed -> statusColor(Status.GOOD); else -> statusColor(Status.BAD) }, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when { st == null -> "Encryption not yet verified on this phone"; st.passed -> "Encryption verified ${ago(st.at)}"; else -> "Self-test found a problem" },
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(onClick = onSelfTest) { Text(if (st == null) "Verify" else "Run again") }
                }
            }

            if (failures > 0) {
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                    StatusRow(
                        Icons.Outlined.History, "$failures failed unlock ${if (failures == 1) "attempt" else "attempts"}",
                        "Someone may have tried to open your vault. See when in the access log.", Status.BAD, onLog,
                    )
                }
            }

            if (attention.isNotEmpty()) {
                SectionLabel("Needs attention")
                ProtectionList(attention) { open = it }
            }
            SectionLabel("Active protections")
            ProtectionList(active) { open = it }
            if (optional.isNotEmpty()) {
                SectionLabel("Optional")
                ProtectionList(optional) { open = it }
            }

            SectionLabel("Passwords")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                val h = health
                when {
                    !hasVault -> StatusRow(Icons.Outlined.HealthAndSafety, "Password health", "Create a vault to start", Status.INFO, onOpenVault)
                    h == null -> StatusRow(Icons.Outlined.HealthAndSafety, "Password health", "Analysing…", Status.INFO)
                    h.total == 0 -> StatusRow(Icons.Outlined.HealthAndSafety, "Password health", "Add logins to see how strong they are", Status.INFO, onOpenVault)
                    else -> StatusRow(
                        Icons.Outlined.HealthAndSafety, "Password health",
                        if (h.healthy == h.total) "All ${h.total} are strong and unique" else "${h.total - h.healthy} of ${h.total} need attention",
                        if (h.score >= 90) Status.GOOD else if (h.score >= 60) Status.WARN else Status.BAD, onHealth, trailing = "${h.score}%",
                    )
                }
                Hairline()
                StatusRow(Icons.Outlined.Speed, "Password checker", "Test any password, offline", Status.INFO, onChecker)
            }

            SectionLabel("Transparency")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                StatusRow(Icons.Outlined.TaskAlt, "Verify encryption", "Run the cryptographic self-test", Status.INFO, onSelfTest)
                Hairline()
                StatusRow(Icons.Outlined.History, "Access log", log.firstOrNull()?.let { "Latest: ${it.event.label.lowercase()}, ${ago(it.at)}" } ?: "No events yet", Status.INFO, onLog)
                Hairline()
                StatusRow(Icons.Outlined.School, "How your data is protected", "Every layer, in plain language", Status.INFO, onHowItWorks)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    open?.let { id ->
        protections.firstOrNull { it.id == id }?.let { p ->
            ProtectionSheet(
                p, hardware = hardware, rooted = rooted, hasVault = hasVault,
                onDismiss = { open = null }, onHowItWorks = { open = null; onHowItWorks() }, onSelfTest = { open = null; onSelfTest() },
                onOpenVault = { open = null; onOpenVault() },
            )
        }
    }
}

private fun autoLockLabel(seconds: Int) = when (seconds) {
    0 -> "Instantly"
    in 1..59 -> "After ${seconds}s"
    else -> "After ${seconds / 60} min"
}

@Composable
private fun ProtectionList(items: List<Protection>, onOpen: (P) -> Unit) {
    Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
        items.forEachIndexed { i, p ->
            if (i > 0) Hairline()
            StatusRow(p.icon, p.title, p.summary, p.status, onClick = { onOpen(p.id) })
        }
    }
}

/** Focused sheet: what the protection is, its current state, and the control to change it, right here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionSheet(
    p: Protection,
    hardware: HardwareLevel?,
    rooted: Boolean,
    hasVault: Boolean,
    onDismiss: () -> Unit,
    onHowItWorks: () -> Unit,
    onSelfTest: () -> Unit,
    onOpenVault: () -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val message = LocalMessenger.current
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val bio by c.vault.biometricEnabled.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val bytes = c.vault.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }
            }.onSuccess { c.vault.recordBackupExported(); message("Encrypted backup saved"); onDismiss() }.onFailure { message("Backup failed") }
        }
    }

    fun openSystem(action: String, data: Uri? = null) {
        runCatching { context.startActivity(Intent(action).apply { if (data != null) setData(data) }) }
            .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = statusColor(p.status)
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(p.icon, null, tint = color)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (p.status) { Status.GOOD -> "Protected"; Status.WARN -> "Recommended to change"; Status.BAD -> "Action needed"; Status.INFO -> "Optional" },
                        style = MaterialTheme.typography.labelLarge, color = color,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            @Composable fun body(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable fun action(label: String, filled: Boolean = true, onClick: () -> Unit) {
                Spacer(Modifier.height(16.dp)); PillButton(label, onClick, Modifier.fillMaxWidth(), filled = filled)
            }

            when (p.id) {
                P.ENCRYPTION -> {
                    body("Everything in your vault is encrypted with AES‑256‑GCM. The key comes from your master password via Argon2id (64 MiB, 3 passes) with a salt unique to your vault, then the file is sealed again by your phone's secure hardware.")
                    action("How it works", onClick = onHowItWorks)
                    action("Verify on this phone", filled = false, onClick = onSelfTest)
                }
                P.KEYS -> {
                    body(
                        (hardware?.detail ?: "Checking…") + ". " + when (hardware) {
                            HardwareLevel.STRONGBOX -> "This is the strongest protection Android offers: a separate tamper-resistant chip."
                            HardwareLevel.TEE -> "Keys live in an isolated part of the processor that apps, including this one, can't read."
                            HardwareLevel.SOFTWARE -> "Your vault is still protected by your master password, but a copied file is easier to attack. Use a long master password."
                            else -> ""
                        },
                    )
                    if (c.keystore.hasStrongBox(context) && hardware != HardwareLevel.STRONGBOX) {
                        Spacer(Modifier.height(8.dp)); body("Your phone has StrongBox. Biometric unlock keys are stored there.")
                    }
                }
                P.NETWORK -> body("Verified just now by reading the permissions of the installed app. Without the internet permission, Android blocks every network connection from this app, so your data has no way out.")
                P.SCREEN_LOCK -> {
                    body(if (p.status == Status.GOOD) "Your phone requires a PIN, pattern, password or biometric to open, which also lets Android bind keys to you." else "Anyone holding your phone can open it, and Android can't fully protect app keys without a screen lock. Set a PIN or password.")
                    if (p.status != Status.GOOD) action("Set a screen lock") { openSystem(Settings.ACTION_SECURITY_SETTINGS) }
                }
                P.INTEGRITY -> body(
                    if (rooted) "This phone shows signs of being rooted or running a modified system. Other apps with root access could bypass Android's isolation and read app memory. Consider keeping your vault on an unmodified phone."
                    else "No signs of root access or a test-signed system were found. This check is a best-effort heuristic; deliberately hidden root can't be detected by any app.",
                )
                P.USB -> {
                    body(if (p.status == Status.GOOD) "USB debugging is off, so a connected computer can't control this phone." else "A computer connected by USB could install apps or read data from this phone. Turn it off unless you're developing.")
                    if (p.status != Status.GOOD) action("Open developer options") { openSystem(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) }
                }
                P.SCREENSHOTS -> {
                    body("Stops screenshots and screen recording, and hides the app's content in the recent apps view.")
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Block screenshots", settings.blockScreenshots, { v -> c.settings.update { it.copy(blockScreenshots = v) } })
                }
                P.AUTOLOCK -> {
                    body("Locks the app and wipes the vault key from memory after you leave.")
                    OptionRow("Lock after leaving", null, listOf(0 to "Instantly", 30 to "30s", 60 to "1 min", 300 to "5 min", 900 to "15 min"), settings.autoLockSeconds) { v ->
                        c.settings.update { it.copy(autoLockSeconds = v) }
                    }
                    ToggleRow("Lock when screen turns off", settings.lockOnScreenOff, { v -> c.settings.update { it.copy(lockOnScreenOff = v) } })
                }
                P.CLIPBOARD -> {
                    body("Copied passwords are hidden from clipboard previews and removed automatically.")
                    OptionRow("Clear after", null, listOf(15 to "15s", 30 to "30s", 60 to "1 min", 120 to "2 min", 0 to "Never"), settings.clipboardClearSeconds) { v ->
                        c.settings.update { it.copy(clipboardClearSeconds = v) }
                    }
                }
                P.BACKUP -> {
                    body("If you lose this phone, an encrypted backup is the only way to recover your vault. It's protected by your master password. Store it somewhere other than this phone.")
                    if (hasVault) action("Back up now") {
                        exportLauncher.launch("mysecurepass-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.msp")
                    } else action("Create a vault", onClick = onOpenVault)
                }
                P.BIOMETRIC -> {
                    body("Unlock with your fingerprint or face. The vault key is sealed in secure hardware and is revoked automatically if your phone's biometrics change.")
                    Spacer(Modifier.height(8.dp))
                    val available = context.canUseStrongBiometrics()
                    ToggleRow(
                        "Biometric unlock", bio, { on ->
                            if (on) enableBiometric(context, onResult = { ok -> message(if (ok) "Biometric unlock enabled" else "Couldn't enable biometrics") })
                            else { c.vault.disableBiometric(); message("Biometric unlock off") }
                        },
                        subtitle = when { !hasVault -> "Create a vault first"; !available -> "No strong biometric enrolled on this phone"; else -> null },
                        enabled = hasVault && available,
                    )
                }
                P.AUTOFILL -> {
                    body("Fill your logins into apps and browsers, and save new ones as you sign up. Credentials are only offered to the matching app or website.")
                    action(if (p.status == Status.GOOD) "Change autofill service" else "Turn on autofill") {
                        openSystem(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${context.packageName}"))
                    }
                }
            }
        }
    }
}

