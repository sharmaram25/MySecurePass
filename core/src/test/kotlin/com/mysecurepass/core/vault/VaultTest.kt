package com.mysecurepass.core.vault

import com.mysecurepass.core.strength.AttackScenario
import com.mysecurepass.core.strength.CrackTime
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultTest {
    /** Fast stand-in for Argon2id; the format logic is what's under test. */
    private val fakeKdf = Kdf { pw, params ->
        MessageDigest.getInstance("SHA-256").run { update(params.salt); update(pw); digest() }
    }
    private val crypto = VaultCrypto(fakeKdf)
    private val params get() = KdfParams(64 * 1024, 3, 1, ByteArray(16) { it.toByte() })

    private fun sampleData() = VaultData(entries = listOf(
        VaultEntry("1", "Example", "alice", "s3cret!", listOf("example.com"), createdAt = 1, updatedAt = 1),
    ))

    @Test
    fun roundTrip() {
        val (header, key) = crypto.create("correct horse".toCharArray(), params)
        val file = crypto.encrypt(header, key, VaultJson.encode(sampleData()))
        val (_, unlocked) = crypto.unlock(file, "correct horse".toCharArray())
        assertContentEquals(key, unlocked)
        assertEquals(sampleData(), VaultJson.decode(crypto.decrypt(file, unlocked)))
    }

    @Test
    fun wrongPasswordRejected() {
        val (header, key) = crypto.create("right".toCharArray(), params)
        val file = crypto.encrypt(header, key, byteArrayOf(1, 2, 3))
        assertFailsWith<WrongPasswordException> { crypto.unlock(file, "wrong".toCharArray()) }
    }

    @Test
    fun everyByteIsAuthenticated() {
        val (header, key) = crypto.create("pw".toCharArray(), params)
        val file = crypto.encrypt(header, key, VaultJson.encode(sampleData()))
        for (i in file.indices) {
            val tampered = file.copyOf().also { it[i] = (it[i].toInt() xor 0x01).toByte() }
            val result = runCatching {
                val (_, k) = crypto.unlock(tampered, "pw".toCharArray())
                crypto.decrypt(tampered, k)
            }
            assertTrue(result.isFailure, "tampering byte $i went undetected")
        }
    }

    @Test
    fun rewrapKeepsDataReadable() {
        val (header, key) = crypto.create("old".toCharArray(), params)
        val newHeader = crypto.rewrap(key, "new".toCharArray(), KdfParams(32 * 1024, 2, 1, ByteArray(16) { 9 }))
        val file = crypto.encrypt(newHeader, key, byteArrayOf(42))
        assertFailsWith<WrongPasswordException> { crypto.unlock(file, "old".toCharArray()) }
        val (_, k) = crypto.unlock(file, "new".toCharArray())
        assertContentEquals(byteArrayOf(42), crypto.decrypt(file, k))
        assertEquals(64 * 1024, header.kdf.memoryKiB)
    }

    @Test
    fun maliciousKdfParamsRejected() {
        val (header, key) = crypto.create("pw".toCharArray(), params)
        val file = crypto.encrypt(header, key, byteArrayOf(1))
        // memKiB lives at offset 6..9; set it to ~2 TiB.
        val evil = file.copyOf().also { it[6] = 0x7f; it[7] = 0x7f }
        assertFailsWith<CorruptVaultException> { crypto.readHeader(evil) }
        assertFailsWith<CorruptVaultException> { crypto.readHeader(byteArrayOf(1, 2, 3)) }
        assertFailsWith<CorruptVaultException> { crypto.readHeader(file.copyOf(20)) }
    }

    @Test
    fun ciphertextIsFreshEachSave() {
        val (header, key) = crypto.create("pw".toCharArray(), params)
        val a = crypto.encrypt(header, key, byteArrayOf(1, 2, 3))
        val b = crypto.encrypt(header, key, byteArrayOf(1, 2, 3))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun matching() {
        val e = VaultEntry("1", "t", urls = listOf("https://www.Example.com/login", "androidapp://com.example.app"), createdAt = 0, updatedAt = 0)
        assertTrue(Matching.matches(e, null, "example.com"))
        assertTrue(Matching.matches(e, null, "accounts.example.com"))
        assertFalse(Matching.matches(e, null, "badexample.com"))
        assertFalse(Matching.matches(e, null, "example.com.evil.io"))
        assertTrue(Matching.matches(e, "com.example.app", null))
        assertFalse(Matching.matches(e, "com.example.app.evil", null))
    }

    @Test
    fun csvImportHandlesChromeAndBitwarden() {
        var id = 0
        val chrome = "name,url,username,password,note\r\nGitHub,https://github.com/login,octo,\"p,a\"\"ss\",\"multi\nline\"\n"
        val r1 = CsvImport.parse(chrome, 0) { (++id).toString() }
        assertEquals(1, r1.entries.size)
        with(r1.entries[0]) {
            assertEquals("GitHub", title); assertEquals("p,a\"ss", password)
            assertEquals(listOf("github.com"), urls); assertEquals("multi\nline", notes)
        }
        val bw = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\n,,login,Mail,,,0,https://mail.example.org,me,pw1,JBSWY3DPEHPK3PXP\n,,note,Secure note,hello,,,,,,\n"
        val r2 = CsvImport.parse(bw, 0) { (++id).toString() }
        assertEquals(1, r2.entries.size); assertEquals(1, r2.skipped)
        assertEquals("JBSWY3DPEHPK3PXP", r2.entries[0].totp)
    }

    @Test
    fun healthReport() {
        val now = 400L * 24 * 3600 * 1000
        val entries = listOf(
            VaultEntry("a", "A", password = "same", createdAt = 0, updatedAt = 0),
            VaultEntry("b", "B", password = "same", createdAt = now, updatedAt = now, passwordChangedAt = now),
            VaultEntry("c", "C", password = "Zq9!vT#2mLw8", createdAt = now, updatedAt = now, passwordChangedAt = now),
        )
        val report = PasswordHealth.analyze(entries, now) { if (it == "same") 0 else 4 }
        assertEquals(2, report.count(HealthIssue.REUSED))
        assertEquals(2, report.count(HealthIssue.WEAK))
        assertEquals(1, report.count(HealthIssue.OLD))
        assertEquals(33, report.score)
    }

    @Test
    fun crackTimeFormatting() {
        assertEquals("Instantly", CrackTime.format(CrackTime.secondsForBits(10.0, AttackScenario.OFFLINE_FAST)))
        assertTrue(CrackTime.format(CrackTime.secondsForBits(128.0, AttackScenario.OFFLINE_FAST)).contains("universe"))
        assertEquals("1 hour", CrackTime.format(3600.0))
    }

    // ---- Security properties -------------------------------------------------------------

    /** Same password must never produce the same salt, key-encryption key, or wrapped key. */
    @Test
    fun saltsAndKeysAreUniquePerVault() {
        val real = VaultCrypto(fakeKdf)
        val vaults = List(50) { real.create("same password".toCharArray()) }
        val salts = vaults.map { it.first.kdf.salt.toList() }.toSet()
        val keys = vaults.map { it.second.toList() }.toSet()
        val wrapped = vaults.map { it.first.wrappedKey.toList() }.toSet()
        assertEquals(50, salts.size, "salts must be random per vault")
        assertEquals(50, keys.size, "vault keys must be random")
        assertEquals(50, wrapped.size)
        assertTrue(vaults.all { it.first.kdf.salt.size >= 16 }, "salt must be >= 128 bits")
    }

    @Test
    fun recommendedKdfMeetsOwasp() {
        val p = KdfParams.recommended()
        assertTrue(p.memoryKiB >= 19 * 1024, "Argon2id memory below OWASP minimum")
        assertTrue(p.iterations >= 2)
        assertEquals(16, p.salt.size)
    }

    /** AES-GCM nonce reuse is catastrophic; every save must use a fresh 96-bit IV. */
    @Test
    fun noncesNeverRepeat() {
        val (header, key) = crypto.create("pw".toCharArray(), params)
        val headerLen = header.encode().size
        val ivs = List(1_000) {
            crypto.encrypt(header, key, byteArrayOf(0)).copyOfRange(headerLen, headerLen + VaultCrypto.IV_BYTES).toList()
        }.toSet()
        assertEquals(1_000, ivs.size)
    }

    /** The plaintext (passwords, usernames, titles) must not appear anywhere in the file. */
    @Test
    fun fileContainsNoPlaintext() {
        val (header, key) = crypto.create("pw".toCharArray(), params)
        val file = crypto.encrypt(header, key, VaultJson.encode(sampleData())).toString(Charsets.ISO_8859_1)
        listOf("s3cret", "alice", "Example", "example.com", "entries").forEach {
            assertFalse(file.contains(it), "'$it' leaked into ciphertext")
        }
    }

    /** The password is passed to the KDF as UTF-8 and the caller's temporary copy is wiped. */
    @Test
    fun passwordBytesAreWipedAfterDerivation() {
        var seen: ByteArray? = null
        val spying = VaultCrypto(Kdf { pw, p -> seen = pw; fakeKdf.derive(pw.copyOf(), p) })
        spying.create("pässwörd".toCharArray(), params)
        assertTrue(seen!!.all { it == 0.toByte() }, "password buffer was not zeroed")
    }

    @Test
    fun swappingHeadersBetweenVaultsIsDetected() {
        val (h1, k1) = crypto.create("pw".toCharArray(), params)
        val (h2, _) = crypto.create("pw".toCharArray(), KdfParams(64 * 1024, 3, 1, ByteArray(16) { 7 }))
        val file1 = crypto.encrypt(h1, k1, byteArrayOf(1, 2, 3))
        val spliced = h2.encode() + file1.copyOfRange(h1.encode().size, file1.size)
        val result = runCatching { val (_, k) = crypto.unlock(spliced, "pw".toCharArray()); crypto.decrypt(spliced, k) }
        assertTrue(result.isFailure)
    }

    @Test
    fun entryToStringRedacts() {
        assertFalse(sampleData().entries[0].toString().contains("s3cret"))
    }
}
