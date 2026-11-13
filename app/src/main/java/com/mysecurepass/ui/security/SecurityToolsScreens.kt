package com.mysecurepass.ui.security

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.security.CheckResult
import com.mysecurepass.core.security.SelfTest
import com.mysecurepass.security.DeviceSecurity
import com.mysecurepass.security.SelfTestReport
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.EmptyState
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.Status
import com.mysecurepass.ui.components.SubScreenBar
import com.mysecurepass.ui.components.statusColor
import com.mysecurepass.ui.theme.MonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.text.DateFormat
import java.util.Date

// ---- Self-test --------------------------------------------------------------------------------

private data class Step(val name: String, val run: () -> CheckResult)

@Composable
fun SelfTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val previous by c.lastSelfTest.collectAsStateWithLifecycle()

    val random = remember { SecureRandom() }
    val steps = remember {
        listOf(
            Step("Random number generator") { SelfTest.randomness(random) },
            Step("AES-256-GCM") { SelfTest.aesGcm() },
            Step("Argon2id") { SelfTest.argon2(c.kdf) },
            Step("Tamper detection") { SelfTest.tamperDetection(c.kdf, random) },
            Step("Hardware keystore") {
                val start = System.nanoTime()
                val ok = c.keystore.roundTripTest()
                val level = c.keystore.hardwareLevel()
                c.hardwareLevel.value = level
                CheckResult("Hardware keystore", ok, if (ok) "Encrypt/decrypt inside ${level.label}" else "Keystore round-trip failed", (System.nanoTime() - start) / 1_000_000)
            },
            Step("Network isolation") {
                CheckResult("Network isolation", DeviceSecurity.hasNoNetworkAccess(context), "No internet permission in the installed app", 0)
            },
        )
    }
    val results = remember { mutableStateListOf<CheckResult?>().apply { repeat(steps.size) { add(null) } } }
    var running by remember { mutableStateOf(-1) }
    var kdfMs by remember { mutableStateOf<Long?>(null) }
    val done = running == steps.size

    fun run() {
        scope.launch {
            for (i in results.indices) results[i] = null
            kdfMs = null
            for (i in steps.indices) {
                running = i
                results[i] = withContext(Dispatchers.Default) { steps[i].run() }
            }
            running = steps.size
            kdfMs = withContext(Dispatchers.Default) { SelfTest.kdfTiming(c.kdf) }
            c.lastSelfTest.value = SelfTestReport(results.filterNotNull(), kdfMs ?: 0, c.hardwareLevel.value ?: com.mysecurepass.security.HardwareLevel.UNKNOWN, System.currentTimeMillis())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            SubScreenBar("Verify encryption", onBack)
            Text(
                "Runs the real cryptography on this phone and compares it with published test values, so you don't have to take it on trust.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                steps.forEachIndexed { i, step ->
                    if (i > 0) Hairline()
                    val r = results[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            when {
                                r != null -> Icon(
                                    if (r.passed) Icons.Outlined.CheckCircle else Icons.Outlined.Error, null,
                                    tint = statusColor(if (r.passed) Status.GOOD else Status.BAD),
                                )
                                running == i -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                r?.detail ?: when (i) {
                                    0 -> "FIPS 140‑2 statistical tests"
                                    1 -> "NIST known-answer test"
                                    2 -> "RFC 9106 known-answer test"
                                    3 -> "Altered data must be rejected"
                                    4 -> "Keys in secure hardware"
                                    else -> "App can't reach the internet"
                                },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r != null && r.millis > 0) {
                            Text("${r.millis} ms", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AnimatedVisibility(done, enter = fadeIn() + slideInVertically { it / 4 }) {
                val passed = results.all { it?.passed == true }
                Column {
                    Spacer(Modifier.height(16.dp))
                    Panel(Modifier.padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (passed) Icons.Outlined.Shield else Icons.Outlined.WarningAmber, null,
                                tint = statusColor(if (passed) Status.GOOD else Status.BAD), modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (passed) "All checks passed" else "A check failed", style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (passed) "Your phone's cryptography behaves exactly as specified." +
                                (kdfMs?.let { " Each master-password guess costs $it ms of work here, so an attacker gets only about ${(1000 / it.coerceAtLeast(1)).coerceAtLeast(1)} guesses per second per processor core." } ?: "")
                            else "Don't store new secrets until this is resolved. Try restarting the phone and running the test again.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            PillButton(
                when { running in steps.indices -> "Running…"; done -> "Run again"; else -> "Run self-test" },
                ::run, Modifier.fillMaxWidth().padding(horizontal = 16.dp), enabled = running !in steps.indices,
            )
            previous?.takeIf { running == -1 }?.let {
                Text(
                    "Last run ${ago(it.at)}: ${if (it.passed) "passed" else "failed"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---- Access log --------------------------------------------------------------------------------

@Composable
fun AccessLogScreen(onBack: () -> Unit) {
    val c = LocalContext.current.container
    val entries by c.securityLog.entries.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SubScreenBar("Access log", onBack) {
                if (entries.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
            Text(
                "Every unlock, failed attempt and sensitive action, recorded on this device only. Item names and secrets are never logged.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (entries.isEmpty()) item { EmptyState(Icons.Outlined.History, "No events yet", "Activity will appear here.") }
        items(entries, key = { it.at.toString() + it.event.name }) { e ->
            val color = if (e.event.alert) statusColor(Status.BAD) else MaterialTheme.colorScheme.onSurfaceVariant
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(if (e.event.alert) color else MaterialTheme.colorScheme.surfaceContainerHighest))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.event.label, style = MaterialTheme.typography.bodyLarge, color = if (e.event.alert) color else MaterialTheme.colorScheme.onSurface)
                    Text(fmt.format(Date(e.at)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear access log?") },
            text = { Text("This removes the history of unlocks and security events from this device.") },
            confirmButton = { TextButton(onClick = { c.securityLog.clear(); confirmClear = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// ---- How it works ------------------------------------------------------------------------------

private data class Layer(val icon: ImageVector, val title: String, val body: String)

@Composable
fun HowItWorksScreen(onBack: () -> Unit) {
    val layers = listOf(
        Layer(Icons.Outlined.Password, "Your master password stays in your head",
            "It is never stored, never written to disk, and never leaves this phone. Nobody, including us, can recover it."),
        Layer(Icons.Outlined.LockClock, "Stretched with Argon2id",
            "Your password goes through Argon2id with 64 MiB of memory and a unique random salt. Each guess costs about a second of work, which makes guessing billions of passwords impractical even with specialised hardware."),
        Layer(Icons.Outlined.Key, "Encrypted with AES‑256‑GCM",
            "A random 256-bit key encrypts your vault. GCM authenticates every byte, so any tampering, even a single changed bit, is detected and rejected."),
        Layer(Icons.Outlined.Memory, "Sealed to this phone",
            "The encrypted file is wrapped again by a key inside your phone's secure hardware (TEE or StrongBox). That key can't be extracted, so a copied vault file is useless on any other device."),
        Layer(Icons.Outlined.Shield, "Unlocked only in memory",
            "When you unlock, your data is decrypted into memory only. Auto-lock wipes the key when you leave the app or turn off the screen."),
        Layer(Icons.Outlined.CloudOff, "Nothing leaves your device",
            "The app has no internet permission at all, and you can verify that in the Security tab. No accounts, analytics, ads or cloud sync. Backups happen only when you export one."),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            SubScreenBar("How your data is protected", onBack)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                layers.forEachIndexed { i, layer ->
                    Row {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(layer.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
                            if (i < layers.lastIndex) Box(Modifier.width(2.dp).height(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.padding(top = 2.dp, bottom = 16.dp)) {
                            Text("${i + 1}. ${layer.title}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(layer.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            SectionLabel("What no app can protect against")
            Panel(Modifier.padding(horizontal = 16.dp)) {
                listOf(
                    "Someone who learns your master password, so never reuse it anywhere.",
                    "A phone that is rooted or infected with malware that controls the screen.",
                    "Someone watching over your shoulder while you reveal a password.",
                    "Losing your phone without an exported backup.",
                ).forEach { Row(Modifier.padding(vertical = 4.dp)) { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(it, style = MaterialTheme.typography.bodyMedium) } }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
