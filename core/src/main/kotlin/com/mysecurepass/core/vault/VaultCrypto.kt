package com.mysecurepass.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Key-derivation parameters, stored in the vault header so they can be raised later. */
class KdfParams(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: ByteArray,
) {
    companion object {
        /**
         * Argon2id, 64 MiB, 3 passes, 1 lane. Above the OWASP 2024 minimum (19 MiB / 2 passes) while
         * still completing in roughly 0.5–2 s on low-end phones.
         */
        fun recommended(random: SecureRandom = SecureRandom()) =
            KdfParams(memoryKiB = 64 * 1024, iterations = 3, parallelism = 1, salt = ByteArray(16).also(random::nextBytes))
    }
}

/** Password-based key derivation (Argon2id in production). Must return a 32-byte key. */
fun interface Kdf {
    fun derive(password: ByteArray, params: KdfParams): ByteArray
}

class WrongPasswordException : Exception("Wrong master password")
class CorruptVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parsed vault header. Layout (big-endian):
 *
 * ```
 * "MSPV" | u8 version | u8 kdf(1=argon2id) | i32 memKiB | i32 iterations | u8 parallelism
 *        | u8 saltLen | salt | 12B wrapIv | u16 wrappedLen | wrappedKey | 12B dataIv | ciphertext
 * ```
 *
 * The random 256-bit vault key is wrapped with AES-256-GCM under the Argon2id-derived key (AAD =
 * header prefix up to the salt). Vault contents are encrypted with AES-256-GCM under the vault key
 * (AAD = the entire header before `dataIv`), so any tampering with the header is detected.
 * Changing the master password only re-wraps the vault key.
 */
class VaultHeader internal constructor(
    val kdf: KdfParams,
    val wrapIv: ByteArray,
    val wrappedKey: ByteArray,
) {
    internal fun kdfPrefix(): ByteArray = ByteArrayOutputStream().also { bos ->
        DataOutputStream(bos).run {
            write(MAGIC)
            writeByte(FORMAT_VERSION)
            writeByte(KDF_ARGON2ID)
            writeInt(kdf.memoryKiB)
            writeInt(kdf.iterations)
            writeByte(kdf.parallelism)
            writeByte(kdf.salt.size)
            write(kdf.salt)
        }
    }.toByteArray()

    internal fun encode(): ByteArray = ByteArrayOutputStream().also { bos ->
        DataOutputStream(bos).run {
            write(kdfPrefix())
            write(wrapIv)
            writeShort(wrappedKey.size)
            write(wrappedKey)
        }
    }.toByteArray()

    companion object {
        internal val MAGIC = byteArrayOf('M'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 'V'.code.toByte())
        internal const val FORMAT_VERSION = 1
        internal const val KDF_ARGON2ID = 1
    }
}

class VaultCrypto(
    private val kdf: Kdf,
    private val random: SecureRandom = SecureRandom(),
) {
    /** Creates a new vault key and header protected by [password]. Caller owns (and must wipe) the key. */
    fun create(password: CharArray, params: KdfParams = KdfParams.recommended(random)): Pair<VaultHeader, ByteArray> {
        val vaultKey = ByteArray(KEY_BYTES).also(random::nextBytes)
        return wrap(vaultKey, password, params) to vaultKey
    }

    /** Re-wraps an existing vault key under a new password (and fresh salt). */
    fun rewrap(vaultKey: ByteArray, newPassword: CharArray, params: KdfParams = KdfParams.recommended(random)): VaultHeader =
        wrap(vaultKey, newPassword, params)

    private fun wrap(vaultKey: ByteArray, password: CharArray, params: KdfParams): VaultHeader {
        validate(params)
        val placeholder = VaultHeader(params, ByteArray(0), ByteArray(0))
        val kek = derive(password, params)
        try {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val wrapped = gcm(Cipher.ENCRYPT_MODE, kek, iv, placeholder.kdfPrefix(), vaultKey)
            return VaultHeader(params, iv, wrapped)
        } finally {
            kek.fill(0)
        }
    }

    /** Reads only the header of a vault file (cheap; no key derivation). */
    fun readHeader(file: ByteArray): VaultHeader = parse(file).first

    /** Derives the key from [password] and unwraps the vault key. Throws [WrongPasswordException]. */
    fun unlock(file: ByteArray, password: CharArray): Pair<VaultHeader, ByteArray> {
        val header = readHeader(file)
        val kek = derive(password, header.kdf)
        try {
            val vaultKey = try {
                gcm(Cipher.DECRYPT_MODE, kek, header.wrapIv, header.kdfPrefix(), header.wrappedKey)
            } catch (e: AEADBadTagException) {
                throw WrongPasswordException()
            }
            if (vaultKey.size != KEY_BYTES) throw CorruptVaultException("Invalid key length")
            return header to vaultKey
        } finally {
            kek.fill(0)
        }
    }

    /** Decrypts the vault payload with an already-unwrapped key. */
    fun decrypt(file: ByteArray, vaultKey: ByteArray): ByteArray {
        val (header, offset) = parse(file)
        if (file.size - offset < IV_BYTES + TAG_BYTES) throw CorruptVaultException("Truncated payload")
        val dataIv = file.copyOfRange(offset, offset + IV_BYTES)
        val ciphertext = file.copyOfRange(offset + IV_BYTES, file.size)
        return try {
            gcm(Cipher.DECRYPT_MODE, vaultKey, dataIv, header.encode(), ciphertext)
        } catch (e: AEADBadTagException) {
            throw CorruptVaultException("Vault contents failed authentication", e)
        }
    }

    /** Encrypts [plaintext] into a complete vault file with a fresh IV. */
    fun encrypt(header: VaultHeader, vaultKey: ByteArray, plaintext: ByteArray): ByteArray {
        val headerBytes = header.encode()
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val ciphertext = gcm(Cipher.ENCRYPT_MODE, vaultKey, iv, headerBytes, plaintext)
        return headerBytes + iv + ciphertext
    }

    private fun derive(password: CharArray, params: KdfParams): ByteArray {
        val pw = utf8(password)
        try {
            val key = kdf.derive(pw, params)
            require(key.size == KEY_BYTES) { "KDF must return $KEY_BYTES bytes" }
            return key
        } finally {
            pw.fill(0)
        }
    }

    private fun parse(file: ByteArray): Pair<VaultHeader, Int> {
        try {
            val input = DataInputStream(ByteArrayInputStream(file))
            val magic = ByteArray(4).also(input::readFully)
            if (!magic.contentEquals(VaultHeader.MAGIC)) throw CorruptVaultException("Not a MySecurePass vault")
            val version = input.readUnsignedByte()
            if (version != VaultHeader.FORMAT_VERSION) throw CorruptVaultException("Unsupported vault version $version")
            val kdfId = input.readUnsignedByte()
            if (kdfId != VaultHeader.KDF_ARGON2ID) throw CorruptVaultException("Unsupported KDF $kdfId")
            val mem = input.readInt()
            val iterations = input.readInt()
            val parallelism = input.readUnsignedByte()
            val saltLen = input.readUnsignedByte()
            val salt = ByteArray(saltLen).also(input::readFully)
            val params = KdfParams(mem, iterations, parallelism, salt)
            validate(params)
            val wrapIv = ByteArray(IV_BYTES).also(input::readFully)
            val wrappedLen = input.readUnsignedShort()
            if (wrappedLen != KEY_BYTES + TAG_BYTES) throw CorruptVaultException("Invalid wrapped key")
            val wrapped = ByteArray(wrappedLen).also(input::readFully)
            val consumed = file.size - input.available()
            return VaultHeader(params, wrapIv, wrapped) to consumed
        } catch (e: EOFException) {
            throw CorruptVaultException("Truncated vault header", e)
        }
    }

    /** Rejects parameters that are too weak or could be used to exhaust memory/CPU (malicious imports). */
    private fun validate(p: KdfParams) {
        if (p.memoryKiB !in MIN_MEMORY_KIB..MAX_MEMORY_KIB) throw CorruptVaultException("KDF memory out of range")
        if (p.iterations !in 1..MAX_ITERATIONS) throw CorruptVaultException("KDF iterations out of range")
        if (p.parallelism !in 1..MAX_PARALLELISM) throw CorruptVaultException("KDF parallelism out of range")
        if (p.salt.size !in 16..64) throw CorruptVaultException("Invalid salt length")
    }

    companion object {
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val MIN_MEMORY_KIB = 16 * 1024
        const val MAX_MEMORY_KIB = 512 * 1024
        const val MAX_ITERATIONS = 20
        const val MAX_PARALLELISM = 8

        fun gcm(mode: Int, key: ByteArray, iv: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, iv))
            cipher.updateAAD(aad)
            return cipher.doFinal(input)
        }

        /** Encodes a password to UTF-8 without creating an immutable String. Caller must wipe the result. */
        fun utf8(chars: CharArray): ByteArray {
            val buffer: ByteBuffer = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(chars))
            val out = ByteArray(buffer.remaining())
            buffer.get(out)
            if (buffer.hasArray()) buffer.array().fill(0)
            return out
        }
    }
}
