package com.mysecurepass.core.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PasswordHistoryItem(val password: String, val replacedAt: Long)

@Serializable
enum class EntryType { LOGIN, CARD, WIFI, NOTE }

@Serializable
data class CustomField(val label: String, val value: String, val hidden: Boolean = false)

@Serializable
data class CardData(
    val holder: String = "",
    val number: String = "",
    /** "MM/YY". */
    val expiry: String = "",
    val cvv: String = "",
    val pin: String = "",
)

@Serializable
enum class WifiSecurity { WPA, WEP, NONE }

@Serializable
data class WifiData(
    val ssid: String = "",
    val security: WifiSecurity = WifiSecurity.WPA,
    val hidden: Boolean = false,
)

/**
 * One vault item. Schema evolves additively: every field added after v1 has a default, so older
 * vaults decode unchanged and unknown future fields are ignored.
 */
@Serializable
data class VaultEntry(
    val id: String,
    val title: String,
    val username: String = "",
    /** Login password or Wi‑Fi passphrase. */
    val password: String = "",
    /** Web domains (`example.com`) or Android apps (`androidapp://com.example.app`). */
    val urls: List<String> = emptyList(),
    val notes: String = "",
    /** Base32 secret or `otpauth://` URI. */
    val totp: String = "",
    val favorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val passwordChangedAt: Long = createdAt,
    val history: List<PasswordHistoryItem> = emptyList(),
    // ---- schema 2 ----
    val type: EntryType = EntryType.LOGIN,
    val tags: List<String> = emptyList(),
    val fields: List<CustomField> = emptyList(),
    val card: CardData? = null,
    val wifi: WifiData? = null,
    /** Set when moved to trash; purged after [TRASH_RETENTION_MS]. */
    val deletedAt: Long? = null,
    /** Last time a secret from this item was copied or autofilled (for "recently used" sorting). */
    val lastUsedAt: Long = 0,
) {
    override fun toString(): String = "VaultEntry(id=$id, type=$type, title=<redacted>)"

    val isDeleted: Boolean get() = deletedAt != null

    /** Returns a copy with a new password, archiving the old one (max 10 history items). */
    fun withPassword(newPassword: String, now: Long): VaultEntry {
        if (newPassword == password) return this
        val newHistory = if (password.isEmpty()) history
        else (listOf(PasswordHistoryItem(password, now)) + history).take(MAX_HISTORY)
        return copy(password = newPassword, passwordChangedAt = now, updatedAt = now, history = newHistory)
    }

    /** Text used for search (never includes secrets). */
    fun searchText(): String = buildString {
        append(title).append(' ').append(username).append(' ')
        urls.forEach { append(it).append(' ') }
        tags.forEach { append(it).append(' ') }
        card?.let { append(it.holder).append(' ').append(Cards.brand(it.number).label) }
        wifi?.let { append(it.ssid) }
    }.lowercase()

    companion object {
        const val MAX_HISTORY = 10
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}

fun List<VaultEntry>.active(): List<VaultEntry> = filter { it.deletedAt == null }
fun List<VaultEntry>.trashed(): List<VaultEntry> = filter { it.deletedAt != null }

/** All distinct tags in use, sorted case-insensitively. */
fun List<VaultEntry>.allTags(): List<String> =
    active().flatMap { it.tags }.distinctBy { it.lowercase() }.sortedWith(String.CASE_INSENSITIVE_ORDER)

@Serializable
data class VaultData(
    val schema: Int = CURRENT_SCHEMA,
    val entries: List<VaultEntry> = emptyList(),
) {
    /** Removes trashed items older than the retention period. */
    fun purgeExpired(now: Long): VaultData {
        val kept = entries.filter { e -> e.deletedAt == null || now - e.deletedAt < VaultEntry.TRASH_RETENTION_MS }
        return if (kept.size == entries.size) this else copy(entries = kept)
    }

    companion object {
        const val CURRENT_SCHEMA = 2
    }
}

object VaultJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    fun encode(data: VaultData): ByteArray =
        json.encodeToString(VaultData.serializer(), data.copy(schema = VaultData.CURRENT_SCHEMA)).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): VaultData = json.decodeFromString(VaultData.serializer(), bytes.toString(Charsets.UTF_8))
}
