package com.mysecurepass.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class SecurityEvent(val label: String, val alert: Boolean = false) {
    VAULT_CREATED("Vault created"),
    UNLOCK_PASSWORD("Unlocked with master password"),
    UNLOCK_BIOMETRIC("Unlocked with biometrics"),
    FAILED_UNLOCK("Failed unlock attempt", alert = true),
    LOCKOUT("Unlocking paused after repeated failures", alert = true),
    PASSWORD_CHANGED("Master password changed"),
    BIOMETRIC_ENABLED("Biometric unlock enabled"),
    BIOMETRIC_DISABLED("Biometric unlock disabled"),
    BIOMETRIC_INVALIDATED("Biometric key revoked: device biometrics changed", alert = true),
    BACKUP_EXPORTED("Encrypted backup exported"),
    BACKUP_RESTORED("Backup restored"),
    CSV_IMPORTED("Logins imported from CSV"),
    CSV_EXPORTED("Plaintext CSV exported", alert = true),
    SELF_DESTRUCT("Vault erased after too many failed attempts", alert = true),
    VAULT_DELETED("Vault deleted"),
}

@Serializable
data class LogEntry(val event: SecurityEvent, val at: Long)

/**
 * Local, append-only record of security-relevant events (no secrets, no item names).
 * Stored in app-private storage and excluded from backups, like everything else.
 */
class SecurityLog(context: Context) {
    private val prefs = context.getSharedPreferences("security_log", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(LogEntry.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    @Synchronized
    fun record(event: SecurityEvent, at: Long = System.currentTimeMillis()) {
        val next = (listOf(LogEntry(event, at)) + _entries.value).take(MAX)
        _entries.value = next
        prefs.edit().putString(KEY, json.encodeToString(serializer, next)).apply()
    }

    /** Last successful unlock before the current session. */
    fun previousUnlock(): LogEntry? =
        _entries.value.filter { it.event == SecurityEvent.UNLOCK_PASSWORD || it.event == SecurityEvent.UNLOCK_BIOMETRIC }.getOrNull(0)

    /** Failed attempts since the last successful unlock. */
    fun failuresSinceLastUnlock(): Int =
        _entries.value.takeWhile { it.event != SecurityEvent.UNLOCK_PASSWORD && it.event != SecurityEvent.UNLOCK_BIOMETRIC }
            .count { it.event == SecurityEvent.FAILED_UNLOCK }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun load(): List<LogEntry> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "log"
        const val MAX = 200
    }
}
