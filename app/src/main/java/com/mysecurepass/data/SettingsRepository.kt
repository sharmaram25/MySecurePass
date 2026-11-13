package com.mysecurepass.data

import android.content.Context
import com.mysecurepass.core.generator.GeneratorSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class VaultSort(val label: String) { NAME("Name"), RECENTLY_USED("Recently used"), RECENTLY_MODIFIED("Recently modified") }

/** Non-secret preferences. Nothing here is sensitive; secrets live only in the encrypted vault. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val blockScreenshots: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val autoLockSeconds: Int = 60,
    val lockOnScreenOff: Boolean = true,
    val haptics: Boolean = true,
    val rememberGenerator: Boolean = true,
    /** Erase the vault after [SELF_DESTRUCT_ATTEMPTS] consecutive failed unlocks. */
    val selfDestruct: Boolean = false,
    val onboarded: Boolean = false,
    val lastBackupAt: Long = 0,
    val vaultSort: VaultSort = VaultSort.NAME,
) {
    companion object { const val SELF_DESTRUCT_ATTEMPTS = 10 }
}

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _generator = MutableStateFlow(loadGenerator())
    val generator: StateFlow<GeneratorSettings> = _generator.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        save(_settings.value)
        if (!_settings.value.rememberGenerator) prefs.edit().remove(KEY_GENERATOR).apply()
    }

    fun updateGenerator(settings: GeneratorSettings) {
        _generator.value = settings
        if (_settings.value.rememberGenerator) {
            prefs.edit().putString(KEY_GENERATOR, json.encodeToString(GeneratorSettings.serializer(), settings)).apply()
        }
    }

    private fun loadGenerator(): GeneratorSettings =
        prefs.getString(KEY_GENERATOR, null)
            ?.let { runCatching { json.decodeFromString(GeneratorSettings.serializer(), it) }.getOrNull() }
            ?: GeneratorSettings()

    private fun load() = AppSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = prefs.getBoolean("dynamicColor", false),
        blockScreenshots = prefs.getBoolean("blockScreenshots", true),
        clipboardClearSeconds = prefs.getInt("clipboardClear", 30),
        autoLockSeconds = prefs.getInt("autoLock", 60),
        lockOnScreenOff = prefs.getBoolean("lockOnScreenOff", true),
        haptics = prefs.getBoolean("haptics", true),
        rememberGenerator = prefs.getBoolean("rememberGenerator", true),
        selfDestruct = prefs.getBoolean("selfDestruct", false),
        onboarded = prefs.getBoolean("onboarded", false),
        lastBackupAt = prefs.getLong("lastBackupAt", 0),
        vaultSort = runCatching { VaultSort.valueOf(prefs.getString("vaultSort", null) ?: "NAME") }.getOrDefault(VaultSort.NAME),
    )

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("blockScreenshots", s.blockScreenshots)
            .putInt("clipboardClear", s.clipboardClearSeconds)
            .putInt("autoLock", s.autoLockSeconds)
            .putBoolean("lockOnScreenOff", s.lockOnScreenOff)
            .putBoolean("haptics", s.haptics)
            .putBoolean("rememberGenerator", s.rememberGenerator)
            .putBoolean("selfDestruct", s.selfDestruct)
            .putBoolean("onboarded", s.onboarded)
            .putLong("lastBackupAt", s.lastBackupAt)
            .putString("vaultSort", s.vaultSort.name)
            .apply()
    }

    // ---- Unlock throttling (not secret; slows down guessing through the UI) ----

    fun failedAttempts(): Int = prefs.getInt("failedAttempts", 0)
    fun lockoutUntil(): Long = prefs.getLong("lockoutUntil", 0)

    fun recordFailure(now: Long): Long {
        val n = failedAttempts() + 1
        // 5 free attempts, then 30s, 60s, 120s ... capped at 15 minutes.
        val delayMs = if (n < 5) 0L else (30_000L shl (n - 5).coerceAtMost(5)).coerceAtMost(15 * 60_000L)
        prefs.edit().putInt("failedAttempts", n).putLong("lockoutUntil", now + delayMs).apply()
        return now + delayMs
    }

    fun resetFailures() {
        prefs.edit().remove("failedAttempts").remove("lockoutUntil").apply()
    }

    private companion object {
        const val KEY_GENERATOR = "generator"
    }
}
