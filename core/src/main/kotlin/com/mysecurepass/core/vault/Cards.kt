package com.mysecurepass.core.vault

import java.util.Calendar

enum class CardBrand(val label: String) {
    VISA("Visa"), MASTERCARD("Mastercard"), AMEX("American Express"), DISCOVER("Discover"),
    RUPAY("RuPay"), JCB("JCB"), DINERS("Diners Club"), UNIONPAY("UnionPay"), MAESTRO("Maestro"), UNKNOWN("Card"),
}

/** Payment-card helpers: brand detection (IIN ranges), Luhn validation, formatting, expiry. */
object Cards {
    fun digits(number: String): String = number.filter(Char::isDigit)

    fun brand(number: String): CardBrand {
        val d = digits(number)
        if (d.isEmpty()) return CardBrand.UNKNOWN
        fun prefix(n: Int) = d.take(n).toIntOrNull() ?: -1
        val p6 = prefix(6)
        return when {
            d.startsWith("34") || d.startsWith("37") -> CardBrand.AMEX
            d.startsWith("4") -> CardBrand.VISA
            prefix(2) in 51..55 || prefix(4) in 2221..2720 -> CardBrand.MASTERCARD
            p6 in 508500..508999 || p6 in 606985..607984 || p6 in 608001..608500 || p6 in 652150..653149 || prefix(2) in 81..82 -> CardBrand.RUPAY
            d.startsWith("6011") || prefix(3) in 644..649 || d.startsWith("65") -> CardBrand.DISCOVER
            prefix(4) in 3528..3589 -> CardBrand.JCB
            prefix(3) in 300..305 || d.startsWith("36") || d.startsWith("38") -> CardBrand.DINERS
            d.startsWith("62") -> CardBrand.UNIONPAY
            d.startsWith("50") || prefix(2) in 56..69 -> CardBrand.MAESTRO
            else -> CardBrand.UNKNOWN
        }
    }

    /** Luhn (mod 10) checksum, used by all major card networks. */
    fun isLuhnValid(number: String): Boolean {
        val d = digits(number)
        if (d.length !in 12..19) return false
        var sum = 0
        d.reversed().forEachIndexed { i, c ->
            var v = c - '0'
            if (i % 2 == 1) { v *= 2; if (v > 9) v -= 9 }
            sum += v
        }
        return sum % 10 == 0
    }

    /** Groups digits for display: Amex 4-6-5, others in fours. */
    fun format(number: String): String {
        val d = digits(number)
        if (brand(d) == CardBrand.AMEX) {
            return listOf(d.take(4), d.drop(4).take(6), d.drop(10)).filter { it.isNotEmpty() }.joinToString(" ")
        }
        return d.chunked(4).joinToString(" ")
    }

    fun masked(number: String): String {
        val d = digits(number)
        return if (d.length < 4) "••••" else "•••• ${d.takeLast(4)}"
    }

    /** Parses "MM/YY" (or "MM/YYYY"); returns (month, fullYear) or null. */
    fun parseExpiry(expiry: String): Pair<Int, Int>? {
        val parts = expiry.trim().split('/', '-', ' ').filter { it.isNotEmpty() }
        if (parts.size != 2) return null
        val month = parts[0].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        val year = when {
            parts[1].length == 2 -> 2000 + y
            parts[1].length == 4 -> y
            else -> return null
        }
        return month to year
    }

    /** True if the card has expired as of [now] (cards are valid through the end of the expiry month). */
    fun isExpired(expiry: String, now: Long): Boolean {
        val (m, y) = parseExpiry(expiry) ?: return false
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val cy = cal.get(Calendar.YEAR); val cm = cal.get(Calendar.MONTH) + 1
        return y < cy || (y == cy && m < cm)
    }
}
