package com.mysecurepass.core.totp

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TotpTest {
    // RFC 6238 Appendix B test vectors (8 digits).
    private val sha1 = TotpParams("12345678901234567890".toByteArray(), 8, 30, TotpAlgorithm.SHA1)
    private val sha256 = TotpParams("12345678901234567890123456789012".toByteArray(), 8, 30, TotpAlgorithm.SHA256)
    private val sha512 = TotpParams(("1234567890".repeat(6) + "1234").toByteArray(), 8, 30, TotpAlgorithm.SHA512)

    @Test
    fun rfc6238Vectors() {
        val vectors = listOf(
            59L to Triple("94287082", "46119246", "90693936"),
            1111111109L to Triple("07081804", "68084774", "25091201"),
            1234567890L to Triple("89005924", "91819424", "93441116"),
            20000000000L to Triple("65353130", "77737706", "47863826"),
        )
        for ((t, codes) in vectors) {
            assertEquals(codes.first, Totp.code(sha1, t * 1000), "SHA1 @ $t")
            assertEquals(codes.second, Totp.code(sha256, t * 1000), "SHA256 @ $t")
            assertEquals(codes.third, Totp.code(sha512, t * 1000), "SHA512 @ $t")
        }
    }

    @Test
    fun parsesOtpauthUri() {
        val p = assertNotNull(Totp.parse("otpauth://totp/ACME%20Co:john@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"))
        assertEquals(8, p.digits); assertEquals(60, p.periodSeconds)
        assertEquals(TotpAlgorithm.SHA256, p.algorithm)
        assertEquals("ACME Co", p.issuer); assertEquals("john@example.com", p.account)
    }

    @Test
    fun parsesRawSecretAndRejectsGarbage() {
        assertNotNull(Totp.parse("jbsw y3dp ehpk 3pxp"))
        assertNull(Totp.parse("not-base32!!"))
        assertNull(Totp.parse("otpauth://hotp/x?secret=JBSWY3DPEHPK3PXP"))
        assertNull(Totp.parse(""))
    }

    @Test
    fun base32RoundTrip() {
        val data = ByteArray(37) { (it * 7).toByte() }
        assertContentEquals(data, Base32.decode(Base32.encode(data)))
        assertContentEquals("Hello!".toByteArray(), Base32.decode("JBSWY3DPEE======"))
    }
}
