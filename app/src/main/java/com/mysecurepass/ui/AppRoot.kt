package com.mysecurepass.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import com.mysecurepass.data.VaultState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mysecurepass.container
import com.mysecurepass.core.vault.EntryType
import com.mysecurepass.ui.checker.CheckerScreen
import com.mysecurepass.ui.components.LocalActionMessenger
import com.mysecurepass.ui.components.LocalHapticsEnabled
import com.mysecurepass.ui.components.LocalMessenger
import com.mysecurepass.ui.components.rememberHaptic
import com.mysecurepass.ui.generator.GeneratorScreen
import com.mysecurepass.ui.security.AccessLogScreen
import com.mysecurepass.ui.security.HowItWorksScreen
import com.mysecurepass.ui.security.SecurityCenterScreen
import com.mysecurepass.ui.security.SelfTestScreen
import com.mysecurepass.ui.settings.SettingsScreen
import com.mysecurepass.ui.theme.Msp
import com.mysecurepass.ui.vault.EntryDetailScreen
import com.mysecurepass.ui.vault.EntryEditScreen
import com.mysecurepass.ui.vault.HealthScreen
import com.mysecurepass.ui.vault.TrashScreen
import com.mysecurepass.ui.vault.VaultTab
import kotlinx.coroutines.launch

object Routes {
    const val VAULT = "vault"
    const val GENERATE = "generate"
    const val SECURITY = "security"
    const val SETTINGS = "settings"
    const val ENTRY = "entry/{id}"
    const val EDIT = "edit/{id}?type={type}"
    const val HEALTH = "health"
    const val TRASH = "trash"
    const val CHECK = "check"
    const val SELF_TEST = "selftest"
    const val LOG = "log"
    const val HOW = "how"
    const val NEW = "new"
    fun entry(id: String) = "entry/$id"
    fun edit(id: String) = "edit/$id"
    fun new(type: EntryType) = "edit/$NEW?type=${type.name}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.GENERATE, "Generate", Icons.Outlined.Key),
    Tab(Routes.VAULT, "Vault", Icons.Outlined.Lock),
    Tab(Routes.SECURITY, "Security", Icons.Outlined.Shield),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle()

    val vaultState by c.vault.state.collectAsStateWithLifecycle()

    if (!settings.onboarded) {
        OnboardingScreen(onDone = { c.settings.update { it.copy(onboarded = true) } })
        return
    }

    // App lock: while a vault exists and is locked, NOTHING else is composed, so no content can show
    // behind the biometric prompt or during a transition. The swap is instant (no cross-fade).
    when (vaultState) {
        VaultState.Loading -> return
        VaultState.Locked -> {
            CompositionLocalProvider(LocalHapticsEnabled provides settings.haptics) { AppLockScreen() }
            return
        }
        else -> Unit
    }
    MainUi(settings.haptics)
}

/** Full-screen lock shown at launch and whenever the vault auto-locks. */
@Composable
private fun AppLockScreen() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        com.mysecurepass.ui.vault.UnlockScreen(branded = true)
    }
}


@Composable
private fun MainUi(haptics: Boolean) {
    val c = LocalContext.current.container
    // Gentle fade-in after unlock (content appears; it never fades *out* into the lock screen).
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { alpha.animateTo(1f, tween(220)) }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    val messenger: (String) -> Unit = remember {
        { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    }
    val actionMessenger: (String, String, () -> Unit) -> Unit = remember {
        { msg, action, onAction ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(msg, actionLabel = action, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) onAction()
            }
        }
    }

    fun goTab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(
        LocalMessenger provides messenger,
        LocalActionMessenger provides actionMessenger,
        LocalHapticsEnabled provides haptics,
    ) {
        Scaffold(
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(data.visuals.message, Modifier.padding(vertical = 14.dp).weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                            data.visuals.actionLabel?.let { label ->
                                TextButton(onClick = { data.performAction() }) { Text(label, color = MaterialTheme.colorScheme.inversePrimary) }
                            } ?: Spacer(Modifier.padding(end = 12.dp))
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                    BottomBar(nav, route)
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = Routes.GENERATE,
                modifier = Modifier.fillMaxSize().padding(padding),
                enterTransition = { fadeIn(tween(180)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) },
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable(Routes.VAULT) {
                    VaultTab(
                        onOpen = { nav.navigate(Routes.entry(it)) },
                        onAdd = { nav.navigate(Routes.new(it)) },
                        onHealth = { nav.navigate(Routes.HEALTH) },
                        onTrash = { nav.navigate(Routes.TRASH) },
                    )
                }
                composable(Routes.GENERATE) {
                    GeneratorScreen(onSaveToVault = { goTab(Routes.VAULT); nav.navigate(Routes.new(EntryType.LOGIN)) })
                }
                composable(Routes.SECURITY) {
                    SecurityCenterScreen(
                        onOpenVault = { goTab(Routes.VAULT) },
                        onHealth = { nav.navigate(Routes.HEALTH) },
                        onChecker = { nav.navigate(Routes.CHECK) },
                        onSelfTest = { nav.navigate(Routes.SELF_TEST) },
                        onLog = { nav.navigate(Routes.LOG) },
                        onHowItWorks = { nav.navigate(Routes.HOW) },
                    )
                }
                composable(Routes.SETTINGS) { SettingsScreen(onAccessLog = { nav.navigate(Routes.LOG) }) }

                val slideIn = slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200))
                val slideOut = slideOutHorizontally(tween(220)) { it / 6 } + fadeOut(tween(160))
                composable(Routes.ENTRY, enterTransition = { slideIn }, popExitTransition = { slideOut }) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    EntryDetailScreen(id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.edit(id)) })
                }
                composable(
                    Routes.EDIT,
                    arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = EntryType.LOGIN.name }),
                    enterTransition = { slideInVertically(tween(260)) { it / 8 } + fadeIn(tween(200)) },
                    popExitTransition = { slideOutVertically(tween(220)) { it / 8 } + fadeOut(tween(160)) },
                ) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val type = runCatching { EntryType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(EntryType.LOGIN)
                    EntryEditScreen(id.takeUnless { it == Routes.NEW }, type, onDone = { nav.popBackStack() })
                }
                composable(Routes.HEALTH, enterTransition = { slideIn }, popExitTransition = { slideOut }) {
                    HealthScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(Routes.entry(it)) })
                }
                composable(Routes.TRASH, enterTransition = { slideIn }, popExitTransition = { slideOut }) { TrashScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.CHECK, enterTransition = { slideIn }, popExitTransition = { slideOut }) { CheckerScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.SELF_TEST, enterTransition = { slideIn }, popExitTransition = { slideOut }) { SelfTestScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.LOG, enterTransition = { slideIn }, popExitTransition = { slideOut }) { AccessLogScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.HOW, enterTransition = { slideIn }, popExitTransition = { slideOut }) { HowItWorksScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    val haptic = rememberHaptic()
    val c = LocalContext.current.container
    val log by c.securityLog.entries.collectAsStateWithLifecycle()
    val securityAlert = remember(log) { c.securityLog.failuresSinceLastUnlock() > 0 }
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Msp.extra.hairline))
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(68.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = route == tab.route
                val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab")
                val pill by animateColorAsState(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.background, label = "pill")
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) {
                            if (!selected) {
                                haptic(HapticFeedbackType.TextHandleMove)
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Box(Modifier.clip(CircleShape).background(pill).padding(horizontal = 18.dp, vertical = 4.dp)) {
                            Icon(tab.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        if (tab.route == Routes.SECURITY && securityAlert) {
                            Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 12.dp).size(8.dp).clip(CircleShape).background(Msp.extra.veryWeak))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
