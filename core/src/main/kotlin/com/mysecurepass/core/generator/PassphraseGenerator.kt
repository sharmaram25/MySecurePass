package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource
import kotlin.math.log2

/** An indexed list of distinct words. */
interface WordList {
    val size: Int
    operator fun get(index: Int): String
}

/**
 * The EFF Large Wordlist (7,776 words, CC-BY 3.0, https://www.eff.org/dice), bundled as a resource.
 * The four hyphenated entries are removed so that hyphen-separated passphrases stay unambiguous,
 * leaving 7,772 words (~12.92 bits per word). Loaded lazily on first use.
 */
object EffWordList : WordList {
    private const val RESOURCE = "/wordlists/eff_large.txt"

    private val words: Array<String> by lazy {
        val stream = EffWordList::class.java.getResourceAsStream(RESOURCE)
            ?: error("Missing word list resource $RESOURCE")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }
                .distinct()
                .toList()
                .toTypedArray()
        }
    }

    override val size: Int get() = words.size
    override fun get(index: Int): String = words[index]
}

object PassphraseGenerator {
    const val SEPARATOR_SYMBOLS = "!@#$%^&*-_+=?"
    private const val DIGITS = "0123456789"

    fun entropyBits(config: PassphraseConfig, wordList: WordList): Double {
        val words = config.words.coerceIn(PassphraseConfig.MIN_WORDS, PassphraseConfig.MAX_WORDS)
        var bits = words * log2(wordList.size.toDouble())
        bits += when (config.separator) {
            Separator.RANDOM_DIGIT -> (words - 1) * log2(DIGITS.length.toDouble())
            Separator.RANDOM_SYMBOL -> (words - 1) * log2(SEPARATOR_SYMBOLS.length.toDouble())
            else -> 0.0
        }
        if (config.capitalization == Capitalization.RANDOM) bits += words.toDouble()
        // One digit appended to one randomly chosen word.
        if (config.includeNumber) bits += log2(DIGITS.length.toDouble() * words)
        return bits
    }

    fun generate(config: PassphraseConfig, wordList: WordList, random: RandomSource): Generated {
        val count = config.words.coerceIn(PassphraseConfig.MIN_WORDS, PassphraseConfig.MAX_WORDS)
        val words = MutableList(count) { wordList[random.nextInt(wordList.size)] }
        for (i in words.indices) {
            words[i] = when (config.capitalization) {
                Capitalization.NONE -> words[i]
                Capitalization.FIRST_LETTER -> words[i].replaceFirstChar { it.uppercaseChar() }
                Capitalization.UPPERCASE -> words[i].uppercase()
                Capitalization.RANDOM -> if (random.nextInt(2) == 1) words[i].uppercase() else words[i]
            }
        }
        if (config.includeNumber) {
            val idx = random.nextInt(count)
            words[idx] = words[idx] + DIGITS[random.nextInt(DIGITS.length)]
        }
        val value = buildString {
            words.forEachIndexed { i, w ->
                if (i > 0) {
                    when (config.separator) {
                        Separator.RANDOM_DIGIT -> append(DIGITS[random.nextInt(DIGITS.length)])
                        Separator.RANDOM_SYMBOL -> append(SEPARATOR_SYMBOLS[random.nextInt(SEPARATOR_SYMBOLS.length)])
                        else -> append(config.separator.literal)
                    }
                }
                append(w)
            }
        }
        return Generated(value, entropyBits(config, wordList))
    }
}
