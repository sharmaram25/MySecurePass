package com.mysecurepass.core.generator

import com.mysecurepass.core.random.RandomSource
import kotlin.math.log2

/**
 * Numeric PIN generator. With [PinConfig.avoidWeak], PINs that attackers try first (repeated
 * digits, straight runs, keypad lines, dates, mirrored halves) are rejected and re-drawn, which
 * keeps the result uniform over the remaining PINs. Entropy is computed exactly by enumeration
 * for short PINs; for longer ones the rejected fraction is far below 0.01 bits and is ignored.
 */
object PinGenerator {
    private const val MAX_TRIES = 10_000
    private const val ENUMERATE_UP_TO = 6

    private val keypadPatterns = setOf(
        "2580", "0852", "1470", "0741", "3690", "0963", "1590", "0951", "3570", "0753",
        "1357", "7531", "2468", "8642", "1379", "9731", "1397", "7913", "1234", "4321",
    )

    private val entropyCache = HashMap<Int, Double>()

    fun isWeak(pin: String): Boolean {
        if (pin.isEmpty()) return true
        val d = pin.map { it - '0' }
        if (d.all { it == d[0] }) return true
        if (d.zipWithNext().all { (a, b) -> b - a == 1 }) return true
        if (d.zipWithNext().all { (a, b) -> a - b == 1 }) return true
        if (pin.length % 2 == 0 && pin.substring(0, pin.length / 2) == pin.substring(pin.length / 2)) return true
        if (pin.length % 2 == 0 && pin.substring(0, pin.length / 2) == pin.substring(pin.length / 2).reversed()) return true
        if (pin.length == 4) {
            if (pin in keypadPatterns) return true
            val n = pin.toInt()
            if (n in 1900..2099) return true // years
            val mm = pin.substring(0, 2).toInt(); val dd = pin.substring(2).toInt()
            if (mm in 1..12 && dd in 1..31) return true // MMDD
            if (dd in 1..12 && mm in 1..31) return true // DDMM
        }
        if (pin.length == 6) {
            // DDMMYY / MMDDYY birthdays
            val a = pin.substring(0, 2).toInt(); val b = pin.substring(2, 4).toInt()
            if ((a in 1..31 && b in 1..12) || (a in 1..12 && b in 1..31)) return true
        }
        return false
    }

    fun entropyBits(config: PinConfig): Double {
        val length = config.length.coerceIn(PinConfig.MIN_LENGTH, PinConfig.MAX_LENGTH)
        val full = length * log2(10.0)
        if (!config.avoidWeak || length > ENUMERATE_UP_TO) return full
        return synchronized(entropyCache) {
            entropyCache.getOrPut(length) {
                var total = 1
                repeat(length) { total *= 10 }
                var valid = 0
                val buf = CharArray(length)
                for (n in 0 until total) {
                    var x = n
                    for (i in length - 1 downTo 0) { buf[i] = '0' + (x % 10); x /= 10 }
                    if (!isWeak(String(buf))) valid++
                }
                log2(valid.toDouble())
            }
        }
    }

    fun generate(config: PinConfig, random: RandomSource): Generated {
        val length = config.length.coerceIn(PinConfig.MIN_LENGTH, PinConfig.MAX_LENGTH)
        val buf = CharArray(length)
        repeat(MAX_TRIES) {
            for (i in 0 until length) buf[i] = '0' + random.nextInt(10)
            val pin = String(buf)
            if (!config.avoidWeak || !isWeak(pin)) {
                buf.fill('0')
                return Generated(pin, entropyBits(config))
            }
        }
        error("unable to generate a PIN")
    }
}
