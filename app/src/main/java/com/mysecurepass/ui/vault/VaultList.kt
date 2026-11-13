package com.mysecurepass.ui.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.AppContainer
import com.mysecurepass.container
import com.mysecurepass.core.totp.Totp
import com.mysecurepass.core.vault.Cards
import com.mysecurepass.core.vault.EntryType
import com.mysecurepass.core.vault.HealthReport
import com.mysecurepass.core.vault.PasswordHealth
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.core.vault.active
import com.mysecurepass.core.vault.allTags
import com.mysecurepass.core.vault.trashed
import com.mysecurepass.data.VaultSort
import com.mysecurepass.data.VaultState
import com.mysecurepass.ui.components.Chip
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.EmptyState
import com.mysecurepass.ui.components.LocalActionMessenger
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.ScreenTitle
import com.mysecurepass.ui.components.fieldColors
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.generator.QrDialog
import com.mysecurepass.ui.theme.Msp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Health analysis with per-entry score caching (zxcvbn is the expensive part). */
suspend fun computeHealth(c: AppContainer, entries: List<VaultEntry>): HealthReport = withContext(Dispatchers.Default) {
    val byPassword = entries.associateBy { it.password }
    PasswordHealth.analyze(entries, System.currentTimeMillis()) { pw ->
        val e = byPassword.getValue(pw)
        c.healthScores.getOrPut("${e.id}:${e.passwordChangedAt}") { c.analyzer.score(pw) }
    }
}

/** Copies a secret, records usage, and confirms (unless Android already shows its own toast). */
fun copySecret(c: AppContainer, entry: VaultEntry, value: String, label: String, message: (String) -> Unit, sensitive: Boolean = true) {
    val secs = c.settings.settings.value.clipboardClearSeconds
    c.clipboard.copy(value, secs, sensitive)
    c.appScope.launch { runCatching { c.vault.markUsed(entry.id) } }
    if (!c.clipboard.systemShowsConfirmation) {
        message(if (sensitive && secs > 0) "$label copied · clears in ${secs}s" else "$label copied")
    }
}

/** The Vault tab routes between setup, unlock and the list depending on vault state. */
@Composable
fun VaultTab(onOpen: (String) -> Unit, onAdd: (EntryType) -> Unit, onHealth: () -> Unit, onTrash: () -> Unit) {
    val c = LocalContext.current.container
    val state by c.vault.state.collectAsStateWithLifecycle()
    val offerBio by c.offerBiometric.collectAsStateWithLifecycle()
    if (offerBio && state is VaultState.Unlocked) BiometricOfferDialog(onDismiss = { c.offerBiometric.value = false })
    AnimatedContent(
        targetState = state::class,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "vault",
    ) { kind ->
        when (kind) {
            VaultState.Empty::class -> SetupScreen()
            VaultState.Locked::class -> UnlockScreen()
            VaultState.Unlocked::class -> VaultList((state as? VaultState.Unlocked)?.entries.orEmpty(), onOpen, onAdd, onHealth, onTrash)
            else -> Box(Modifier.fillMaxSize())
        }
    }
}

private sealed interface Filter {
    data object All : Filter
    data object Favourites : Filter
    data class Type(val type: EntryType) : Filter
    data class Tag(val tag: String) : Filter
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun VaultList(
    allEntries: List<VaultEntry>,
    onOpen: (String) -> Unit,
    onAdd: (EntryType) -> Unit,
    onHealth: () -> Unit,
    onTrash: () -> Unit,
) {
    val c = LocalContext.current.container
    val message = LocalMessenger.current
    val haptic = rememberHaptic()
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Filter>(Filter.All) }
    var sortMenu by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf<VaultEntry?>(null) }
    var addSheet by remember { mutableStateOf(false) }

    val entries = remember(allEntries) { allEntries.active() }
    val trashCount = remember(allEntries) { allEntries.trashed().size }
    val tags = remember(allEntries) { allEntries.allTags() }
    val typesPresent = remember(entries) { EntryType.entries.filter { t -> entries.any { it.type == t } } }

    val shown = remember(entries, query, filter, settings.vaultSort) {
        val q = query.trim().lowercase()
        entries
            .filter { e ->
                when (val f = filter) {
                    Filter.All -> true
                    Filter.Favourites -> e.favorite
                    is Filter.Type -> e.type == f.type
                    is Filter.Tag -> e.tags.any { it.equals(f.tag, ignoreCase = true) }
                }
            }
            .filter { e -> q.isEmpty() || e.searchText().contains(q) }
            .sortedWith(
                when (settings.vaultSort) {
                    VaultSort.NAME -> compareByDescending<VaultEntry> { it.favorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    VaultSort.RECENTLY_USED -> compareByDescending<VaultEntry> { it.lastUsedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                    VaultSort.RECENTLY_MODIFIED -> compareByDescending { it.updatedAt }
                },
            )
    }
    val health by produceState<HealthReport?>(null, allEntries) { value = computeHealth(c, allEntries) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item(key = "header") {
                ContentWidth {
                    ScreenTitle("Vault", "${entries.size} ${if (entries.size == 1) "item" else "items"} · encrypted") {
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sort") }
                            DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                                VaultSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        onClick = { c.settings.update { it.copy(vaultSort = s) }; sortMenu = false },
                                        trailingIcon = if (s == settings.vaultSort) ({ Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)) }) else null,
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { c.vault.lock(); haptic(HapticFeedbackType.LongPress) }) { Icon(Icons.Outlined.Lock, "Lock now") }
                    }
                    if (entries.isNotEmpty()) {
                        OutlinedTextField(
                            query, { query = it }, placeholder = { Text("Search ${entries.size} items") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                            shape = CircleShape, colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Chip("All", filter == Filter.All, { filter = Filter.All })
                            if (entries.any { it.favorite }) Chip("Favourites", filter == Filter.Favourites, { filter = Filter.Favourites })
                            if (typesPresent.size > 1) typesPresent.forEach { t -> Chip(t.pluralLabel, filter == Filter.Type(t), { filter = Filter.Type(t) }) }
                            tags.forEach { tag -> Chip("#$tag", filter == Filter.Tag(tag), { filter = Filter.Tag(tag) }) }
                        }
                    }
                    val h = health
                    if (h != null && h.total > 0 && h.score < 100 && query.isEmpty() && filter == Filter.All) {
                        Spacer(Modifier.height(12.dp))
                        BannerRow(Icons.Outlined.HealthAndSafety, "${h.total - h.healthy} need attention", "Password health ${h.score}%", Msp.extra.weak, onHealth)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.Inventory2, "Your vault is empty",
                        "Add logins, cards, Wi‑Fi networks and notes, or import from another password manager in Settings.",
                        Modifier.padding(top = 32.dp),
                    ) { PillButton("Add an item", { addSheet = true }, icon = Icons.Outlined.Add) }
                }
            } else if (shown.isEmpty()) {
                item(key = "nomatch") { EmptyState(Icons.Outlined.Search, "No matches", "Try a different search or filter.") }
            }
            items(shown, key = { it.id }) { entry ->
                Box(Modifier.fillMaxWidth().animateItem(), contentAlignment = Alignment.TopCenter) {
                    Row(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpen(entry.id) },
                                onLongClick = { haptic(HapticFeedbackType.LongPress); quick = entry },
                            )
                            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EntryAvatar(entry)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = subtitleOf(entry)
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        primaryCopy(entry)?.let { (label, value) ->
                            IconButton(onClick = { haptic(HapticFeedbackType.LongPress); copySecret(c, entry, value, label, message) }) {
                                Icon(Icons.Outlined.ContentCopy, "Copy $label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (trashCount > 0 && query.isEmpty()) {
                item(key = "trash") {
                    ContentWidth {
                        Spacer(Modifier.height(12.dp))
                        BannerRow(Icons.Outlined.DeleteOutline, "Trash", "$trashCount ${if (trashCount == 1) "item" else "items"} · deleted after 30 days", MaterialTheme.colorScheme.onSurfaceVariant, onTrash)
                    }
                }
            }
        }

        if (entries.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.BottomEnd).padding(20.dp).size(60.dp).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface).clickable { addSheet = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Add, "Add item", tint = MaterialTheme.colorScheme.surface) }
        }
    }

    if (addSheet) {
        ModalBottomSheet(onDismissRequest = { addSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Add to vault", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp))
                EntryType.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { addSheet = false; onAdd(t) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                            Icon(t.icon, null, tint = t.accent())
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.label, style = MaterialTheme.typography.bodyLarge)
                            Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    quick?.let { entry -> QuickActions(entry, onDismiss = { quick = null }, onOpen = { quick = null; onOpen(entry.id) }) }
}

/** The single most useful thing to copy from the list row. */
private fun primaryCopy(e: VaultEntry): Pair<String, String>? = when (e.type) {
    EntryType.LOGIN, EntryType.WIFI -> e.password.takeIf { it.isNotEmpty() }?.let { "Password" to it }
    EntryType.CARD -> e.card?.number?.takeIf { it.isNotEmpty() }?.let { "Card number" to Cards.digits(it) }
    EntryType.NOTE -> null
}

@Composable
private fun BannerRow(icon: ImageVector, title: String, subtitle: String, tint: Color, onClick: () -> Unit) {
    Panel(Modifier.padding(horizontal = 16.dp), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private class QuickAction(val icon: ImageVector, val label: String, val destructive: Boolean = false, val run: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActions(entry: VaultEntry, onDismiss: () -> Unit, onOpen: () -> Unit) {
    val c = LocalContext.current.container
    val message = LocalMessenger.current
    val undo = LocalActionMessenger.current
    var showQr by remember { mutableStateOf(false) }

    val actions = buildList {
        fun copy(label: String, value: String, sensitive: Boolean = true) =
            add(QuickAction(Icons.Outlined.ContentCopy, "Copy ${label.lowercase()}") { copySecret(c, entry, value, label, message, sensitive); onDismiss() })
        when (entry.type) {
            EntryType.LOGIN -> {
                if (entry.username.isNotEmpty()) copy("Username", entry.username, sensitive = false)
                if (entry.password.isNotEmpty()) copy("Password", entry.password)
                Totp.parse(entry.totp)?.let { p ->
                    add(QuickAction(Icons.Outlined.ContentCopy, "Copy 2FA code") { copySecret(c, entry, Totp.code(p, System.currentTimeMillis()), "Code", message); onDismiss() })
                }
            }
            EntryType.CARD -> entry.card?.let { card ->
                if (card.number.isNotEmpty()) copy("Card number", Cards.digits(card.number))
                if (card.expiry.isNotEmpty()) copy("Expiry", card.expiry, sensitive = false)
                if (card.cvv.isNotEmpty()) copy("Security code", card.cvv)
            }
            EntryType.WIFI -> {
                if (entry.password.isNotEmpty()) copy("Password", entry.password)
                if (entry.password.isNotEmpty()) add(QuickAction(Icons.Outlined.QrCode2, "Show join QR code") { showQr = true })
            }
            EntryType.NOTE -> if (entry.notes.isNotEmpty()) copy("Note", entry.notes)
        }
        add(QuickAction(if (entry.favorite) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (entry.favorite) "Remove from favourites" else "Add to favourites") {
            c.appScope.launch { c.vault.upsert(entry.copy(favorite = !entry.favorite)) }; onDismiss()
        })
        add(QuickAction(Icons.AutoMirrored.Outlined.OpenInNew, "Open") { onOpen() })
        add(QuickAction(Icons.Outlined.DeleteOutline, "Move to trash", destructive = true) {
            c.appScope.launch { c.vault.trash(entry.id) }
            onDismiss()
            undo("Moved to trash", "Undo") { c.appScope.launch { c.vault.restore(entry.id) } }
        })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(Modifier.padding(start = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                EntryAvatar(entry)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitleOf(entry), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = a.run).padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(a.icon, null, tint = tint)
                    Spacer(Modifier.width(16.dp))
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, color = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (showQr) QrDialog(entry.password, initialSsid = entry.wifi?.ssid, onDismiss = { showQr = false; onDismiss() })
}
