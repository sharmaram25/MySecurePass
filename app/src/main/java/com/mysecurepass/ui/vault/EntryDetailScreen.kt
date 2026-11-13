package com.mysecurepass.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.core.totp.Totp
import com.mysecurepass.core.vault.Cards
import com.mysecurepass.core.vault.EntryType
import com.mysecurepass.core.vault.Matching
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.core.vault.WifiSecurity
import com.mysecurepass.data.VaultState
import com.mysecurepass.ui.components.Badge
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.LocalActionMessenger
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.SecretText
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.SubScreenBar
import com.mysecurepass.ui.components.ratingColor
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.generator.QrDialog
import com.mysecurepass.ui.theme.MonoFamily
import com.mysecurepass.ui.theme.Msp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Shows an unlock screen in place of content whenever the vault is locked (e.g. auto-lock while open). */
@Composable
fun RequireUnlocked(content: @Composable (List<VaultEntry>) -> Unit) {
    val state by LocalContext.current.container.vault.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is VaultState.Unlocked -> content(s.entries)
        VaultState.Empty -> SetupScreen()
        else -> UnlockScreen()
    }
}

@Composable
internal fun FieldRow(
    label: String,
    onCopy: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            content()
        }
        trailing()
        if (onCopy != null) IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Copy $label", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A secret value with reveal toggle and copy. */
@Composable
private fun SecretRow(label: String, value: String, onCopy: () -> Unit, extra: @Composable () -> Unit = {}) {
    var reveal by remember { mutableStateOf(false) }
    FieldRow(label, onCopy, trailing = {
        IconButton(onClick = { reveal = !reveal }) {
            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (reveal) "Hide" else "Reveal", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }) {
        SecretText(value, reveal, MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily), modifier = Modifier.clickable { reveal = !reveal })
        extra()
    }
}

@Composable
fun EntryDetailScreen(id: String, onBack: () -> Unit, onEdit: () -> Unit) = RequireUnlocked { entries ->
    val c = LocalContext.current.container
    val message = LocalMessenger.current
    val undo = LocalActionMessenger.current
    val haptic = rememberHaptic()
    val entry = entries.firstOrNull { it.id == id && !it.isDeleted }
    if (entry == null) { LaunchedEffect(Unit) { onBack() }; return@RequireUnlocked }

    var showHistory by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }

    fun copy(label: String, value: String, sensitive: Boolean = true) {
        haptic(HapticFeedbackType.LongPress)
        copySecret(c, entry, value, label, message, sensitive)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            SubScreenBar("", onBack) {
                IconButton(onClick = {
                    c.appScope.launch { c.vault.upsert(entry.copy(favorite = !entry.favorite)) }
                    haptic(HapticFeedbackType.TextHandleMove)
                }) { Icon(if (entry.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, "Favourite", tint = if (entry.favorite) Msp.extra.fair else MaterialTheme.colorScheme.onSurface) }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
            }
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                EntryAvatar(entry, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(entry.title, style = MaterialTheme.typography.headlineSmall)
                    Text(entry.type.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (entry.tags.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 24.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tags.forEach { Badge("#$it", MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (entry.type == EntryType.CARD) entry.card?.let { card ->
                CardVisual(entry.title, card.holder, card.number, card.expiry)
                Spacer(Modifier.height(16.dp))
            }

            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                var first = true
                @Composable fun divider() { if (!first) Hairline(); first = false }

                when (entry.type) {
                    EntryType.LOGIN -> {
                        if (entry.username.isNotEmpty()) { divider(); FieldRow("Username", { copy("Username", entry.username, false) }) { Text(entry.username, style = MaterialTheme.typography.bodyLarge) } }
                        if (entry.password.isNotEmpty()) {
                            divider()
                            val score by produceState<Int?>(null, entry.password) { value = c.analyzer.analyze(entry.password).score }
                            SecretRow("Password", entry.password, { copy("Password", entry.password) }) {
                                score?.let { s -> Spacer(Modifier.height(6.dp)); val r = Rating.fromScore(s); Badge(r.label, ratingColor(r)) }
                            }
                        }
                        if (entry.totp.isNotBlank()) { divider(); TotpRow(entry.totp) { copy("Code", it) } }
                        entry.urls.forEach { url ->
                            divider()
                            FieldRow(if (Matching.packageOf(url) != null) "App" else "Website", null) { Text(Matching.packageOf(url) ?: url, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                    EntryType.CARD -> entry.card?.let { card ->
                        if (card.holder.isNotEmpty()) { divider(); FieldRow("Cardholder", { copy("Cardholder", card.holder, false) }) { Text(card.holder, style = MaterialTheme.typography.bodyLarge) } }
                        if (card.number.isNotEmpty()) { divider(); SecretRow("Card number", Cards.format(card.number), { copy("Card number", Cards.digits(card.number)) }) }
                        if (card.expiry.isNotEmpty()) {
                            divider()
                            FieldRow("Expiry", { copy("Expiry", card.expiry, false) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(card.expiry, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily))
                                    if (Cards.isExpired(card.expiry, System.currentTimeMillis())) { Spacer(Modifier.width(8.dp)); Badge("Expired", Msp.extra.veryWeak) }
                                }
                            }
                        }
                        if (card.cvv.isNotEmpty()) { divider(); SecretRow("Security code", card.cvv, { copy("Security code", card.cvv) }) }
                        if (card.pin.isNotEmpty()) { divider(); SecretRow("PIN", card.pin, { copy("PIN", card.pin) }) }
                    }
                    EntryType.WIFI -> {
                        entry.wifi?.let { w ->
                            divider(); FieldRow("Network name", { copy("Network name", w.ssid, false) }) { Text(w.ssid, style = MaterialTheme.typography.bodyLarge) }
                            divider(); FieldRow("Security", null) {
                                Text(when (w.security) { WifiSecurity.WPA -> "WPA/WPA2/WPA3"; WifiSecurity.WEP -> "WEP (insecure)"; WifiSecurity.NONE -> "Open network" } + if (w.hidden) " · hidden" else "", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        if (entry.password.isNotEmpty()) { divider(); SecretRow("Password", entry.password, { copy("Password", entry.password) }) }
                    }
                    EntryType.NOTE -> Unit
                }
                entry.fields.forEach { f ->
                    divider()
                    if (f.hidden) SecretRow(f.label, f.value, { copy(f.label, f.value) })
                    else FieldRow(f.label, { copy(f.label, f.value, false) }) { Text(f.value, style = MaterialTheme.typography.bodyLarge) }
                }
                if (entry.notes.isNotBlank()) {
                    divider()
                    FieldRow(if (entry.type == EntryType.NOTE) "Note" else "Notes", { copy("Note", entry.notes) }) {
                        Text(entry.notes, style = if (entry.type == EntryType.NOTE) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium)
                    }
                }
                if (first) Text("No details yet. Tap edit to add some.", Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (entry.type == EntryType.WIFI && entry.password.isNotEmpty() && entry.wifi != null) {
                Spacer(Modifier.height(12.dp))
                PillButton("Share network via QR", { showQr = true }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), icon = Icons.Outlined.QrCode2, filled = false)
            }

            if (entry.history.isNotEmpty()) {
                SectionLabel("Password history")
                Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    TextButton(onClick = { showHistory = !showHistory }) {
                        Text(if (showHistory) "Hide ${entry.history.size} previous" else "Show ${entry.history.size} previous", color = MaterialTheme.colorScheme.onSurface)
                    }
                    AnimatedVisibility(showHistory) {
                        Column {
                            entry.history.forEach { h ->
                                Hairline()
                                SecretRow("Replaced ${formatDate(h.replacedAt)}", h.password, { copy("Password", h.password) })
                            }
                        }
                    }
                }
            }

            Text(
                "Created ${formatDate(entry.createdAt)} · Modified ${formatDate(entry.updatedAt)}" +
                    if (entry.lastUsedAt > 0) " · Used ${formatDate(entry.lastUsedAt)}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            )
            PillButton(
                "Move to trash", {
                    c.appScope.launch { c.vault.trash(entry.id) }
                    undo("Moved to trash", "Undo") { c.appScope.launch { c.vault.restore(entry.id) } }
                    // The screen pops itself once the entry leaves the active list (see null check above).
                },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), icon = Icons.Outlined.DeleteOutline, filled = false, destructive = true,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQr) entry.wifi?.let { w ->
        QrDialog(
            entry.password, initialSsid = w.ssid,
            security = when (w.security) { WifiSecurity.WPA -> "WPA"; WifiSecurity.WEP -> "WEP"; WifiSecurity.NONE -> "nopass" },
            hidden = w.hidden, onDismiss = { showQr = false },
        )
    }
}

/** A minimal, premium rendering of a payment card. The number is masked unless tapped. */
@Composable
private fun CardVisual(title: String, holder: String, number: String, expiry: String) {
    var reveal by remember { mutableStateOf(false) }
    val brand = Cards.brand(number)
    val base = Msp.extra.digit
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(1.586f)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF15161B), Color(0xFF23252E), base.copy(alpha = 0.35f))))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .clickable { reveal = !reveal }
            .padding(22.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.TopStart))
        Text(brand.label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.TopEnd))
        Text(
            if (reveal) Cards.format(number) else Cards.masked(number),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFamily, letterSpacing = 2.sp),
            color = Color.White, modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(holder.uppercase().ifEmpty { " " }, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            if (expiry.isNotEmpty()) Text(expiry, style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFamily), color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun TotpRow(secret: String, onCopy: (String) -> Unit) {
    val params = remember(secret) { Totp.parse(secret) }
    if (params == null) {
        FieldRow("One-time code", null) { Text("Invalid secret", color = MaterialTheme.colorScheme.error) }
        return
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(params) { while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) } }
    val code = remember(params, now / 1000 / params.periodSeconds) { Totp.code(params, now) }
    val remaining = Totp.secondsRemaining(params, now)
    val progress by animateFloatAsState(remaining / params.periodSeconds.toFloat(), tween(900), label = "totp")
    val grouped = if (code.length == 6) code.substring(0, 3) + " " + code.substring(3) else code
    FieldRow("One-time code" + (params.issuer?.let { " · $it" } ?: ""), { onCopy(code) }, trailing = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 4.dp)) {
            CircularProgressIndicator(
                progress = { progress }, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp,
                color = if (remaining <= 5) Msp.extra.weak else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text("$remaining", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp))
        }
    }) {
        Text(grouped, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFamily, letterSpacing = 2.sp))
    }
}

internal fun formatDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
