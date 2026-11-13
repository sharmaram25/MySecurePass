package com.mysecurepass.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mysecurepass.core.vault.Cards
import com.mysecurepass.core.vault.EntryType
import com.mysecurepass.core.vault.Matching
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.ui.theme.Msp

val EntryType.label: String
    get() = when (this) {
        EntryType.LOGIN -> "Login"
        EntryType.CARD -> "Card"
        EntryType.WIFI -> "Wi‑Fi"
        EntryType.NOTE -> "Secure note"
    }

val EntryType.pluralLabel: String
    get() = when (this) {
        EntryType.LOGIN -> "Logins"
        EntryType.CARD -> "Cards"
        EntryType.WIFI -> "Wi‑Fi"
        EntryType.NOTE -> "Notes"
    }

val EntryType.icon: ImageVector
    get() = when (this) {
        EntryType.LOGIN -> Icons.Outlined.Key
        EntryType.CARD -> Icons.Outlined.CreditCard
        EntryType.WIFI -> Icons.Outlined.Wifi
        EntryType.NOTE -> Icons.AutoMirrored.Outlined.StickyNote2
    }

val EntryType.description: String
    get() = when (this) {
        EntryType.LOGIN -> "Website or app account"
        EntryType.CARD -> "Credit or debit card"
        EntryType.WIFI -> "Network name and password"
        EntryType.NOTE -> "Private text, recovery codes, keys"
    }

@Composable
fun EntryType.accent(): Color = when (this) {
    EntryType.LOGIN -> MaterialTheme.colorScheme.onSurface
    EntryType.CARD -> Msp.extra.digit
    EntryType.WIFI -> Msp.extra.strong
    EntryType.NOTE -> Msp.extra.fair
}

/** Letter tile for logins; type icon for other items; small type badge in the corner. */
@Composable
fun EntryAvatar(entry: VaultEntry, size: Dp = 44.dp) {
    Box(Modifier.size(size)) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.type == EntryType.LOGIN) {
                Text(
                    entry.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Icon(entry.type.icon, null, tint = entry.type.accent(), modifier = Modifier.size(size * 0.48f))
            }
        }
        if (entry.favorite) {
            Box(
                Modifier.align(Alignment.TopEnd).size(size * 0.28f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(size * 0.18f).clip(CircleShape).background(Msp.extra.fair)) }
        }
    }
}


/** One-line summary under the title. Never shows secrets. */
fun subtitleOf(e: VaultEntry): String = when (e.type) {
    EntryType.LOGIN -> e.username.ifEmpty { e.urls.firstOrNull()?.let { Matching.packageOf(it) ?: it }.orEmpty() }
    EntryType.CARD -> e.card?.let { c -> listOf(Cards.brand(c.number).label, Cards.masked(c.number)).joinToString(" ") }.orEmpty()
    EntryType.WIFI -> e.wifi?.ssid.orEmpty()
    EntryType.NOTE -> if (e.notes.isEmpty()) "" else "${e.notes.lines().size} lines"
}
