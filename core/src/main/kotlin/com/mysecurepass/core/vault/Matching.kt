package com.mysecurepass.core.vault

/** Matches vault entries against an Android package or a web domain (for autofill and search). */
object Matching {
    const val ANDROID_APP_SCHEME = "androidapp://"

    /** Normalises user input like `https://www.Example.com/login` to `example.com`. */
    fun normalizeHost(input: String): String? {
        var s = input.trim().lowercase()
        if (s.isEmpty() || s.startsWith(ANDROID_APP_SCHEME)) return null
        s = s.substringAfter("://")
        s = s.substringBefore('/').substringBefore('?').substringBefore('#')
        s = s.substringAfterLast('@').substringBefore(':')
        s = s.removePrefix("www.").trimEnd('.')
        if (s.isEmpty() || !s.contains('.') || s.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return null
        return s
    }

    fun packageOf(url: String): String? =
        url.trim().takeIf { it.startsWith(ANDROID_APP_SCHEME, ignoreCase = true) }
            ?.substring(ANDROID_APP_SCHEME.length)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * True if [entry] should be offered for the given context.
     * - Apps match by exact package name.
     * - Web domains match exactly or as a subdomain of the saved host (`accounts.example.com` matches
     *   a saved `example.com`), never the other way round and never by suffix alone
     *   (`badexample.com` does not match `example.com`).
     */
    fun matches(entry: VaultEntry, packageName: String?, webDomain: String?): Boolean {
        val host = webDomain?.let(::normalizeHost)
        for (url in entry.urls) {
            val pkg = packageOf(url)
            if (pkg != null) {
                if (packageName != null && pkg == packageName) return true
                continue
            }
            val saved = normalizeHost(url) ?: continue
            if (host != null && (host == saved || host.endsWith(".$saved"))) return true
        }
        return false
    }
}
