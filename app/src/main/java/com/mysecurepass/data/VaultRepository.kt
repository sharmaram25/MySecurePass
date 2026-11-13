package com.mysecurepass.data

import android.content.Context
import android.util.AtomicFile
import com.mysecurepass.core.vault.CorruptVaultException
import com.mysecurepass.core.vault.CsvExport
import com.mysecurepass.core.vault.CsvImport
import com.mysecurepass.core.vault.VaultCrypto
import com.mysecurepass.core.vault.VaultData
import com.mysecurepass.core.vault.VaultEntry
import com.mysecurepass.core.vault.VaultHeader
import com.mysecurepass.core.vault.VaultJson
import com.mysecurepass.core.vault.WrongPasswordException
import com.mysecurepass.security.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.crypto.Cipher

sealed interface VaultState {
    data object Loading : VaultState
    data object Empty : VaultState
    data object Locked : VaultState
    data class Unlocked(val entries: List<VaultEntry>) : VaultState
}

sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPassword(val lockoutUntil: Long, val attemptsBeforeWipe: Int?) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
    data object Wiped : UnlockResult
    data class Error(val message: String) : UnlockResult
}

/**
 * Owns the encrypted vault. On disk the file is:
 *
 *   Keystore-AES-GCM( VaultCrypto file )   — device layer, hardware-bound
 *
 * and the inner VaultCrypto file is Argon2id + AES-256-GCM protected by the master password
 * (the same bytes are used for portable encrypted exports).
 *
 * While unlocked, the 256-bit vault key and decrypted entries live only in this process's memory;
 * [lock] wipes the key. Nothing is ever written in plaintext.
 */
class VaultRepository(
    context: Context,
    private val crypto: VaultCrypto,
    private val keystore: KeystoreManager,
    private val settings: SettingsRepository,
    private val log: SecurityLog,
) {
    private class Session(val header: VaultHeader, val key: ByteArray, var data: VaultData)

    private val vaultFile = AtomicFile(File(context.filesDir, "vault.msp"))
    private val bioFile = AtomicFile(File(context.filesDir, "vault.bio"))
    private val mutex = Mutex()
    private var session: Session? = null

    private val _state = MutableStateFlow<VaultState>(VaultState.Loading)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    init {
        _state.value = if (vaultFile.baseFile.exists()) VaultState.Locked else VaultState.Empty
        _biometricEnabled.value = bioFile.baseFile.exists()
    }

    val isUnlocked: Boolean get() = session != null

    fun entries(): List<VaultEntry> = session?.data?.entries.orEmpty()

    // ---- Lifecycle ----------------------------------------------------------------------

    suspend fun create(password: CharArray) = mutex.withLock {
        withContext(Dispatchers.Default) {
            val (header, key) = crypto.create(password)
            password.fill('\u0000')
            val data = VaultData()
            writeVault(header, key, data)
            open(Session(header, key, data))
            settings.resetFailures()
            log.record(SecurityEvent.VAULT_CREATED)
        }
    }

    suspend fun unlock(password: CharArray): UnlockResult = mutex.withLock {
        val now = System.currentTimeMillis()
        if (settings.lockoutUntil() > now) {
            password.fill('\u0000')
            return UnlockResult.LockedOut(settings.lockoutUntil())
        }
        withContext(Dispatchers.Default) {
            try {
                val inner = readInner()
                val (header, key) = crypto.unlock(inner, password)
                val data = VaultJson.decode(crypto.decrypt(inner, key))
                openAndPurge(Session(header, key, data))
                settings.resetFailures()
                log.record(SecurityEvent.UNLOCK_PASSWORD)
                UnlockResult.Success
            } catch (e: WrongPasswordException) {
                onFailedAttempt()
            } catch (e: CorruptVaultException) {
                UnlockResult.Error(e.message ?: "Vault is damaged")
            } catch (e: Exception) {
                UnlockResult.Error("Couldn't open the vault on this device")
            } finally {
                password.fill('\u0000')
            }
        }
    }

    private fun onFailedAttempt(): UnlockResult {
        val now = System.currentTimeMillis()
        val until = settings.recordFailure(now)
        log.record(SecurityEvent.FAILED_UNLOCK)
        val attempts = settings.failedAttempts()
        if (settings.settings.value.selfDestruct) {
            val left = AppSettings.SELF_DESTRUCT_ATTEMPTS - attempts
            if (left <= 0) {
                wipeFiles()
                log.record(SecurityEvent.SELF_DESTRUCT)
                return UnlockResult.Wiped
            }
            if (until > now) log.record(SecurityEvent.LOCKOUT)
            return UnlockResult.WrongPassword(until, left)
        }
        if (until > now) log.record(SecurityEvent.LOCKOUT)
        return UnlockResult.WrongPassword(until, null)
    }

    /** Re-authentication for sensitive actions (export, reveal-all). Does not count toward lockout. */
    suspend fun verifyPassword(password: CharArray): Boolean = withContext(Dispatchers.Default) {
        try {
            crypto.unlock(readInner(), password).second.fill(0)
            true
        } catch (e: Exception) {
            false
        } finally {
            password.fill('\u0000')
        }
    }

    fun lock() {
        session?.key?.fill(0)
        session = null
        if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
    }

    // ---- Biometrics ---------------------------------------------------------------------

    /** Cipher to pass to BiometricPrompt for unlocking, or null if biometric unlock is unavailable. */
    fun biometricUnlockCipher(): Cipher? {
        val blob = runCatching { bioFile.readFully() }.getOrNull() ?: return null
        if (blob.size <= KeystoreManager.IV_BYTES) return null
        val cipher = keystore.biometricDecryptCipher(blob.copyOfRange(0, KeystoreManager.IV_BYTES))
        if (cipher == null) {
            log.record(SecurityEvent.BIOMETRIC_INVALIDATED)
            disableBiometric(logEvent = false)
        }
        return cipher
    }

    suspend fun unlockWithBiometric(cipher: Cipher): UnlockResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            try {
                val blob = bioFile.readFully()
                val key = cipher.doFinal(blob, KeystoreManager.IV_BYTES, blob.size - KeystoreManager.IV_BYTES)
                val inner = readInner()
                val header = crypto.readHeader(inner)
                val data = VaultJson.decode(crypto.decrypt(inner, key))
                openAndPurge(Session(header, key, data))
                settings.resetFailures()
                log.record(SecurityEvent.UNLOCK_BIOMETRIC)
                UnlockResult.Success
            } catch (e: Exception) {
                UnlockResult.Error("Biometric unlock failed. Use your master password.")
            }
        }
    }

    fun biometricEnrollCipher(): Cipher = keystore.biometricEncryptCipher()

    suspend fun enableBiometric(authorizedCipher: Cipher) = mutex.withLock {
        val s = session ?: error("Vault is locked")
        withContext(Dispatchers.IO) {
            val wrapped = authorizedCipher.doFinal(s.key)
            writeAtomic(bioFile, authorizedCipher.iv + wrapped)
            _biometricEnabled.value = true
            log.record(SecurityEvent.BIOMETRIC_ENABLED)
        }
    }

    fun disableBiometric(logEvent: Boolean = true) {
        keystore.deleteBiometricKey()
        bioFile.delete()
        _biometricEnabled.value = false
        if (logEvent) log.record(SecurityEvent.BIOMETRIC_DISABLED)
    }

    // ---- Entries ------------------------------------------------------------------------

    suspend fun upsert(entry: VaultEntry) = mutate { data ->
        val exists = data.entries.any { it.id == entry.id }
        data.copy(entries = if (exists) data.entries.map { if (it.id == entry.id) entry else it } else data.entries + entry)
    }

    /** Moves an item to the trash (restorable for 30 days). */
    suspend fun trash(id: String) = mutate { data ->
        val now = System.currentTimeMillis()
        data.copy(entries = data.entries.map { if (it.id == id) it.copy(deletedAt = now) else it })
    }

    suspend fun restore(id: String) = mutate { data ->
        data.copy(entries = data.entries.map { if (it.id == id) it.copy(deletedAt = null) else it })
    }

    /** Permanently deletes an item. */
    suspend fun purge(id: String) = mutate { data -> data.copy(entries = data.entries.filterNot { it.id == id }) }

    suspend fun emptyTrash() = mutate { data -> data.copy(entries = data.entries.filter { it.deletedAt == null }) }

    /** Records that a secret was used (copied/autofilled) for "recently used" sorting. */
    suspend fun markUsed(id: String) = mutate { data ->
        val now = System.currentTimeMillis()
        data.copy(entries = data.entries.map { if (it.id == id) it.copy(lastUsedAt = now) else it })
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- Account ------------------------------------------------------------------------

    suspend fun changePassword(current: CharArray, new: CharArray): Boolean = mutex.withLock {
        val s = session ?: return@withLock false
        withContext(Dispatchers.Default) {
            try {
                crypto.unlock(readInner(), current).second.fill(0)
                val header = crypto.rewrap(s.key, new)
                writeVault(header, s.key, s.data)
                session = Session(header, s.key, s.data)
                log.record(SecurityEvent.PASSWORD_CHANGED)
                true
            } catch (e: WrongPasswordException) {
                false
            } finally {
                current.fill('\u0000'); new.fill('\u0000')
            }
        }
    }

    /** Portable encrypted backup (protected by the master password, not bound to this device). */
    suspend fun exportBackup(): ByteArray = mutex.withLock {
        val s = session ?: error("Vault is locked")
        withContext(Dispatchers.Default) { crypto.encrypt(s.header, s.key, VaultJson.encode(s.data)) }
    }

    fun recordBackupExported() {
        val now = System.currentTimeMillis()
        settings.update { it.copy(lastBackupAt = now) }
        log.record(SecurityEvent.BACKUP_EXPORTED, now)
    }

    /** Plaintext CSV of logins. Callers must re-authenticate the user first. */
    suspend fun exportCsv(): String = mutex.withLock {
        val s = session ?: error("Vault is locked")
        withContext(Dispatchers.Default) { CsvExport.export(s.data.entries) }.also { log.record(SecurityEvent.CSV_EXPORTED) }
    }

    /** Merges entries from an encrypted backup. Returns the number of entries added. */
    suspend fun importBackup(bytes: ByteArray, password: CharArray): Int {
        val imported = withContext(Dispatchers.Default) {
            try {
                val (_, key) = crypto.unlock(bytes, password)
                try { VaultJson.decode(crypto.decrypt(bytes, key)).entries } finally { key.fill(0) }
            } finally {
                password.fill('\u0000')
            }
        }
        return merge(imported).also { log.record(SecurityEvent.BACKUP_RESTORED) }
    }

    /** Imports a CSV export from another password manager. Returns (added, skipped). */
    suspend fun importCsv(text: String): Pair<Int, Int> {
        val result = withContext(Dispatchers.Default) {
            CsvImport.parse(text, System.currentTimeMillis(), ::newId)
        }
        return (merge(result.entries) to result.skipped).also { log.record(SecurityEvent.CSV_IMPORTED) }
    }

    private suspend fun merge(incoming: List<VaultEntry>): Int {
        var added = 0
        mutate { data ->
            val existingIds = data.entries.map { it.id }.toHashSet()
            val fingerprints = data.entries.map { Triple(it.title, it.username, it.password) }.toHashSet()
            val toAdd = incoming.mapNotNull { e ->
                if (Triple(e.title, e.username, e.password) in fingerprints) null
                else if (e.id in existingIds) e.copy(id = newId()) else e
            }
            added = toAdd.size
            data.copy(entries = data.entries + toAdd)
        }
        return added
    }

    suspend fun wipe() = mutex.withLock {
        withContext(Dispatchers.IO) {
            wipeFiles()
            log.record(SecurityEvent.VAULT_DELETED)
        }
    }

    private fun wipeFiles() {
        session?.key?.fill(0)
        session = null
        vaultFile.delete()
        bioFile.delete()
        keystore.deleteAll()
        settings.resetFailures()
        _biometricEnabled.value = false
        _state.value = VaultState.Empty
    }

    // ---- Internals ----------------------------------------------------------------------

    private suspend fun mutate(transform: (VaultData) -> VaultData) = mutex.withLock {
        val s = session ?: error("Vault is locked")
        withContext(Dispatchers.Default) {
            val newData = transform(s.data)
            writeVault(s.header, s.key, newData)
            s.data = newData
            _state.value = VaultState.Unlocked(newData.entries)
        }
    }

    private fun openAndPurge(s: Session) {
        val purged = s.data.purgeExpired(System.currentTimeMillis())
        if (purged !== s.data) {
            writeVault(s.header, s.key, purged)
            s.data = purged
        }
        open(s)
    }

    private fun open(s: Session) {
        session?.key?.fill(0)
        session = s
        _state.value = VaultState.Unlocked(s.data.entries)
    }

    private fun readInner(): ByteArray = keystore.deviceDecrypt(vaultFile.readFully())

    private fun writeVault(header: VaultHeader, key: ByteArray, data: VaultData) {
        val plaintext = VaultJson.encode(data)
        try {
            val inner = crypto.encrypt(header, key, plaintext)
            writeAtomic(vaultFile, keystore.deviceEncrypt(inner))
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeAtomic(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
