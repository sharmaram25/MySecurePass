package com.mysecurepass.core.security

import com.mysecurepass.core.vault.Kdf
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfTestTest {
    /** Bouncy Castle's Argon2id: an implementation independent of the native one used on Android. */
    private val bcArgon2 = Kdf { pw, p ->
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(p.iterations).withMemoryAsKB(p.memoryKiB).withParallelism(p.parallelism)
            .withSalt(p.salt).build()
        ByteArray(32).also { out -> Argon2BytesGenerator().apply { init(params) }.generateBytes(pw, out) }
    }

    @Test
    fun aesGcmKnownAnswerMatchesJdk() = assertTrue(SelfTest.aesGcm().passed)

    @Test
    fun argon2KnownAnswerMatchesBouncyCastle() {
        val r = SelfTest.argon2(bcArgon2)
        assertTrue(r.passed, "ARGON_EXPECTED does not match Bouncy Castle")
    }

    @Test
    fun argon2DetectsWrongImplementation() {
        val broken = Kdf { pw, p -> bcArgon2.derive(pw, p).also { it[0] = (it[0].toInt() xor 1).toByte() } }
        assertFalse(SelfTest.argon2(broken).passed)
    }

    @Test
    fun tamperDetectionPasses() = assertTrue(SelfTest.tamperDetection(bcArgon2).passed)

    @Test
    fun randomnessPassesForSecureRandom() = assertTrue(SelfTest.randomness().passed)

    @Test
    fun randomnessFailsForBrokenGenerator() {
        val constant = object : SecureRandom() { override fun nextBytes(bytes: ByteArray) = bytes.fill(0x55) }
        assertFalse(SelfTest.randomness(constant).passed)
        val biased = object : SecureRandom() {
            val r = java.util.Random(1)
            override fun nextBytes(bytes: ByteArray) { for (i in bytes.indices) bytes[i] = (r.nextInt(256) or 0x01).toByte() }
        }
        assertFalse(SelfTest.randomness(biased).passed)
    }
}
