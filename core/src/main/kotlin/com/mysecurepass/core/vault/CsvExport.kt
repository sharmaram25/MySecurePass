package com.mysecurepass.core.vault

/**
 * Plaintext CSV export for moving to another manager. Logins only (the format other tools
 * understand); columns match what [CsvImport] and Bitwarden/Chrome importers recognise.
 */
object CsvExport {
    private val header = listOf("name", "url", "username", "password", "totp", "notes")

    fun export(entries: List<VaultEntry>): String = buildString {
        appendRow(header)
        entries.active().filter { it.type == EntryType.LOGIN }.forEach { e ->
            val url = e.urls.firstOrNull()?.let { u -> if (Matching.packageOf(u) != null) u else "https://$u" }.orEmpty()
            appendRow(listOf(e.title, url, e.username, e.password, e.totp, e.notes))
        }
    }

    private fun StringBuilder.appendRow(values: List<String>) {
        values.forEachIndexed { i, v ->
            if (i > 0) append(',')
            append(escape(v))
        }
        append("\r\n")
    }

    /** RFC 4180 quoting. Values are never altered: a modified password would be a corrupted password. */
    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || value.startsWith(' ') || value.endsWith(' ')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
