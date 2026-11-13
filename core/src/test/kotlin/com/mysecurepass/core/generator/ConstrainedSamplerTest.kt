package com.mysecurepass.core.generator

import com.mysecurepass.core.random.SecureRandomSource
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.log2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConstrainedSamplerTest {
    private val random = SecureRandomSource(SecureRandom())

    private fun isValid(s: String, length: Int, classes: List<CharClass>): Boolean {
        if (s.length != length) return false
        val all = classes.joinToString("") { it.chars }
        if (s.any { it !in all }) return false
        for (c in classes) {
            if (s.count { it in c.chars } < c.min) return false
            if (c.edgeForbidden && (s.first() in c.chars || s.last() in c.chars)) return false
        }
        return true
    }

    private fun enumerate(length: Int, classes: List<CharClass>): List<String> {
        val alphabet = classes.joinToString("") { it.chars }
        val out = ArrayList<String>()
        fun rec(prefix: String) {
            if (prefix.length == length) { if (isValid(prefix, length, classes)) out += prefix; return }
            for (c in alphabet) rec(prefix + c)
        }
        rec("")
        return out
    }

    private val cases = listOf(
        5 to listOf(CharClass("ab", 1), CharClass("12", 1), CharClass("#", 1, edgeForbidden = true)),
        4 to listOf(CharClass("abc", 0), CharClass("9", 2)),
        6 to listOf(CharClass("xy", 1), CharClass("01", 1), CharClass("!?", 2, edgeForbidden = true)),
        3 to listOf(CharClass("a", 0), CharClass("%&", 1, edgeForbidden = true)),
        1 to listOf(CharClass("ab", 0), CharClass("#", 0, edgeForbidden = true)),
        2 to listOf(CharClass("ab", 0), CharClass("#", 1, edgeForbidden = true)), // unsatisfiable
        5 to listOf(CharClass("ab", 0, edgeForbidden = true), CharClass("12", 1, edgeForbidden = true), CharClass("z", 0)),
    )

    @Test
    fun countMatchesBruteForce() {
        for ((length, classes) in cases) {
            val expected = enumerate(length, classes).size
            val sampler = ConstrainedSampler(length, classes)
            assertEquals(expected.toDouble(), sampler.count, 1e-6, "length=$length classes=$classes")
        }
    }

    @Test
    fun unsatisfiableIsReported() {
        val sampler = ConstrainedSampler(2, listOf(CharClass("ab"), CharClass("#", 1, edgeForbidden = true)))
        assertFalse(sampler.isSatisfiable)
        assertEquals(0.0, sampler.entropyBits)
    }

    @Test
    fun samplesAlwaysSatisfyConstraints() {
        for ((length, classes) in cases) {
            val sampler = ConstrainedSampler(length, classes)
            if (!sampler.isSatisfiable) continue
            repeat(2_000) {
                val s = sampler.sample(random)
                assertTrue(isValid(s, length, classes), "invalid sample '$s' for $classes")
            }
        }
    }

    /** Chi-square goodness-of-fit: every valid string must be equally likely. */
    @Test
    fun samplingIsUniform() {
        val length = 5
        val classes = listOf(CharClass("ab", 1), CharClass("12", 1), CharClass("#", 1, edgeForbidden = true))
        val valid = enumerate(length, classes)
        val sampler = ConstrainedSampler(length, classes)
        val n = 200 * valid.size
        val counts = HashMap<String, Int>()
        repeat(n) { val s = sampler.sample(random); counts[s] = (counts[s] ?: 0) + 1 }
        assertEquals(valid.toSet(), counts.keys, "every valid string should appear")
        val expected = n.toDouble() / valid.size
        val chi2 = valid.sumOf { val d = (counts[it] ?: 0) - expected; d * d / expected }
        val df = valid.size - 1
        // Mean df, sd sqrt(2 df). Allow 5 standard deviations to keep the test non-flaky.
        val limit = df + 5 * Math.sqrt(2.0 * df)
        assertTrue(chi2 < limit, "chi2=$chi2 exceeds $limit (df=$df) — sampler is biased")
    }

    @Test
    fun entropyEqualsLog2Count() {
        val sampler = ConstrainedSampler(16, listOf(CharClass(CharSets.LOWER)))
        assertTrue(abs(sampler.entropyBits - 16 * log2(26.0)) < 1e-9)
    }

    @Test
    fun maxLengthDoesNotOverflow() {
        val classes = PasswordGenerator.classesFor(PasswordConfig(length = 128, symbolSet = SymbolSets.ALL))
        val sampler = ConstrainedSampler(128, classes)
        assertTrue(sampler.count.isFinite())
        assertTrue(sampler.entropyBits > 800)
        assertEquals(128, sampler.sample(random).length)
    }
}
