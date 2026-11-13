package com.mysecurepass.ui.vault

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mysecurepass.container
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.core.vault.trashed
import com.mysecurepass.ui.components.EmptyState
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.SubScreenBar
import kotlinx.coroutines.launch

@Composable
fun TrashScreen(onBack: () -> Unit) = RequireUnlocked { all ->
    val c = LocalContext.current.container
    val message = LocalMessenger.current
    val items = remember(all) { all.trashed().sortedByDescending { it.deletedAt } }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf<VaultEntry?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            SubScreenBar("Trash", onBack) {
                if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Empty", color = MaterialTheme.colorScheme.error) }
            }
            Text(
                "Items here are still encrypted and are permanently deleted 30 days after you remove them.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (items.isEmpty()) item { EmptyState(Icons.Outlined.DeleteOutline, "Trash is empty", "Deleted items appear here for 30 days.") }
        items(items, key = { it.id }) { e ->
            val daysLeft = (30 - (System.currentTimeMillis() - (e.deletedAt ?: 0)) / 86_400_000L).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                EntryAvatar(e)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text("Deleted ${formatDate(e.deletedAt ?: 0)} · $daysLeft days left", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { c.appScope.launch { c.vault.restore(e.id) }; message("Restored “${e.title}”") }) {
                    Icon(Icons.Outlined.RestoreFromTrash, "Restore")
                }
                IconButton(onClick = { confirmPurge = e }) { Icon(Icons.Outlined.DeleteForever, "Delete forever", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("${items.size} ${if (items.size == 1) "item" else "items"} will be permanently deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmEmpty = false; c.appScope.launch { c.vault.emptyTrash() } }) { Text("Delete all", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") } },
        )
    }
    confirmPurge?.let { e ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Delete “${e.title}” forever?") },
            text = { Text("This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmPurge = null; c.appScope.launch { c.vault.purge(e.id) } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Cancel") } },
        )
    }
}
