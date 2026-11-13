package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource
import java.util.Base64
import kotlin.math.log2

/** Developer secrets: API keys, signing secrets, UUIDs. */
object TokenGenerator {
    private const val HEX = "0123456789abcdef"
    private const val ALNUM = CharSets.UPPER + CharSets.LOWER + CharSets.DIGITS

    fun entropyBits(config: TokenConfig): Double {
        val size = config.size.coerceIn(TokenConfig.MIN_SIZE, TokenConfig.MAX_SIZE)
        return when (config.format) {
            TokenFormat.HEX, TokenFormat.BASE64URL -> size * 8.0
            TokenFormat.ALPHANUMERIC -> size * log2(ALNUM.length.toDouble())
            TokenFormat.UUID -> 122.0
        }
    }

    fun generate(config: TokenConfig, random: RandomSource): Generated {
        val size = config.size.coerceIn(TokenConfig.MIN_SIZE, TokenConfig.MAX_SIZE)
        val value = when (config.format) {
            TokenFormat.HEX -> {
                val bytes = ByteArray(size).also(random::nextBytes)
                buildString(size * 2) {
                    for (b in bytes) {
                        val v = b.toInt() and 0xff
                        append(HEX[v ushr 4]); append(HEX[v and 0x0f])
                    }
                }.also { bytes.fill(0) }
            }
            TokenFormat.BASE64URL -> {
                val bytes = ByteArray(size).also(random::nextBytes)
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { bytes.fill(0) }
            }
            TokenFormat.ALPHANUMERIC -> buildString(size) {
                repeat(size) { append(ALNUM[random.nextInt(ALNUM.length)]) }
            }
            TokenFormat.UUID -> uuidV4(random)
        }
        return Generated(value, entropyBits(config))
    }

    private fun uuidV4(random: RandomSource): String {
        val b = ByteArray(16).also(random::nextBytes)
        b[6] = ((b[6].toInt() and 0x0f) or 0x40).toByte() // version 4
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte() // IETF variant
        val hex = buildString(32) {
            for (x in b) { val v = x.toInt() and 0xff; append(HEX[v ushr 4]); append(HEX[v and 0x0f]) }
        }
        b.fill(0)
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
