package com.mysecurepass.core.generator

import kotlinx.serialization.Serializable

/** Result of a generation: the secret plus the exact entropy of the process that produced it. */
class Generated(val value: String, val entropyBits: Double) {
    override fun toString(): String = "Generated(<redacted>, ${"%.1f".format(entropyBits)} bits)"
}

sealed interface GeneratorConfig

@Serializable
enum class GeneratorMode { PASSWORD, PASSPHRASE, PIN, TOKEN }

object SymbolSets {
    /** Accepted by virtually every site's password rules. */
    const val COMMON = "!@#$%^&*-_+=?"
    /** All printable ASCII punctuation. */
    const val ALL = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
}

object CharSets {
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val DIGITS = "0123456789"
    /** Characters easily confused with each other in common fonts. */
    const val AMBIGUOUS = "Il1|O0o`'\""
}

@Serializable
data class PasswordConfig(
    val length: Int = 20,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val minDigits: Int = 1,
    val minSymbols: Int = 1,
    val avoidAmbiguous: Boolean = false,
    val noSymbolsAtEdges: Boolean = false,
    val symbolSet: String = SymbolSets.COMMON,
    val excludeChars: String = "",
) : GeneratorConfig {
    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = ConstrainedSampler.MAX_LENGTH
    }
}

@Serializable
enum class Separator(val literal: String?) {
    HYPHEN("-"), PERIOD("."), UNDERSCORE("_"), SPACE(" "), COMMA(","), NONE(""),
    /** A random digit between each pair of words (adds entropy). */
    RANDOM_DIGIT(null),
    /** A random symbol from [PassphraseGenerator.SEPARATOR_SYMBOLS] between words (adds entropy). */
    RANDOM_SYMBOL(null),
}

@Serializable
enum class Capitalization { NONE, FIRST_LETTER, UPPERCASE, RANDOM }

@Serializable
data class PassphraseConfig(
    val words: Int = 5,
    val separator: Separator = Separator.HYPHEN,
    val capitalization: Capitalization = Capitalization.FIRST_LETTER,
    val includeNumber: Boolean = true,
) : GeneratorConfig {
    companion object {
        const val MIN_WORDS = 3
        const val MAX_WORDS = 15
    }
}

@Serializable
data class PinConfig(
    val length: Int = 6,
    val avoidWeak: Boolean = true,
) : GeneratorConfig {
    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 16
    }
}

@Serializable
enum class TokenFormat { HEX, BASE64URL, ALPHANUMERIC, UUID }

@Serializable
data class TokenConfig(
    val format: TokenFormat = TokenFormat.HEX,
    /** Bytes for HEX/BASE64URL, characters for ALPHANUMERIC, ignored for UUID. */
    val size: Int = 32,
) : GeneratorConfig {
    companion object {
        const val MIN_SIZE = 8
        const val MAX_SIZE = 128
    }
}

/** All per-mode configs, so switching modes never loses the user's settings. */
@Serializable
data class GeneratorSettings(
    val mode: GeneratorMode = GeneratorMode.PASSWORD,
    val password: PasswordConfig = PasswordConfig(),
    val passphrase: PassphraseConfig = PassphraseConfig(),
    val pin: PinConfig = PinConfig(),
    val token: TokenConfig = TokenConfig(),
) {
    val active: GeneratorConfig
        get() = when (mode) {
            GeneratorMode.PASSWORD -> password
            GeneratorMode.PASSPHRASE -> passphrase
            GeneratorMode.PIN -> pin
            GeneratorMode.TOKEN -> token
        }
}
