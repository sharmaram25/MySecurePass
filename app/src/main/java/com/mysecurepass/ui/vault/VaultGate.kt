package com.mysecurepass.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.generator.Capitalization
import com.mysecurepass.core.generator.PassphraseConfig
import com.mysecurepass.core.generator.Separator
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.data.UnlockResult
import com.mysecurepass.ui.authenticateWithCipher
import com.mysecurepass.ui.canUseStrongBiometrics
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.SecretField
import com.mysecurepass.ui.components.StrengthMeter
import com.mysecurepass.ui.components.ratingColor
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun GateHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** First-run: choose a master password. Enforces a realistic strength floor (zxcvbn score >= 3). */
@Composable
fun SetupScreen(onCreated: () -> Unit = {}) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val message = LocalMessenger.current

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var warning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(password) {
        if (password.isEmpty()) { score = 0; warning = null; return@LaunchedEffect }
        delay(80)
        val a = c.analyzer.analyze(password)
        score = a.score; warning = a.warning
    }

    val matches = password.isNotEmpty() && password == confirm
    val strongEnough = score >= 3 && password.length >= 10
    val canCreate = strongEnough && matches && acknowledged && !busy

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        ContentWidth(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(16.dp))
            GateHeader(
                Icons.Outlined.Shield, "Create your vault",
                "One master password protects everything. It's stretched with Argon2id and never stored anywhere, so choose one you'll remember.",
            )
            Spacer(Modifier.height(32.dp))
            SecretField(password, { password = it }, "Master password", imeAction = ImeAction.Next, monospace = false)
            Spacer(Modifier.height(12.dp))
            val rating = if (password.isEmpty()) Rating.VERY_WEAK else Rating.fromScore(score)
            StrengthMeter(rating, fraction = if (password.isEmpty()) 0f else (score + 1) / 5f)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        password.isEmpty() -> "Use 4+ random words or 12+ mixed characters"
                        !strongEnough -> warning ?: "Too easy to guess"
                        else -> rating.label
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (password.isNotEmpty()) ratingColor(rating) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val phrase = c.generator.generate(PassphraseConfig(5, Separator.HYPHEN, Capitalization.FIRST_LETTER, true)).value
                    password = phrase; confirm = ""
                    message("Suggested a 5-word passphrase. Write it down somewhere safe.")
                }) { Text("Suggest") }
            }
            Spacer(Modifier.height(12.dp))
            SecretField(
                confirm, { confirm = it }, "Confirm master password", monospace = false,
                isError = confirm.isNotEmpty() && !matches,
                supporting = if (confirm.isNotEmpty() && !matches) "Passwords don't match" else null,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { acknowledged = !acknowledged }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(acknowledged, { acknowledged = it }, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
                Text(
                    "I understand that if I forget this password, my vault cannot be recovered by anyone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            PillButton(
                if (busy) "Securing…" else "Create vault",
                onClick = {
                    busy = true
                    val chars = password.toCharArray()
                    scope.launch {
                        c.vault.create(chars)
                        password = ""; confirm = ""
                        busy = false
                        haptic(HapticFeedbackType.LongPress)
                        // This screen leaves composition once the vault unlocks, so the biometric
                        // offer is raised through the container and shown by the vault tab.
                        if (context.canUseStrongBiometrics()) c.offerBiometric.value = true
                        onCreated()
                    }
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun BiometricOfferDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val message = LocalMessenger.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Fingerprint, null) },
        title = { Text("Unlock with biometrics?") },
        text = { Text("Your vault key will be sealed in this phone's secure hardware and released only after a strong biometric check. You can still use your master password at any time.") },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                enableBiometric(context, onResult = { ok -> if (ok) message("Biometric unlock enabled") })
            }) { Text("Enable") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

fun enableBiometric(context: android.content.Context, onDone: () -> Unit = {}, onResult: (Boolean) -> Unit = {}) {
    val activity = context.findActivity() ?: return onDone()
    val vault = context.container.vault
    val cipher = runCatching { vault.biometricEnrollCipher() }.getOrNull() ?: run { onResult(false); return onDone() }
    activity.authenticateWithCipher(
        "Enable biometric unlock", "Confirm to seal your vault key", cipher,
        onSuccess = { authorized ->
            kotlinx.coroutines.MainScope().launch {
                runCatching { vault.enableBiometric(authorized) }.onSuccess { onResult(true) }.onFailure { onResult(false) }
                onDone()
            }
        },
        onError = { onResult(false); onDone() },
    )
}

/** Unlock with master password or biometrics. Reused by the autofill flow. */
@Composable
fun UnlockScreen(subtitle: String? = null, branded: Boolean = false, onUnlocked: () -> Unit = {}) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val bioEnabled by c.vault.biometricEnabled.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lockoutUntil by remember { mutableLongStateOf(c.settings.lockoutUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val previous = remember { c.securityLog.previousUnlock() }
    var failures by remember { mutableStateOf(c.securityLog.failuresSinceLastUnlock()) }

    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(500) }
        now = System.currentTimeMillis()
    }
    val lockedOut = lockoutUntil > now

    fun handle(result: UnlockResult) {
        busy = false
        when (result) {
            UnlockResult.Success -> { haptic(HapticFeedbackType.LongPress); password = ""; error = null; onUnlocked() }
            is UnlockResult.WrongPassword -> {
                haptic(HapticFeedbackType.LongPress)
                error = result.attemptsBeforeWipe?.let { left ->
                    "Wrong password. The vault will be erased after $left more failed ${if (left == 1) "attempt" else "attempts"}."
                } ?: "Wrong password"
                lockoutUntil = result.lockoutUntil
                password = ""
                failures = c.securityLog.failuresSinceLastUnlock()
            }
            UnlockResult.Wiped -> error = "Too many failed attempts. The vault was erased."
            is UnlockResult.LockedOut -> { lockoutUntil = result.until }
            is UnlockResult.Error -> error = result.message
        }
    }

    fun biometric() {
        val activity = context.findActivity() ?: return
        val cipher = c.vault.biometricUnlockCipher()
        if (cipher == null) { error = "Biometrics changed. Use your master password, then re-enable in Settings."; return }
        activity.authenticateWithCipher(
            "Unlock MySecurePass", "Confirm it's you", cipher,
            onSuccess = { authorized -> busy = true; scope.launch { handle(c.vault.unlockWithBiometric(authorized)) } },
            onError = { msg -> if (msg != null) error = msg },
        )
    }

    LaunchedEffect(Unit) { if (bioEnabled && !lockedOut) biometric() }

    fun submit() {
        if (password.isEmpty() || busy || lockedOut) return
        busy = true; error = null
        val chars = password.toCharArray()
        scope.launch { handle(c.vault.unlock(chars)) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.Center) {
        ContentWidth(Modifier.padding(24.dp)) {
            Spacer(Modifier.height(48.dp))
            if (branded) BrandHeader(subtitle ?: "Locked. Unlock to continue.")
            else GateHeader(Icons.Outlined.Lock, "Vault locked", subtitle ?: "Enter your master password")
            Spacer(Modifier.height(32.dp))
            SecretField(
                password, { password = it; error = null }, "Master password",
                imeAction = ImeAction.Go, onImeAction = ::submit, monospace = false,
                isError = error != null, supporting = error,
            )
            TrustLine(previous, failures)
            AnimatedVisibility(lockedOut) {
                Text(
                    "Too many attempts. Try again in ${((lockoutUntil - now) / 1000).coerceAtLeast(1)}s",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    PillButton(
                        if (busy) "Unlocking…" else "Unlock", ::submit,
                        icon = if (busy) null else Icons.Outlined.LockOpen,
                        enabled = password.isNotEmpty() && !busy && !lockedOut, modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (bioEnabled) {
                    com.mysecurepass.ui.components.CircleIconButton(Icons.Outlined.Fingerprint, "Unlock with biometrics", ::biometric)
                }
            }
        }
    }
}

/** "Last unlocked …" plus a warning if someone has tried wrong passwords since. Builds trust at a glance. */
@Composable
private fun TrustLine(previous: com.mysecurepass.data.LogEntry?, failures: Int) {
    val fmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (previous != null) {
            Text(
                "Last unlocked ${fmt.format(java.util.Date(previous.at))}" +
                    if (previous.event == com.mysecurepass.data.SecurityEvent.UNLOCK_BIOMETRIC) " with biometrics" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        if (failures > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "$failures failed ${if (failures == 1) "attempt" else "attempts"} since then",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
            )
        }
    }
}

/** App identity for the full-screen lock: logo tile, name, and a calm status line. */
@Composable
private fun BrandHeader(subtitle: String) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(28.dp)).background(androidx.compose.ui.graphics.Color(0xFF0B0B0D)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.mysecurepass.R.drawable.ic_launcher_foreground), null,
                Modifier.size(84.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("MySecurePass", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
