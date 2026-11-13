package com.mysecurepass.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mysecurepass.container
import com.mysecurepass.core.vault.Matching
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.data.ThemeMode
import com.mysecurepass.data.VaultState
import com.mysecurepass.ui.components.ContentWidth
import com.mysecurepass.ui.components.PillButton
import com.mysecurepass.ui.components.ScreenTitle
import com.mysecurepass.ui.components.fieldColors
import com.mysecurepass.ui.theme.MspTheme
import com.mysecurepass.ui.vault.EntryAvatar
import com.mysecurepass.ui.vault.SetupScreen
import com.mysecurepass.ui.vault.UnlockScreen
import com.mysecurepass.ui.vault.subtitleOf
import kotlinx.coroutines.launch

/** Unlock-and-pick (fill) and confirm-and-store (save) flows launched by [MspAutofillService]. */
class AutofillActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val usernameIds = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_USER_IDS, AutofillId::class.java).orEmpty()
        val passwordIds = IntentCompat.getParcelableArrayListExtra(intent, EXTRA_PASS_IDS, AutofillId::class.java).orEmpty()
        val fields = ParsedFields(
            usernameIds, passwordIds,
            intent.getStringExtra(EXTRA_PACKAGE).orEmpty(), intent.getStringExtra(EXTRA_DOMAIN),
            intent.getStringExtra(EXTRA_USERNAME), intent.getStringExtra(EXTRA_PASSWORD),
        )
        val saveMode = intent.getBooleanExtra(EXTRA_SAVE, false)

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            MspTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.safeDrawingPadding()) {
                        val state by container.vault.state.collectAsStateWithLifecycle()
                        when (val s = state) {
                            VaultState.Empty -> SetupScreen()
                            is VaultState.Unlocked -> if (saveMode) SaveContent(fields) else PickContent(s.entries.filter { !it.isDeleted && it.type == com.mysecurepass.core.vault.EntryType.LOGIN }, fields)
                            else -> UnlockScreen(subtitle = "Unlock to fill ${fields.webDomain ?: appLabel(fields.packageName)}")
                        }
                    }
                }
            }
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun finishWith(entry: VaultEntry, fields: ParsedFields) {
        container.appScope.launch { runCatching { container.vault.markUsed(entry.id) } }
        val dataset = MspAutofillService.datasetFor(this, entry, fields)
        if (dataset == null) { setResult(Activity.RESULT_CANCELED); finish(); return }
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
        finish()
    }

    @Composable
    private fun PickContent(entries: List<VaultEntry>, fields: ParsedFields) {
        var query by remember { mutableStateOf("") }
        val matches = remember(entries) { entries.filter { Matching.matches(it, fields.packageName, fields.webDomain) } }
        val shown = remember(entries, query) {
            val q = query.trim().lowercase()
            if (q.isEmpty()) (matches + entries.filterNot { it in matches }.sortedBy { it.title.lowercase() })
            else entries.filter { it.title.lowercase().contains(q) || it.username.lowercase().contains(q) || it.urls.any { u -> u.contains(q) } }
        }
        ContentWidth {
            ScreenTitle("Choose a login", "For ${fields.webDomain ?: appLabel(fields.packageName)}")
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = CircleShape, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().clickable { confirmFill(e, fields) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EntryAvatar(e)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(subtitleOf(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (e in matches) Text("Match", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    /** Filling an entry into an app it wasn't saved for: link it so next time it's offered directly. */
    private fun confirmFill(entry: VaultEntry, fields: ParsedFields) {
        val alreadyMatches = Matching.matches(entry, fields.packageName, fields.webDomain)
        if (!alreadyMatches) {
            val link = fields.webDomain?.let(Matching::normalizeHost) ?: (Matching.ANDROID_APP_SCHEME + fields.packageName)
            container.appScope.launch {
                container.vault.upsert(entry.copy(urls = (entry.urls + link).distinct(), updatedAt = System.currentTimeMillis()))
            }
        }
        finishWith(entry, fields)
    }

    @Composable
    private fun SaveContent(fields: ParsedFields) {
        val target = fields.webDomain?.let(Matching::normalizeHost) ?: appLabel(fields.packageName)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
            ContentWidth {
                ScreenTitle("Save login?", "MySecurePass will encrypt it in your vault")
                var title by remember { mutableStateOf(target) }
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, colors = fieldColors())
                    Spacer(Modifier.height(12.dp))
                    if (!fields.usernameValue.isNullOrBlank()) {
                        Text("Username", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fields.usernameValue, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("Password", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("•".repeat((fields.passwordValue?.length ?: 0).coerceAtMost(16)), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(24.dp))
                    PillButton("Save", onClick = {
                        val now = System.currentTimeMillis()
                        val existing = container.vault.entries().filter { !it.isDeleted }.firstOrNull {
                            Matching.matches(it, fields.packageName, fields.webDomain) && it.username == fields.usernameValue.orEmpty()
                        }
                        val link = fields.webDomain?.let(Matching::normalizeHost) ?: (Matching.ANDROID_APP_SCHEME + fields.packageName)
                        val entry = existing?.withPassword(fields.passwordValue.orEmpty(), now)
                            ?: VaultEntry(
                                id = container.vault.newId(), title = title.ifBlank { target }, username = fields.usernameValue.orEmpty(),
                                password = fields.passwordValue.orEmpty(), urls = listOf(link), createdAt = now, updatedAt = now, passwordChangedAt = now,
                            )
                        container.appScope.launch { container.vault.upsert(entry) }
                        setResult(Activity.RESULT_OK); finish()
                    }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    PillButton("Not now", onClick = { setResult(Activity.RESULT_CANCELED); finish() }, filled = false, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    companion object {
        private const val EXTRA_USER_IDS = "u_ids"
        private const val EXTRA_PASS_IDS = "p_ids"
        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_DOMAIN = "domain"
        private const val EXTRA_USERNAME = "username"
        private const val EXTRA_PASSWORD = "password"
        private const val EXTRA_SAVE = "save"

        private fun base(context: Context, fields: ParsedFields) = Intent(context, AutofillActivity::class.java).apply {
            putParcelableArrayListExtra(EXTRA_USER_IDS, ArrayList(fields.usernameIds))
            putParcelableArrayListExtra(EXTRA_PASS_IDS, ArrayList(fields.passwordIds))
            putExtra(EXTRA_PACKAGE, fields.packageName)
            putExtra(EXTRA_DOMAIN, fields.webDomain)
        }

        fun fillIntent(context: Context, fields: ParsedFields) = base(context, fields)

        fun saveIntent(context: Context, fields: ParsedFields) = base(context, fields).apply {
            putExtra(EXTRA_SAVE, true)
            putExtra(EXTRA_USERNAME, fields.usernameValue)
            putExtra(EXTRA_PASSWORD, fields.passwordValue)
        }

    }
}
