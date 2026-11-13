package com.mysecurepass.core.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Schema2Test {

    @Test
    fun schema1VaultStillDecodes() {
        val v1 = """{"schema":1,"entries":[{"id":"a","title":"Old","password":"x","createdAt":5,"updatedAt":6}]}"""
        val data = VaultJson.decode(v1.toByteArray())
        val e = data.entries.single()
        assertEquals(EntryType.LOGIN, e.type)
        assertEquals(5, e.passwordChangedAt)
        assertNull(e.deletedAt)
        assertTrue(e.tags.isEmpty())
    }

    @Test
    fun newTypesRoundTrip() {
        val data = VaultData(entries = listOf(
            VaultEntry("c", "Visa", type = EntryType.CARD, card = CardData("A B", "4111 1111 1111 1111", "12/30", "123"), createdAt = 1, updatedAt = 1),
            VaultEntry("w", "Home", type = EntryType.WIFI, password = "pw", wifi = WifiData("HomeNet", WifiSecurity.WPA), createdAt = 1, updatedAt = 1),
            VaultEntry("n", "Note", type = EntryType.NOTE, notes = "secret", tags = listOf("Work"), fields = listOf(CustomField("PIN", "1234", true)), createdAt = 1, updatedAt = 1),
        ))
        val decoded = VaultJson.decode(VaultJson.encode(data))
        assertEquals(data.entries, decoded.entries)
        assertEquals(VaultData.CURRENT_SCHEMA, decoded.schema)
    }

    @Test
    fun trashPurgesOnlyExpired() {
        val day = 24L * 3600 * 1000
        val now = 100 * day
        val data = VaultData(entries = listOf(
            VaultEntry("keep", "k", createdAt = 0, updatedAt = 0),
            VaultEntry("recent", "r", deletedAt = now - 5 * day, createdAt = 0, updatedAt = 0),
            VaultEntry("old", "o", deletedAt = now - 31 * day, createdAt = 0, updatedAt = 0),
        ))
        assertEquals(listOf("keep", "recent"), data.purgeExpired(now).entries.map { it.id })
        assertEquals(listOf("keep"), data.entries.active().map { it.id })
    }

    @Test
    fun healthIgnoresTrashAndNonLogins() {
        val entries = listOf(
            VaultEntry("a", "A", password = "same", createdAt = 0, updatedAt = 0, deletedAt = 1),
            VaultEntry("b", "B", password = "same", createdAt = 0, updatedAt = 0),
            VaultEntry("w", "W", password = "same", type = EntryType.WIFI, createdAt = 0, updatedAt = 0),
        )
        val r = PasswordHealth.analyze(entries, 0) { 4 }
        assertEquals(1, r.total)
        assertEquals(0, r.count(HealthIssue.REUSED))
    }

    @Test
    fun cards() {
        assertEquals(CardBrand.VISA, Cards.brand("4111 1111 1111 1111"))
        assertEquals(CardBrand.MASTERCARD, Cards.brand("5555555555554444"))
        assertEquals(CardBrand.MASTERCARD, Cards.brand("2223003122003222"))
        assertEquals(CardBrand.AMEX, Cards.brand("378282246310005"))
        assertEquals(CardBrand.DISCOVER, Cards.brand("6011111111111117"))
        assertEquals(CardBrand.RUPAY, Cards.brand("6521 5000 0000 0000"))
        assertEquals(CardBrand.JCB, Cards.brand("3530111333300000"))
        assertTrue(Cards.isLuhnValid("4111-1111-1111-1111"))
        assertFalse(Cards.isLuhnValid("4111111111111112"))
        assertEquals("3782 822463 10005", Cards.format("378282246310005"))
        assertEquals("4111 1111 1111 1111", Cards.format("4111111111111111"))
        assertEquals("•••• 1111", Cards.masked("4111111111111111"))
        assertEquals(12 to 2030, Cards.parseExpiry("12/30"))
        assertNull(Cards.parseExpiry("13/30"))
        val jan2027 = java.util.Calendar.getInstance().apply { set(2027, 0, 15) }.timeInMillis
        assertTrue(Cards.isExpired("12/26", jan2027))
        assertFalse(Cards.isExpired("01/27", jan2027))
    }

    @Test
    fun csvExportRoundTripsThroughImport() {
        val entries = listOf(
            VaultEntry("1", "Git, Hub", "octo", "p\"a,ss\n=1", listOf("github.com"), "line1\nline2", "JBSWY3DPEHPK3PXP", createdAt = 0, updatedAt = 0),
            VaultEntry("2", "-dash", "u", "-starts-with-dash", listOf("androidapp://com.x"), createdAt = 0, updatedAt = 0),
            VaultEntry("3", "Card", type = EntryType.CARD, createdAt = 0, updatedAt = 0),
            VaultEntry("4", "Gone", password = "x", deletedAt = 1, createdAt = 0, updatedAt = 0),
        )
        val csv = CsvExport.export(entries)
        var id = 0
        val back = CsvImport.parse(csv, 0) { (++id).toString() }.entries
        assertEquals(2, back.size)
        assertEquals("p\"a,ss\n=1", back[0].password)
        assertEquals("Git, Hub", back[0].title)
        assertEquals(listOf("github.com"), back[0].urls)
        assertEquals("line1\nline2", back[0].notes)
        assertEquals("-starts-with-dash", back[1].password)
        assertEquals(listOf("androidapp://com.x"), back[1].urls)
    }

    @Test
    fun searchTextExcludesSecrets() {
        val e = VaultEntry("1", "Bank", "me", "hunter2", notes = "secret note", tags = listOf("Finance"),
            card = CardData(number = "4111111111111111", cvv = "999"), createdAt = 0, updatedAt = 0)
        val s = e.searchText()
        assertTrue("bank" in s && "finance" in s && "visa" in s)
        assertFalse("hunter2" in s || "secret" in s || "999" in s || "4111" in s)
    }
}
