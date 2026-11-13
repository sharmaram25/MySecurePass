package com.mysecurepass.core.random

import java.security.SecureRandom

/**
 * Source of randomness for every generator. Production code uses [SecureRandomSource];
 * tests can inject a deterministic implementation.
 */
interface RandomSource {
    /** Uniform integer in `[0, bound)`. Must be free of modulo bias. */
    fun nextInt(bound: Int): Int

    /** Uniform double in `[0, 1)` with 53 bits of precision. */
    fun nextDouble(): Double

    fun nextBytes(bytes: ByteArray)
}

/**
 * CSPRNG-backed source. On Android, [SecureRandom] is provided by Conscrypt / the kernel
 * entropy pool (`/dev/urandom`); on the JVM it is the platform default (NativePRNG / DRBG).
 *
 * [SecureRandom.nextInt] with a bound already uses rejection sampling, so results are unbiased.
 */
class SecureRandomSource(private val rng: SecureRandom = SecureRandom()) : RandomSource {
    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return rng.nextInt(bound)
    }

    override fun nextDouble(): Double = rng.nextDouble()

    override fun nextBytes(bytes: ByteArray) = rng.nextBytes(bytes)
}

/** Fisher–Yates shuffle driven by [RandomSource]. */
fun <T> MutableList<T>.secureShuffle(random: RandomSource) {
    for (i in lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}

fun IntArray.secureShuffle(random: RandomSource) {
    for (i in lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}
