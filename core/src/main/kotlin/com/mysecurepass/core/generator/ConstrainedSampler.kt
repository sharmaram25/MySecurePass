package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource
import com.mysecurepass.core.random.secureShuffle
import kotlin.math.ln
import kotlin.math.pow

/**
 * A set of characters that may appear in a password together with a minimum count.
 *
 * @property edgeForbidden if true, characters of this class may not appear in the first or last position.
 */
data class CharClass(
    val chars: String,
    val min: Int = 0,
    val edgeForbidden: Boolean = false,
) {
    init {
        require(chars.isNotEmpty()) { "character class must not be empty" }
        require(min >= 0) { "min must be >= 0" }
    }

    val size: Int get() = chars.length
}

/**
 * Draws strings **exactly uniformly** from the set of all strings of a given length that satisfy
 * per-class minimum counts and edge restrictions, and reports the exact size of that set.
 *
 * Why this matters: the common "generate, then overwrite random positions to meet the rules"
 * approach produces a non-uniform distribution whose real entropy is unknown. Here we count the
 * valid strings with dynamic programming, pick a class composition with probability proportional
 * to the number of strings having it, then choose positions and characters uniformly. Every
 * valid string is therefore equally likely and `entropy = log2(count)` holds exactly.
 *
 * Counts are kept as doubles: with length <= [MAX_LENGTH] and at most 256 distinct characters the
 * count stays below 2^1024. Relative rounding error (~1e-15) has no meaningful effect on sampling.
 *
 * Classes must be pairwise disjoint.
 */
class ConstrainedSampler(
    private val length: Int,
    classes: List<CharClass>,
) {
    private val restricted = classes.filter { it.edgeForbidden }
    private val free = classes.filterNot { it.edgeForbidden }

    /** Number of positions where edge-restricted classes may go. */
    private val interior: Int = when {
        restricted.isEmpty() -> length
        length <= 2 -> 0
        else -> length - 2
    }

    private val binom: Array<DoubleArray> = pascal(length)

    /** freeWays[i][r] = ways to fill r positions using free classes i.. with their minimums. */
    private val freeWays: Array<DoubleArray>

    /** restrictedWays[i][u] = ways to complete the string when restricted classes 0..i-1 used u interior slots. */
    private val restrictedWays: Array<DoubleArray>

    /** Total number of valid strings (as a double). 0 means the constraints are unsatisfiable. */
    val count: Double

    init {
        require(length in 1..MAX_LENGTH) { "length must be in 1..$MAX_LENGTH" }
        require(classes.isNotEmpty()) { "at least one character class is required" }
        val all = classes.joinToString("") { it.chars }
        require(all.toSet().size == all.length) { "character classes must be disjoint and free of duplicates" }

        freeWays = Array(free.size + 1) { DoubleArray(length + 1) }
        freeWays[free.size][0] = 1.0
        for (i in free.indices.reversed()) {
            val cls = free[i]
            for (r in 0..length) {
                var sum = 0.0
                for (n in cls.min..r) {
                    val rest = freeWays[i + 1][r - n]
                    if (rest == 0.0) continue
                    sum += binom[r][n] * cls.size.toDouble().pow(n) * rest
                }
                freeWays[i][r] = sum
            }
        }

        restrictedWays = Array(restricted.size + 1) { DoubleArray(interior + 1) }
        for (u in 0..interior) restrictedWays[restricted.size][u] = freeWays[0][length - u]
        for (i in restricted.indices.reversed()) {
            val cls = restricted[i]
            for (u in 0..interior) {
                var sum = 0.0
                val available = interior - u
                for (n in cls.min..available) {
                    val rest = restrictedWays[i + 1][u + n]
                    if (rest == 0.0) continue
                    sum += binom[available][n] * cls.size.toDouble().pow(n) * rest
                }
                restrictedWays[i][u] = sum
            }
        }
        count = restrictedWays[0][0]
    }

    val isSatisfiable: Boolean get() = count > 0.0

    /** Exact entropy in bits of a single sample. */
    val entropyBits: Double get() = if (count <= 0.0) 0.0 else ln(count) / LN2

    fun sample(random: RandomSource): String {
        check(isSatisfiable) { "constraints cannot be satisfied" }

        // 1. Choose how many characters each class contributes, weighted by number of completions.
        val restrictedCounts = IntArray(restricted.size)
        var used = 0
        for (i in restricted.indices) {
            val cls = restricted[i]
            val available = interior - used
            val n = weightedPick(random, cls.min..available) { n ->
                binom[available][n] * cls.size.toDouble().pow(n) * restrictedWays[i + 1][used + n]
            }
            restrictedCounts[i] = n
            used += n
        }
        val freeCounts = IntArray(free.size)
        var remaining = length - used
        for (i in free.indices) {
            val cls = free[i]
            val r = remaining
            val n = weightedPick(random, cls.min..r) { n ->
                binom[r][n] * cls.size.toDouble().pow(n) * freeWays[i + 1][r - n]
            }
            freeCounts[i] = n
            remaining -= n
        }
        check(remaining == 0)

        // 2. Assign positions uniformly.
        val out = CharArray(length)
        val interiorPositions = if (restricted.isEmpty()) IntArray(0) else IntArray(interior) { it + 1 }
        interiorPositions.secureShuffle(random)
        var cursor = 0
        val taken = BooleanArray(length)
        for (i in restricted.indices) {
            val cls = restricted[i]
            repeat(restrictedCounts[i]) {
                val pos = interiorPositions[cursor++]
                out[pos] = cls.chars[random.nextInt(cls.size)]
                taken[pos] = true
            }
        }
        val freePositions = (0 until length).filterNot { taken[it] }.toMutableList()
        freePositions.secureShuffle(random)
        cursor = 0
        for (i in free.indices) {
            val cls = free[i]
            repeat(freeCounts[i]) {
                out[freePositions[cursor++]] = cls.chars[random.nextInt(cls.size)]
            }
        }
        return String(out).also { out.fill('\u0000') }
    }

    private inline fun weightedPick(random: RandomSource, range: IntRange, weight: (Int) -> Double): Int {
        var total = 0.0
        for (n in range) total += weight(n)
        check(total > 0.0) { "no valid choice" }
        var x = random.nextDouble() * total
        var last = range.first
        for (n in range) {
            val w = weight(n)
            if (w == 0.0) continue
            last = n
            if (x < w) return n
            x -= w
        }
        return last // floating-point remainder lands on the last non-zero bucket
    }

    companion object {
        const val MAX_LENGTH = 128
        private val LN2 = ln(2.0)

        private fun pascal(n: Int): Array<DoubleArray> {
            val c = Array(n + 1) { DoubleArray(n + 1) }
            for (i in 0..n) {
                c[i][0] = 1.0
                for (j in 1..i) c[i][j] = c[i - 1][j - 1] + c[i - 1][j]
            }
            return c
        }
    }
}
