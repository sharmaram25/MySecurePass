package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource
import com.mysecurepass.core.random.SecureRandomSource

/** Single entry point for all generation modes. Thread-safe. */
class Generator(
    private val random: RandomSource = SecureRandomSource(),
    private val wordList: WordList = EffWordList,
) {
    fun generate(config: GeneratorConfig): Generated = when (config) {
        is PasswordConfig -> PasswordGenerator.generate(config, random)
        is PassphraseConfig -> PassphraseGenerator.generate(config, wordList, random)
        is PinConfig -> PinGenerator.generate(config, random)
        is TokenConfig -> TokenGenerator.generate(config, random)
    }

    fun generate(settings: GeneratorSettings): Generated = generate(settings.active)

    fun entropyBits(config: GeneratorConfig): Double = when (config) {
        is PasswordConfig -> PasswordGenerator.sampler(config).entropyBits
        is PassphraseConfig -> PassphraseGenerator.entropyBits(config, wordList)
        is PinConfig -> PinGenerator.entropyBits(config)
        is TokenConfig -> TokenGenerator.entropyBits(config)
    }
}

/** Ready-made configurations for common situations. */
enum class Preset(val label: String, val description: String, val settings: (GeneratorSettings) -> GeneratorSettings) {
    STRONG("Strong", "20 chars, all types", {
        it.copy(mode = GeneratorMode.PASSWORD, password = PasswordConfig())
    }),
    MAXIMUM("Maximum", "32 chars, all symbols", {
        it.copy(mode = GeneratorMode.PASSWORD, password = PasswordConfig(length = 32, symbolSet = SymbolSets.ALL, minDigits = 2, minSymbols = 2))
    }),
    MEMORABLE("Memorable", "5-word passphrase", {
        it.copy(mode = GeneratorMode.PASSPHRASE, passphrase = PassphraseConfig())
    }),
    WIFI("Wi‑Fi", "Easy to type on TVs", {
        it.copy(mode = GeneratorMode.PASSWORD, password = PasswordConfig(length = 20, symbols = false, avoidAmbiguous = true, minDigits = 2))
    }),
    LEGACY("Strict sites", "16 chars, safe symbols", {
        it.copy(mode = GeneratorMode.PASSWORD, password = PasswordConfig(length = 16, symbolSet = "!@#$%*", avoidAmbiguous = true, noSymbolsAtEdges = true))
    }),
    PIN("PIN", "6 digits, no patterns", {
        it.copy(mode = GeneratorMode.PIN, pin = PinConfig(6, true))
    }),
    API_KEY("API key", "256-bit hex", {
        it.copy(mode = GeneratorMode.TOKEN, token = TokenConfig(TokenFormat.HEX, 32))
    }),
}
