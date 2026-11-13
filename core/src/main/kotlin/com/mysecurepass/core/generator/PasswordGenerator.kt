package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource

/**
 * Character-based password generator. Translates a [PasswordConfig] into disjoint character
 * classes and delegates to [ConstrainedSampler] for exactly-uniform sampling.
 */
object PasswordGenerator {

    /** Builds the character classes for a config, applying exclusions and clamping minimums. */
    fun classesFor(config: PasswordConfig): List<CharClass> {
        val excluded = buildSet {
            config.excludeChars.forEach { add(it) }
            if (config.avoidAmbiguous) CharSets.AMBIGUOUS.forEach { add(it) }
        }

        fun filtered(chars: String) = chars.toCharArray().distinct().filterNot { it in excluded }.joinToString("")

        val letterDigit = CharSets.UPPER + CharSets.LOWER + CharSets.DIGITS
        val upper = if (config.uppercase) filtered(CharSets.UPPER) else ""
        val lower = if (config.lowercase) filtered(CharSets.LOWER) else ""
        val digits = if (config.digits) filtered(CharSets.DIGITS) else ""
        // Symbols may only contain non-alphanumeric, non-whitespace characters so classes stay disjoint.
        val symbols = if (config.symbols) {
            filtered(config.symbolSet.filterNot { it in letterDigit || it.isWhitespace() })
        } else ""

        val length = config.length.coerceIn(PasswordConfig.MIN_LENGTH, PasswordConfig.MAX_LENGTH)

        data class Spec(val chars: String, val wantedMin: Int, val edge: Boolean)
        val specs = listOf(
            Spec(upper, 1, false),
            Spec(lower, 1, false),
            Spec(digits, config.minDigits.coerceAtLeast(0), false),
            Spec(symbols, config.minSymbols.coerceAtLeast(0), config.noSymbolsAtEdges),
        ).filter { it.chars.isNotEmpty() }

        if (specs.isEmpty()) return listOf(CharClass(CharSets.LOWER, 0))

        // Clamp minimums so they fit: symbols cannot sit on edges, and the total must fit the length.
        val mins = specs.map { it.wantedMin }.toMutableList()
        specs.forEachIndexed { i, s ->
            if (s.edge) mins[i] = mins[i].coerceAtMost(if (length <= 2) 0 else length - 2)
        }
        var i = specs.lastIndex
        while (mins.sum() > length && i >= 0) {
            val excess = mins.sum() - length
            mins[i] = (mins[i] - excess).coerceAtLeast(0)
            i--
        }
        return specs.mapIndexed { idx, s -> CharClass(s.chars, mins[idx], s.edge) }
    }

    fun sampler(config: PasswordConfig): ConstrainedSampler =
        ConstrainedSampler(config.length.coerceIn(PasswordConfig.MIN_LENGTH, PasswordConfig.MAX_LENGTH), classesFor(config))

    fun generate(config: PasswordConfig, random: RandomSource): Generated {
        val sampler = sampler(config)
        return Generated(sampler.sample(random), sampler.entropyBits)
    }
}
