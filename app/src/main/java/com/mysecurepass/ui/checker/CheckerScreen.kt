package com.mysecurepass.ui.checker

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mysecurepass.container
import com.mysecurepass.core.strength.Rating
import com.mysecurepass.data.Analysis
import com.mysecurepass.ui.components.Badge
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.EmptyState
import com.mysecurepass.ui.components.Hairline
import com.mysecurepass.ui.components.Panel
import com.mysecurepass.ui.components.SecretField
import com.mysecurepass.ui.components.SectionLabel
import com.mysecurepass.ui.components.StrengthMeter
import com.mysecurepass.ui.components.ratingColor
import com.mysecurepass.ui.theme.MonoFamily
import com.mysecurepass.ui.theme.Msp
import kotlinx.coroutines.delay

@Composable
fun CheckerScreen(onBack: () -> Unit) {
    val analyzer = LocalContext.current.container.analyzer
    // Plain remember (not rememberSaveable): the password must never be written to saved state.
    var input by remember { mutableStateOf("") }
    var analysis by remember { mutableStateOf<Analysis?>(null) }

    LaunchedEffect(Unit) { analyzer.warmUp() }
    LaunchedEffect(input) {
        if (input.isEmpty()) { analysis = null; return@LaunchedEffect }
        delay(90) // debounce keystrokes
        analysis = analyzer.analyze(input)
    }
    DisposableEffect(Unit) { onDispose { input = ""; analysis = null } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ContentWidth {
            com.mysecurepass.ui.components.SubScreenBar("Password checker", onBack)
            Text("How strong is a password you already use?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                SecretField(input, { input = it }, "Password to test")
            }

            AnimatedContent(analysis == null, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "checker") { empty ->
                if (empty) {
                    EmptyState(
                        Icons.Outlined.Shield,
                        "Private by design",
                        "Analysis runs entirely on this device against built-in lists of common passwords, names, words, dates and keyboard patterns. Nothing is sent or stored.",
                        Modifier.padding(top = 24.dp),
                    )
                } else analysis?.let { AnalysisView(it) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalysisView(a: Analysis) {
    val rating = Rating.fromScore(a.score)
    Column {
        Spacer(Modifier.height(16.dp))
        Panel(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(rating.label, style = MaterialTheme.typography.headlineSmall, color = ratingColor(rating), modifier = Modifier.weight(1f))
                Text(
                    "10^${"%.1f".format(a.guessesLog10)} guesses",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            StrengthMeter(rating)
            if (a.warning != null) {
                Spacer(Modifier.height(16.dp))
                Text(a.warning, style = MaterialTheme.typography.bodyMedium, color = Msp.extra.weak)
            }
        }

        SectionLabel("Time to crack")
        Panel(Modifier.padding(horizontal = 16.dp)) {
            a.crackTimes.forEachIndexed { i, (scenario, time) ->
                if (i > 0) Hairline()
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(scenario.label, style = MaterialTheme.typography.bodyMedium)
                        Text(scenario.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(time, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.width(120.dp))
                }
            }
        }

        AnimatedVisibility(a.suggestions.isNotEmpty()) {
            Column {
                SectionLabel("Suggestions")
                Panel(Modifier.padding(horizontal = 16.dp)) {
                    a.suggestions.forEach { s ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("•  ", color = MaterialTheme.colorScheme.primary)
                            Text(s, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        val weakParts = a.parts.filter { it.kind != "Random" }
        if (weakParts.isNotEmpty()) {
            SectionLabel("Patterns found")
            Panel(Modifier.padding(horizontal = 16.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weakParts.forEach { Badge(it.kind, Msp.extra.weak) }
                }
            }
        }
        Text(
            "Estimates use zxcvbn, the realistic model used by leading password managers. A password you've reused elsewhere is weak no matter what it scores.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        )
    }
}
