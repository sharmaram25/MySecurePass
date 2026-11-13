package com.mysecurepass.core.security

import com.mysecurepass.core.vault.Kdf
import com.mysecurepass.core.vault.KdfParams
import com.mysecurepass.core.vault.VaultCrypto
import java.security.SecureRandom
import javax.crypto.Cipher

data class CheckResult(val name: String, val passed: Boolean, val detail: String, val millis: Long)

/**
 * On-device verification of the cryptography the vault depends on. Each check runs the real
 * primitive on this device and compares against published or independently verified values, so a
 * broken provider, a mis-linked native library or a faulty RNG is detected rather than trusted.
 */
object SelfTest {

    // ---- AES-256-GCM: McGrew & Viega GCM spec, Test Case 15 (also in NIST CAVP) ----
    private const val GCM_KEY = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308"
    private const val GCM_IV = "cafebabefacedbaddecaf888"
    private const val GCM_PT = "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a72" +
        "1c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255"
    private const val GCM_CT_TAG = "522dc1f099567d07f47f37a32a84427d643a8cdcbfe5c0c97598a2bd2555d1aa" +
        "8cb08e48590dbb3da7b08b1056828838c5f61e6393ba7a0abcc9f662898015ad" +
        "b094dac5d93471bdec1a502270e3cc6c"

    // ---- Argon2id v1.3 known answer (verified against Bouncy Castle in unit tests) ----
    internal const val ARGON_PASSWORD = "password"
    internal const val ARGON_SALT = "somesaltsomesalt"
    internal const val ARGON_MEMORY_KIB = 4096
    internal const val ARGON_ITERATIONS = 2
    internal const val ARGON_EXPECTED = "1f99999fc42c145c27b4f92d75b8c636da81f830c9fa78b1abde2e240668a889"

    fun aesGcm(): CheckResult = timed("AES-256-GCM", "Known-answer test (NIST/McGrew–Viega TC15)") {
        val out = VaultCrypto.gcm(Cipher.ENCRYPT_MODE, hex(GCM_KEY), hex(GCM_IV), ByteArray(0), hex(GCM_PT))
        val back = VaultCrypto.gcm(Cipher.DECRYPT_MODE, hex(GCM_KEY), hex(GCM_IV), ByteArray(0), out)
        out.contentEquals(hex(GCM_CT_TAG)) && back.contentEquals(hex(GCM_PT))
    }

    fun argon2(kdf: Kdf): CheckResult = timed("Argon2id", "Known-answer test (RFC 9106, v1.3)") {
        val params = KdfParams(ARGON_MEMORY_KIB, ARGON_ITERATIONS, 1, ARGON_SALT.toByteArray())
        kdf.derive(ARGON_PASSWORD.toByteArray(), params).contentEquals(hex(ARGON_EXPECTED))
    }

    /** Encrypts a vault with the real KDF, flips one bit, and requires decryption to fail. */
    fun tamperDetection(kdf: Kdf, random: SecureRandom = SecureRandom()): CheckResult =
        timed("Tamper detection", "Altered vault data is rejected") {
            val crypto = VaultCrypto(kdf, random)
            val params = KdfParams(VaultCrypto.MIN_MEMORY_KIB, 1, 1, ByteArray(16).also(random::nextBytes))
            val (header, key) = crypto.create("self-test".toCharArray(), params)
            val file = crypto.encrypt(header, key, "integrity".toByteArray())
            val intact = crypto.decrypt(file, key).contentEquals("integrity".toByteArray())
            val tampered = file.copyOf().also { it[it.size - 20] = (it[it.size - 20].toInt() xor 1).toByte() }
            val rejected = runCatching { crypto.decrypt(tampered, key) }.isFailure
            val wrongPw = runCatching { crypto.unlock(file, "wrong".toCharArray()) }.isFailure
            key.fill(0)
            intact && rejected && wrongPw
        }

    /** FIPS 140-2 statistical tests (monobit, poker, runs, long run) on 20,000 random bits. */
    fun randomness(random: SecureRandom = SecureRandom()): CheckResult =
        timed("Random number generator", "FIPS 140‑2 statistical tests on 20,000 bits") {
            // Statistical tests have a tiny false-failure rate; one retry makes a spurious failure ~1e-8.
            fips140(random) || fips140(random)
        }

    /** Measures key-derivation time with the production parameters on this device. */
    fun kdfTiming(kdf: Kdf): Long {
        val start = System.nanoTime()
        kdf.derive("timing".toByteArray(), KdfParams.recommended()).fill(0)
        return (System.nanoTime() - start) / 1_000_000
    }

    internal fun fips140(random: SecureRandom): Boolean {
        val bytes = ByteArray(2500).also(random::nextBytes)
        val bits = IntArray(20_000) { i -> (bytes[i / 8].toInt() ushr (7 - i % 8)) and 1 }
        val ones = bits.sum()
        if (ones !in 9726..10274) return false
        val counts = IntArray(16)
        for (i in 0 until 5000) {
            val n = (bits[4 * i] shl 3) or (bits[4 * i + 1] shl 2) or (bits[4 * i + 2] shl 1) or bits[4 * i + 3]
            counts[n]++
        }
        val x = 16.0 / 5000 * counts.sumOf { it.toLong() * it } - 5000
        if (x <= 2.16 || x >= 46.17) return false
        val runs = Array(2) { IntArray(7) }
        var len = 1
        for (i in 1..bits.size) {
            if (i < bits.size && bits[i] == bits[i - 1]) { len++; continue }
            if (len >= 26) return false
            runs[bits[i - 1]][minOf(len, 6)]++
            len = 1
        }
        val bounds = listOf(2315..2685, 1114..1386, 527..723, 240..384, 103..209, 103..209)
        for (b in 0..1) for (l in 1..6) if (runs[b][l] !in bounds[l - 1]) return false
        return true
    }

    private inline fun timed(name: String, detail: String, block: () -> Boolean): CheckResult {
        val start = System.nanoTime()
        val ok = runCatching(block).getOrDefault(false)
        return CheckResult(name, ok, detail, (System.nanoTime() - start) / 1_000_000)
    }

    internal fun hex(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}
