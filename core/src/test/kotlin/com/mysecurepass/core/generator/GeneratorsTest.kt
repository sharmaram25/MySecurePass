package com.mysecurepass.core.generator

import com.mysecurepass.core.random.SecureRandomSource
import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratorsTest {
    private val random = SecureRandomSource()
    private val generator = Generator(random)

    @Test
    fun passwordRespectsAllRules() {
        val config = PasswordConfig(
            length = 24, minDigits = 3, minSymbols = 4, avoidAmbiguous = true,
            noSymbolsAtEdges = true, excludeChars = "xyzXYZ",
        )
        repeat(2_000) {
            val p = generator.generate(config).value
            assertEquals(24, p.length)
            assertTrue(p.count { it.isDigit() } >= 3)
            assertTrue(p.count { it in config.symbolSet } >= 4)
            assertTrue(p.any { it.isUpperCase() } && p.any { it.isLowerCase() })
            assertFalse(p.first() in config.symbolSet || p.last() in config.symbolSet)
            assertTrue(p.none { it in CharSets.AMBIGUOUS || it in "xyzXYZ" })
        }
    }

    @Test
    fun impossibleMinimumsAreClampedNotCrashing() {
        val config = PasswordConfig(length = 4, minDigits = 10, minSymbols = 10, noSymbolsAtEdges = true)
        val g = generator.generate(config)
        assertEquals(4, g.value.length)
        assertTrue(g.entropyBits > 0)
    }

    @Test
    fun noCharacterTypesFallsBackToLowercase() {
        val g = generator.generate(PasswordConfig(uppercase = false, lowercase = false, digits = false, symbols = false))
        assertTrue(g.value.all { it in CharSets.LOWER })
    }

    @Test
    fun symbolSetCannotLeakLettersOrSpaces() {
        val classes = PasswordGenerator.classesFor(PasswordConfig(symbolSet = "a! b?"))
        val symbols = classes.last().chars
        assertEquals("!?", symbols)
    }

    @Test
    fun passphraseStructureAndEntropy() {
        val config = PassphraseConfig(words = 6, separator = Separator.PERIOD, capitalization = Capitalization.FIRST_LETTER, includeNumber = true)
        val g = generator.generate(config)
        val parts = g.value.split('.')
        assertEquals(6, parts.size)
        assertTrue(parts.all { it[0].isUpperCase() })
        assertEquals(1, g.value.count { it.isDigit() })
        val expected = 6 * log2(EffWordList.size.toDouble()) + log2(60.0)
        assertTrue(abs(g.entropyBits - expected) < 1e-9)
    }

    @Test
    fun wordListIsCleanEff() {
        assertEquals(7772, EffWordList.size)
        assertTrue((0 until EffWordList.size).all { EffWordList[it].all { c -> c in 'a'..'z' } })
    }

    @Test
    fun randomSeparatorsAddEntropy() {
        val base = PassphraseGenerator.entropyBits(PassphraseConfig(words = 4, includeNumber = false), EffWordList)
        val withDigits = PassphraseGenerator.entropyBits(PassphraseConfig(words = 4, separator = Separator.RANDOM_DIGIT, includeNumber = false), EffWordList)
        assertTrue(abs(withDigits - base - 3 * log2(10.0)) < 1e-9)
    }

    @Test
    fun pinsAvoidWeakPatterns() {
        repeat(5_000) {
            val pin = generator.generate(PinConfig(4, avoidWeak = true)).value
            assertFalse(PinGenerator.isWeak(pin), pin)
        }
        listOf("0000", "1234", "4321", "2580", "1987", "1212", "1221", "0412").forEach {
            assertTrue(PinGenerator.isWeak(it), it)
        }
        val bits = PinGenerator.entropyBits(PinConfig(4, true))
        assertTrue(bits < log2(10_000.0) && bits > 13.0)
    }

    @Test
    fun tokens() {
        assertEquals(64, generator.generate(TokenConfig(TokenFormat.HEX, 32)).value.length)
        assertEquals(43, generator.generate(TokenConfig(TokenFormat.BASE64URL, 32)).value.length)
        assertEquals(20, generator.generate(TokenConfig(TokenFormat.ALPHANUMERIC, 20)).value.length)
        val uuid = generator.generate(TokenConfig(TokenFormat.UUID)).value
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(uuid), uuid)
    }

    @Test
    fun presetsProduceValidOutput() {
        Preset.entries.forEach { preset ->
            val settings = preset.settings(GeneratorSettings())
            val g = generator.generate(settings)
            assertTrue(g.value.isNotEmpty() && g.entropyBits > 15, preset.name)
        }
    }

    @Test
    fun generatedToStringDoesNotLeakSecret() {
        val g = generator.generate(PasswordConfig())
        assertFalse(g.toString().contains(g.value))
    }
}
