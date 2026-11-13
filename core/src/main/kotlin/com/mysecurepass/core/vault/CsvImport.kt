package com.mysecurepass.core.vault

/**
 * Imports logins from CSV exports of Google/Chrome Password Manager, Firefox, Bitwarden,
 * 1Password, KeePassXC and similar tools by recognising common column names.
 */
object CsvImport {

    data class Result(val entries: List<VaultEntry>, val skipped: Int)

    private val titleCols = listOf("name", "title", "account", "item name")
    private val urlCols = listOf("url", "login_uri", "website", "web site", "uri", "urls", "login url")
    private val userCols = listOf("username", "login_username", "user name", "login", "email", "login name")
    private val passCols = listOf("password", "login_password", "pass")
    private val noteCols = listOf("note", "notes", "comments", "extra")
    private val totpCols = listOf("totp", "login_totp", "otpauth", "one-time password", "otp")

    fun parse(text: String, now: Long, newId: () -> String): Result {
        val rows = parseCsv(text.removePrefix("﻿"))
        if (rows.isEmpty()) return Result(emptyList(), 0)
        val header = rows.first().map { it.trim().lowercase() }

        fun col(names: List<String>): Int = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } } ?: -1
        val iTitle = col(titleCols); val iUrl = col(urlCols); val iUser = col(userCols)
        val iPass = col(passCols); val iNote = col(noteCols); val iTotp = col(totpCols)
        require(iPass >= 0) { "No password column found. Supported: Chrome, Firefox, Bitwarden, 1Password, KeePassXC CSV." }

        val entries = ArrayList<VaultEntry>()
        var skipped = 0
        for (row in rows.drop(1)) {
            fun get(i: Int) = if (i in row.indices) row[i].trim() else ""
            val password = if (iPass in row.indices) row[iPass] else ""
            val username = get(iUser)
            val url = get(iUrl)
            if (password.isEmpty() && username.isEmpty()) { skipped++; continue }
            val host = Matching.normalizeHost(url)
            val urls = when {
                url.startsWith(Matching.ANDROID_APP_SCHEME) -> listOf(url)
                host != null -> listOf(host)
                else -> emptyList()
            }
            val title = get(iTitle).ifEmpty { host ?: username.ifEmpty { "Imported login" } }
            entries += VaultEntry(
                id = newId(), title = title, username = username, password = password, urls = urls,
                notes = get(iNote), totp = get(iTotp), createdAt = now, updatedAt = now, passwordChangedAt = now,
            )
        }
        return Result(entries, skipped)
    }

    /** RFC 4180 parser: quoted fields, escaped quotes, embedded newlines, CRLF/LF. */
    fun parseCsv(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.setLength(0); if (row.any { it.isNotEmpty() }) rows.add(row); row = ArrayList() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); if (row.any { it.isNotEmpty() }) rows.add(row) }
        return rows
    }
}
