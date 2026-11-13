package com.mysecurepass.core.totp

import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class TotpAlgorithm(val macName: String) { SHA1("HmacSHA1"), SHA256("HmacSHA256"), SHA512("HmacSHA512") }

/** Parameters for an RFC 6238 time-based one-time password. */
data class TotpParams(
    val secret: ByteArray,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val issuer: String? = null,
    val account: String? = null,
) {
    override fun equals(other: Any?): Boolean = other is TotpParams && secret.contentEquals(other.secret) &&
        digits == other.digits && periodSeconds == other.periodSeconds && algorithm == other.algorithm
    override fun hashCode(): Int = secret.contentHashCode() * 31 + digits
    override fun toString(): String = "TotpParams(<redacted>, digits=$digits, period=$periodSeconds, $algorithm)"
}

object Totp {
    /** RFC 4226 HOTP value for [counter]. */
    fun hotp(params: TotpParams, counter: Long): String {
        val mac = Mac.getInstance(params.algorithm.macName)
        mac.init(SecretKeySpec(params.secret, params.algorithm.macName))
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        var mod = 1L
        repeat(params.digits) { mod *= 10 }
        return (binary % mod).toString().padStart(params.digits, '0')
    }

    fun code(params: TotpParams, epochMillis: Long): String = hotp(params, epochMillis / 1000 / params.periodSeconds)

    /** Seconds left until the current code expires. */
    fun secondsRemaining(params: TotpParams, epochMillis: Long): Int =
        params.periodSeconds - ((epochMillis / 1000) % params.periodSeconds).toInt()

    /**
     * Accepts either a raw Base32 secret (spaces/dashes allowed) or an `otpauth://totp/...` URI.
     * Returns null if the input is not valid.
     */
    fun parse(input: String): TotpParams? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("otpauth://", ignoreCase = true)) return parseUri(trimmed)
        val secret = Base32.decode(trimmed) ?: return null
        if (secret.size < 10) return null // RFC 4226 requires at least 128 bits; accept >= 80 for legacy sites
        return TotpParams(secret)
    }

    private fun parseUri(uri: String): TotpParams? {
        val withoutScheme = uri.substring("otpauth://".length)
        val type = withoutScheme.substringBefore('/').lowercase()
        if (type != "totp") return null
        val label = URLDecoder.decode(withoutScheme.substringAfter('/').substringBefore('?'), "UTF-8")
        val query = withoutScheme.substringAfter('?', "")
        val params = query.split('&').filter { it.contains('=') }.associate {
            it.substringBefore('=').lowercase() to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        val secret = params["secret"]?.let(Base32::decode) ?: return null
        if (secret.isEmpty()) return null
        val digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
        val period = params["period"]?.toIntOrNull()?.takeIf { it in 10..300 } ?: 30
        val algorithm = when (params["algorithm"]?.uppercase()) {
            "SHA256" -> TotpAlgorithm.SHA256
            "SHA512" -> TotpAlgorithm.SHA512
            else -> TotpAlgorithm.SHA1
        }
        val issuer = params["issuer"] ?: label.substringBefore(':', "").ifEmpty { null }
        val account = if (label.contains(':')) label.substringAfter(':').trim() else label.ifEmpty { null }
        return TotpParams(secret, digits, period, algorithm, issuer, account)
    }
}

object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray? {
        val clean = input.uppercase().filterNot { it == ' ' || it == '-' || it == '=' }
        if (clean.isEmpty()) return null
        val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer ushr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun encode(data: ByteArray): String = buildString {
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                append(ALPHABET[(buffer ushr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) append(ALPHABET[(buffer shl (5 - bits)) and 31])
    }
}
