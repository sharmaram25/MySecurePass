package com.mysecurepass.core.strength

import kotlin.math.log10
import kotlin.math.pow

enum class Rating(val label: String) {
    VERY_WEAK("Very weak"),
    WEAK("Weak"),
    FAIR("Fair"),
    STRONG("Strong"),
    EXCELLENT("Excellent");

    companion object {
        /**
         * Thresholds for *randomly generated* secrets, where entropy is exact.
         * 70+ bits resists offline GPU attacks against fast hashes for decades; 100+ is beyond any
         * foreseeable classical attacker.
         */
        fun fromBits(bits: Double): Rating = when {
            bits < 30 -> VERY_WEAK
            bits < 50 -> WEAK
            bits < 70 -> FAIR
            bits < 100 -> STRONG
            else -> EXCELLENT
        }

        /** Maps a zxcvbn score (0–4) to a rating. */
        fun fromScore(score: Int): Rating = entries[score.coerceIn(0, 4)]
    }
}

/** An attacker model: how many guesses per second they can make. */
enum class AttackScenario(val label: String, val detail: String, val guessesPerSecond: Double) {
    ONLINE("Online attack", "Website with rate limiting, ~10 guesses/s", 10.0),
    OFFLINE_SLOW("Offline, strong hashing", "Stolen database hashed with bcrypt / Argon2, ~10k guesses/s", 1e4),
    OFFLINE_FAST("Offline, weak hashing", "Stolen MD5/SHA-1 hashes on a GPU cluster, ~1 trillion guesses/s", 1e12),
}

object CrackTime {
    private const val SECONDS_PER_YEAR = 31_557_600.0
    private const val AGE_OF_UNIVERSE_YEARS = 1.38e10

    /** Expected time to crack (half the keyspace) in seconds, given log2 of the number of guesses. */
    fun secondsForBits(bits: Double, scenario: AttackScenario): Double =
        2.0.pow(bits - 1.0) / scenario.guessesPerSecond

    /** Same, for an estimated number of guesses expressed as log10 (zxcvbn style). */
    fun secondsForLog10Guesses(log10Guesses: Double, scenario: AttackScenario): Double =
        10.0.pow(log10Guesses) / 2.0 / scenario.guessesPerSecond

    fun format(seconds: Double): String {
        if (seconds.isNaN()) return "—"
        if (seconds < 1) return "Instantly"
        val minute = 60.0; val hour = 3600.0; val day = 86400.0; val month = SECONDS_PER_YEAR / 12
        fun plural(n: Long, unit: String) = if (n == 1L) "1 $unit" else "$n ${unit}s"
        return when {
            seconds < minute -> plural(seconds.toLong(), "second")
            seconds < hour -> plural((seconds / minute).toLong(), "minute")
            seconds < day -> plural((seconds / hour).toLong(), "hour")
            seconds < month -> plural((seconds / day).toLong(), "day")
            seconds < SECONDS_PER_YEAR -> plural((seconds / month).toLong(), "month")
            else -> formatYears(seconds / SECONDS_PER_YEAR)
        }
    }

    private fun formatYears(years: Double): String = when {
        years < 1_000 -> if (years < 2) "1 year" else "${years.toLong()} years"
        years < 1e6 -> "${trim(years / 1e3)} thousand years"
        years < 1e9 -> "${trim(years / 1e6)} million years"
        years < AGE_OF_UNIVERSE_YEARS -> "${trim(years / 1e9)} billion years"
        else -> {
            val times = years / AGE_OF_UNIVERSE_YEARS
            if (times < 1e6) "${trim(times)}× age of the universe"
            else "10^${log10(times).toInt()}× age of the universe"
        }
    }

    private fun trim(v: Double): String = if (v < 10) "%.1f".format(v).removeSuffix(".0") else v.toLong().toString()
}
