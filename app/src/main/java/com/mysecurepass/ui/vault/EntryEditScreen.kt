package com.mysecurepass.ui.vault

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mysecurepass.container
import com.mysecurepass.core.totp.Totp
import com.mysecurepass.core.vault.CardData
import com.mysecurepass.core.vault.Cards
import com.mysecurepass.core.vault.CustomField
import com.mysecurepass.core.vault.EntryType
import com.mysecurepass.core.vault.Matching
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.core.vault.WifiData
import com.mysecurepass.core.vault.WifiSecurity
import com.mysecurepass.core.vault.allTags
import com.mysecurepass.ui.components.Chip
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.SecretField
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.SubScreenBar
import com.mysecurepass.ui.components.ToggleRow
import com.mysecurepass.ui.components.fieldColors
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.theme.MonoFamily
import kotlinx.coroutines.launch

private class FieldDraft(label: String, value: String, hidden: Boolean) {
    var label by mutableStateOf(label)
    var value by mutableStateOf(value)
    var hidden by mutableStateOf(hidden)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryEditScreen(id: String?, newType: EntryType, onDone: () -> Unit) = RequireUnlocked { entries ->
    val context = LocalContext.current
    val c = context.container
    val message = LocalMessenger.current
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val existing = remember(id) { entries.firstOrNull { it.id == id } }
    val type = existing?.type ?: newType
    val knownTags = remember(entries) { entries.allTags() }

    // Plain remember (never rememberSaveable): secrets must not be written to saved instance state.
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf(existing?.password ?: c.draftPassword.orEmpty().also { c.draftPassword = null }) }
    var urls by remember { mutableStateOf(existing?.urls?.joinToString("\n").orEmpty()) }
    var totp by remember { mutableStateOf(existing?.totp.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var tags by remember { mutableStateOf(existing?.tags.orEmpty()) }
    var tagInput by remember { mutableStateOf("") }
    val fields = remember { mutableStateListOf<FieldDraft>().apply { existing?.fields?.forEach { add(FieldDraft(it.label, it.value, it.hidden)) } } }
    // Card
    var holder by remember { mutableStateOf(existing?.card?.holder.orEmpty()) }
    var number by remember { mutableStateOf(existing?.card?.number.orEmpty()) }
    var expiry by remember { mutableStateOf(existing?.card?.expiry.orEmpty()) }
    var cvv by remember { mutableStateOf(existing?.card?.cvv.orEmpty()) }
    var pin by remember { mutableStateOf(existing?.card?.pin.orEmpty()) }
    // Wi‑Fi
    var ssid by remember { mutableStateOf(existing?.wifi?.ssid.orEmpty()) }
    var security by remember { mutableStateOf(existing?.wifi?.security ?: WifiSecurity.WPA) }
    var hiddenNet by remember { mutableStateOf(existing?.wifi?.hidden ?: false) }

    var confirmDiscard by remember { mutableStateOf(false) }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val text = QrImageDecoder.decode(context, uri)
            when {
                text == null -> message("No QR code found in that image")
                Totp.parse(text) == null -> message("That QR code isn't a 2FA setup code")
                else -> {
                    totp = text
                    haptic(HapticFeedbackType.LongPress)
                    message("2FA code added")
                }
            }
        }
    }

    val totpValid = totp.isBlank() || Totp.parse(totp) != null
    val digits = Cards.digits(number)
    val cardNumberWarning = type == EntryType.CARD && digits.length >= 12 && !Cards.isLuhnValid(digits)
    val expiryValid = expiry.isBlank() || Cards.parseExpiry(expiry) != null
    val effectiveTitle = title.ifBlank {
        when (type) { EntryType.WIFI -> ssid; EntryType.CARD -> Cards.brand(number).takeIf { digits.isNotEmpty() }?.label.orEmpty(); else -> "" }
    }
    val canSave = effectiveTitle.isNotBlank() && totpValid && expiryValid

    fun build(now: Long): VaultEntry {
        val parsedUrls = urls.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { u ->
            Matching.packageOf(u)?.let { Matching.ANDROID_APP_SCHEME + it } ?: Matching.normalizeHost(u)
        }.distinct()
        val base = existing ?: VaultEntry(id = c.vault.newId(), title = effectiveTitle.trim(), type = type, createdAt = now, updatedAt = now, passwordChangedAt = now)
        val updated = base.copy(
            title = effectiveTitle.trim(),
            username = if (type == EntryType.LOGIN) username.trim() else "",
            urls = if (type == EntryType.LOGIN) parsedUrls else emptyList(),
            totp = if (type == EntryType.LOGIN) totp.trim() else "",
            notes = notes,
            tags = tags,
            fields = fields.filter { it.label.isNotBlank() || it.value.isNotEmpty() }.map { CustomField(it.label.trim().ifEmpty { "Field" }, it.value, it.hidden) },
            card = if (type == EntryType.CARD) CardData(holder.trim(), digits, expiry.trim(), cvv.trim(), pin.trim()) else null,
            wifi = if (type == EntryType.WIFI) WifiData(ssid.trim(), security, hiddenNet) else null,
            updatedAt = now,
        )
        val pw = if (type == EntryType.LOGIN || type == EntryType.WIFI) password else ""
        return if (existing == null) updated.copy(password = pw) else updated.withPassword(pw, now)
    }

    val dirty = remember(title, username, password, urls, totp, notes, tags, holder, number, expiry, cvv, pin, ssid, security, hiddenNet, fields.size) {
        if (existing == null) listOf(title, username, password, urls, totp, notes, holder, number, cvv, pin, ssid).any { it.isNotEmpty() } || tags.isNotEmpty() || fields.isNotEmpty()
        else build(existing.updatedAt).copy(updatedAt = existing.updatedAt, passwordChangedAt = existing.passwordChangedAt, history = existing.history) != existing
    }

    fun save() {
        val entry = build(System.currentTimeMillis())
        c.appScope.launch { c.vault.upsert(entry) }
        haptic(HapticFeedbackType.LongPress)
        message(if (existing == null) "Saved to vault" else "Changes saved")
        onDone()
    }

    BackHandler(enabled = dirty) { confirmDiscard = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        ContentWidth {
            SubScreenBar(if (existing == null) "New ${type.label.lowercase()}" else "Edit ${type.label.lowercase()}", { if (dirty) confirmDiscard = true else onDone() }) {
                TextButton(onClick = ::save, enabled = canSave) { Text("Save", style = MaterialTheme.typography.labelLarge) }
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(
                    when (type) { EntryType.WIFI -> "Name (optional)"; EntryType.CARD -> "Card name (optional)"; else -> "Title" },
                    title, { title = it }, imeAction = ImeAction.Next, capitalize = true,
                )

                when (type) {
                    EntryType.LOGIN -> {
                        Field("Username or email", username, { username = it }, keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                        SecretField(
                            password, { password = it }, "Password", imeAction = ImeAction.Next, initiallyVisible = existing == null && password.isNotEmpty(),
                            trailing = { GenerateButton { password = it } },
                        )
                        Field("Websites or apps", urls, { urls = it }, singleLine = false, keyboardType = KeyboardType.Uri, supporting = "One per line: example.com or androidapp://com.example.app")
                        OutlinedTextField(
                            totp, { totp = it.trim() }, label = { Text("Two-factor secret (optional)") }, singleLine = true,
                            isError = !totpValid,
                            supportingText = { Text(if (!totpValid) "Not a valid Base32 secret or otpauth:// link" else "Paste the setup key, or scan a QR from a screenshot") },
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            trailingIcon = {
                                IconButton(onClick = { qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Outlined.QrCodeScanner, "Scan QR from image")
                                }
                            },
                        )
                    }
                    EntryType.CARD -> {
                        Field("Cardholder name", holder, { holder = it }, imeAction = ImeAction.Next, capitalize = true)
                        OutlinedTextField(
                            Cards.format(number), { number = Cards.digits(it).take(19) }, label = { Text("Card number") }, singleLine = true,
                            isError = cardNumberWarning,
                            supportingText = {
                                Text(
                                    when {
                                        digits.isEmpty() -> "Brand is detected automatically"
                                        cardNumberWarning -> "This number fails the card checksum. Check for typos."
                                        else -> Cards.brand(digits).label
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                expiry, { v -> expiry = formatExpiryInput(v) }, label = { Text("Expiry MM/YY") }, singleLine = true,
                                isError = !expiryValid, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            )
                            Column(Modifier.weight(1f)) {
                                SecretField(cvv, { cvv = it.filter(Char::isDigit).take(4) }, "CVV", imeAction = ImeAction.Next)
                            }
                        }
                        SecretField(pin, { pin = it.filter(Char::isDigit).take(12) }, "Card PIN (optional)", imeAction = ImeAction.Next)
                    }
                    EntryType.WIFI -> {
                        Field("Network name (SSID)", ssid, { ssid = it }, imeAction = ImeAction.Next)
                        if (security != WifiSecurity.NONE) {
                            SecretField(password, { password = it }, "Password", imeAction = ImeAction.Next, initiallyVisible = existing == null, trailing = { GenerateButton { password = it } })
                        }
                        Text("Security", style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("WPA/WPA2/WPA3", security == WifiSecurity.WPA, { security = WifiSecurity.WPA })
                            Chip("WEP", security == WifiSecurity.WEP, { security = WifiSecurity.WEP })
                            Chip("Open", security == WifiSecurity.NONE, { security = WifiSecurity.NONE })
                        }
                        ToggleRow("Hidden network", hiddenNet, { hiddenNet = it }, "The network doesn't broadcast its name")
                    }
                    EntryType.NOTE -> Unit
                }

                Field(
                    if (type == EntryType.NOTE) "Note" else "Notes", notes, { notes = it },
                    singleLine = false, minLines = if (type == EntryType.NOTE) 8 else 3, capitalize = true,
                )
            }

            // ---- Tags ----
            SectionLabel("Tags")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(16.dp)) {
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { t -> Chip("#$t  ×", true, { tags = tags - t }) }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                fun addTag() {
                    val t = tagInput.trim().removePrefix("#").take(24)
                    if (t.isNotEmpty() && tags.none { it.equals(t, true) }) tags = tags + t
                    tagInput = ""
                }
                OutlinedTextField(
                    tagInput, { v -> if (v.endsWith(",") || v.endsWith(" ")) { tagInput = v.dropLast(1); addTag() } else tagInput = v },
                    placeholder = { Text("Add a tag (e.g. Work, Finance)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Words),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addTag() }),
                    trailingIcon = { if (tagInput.isNotBlank()) IconButton(onClick = ::addTag) { Icon(Icons.Outlined.Add, "Add tag") } },
                )
                val suggestions = knownTags.filter { k -> tags.none { it.equals(k, true) } && (tagInput.isBlank() || k.contains(tagInput, true)) }.take(6)
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> Chip("#$s", false, { tags = tags + s; tagInput = "" }) }
                    }
                }
            }

            // ---- Custom fields ----
            SectionLabel("Custom fields")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                fields.forEachIndexed { i, f ->
                    Panel(padding = PaddingValues(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                f.label, { f.label = it }, label = { Text("Label") }, singleLine = true,
                                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            )
                            IconButton(onClick = { fields.removeAt(i) }) { Icon(Icons.Outlined.RemoveCircleOutline, "Remove field", tint = MaterialTheme.colorScheme.error) }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (f.hidden) SecretField(f.value, { f.value = it }, "Value")
                        else Field("Value", f.value, { f.value = it })
                        ToggleRow("Hide value", f.hidden, { f.hidden = it }, "Masked like a password")
                    }
                }
                PillButton(
                    "Add field", { fields.add(FieldDraft("", "", false)) }, Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Add, filled = false,
                )
                Spacer(Modifier.height(8.dp))
                PillButton("Save", ::save, Modifier.fillMaxWidth(), enabled = canSave)
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDone() }) { Text("Discard", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

/** Inserts the slash in MM/YY as the user types digits. */
private fun formatExpiryInput(input: String): String {
    val d = input.filter(Char::isDigit).take(4)
    return if (d.length <= 2) d else d.substring(0, 2) + "/" + d.substring(2)
}

@Composable
private fun GenerateButton(onGenerated: (String) -> Unit) {
    val c = LocalContext.current.container
    val haptic = rememberHaptic()
    IconButton(onClick = {
        val settings = c.settings.generator.value
        // PIN/token modes make poor account passwords; fall back to the password config for this button.
        val config = when (settings.mode) {
            com.mysecurepass.core.generator.GeneratorMode.PASSPHRASE -> settings.passphrase
            else -> settings.password
        }
        onGenerated(c.generator.generate(config).value)
        haptic(HapticFeedbackType.TextHandleMove)
    }) { Icon(Icons.Outlined.AutoAwesome, "Generate password") }
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    isError: Boolean = false,
    supporting: String? = null,
    mono: Boolean = false,
    capitalize: Boolean = false,
) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        isError = isError, supportingText = supporting?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType, imeAction = imeAction,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = keyboardType == KeyboardType.Text && !singleLine,
        ),
    )
}

