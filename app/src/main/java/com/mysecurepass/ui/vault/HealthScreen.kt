package com.mysecurepass.ui.vault

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.core.vault.HealthIssue
import com.mysecurepass.core.vault.HealthReport
import com.mysecurepass.data.VaultState
import com.mysecurepass.ui.components.Badge
import com.mysecurepass.ui.components.Chip
import com.mysecurepass.ui.components.EmptyState
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.StrengthMeter
import com.mysecurepass.ui.components.ratingColor
import com.mysecurepass.ui.theme.Msp

@Composable
fun HealthScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalContext.current.container
    val state by c.vault.state.collectAsStateWithLifecycle()
    val entries = (state as? VaultState.Unlocked)?.entries
    if (entries == null) { UnlockScreen(); return }

    val report by produceState<HealthReport?>(null, entries) { value = computeHealth(c, entries) }
    var filter by remember { mutableStateOf<HealthIssue?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text("Password health", style = MaterialTheme.typography.titleLarge)
            }
        }
        val r = report
        if (r == null) {
            item { Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() } }
            return@LazyColumn
        }
        item {
            val score by animateIntAsState(r.score, tween(700), label = "score")
            val rating = when { r.score >= 95 -> Rating.EXCELLENT; r.score >= 80 -> Rating.STRONG; r.score >= 60 -> Rating.FAIR; r.score >= 40 -> Rating.WEAK; else -> Rating.VERY_WEAK }
            Panel(Modifier.padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$score", style = MaterialTheme.typography.displaySmall, color = ratingColor(rating))
                    Text(" / 100", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                StrengthMeter(rating, fraction = r.score / 100f)
                Spacer(Modifier.height(12.dp))
                Text("${r.healthy} of ${r.total} passwords are strong, unique and recent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("All", filter == null, { filter = null })
                listOf(HealthIssue.WEAK to "Weak", HealthIssue.REUSED to "Reused", HealthIssue.OLD to "Old", HealthIssue.MISSING_2FA to "No 2FA").forEach { (issue, label) ->
                    val n = r.count(issue)
                    if (n > 0) Chip("$label $n", filter == issue, { filter = if (filter == issue) null else issue })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        val flagged = entries.filter { e -> r.issues[e.id]?.let { filter == null || filter in it } == true }
        if (flagged.isEmpty()) {
            item { EmptyState(Icons.Outlined.CheckCircle, "All clear", "Nothing needs your attention here.", Modifier.padding(top = 16.dp)) }
        }
        items(flagged, key = { it.id }) { e ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(e.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EntryAvatar(e)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        r.issues[e.id].orEmpty().forEach { issue ->
                            val color = when (issue) {
                                HealthIssue.WEAK -> Msp.extra.veryWeak
                                HealthIssue.REUSED -> Msp.extra.weak
                                HealthIssue.OLD -> Msp.extra.fair
                                HealthIssue.MISSING_2FA -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Badge(when (issue) { HealthIssue.WEAK -> "Weak"; HealthIssue.REUSED -> "Reused"; HealthIssue.OLD -> "Old"; HealthIssue.MISSING_2FA -> "No 2FA" }, color)
                        }
                    }
                }
            }
        }
    }
}
