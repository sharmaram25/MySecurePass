package com.mysecurepass.ui.generator

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mysecurepass.container
import com.mysecurepass.core.generator.Capitalization
import com.mysecurepass.core.generator.GeneratorMode
import com.mysecurepass.core.generator.GeneratorSettings
import com.mysecurepass.core.generator.PassphraseConfig
import com.mysecurepass.core.generator.PasswordConfig
import com.mysecurepass.core.generator.PinConfig
import com.mysecurepass.core.generator.Preset
import com.mysecurepass.core.generator.Separator
import com.mysecurepass.core.generator.SymbolSets
import com.mysecurepass.core.generator.TokenFormat
import com.mysecurepass.core.strength.AttackScenario
import com.mysecurepass.core.strength.CrackTime
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.ui.components.Chip
import com.mysecurepass.ui.components.CircleIconButton
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.ScreenTitle
import com.mysecurepass.ui.components.SecretText
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.SegmentedControl
import com.mysecurepass.ui.components.SliderRow
import com.mysecurepass.ui.components.StrengthMeter
import com.mysecurepass.ui.components.ToggleRow
import com.mysecurepass.ui.components.fieldColors
import com.mysecurepass.ui.components.ratingColor
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.theme.MonoFamily
import com.mysecurepass.ui.theme.PasswordStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(onSaveToVault: () -> Unit) {
    val container = LocalContext.current.container
    val vm: GeneratorViewModel = viewModel { GeneratorViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val spins by vm.spins.collectAsStateWithLifecycle()
    val message = LocalMessenger.current
    val haptic = rememberHaptic()

    var copied by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }

    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }

    val rotation by animateFloatAsState(spins * 180f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "spin")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            ScreenTitle("Generate") {
                IconButton(onClick = { showHistory = true }) { Icon(Icons.Outlined.History, "Recently copied") }
            }

            // ---- Hero: the secret ----
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(24.dp)) {
                val value = current?.value.orEmpty()
                Box(Modifier.fillMaxWidth().heightIn(min = 112.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = value,
                        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
                        label = "secret",
                    ) { v ->
                        val size = when {
                            v.length > 48 -> 17.sp
                            v.length > 32 -> 20.sp
                            v.length > 20 -> 23.sp
                            else -> 28.sp
                        }
                        SecretText(
                            v, visible = true,
                            style = PasswordStyle.copy(fontSize = size, lineHeight = size * 1.35f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clickable { vm.spin(); haptic(HapticFeedbackType.LongPress) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                val bits = current?.entropyBits ?: 0.0
                val rating = Rating.fromBits(bits)
                StrengthMeter(rating, fraction = (bits / 128.0).toFloat().coerceIn(0.05f, 1f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rating.label, style = MaterialTheme.typography.titleSmall, color = ratingColor(rating))
                    Text(
                        "  ·  ${bits.roundToInt()} bits",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) "Hide" else "Crack time", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(showDetails, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(Modifier.padding(top = 8.dp)) {
                        AttackScenario.entries.forEach { s ->
                            Hairline()
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(s.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(s.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    CrackTime.format(CrackTime.secondsForBits(bits, s)),
                                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End,
                                    modifier = Modifier.width(120.dp),
                                )
                            }
                        }
                        Text(
                            "Exact entropy of the generator, not a guess from the output. Average time for an attacker who knows exactly how it was made.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ---- Actions ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(
                    if (copied) "Copied" else "Copy",
                    onClick = {
                        val secs = vm.copy()
                        copied = true
                        haptic(HapticFeedbackType.LongPress)
                        if (!vm.systemShowsCopyToast) message(if (secs > 0) "Copied · clears in ${secs}s" else "Copied")
                        else if (secs > 0) message("Clipboard clears in ${secs}s")
                    },
                    icon = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(Icons.Outlined.Refresh, "Generate new", { vm.spin(); haptic(HapticFeedbackType.TextHandleMove) }, rotation = rotation)
                CircleIconButton(Icons.Outlined.QrCode2, "Show QR code", { showQr = true })
                CircleIconButton(Icons.Outlined.BookmarkAdd, "Save to vault", { vm.handOffToVault(); onSaveToVault() })
            }

            // ---- Mode ----
            SegmentedControl(
                options = GeneratorMode.entries,
                selected = settings.mode,
                label = { when (it) { GeneratorMode.PASSWORD -> "Password"; GeneratorMode.PASSPHRASE -> "Phrase"; GeneratorMode.PIN -> "PIN"; GeneratorMode.TOKEN -> "Token" } },
                onSelect = { mode -> vm.update { it.copy(mode = mode) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            SectionLabel("Presets")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Preset.entries.forEach { p ->
                    val active = p.settings(settings).active == settings.active && p.settings(settings).mode == settings.mode
                    Chip(p.label, active, onClick = { vm.applyPreset(p); haptic(HapticFeedbackType.TextHandleMove) })
                }
            }

            SectionLabel("Options")
            Panel(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                when (settings.mode) {
                    GeneratorMode.PASSWORD -> PasswordOptions(settings.password) { cfg -> vm.update { it.copy(password = cfg) } }
                    GeneratorMode.PASSPHRASE -> PassphraseOptions(settings.passphrase) { cfg -> vm.update { it.copy(passphrase = cfg) } }
                    GeneratorMode.PIN -> PinOptions(settings.pin) { cfg -> vm.update { it.copy(pin = cfg) } }
                    GeneratorMode.TOKEN -> TokenOptions(settings) { cfg -> vm.update { it.copy(token = cfg) } }
                }
            }

            Text(
                "Generated on this device with Android's cryptographically secure random number generator. Nothing ever leaves your phone. This app has no internet access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            )
        }
    }

    if (showQr) current?.let { QrDialog(it.value) { showQr = false } }

    if (showHistory) {
        val items by vm.history.items.collectAsStateWithLifecycle()
        ModalBottomSheet(onDismissRequest = { showHistory = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Recently copied", style = MaterialTheme.typography.titleLarge)
                        Text("Kept in memory only. Cleared when the app locks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (items.isNotEmpty()) TextButton(onClick = { vm.history.clear() }) { Text("Clear") }
                }
                Spacer(Modifier.height(12.dp))
                if (items.isEmpty()) {
                    Text("Nothing yet.", Modifier.padding(vertical = 32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(items, key = { it.at.toString() + it.value.hashCode() }) { item ->
                            var reveal by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SecretText(
                                    item.value, reveal, MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                                    modifier = Modifier.weight(1f).clickable { reveal = !reveal }, maxLines = 2,
                                )
                                IconButton(onClick = { vm.copyHistory(item.value); message("Copied") }) { Icon(Icons.Outlined.ContentCopy, "Copy") }
                            }
                            Hairline()
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasswordOptions(c: PasswordConfig, onChange: (PasswordConfig) -> Unit) {
    SliderRow("Length", c.length, PasswordConfig.MIN_LENGTH..64, { onChange(c.copy(length = it)) })
    Spacer(Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("A–Z", c.uppercase, { onChange(c.copy(uppercase = !c.uppercase)) })
        Chip("a–z", c.lowercase, { onChange(c.copy(lowercase = !c.lowercase)) })
        Chip("0–9", c.digits, { onChange(c.copy(digits = !c.digits)) })
        Chip("!@#", c.symbols, { onChange(c.copy(symbols = !c.symbols)) })
    }
    Spacer(Modifier.height(8.dp))
    ToggleRow("Avoid look‑alikes", c.avoidAmbiguous, { onChange(c.copy(avoidAmbiguous = it)) }, "Skips I l 1 | O 0 o ` ' \"")

    // Rarely needed rules live behind a disclosure so the main screen stays calm.
    var advanced by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (advanced) 180f else 0f, label = "chevron")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { advanced = !advanced }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Advanced rules", style = MaterialTheme.typography.bodyLarge)
            Text("Minimums, symbol set, excluded characters", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ExpandMore, if (advanced) "Collapse" else "Expand", modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AnimatedVisibility(advanced, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column { AdvancedPasswordRules(c, onChange) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedPasswordRules(c: PasswordConfig, onChange: (PasswordConfig) -> Unit) {
    if (c.digits) SliderRow("Minimum digits", c.minDigits, 0..6, { onChange(c.copy(minDigits = it)) })
    if (c.symbols) SliderRow("Minimum symbols", c.minSymbols, 0..6, { onChange(c.copy(minSymbols = it)) })
    if (c.symbols) {
        ToggleRow("No symbols at start or end", c.noSymbolsAtEdges, { onChange(c.copy(noSymbolsAtEdges = it)) }, "Some sites reject them")
        Spacer(Modifier.height(8.dp))
        Text("Symbols", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        val custom = c.symbolSet != SymbolSets.COMMON && c.symbolSet != SymbolSets.ALL
        var editing by remember { mutableStateOf(custom) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Common", c.symbolSet == SymbolSets.COMMON && !editing, { editing = false; onChange(c.copy(symbolSet = SymbolSets.COMMON)) })
            Chip("All", c.symbolSet == SymbolSets.ALL && !editing, { editing = false; onChange(c.copy(symbolSet = SymbolSets.ALL)) })
            Chip("Custom", editing || custom, { editing = true })
        }
        if (editing || custom) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = c.symbolSet, onValueChange = { onChange(c.copy(symbolSet = it.filterNot(Char::isLetterOrDigit).filterNot(Char::isWhitespace).take(32))) },
                label = { Text("Allowed symbols") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = c.excludeChars, onValueChange = { onChange(c.copy(excludeChars = it.filterNot(Char::isWhitespace).take(64))) },
        label = { Text("Never use these characters") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily), shape = MaterialTheme.shapes.medium, colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassphraseOptions(c: PassphraseConfig, onChange: (PassphraseConfig) -> Unit) {
    SliderRow("Words", c.words, PassphraseConfig.MIN_WORDS..12, { onChange(c.copy(words = it)) })
    Text("Separator", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Separator.HYPHEN to "-", Separator.PERIOD to ".", Separator.UNDERSCORE to "_", Separator.SPACE to "Space",
            Separator.NONE to "None", Separator.RANDOM_DIGIT to "0–9", Separator.RANDOM_SYMBOL to "!@#",
        ).forEach { (sep, label) -> Chip(label, c.separator == sep, { onChange(c.copy(separator = sep)) }) }
    }
    Spacer(Modifier.height(16.dp))
    Text("Capitalisation", style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Capitalization.FIRST_LETTER to "Title", Capitalization.NONE to "lower", Capitalization.UPPERCASE to "UPPER", Capitalization.RANDOM to "rAnDoM",
        ).forEach { (cap, label) -> Chip(label, c.capitalization == cap, { onChange(c.copy(capitalization = cap)) }) }
    }
    Spacer(Modifier.height(8.dp))
    ToggleRow("Include a number", c.includeNumber, { onChange(c.copy(includeNumber = it)) }, "Helps meet site rules")
    Text(
        "Words come from the EFF diceware list (7,772 words, ~12.9 bits each).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PinOptions(c: PinConfig, onChange: (PinConfig) -> Unit) {
    SliderRow("Digits", c.length, PinConfig.MIN_LENGTH..12, { onChange(c.copy(length = it)) })
    ToggleRow("Avoid guessable PINs", c.avoidWeak, { onChange(c.copy(avoidWeak = it)) }, "No repeats, runs, keypad lines, years or dates")
    Text(
        "PINs are only safe where attempts are limited (phones, cards, SIMs).",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TokenOptions(s: GeneratorSettings, onChange: (com.mysecurepass.core.generator.TokenConfig) -> Unit) {
    val c = s.token
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TokenFormat.HEX to "Hex", TokenFormat.BASE64URL to "Base64url", TokenFormat.ALPHANUMERIC to "A‑z 0‑9", TokenFormat.UUID to "UUID v4")
            .forEach { (f, label) -> Chip(label, c.format == f, { onChange(c.copy(format = f)) }) }
    }
    when (c.format) {
        TokenFormat.UUID -> Text("122 random bits, RFC 4122 format.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        TokenFormat.ALPHANUMERIC -> SliderRow("Characters", c.size, 8..128, { onChange(c.copy(size = it)) })
        else -> SliderRow("Bytes", c.size, 8..128, { onChange(c.copy(size = it)) }, valueLabel = "${c.size} · ${c.size * 8} bit")
    }
}
