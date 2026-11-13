package com.mysecurepass.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mysecurepass.ui.components.PillButton
import kotlinx.coroutines.launch

private data class Page(val icon: ImageVector, val title: String, val body: String, val points: List<String>)

private val pages = listOf(
    Page(
        Icons.Outlined.CloudOff, "Private by design",
        "MySecurePass works entirely on your phone.",
        listOf("No internet permission: it physically can't send data", "No account, sign-up or tracking", "No ads, analytics or cloud"),
    ),
    Page(
        Icons.Outlined.VerifiedUser, "Protected in layers",
        "Industry-standard cryptography, sealed to your phone's secure hardware.",
        listOf("Argon2id key stretching with a unique salt", "AES‑256‑GCM authenticated encryption", "Hardware Keystore and biometric unlock"),
    ),
    Page(
        Icons.Outlined.Key, "You hold the only key",
        "Your master password is never stored anywhere.",
        listOf("Nobody can reset it, not even us", "Export an encrypted backup to stay safe", "Verify the encryption yourself in the Security tab"),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val page = pages[index]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 420.dp).padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(page.icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.height(32.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(28.dp))
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        page.points.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                Spacer(Modifier.width(14.dp))
                                Text(p, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val selected = i == pager.currentPage
                val w by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest, label = "dotColor")
                Box(Modifier.padding(horizontal = 4.dp).height(8.dp).width(w).clip(CircleShape).background(color))
            }
        }
        PillButton(
            if (last) "Get started" else "Next",
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}
