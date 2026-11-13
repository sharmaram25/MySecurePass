package com.mysecurepass.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/** Login fields found in a screen, plus who is asking. */
class ParsedFields(
    val usernameIds: List<AutofillId>,
    val passwordIds: List<AutofillId>,
    val packageName: String,
    val webDomain: String?,
    val usernameValue: String?,
    val passwordValue: String?,
) {
    val allIds: List<AutofillId> get() = usernameIds + passwordIds
    val isEmpty: Boolean get() = passwordIds.isEmpty() && usernameIds.isEmpty()
}

/**
 * Heuristic field detection: explicit autofill hints first, then input types, then HTML attributes
 * and view id / hint text. Falls back to "the text field just before a password field" for usernames.
 */
object AutofillParser {

    private val passwordWords = listOf("password", "passwd", "pwd", "passcode", "pin", "contraseña", "passwort", "mot de passe", "senha")
    private val usernameWords = listOf("user", "email", "e-mail", "login", "account", "username", "identifier", "phone", "mobile")
    private val ignoreWords = listOf("search", "captcha", "otp", "code", "promo", "coupon", "zip", "postal")

    fun parse(structure: AssistStructure): ParsedFields {
        val usernames = ArrayList<Pair<AutofillId, String?>>()
        val passwords = ArrayList<Pair<AutofillId, String?>>()
        val candidates = ArrayList<Pair<AutofillId, String?>>() // plain text fields, in order
        var webDomain: String? = null
        var lastTextBeforePassword: Pair<AutofillId, String?>? = null

        fun visit(node: AssistStructure.ViewNode) {
            if (webDomain == null && !node.webDomain.isNullOrBlank()) webDomain = node.webDomain
            val id = node.autofillId
            if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT && node.visibility == View.VISIBLE) {
                val value = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()
                when (classify(node)) {
                    Kind.PASSWORD -> {
                        passwords += id to value
                        if (usernames.isEmpty()) lastTextBeforePassword = candidates.lastOrNull()
                    }
                    Kind.USERNAME -> usernames += id to value
                    Kind.TEXT -> candidates += id to value
                    Kind.IGNORE -> Unit
                }
            }
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }

        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)

        if (usernames.isEmpty()) lastTextBeforePassword?.let { usernames += it }

        return ParsedFields(
            usernameIds = usernames.map { it.first },
            passwordIds = passwords.map { it.first },
            packageName = structure.activityComponent.packageName,
            webDomain = webDomain,
            usernameValue = usernames.firstNotNullOfOrNull { it.second?.takeIf(String::isNotBlank) },
            passwordValue = passwords.firstNotNullOfOrNull { it.second?.takeIf(String::isNotEmpty) },
        )
    }

    private enum class Kind { USERNAME, PASSWORD, TEXT, IGNORE }

    private fun classify(node: AssistStructure.ViewNode): Kind {
        val hints = node.autofillHints?.map { it.lowercase() }.orEmpty()
        if (hints.any { it.contains("password") }) return Kind.PASSWORD
        if (hints.any { it == View.AUTOFILL_HINT_USERNAME.lowercase() || it == View.AUTOFILL_HINT_EMAIL_ADDRESS.lowercase() || it.contains("username") || it.contains("email") }) return Kind.USERNAME
        if (hints.isNotEmpty() && hints.none { it.contains("phone") }) return Kind.IGNORE // other explicit hint (address, card, ...)

        val type = node.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (cls == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return Kind.PASSWORD
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return Kind.USERNAME
            }
        }
        if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return Kind.PASSWORD

        val html = node.htmlInfo
        if (html != null) {
            val attrs = html.attributes.orEmpty().associate { it.first.lowercase() to (it.second ?: "").lowercase() }
            when (attrs["type"]) {
                "password" -> return Kind.PASSWORD
                "email" -> return Kind.USERNAME
                "hidden", "submit", "button", "checkbox", "radio", "search" -> return Kind.IGNORE
            }
            val ac = attrs["autocomplete"].orEmpty()
            if (ac.contains("password")) return Kind.PASSWORD
            if (ac.contains("username") || ac.contains("email")) return Kind.USERNAME
            val text = listOfNotNull(attrs["name"], attrs["id"], attrs["placeholder"], attrs["aria-label"]).joinToString(" ")
            textKind(text)?.let { return it }
        }

        val text = listOfNotNull(node.idEntry, node.hint, node.contentDescription?.toString()).joinToString(" ").lowercase()
        textKind(text)?.let { return it }
        return if (cls == InputType.TYPE_CLASS_TEXT || html != null) Kind.TEXT else Kind.IGNORE
    }

    private fun textKind(text: String): Kind? {
        if (text.isBlank()) return null
        if (ignoreWords.any { text.contains(it) }) return Kind.IGNORE
        if (passwordWords.any { text.contains(it) }) return Kind.PASSWORD
        if (usernameWords.any { text.contains(it) }) return Kind.USERNAME
        return null
    }
}
