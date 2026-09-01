package com.dawood.orbit.tools.password

import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Password generation.
 *
 * Uses [SecureRandom] rather than `Random`, and guarantees at least one
 * character from every selected set — a password that happens to contain no
 * digit is weaker than the settings promise.
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

    /** Characters that are easy to misread when typed from a screen. */
    private const val AMBIGUOUS = "Il1O0o|`'\""

    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 64

    data class Options(
        val length: Int = 20,
        val lowercase: Boolean = true,
        val uppercase: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val avoidAmbiguous: Boolean = false,
    )

    data class Generated(val password: String, val entropyBits: Int, val poolSize: Int)

    private val random = SecureRandom()

    /** The set a password will be drawn from, after filtering. */
    fun pool(options: Options): String {
        val builder = StringBuilder()
        if (options.lowercase) builder.append(LOWER)
        if (options.uppercase) builder.append(UPPER)
        if (options.digits) builder.append(DIGITS)
        if (options.symbols) builder.append(SYMBOLS)
        val raw = builder.toString()
        return if (options.avoidAmbiguous) raw.filterNot { it in AMBIGUOUS } else raw
    }

    /** Returns null when every character set has been switched off. */
    fun generate(options: Options): Generated? {
        val length = options.length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val pool = pool(options)
        if (pool.isEmpty()) return null

        val required = buildList {
            if (options.lowercase) add(filtered(LOWER, options))
            if (options.uppercase) add(filtered(UPPER, options))
            if (options.digits) add(filtered(DIGITS, options))
            if (options.symbols) add(filtered(SYMBOLS, options))
        }.filter { it.isNotEmpty() }

        val characters = ArrayList<Char>(length)
        // Seed one from each selected set so the result honours the settings,
        // then fill the rest freely and shuffle so positions stay unpredictable.
        required.take(length).forEach { set -> characters += set[random.nextInt(set.length)] }
        while (characters.size < length) {
            characters += pool[random.nextInt(pool.length)]
        }
        for (index in characters.indices.reversed()) {
            val swap = random.nextInt(index + 1)
            val held = characters[index]
            characters[index] = characters[swap]
            characters[swap] = held
        }

        return Generated(
            password = characters.joinToString(""),
            entropyBits = entropyBits(length, pool.length),
            poolSize = pool.length,
        )
    }

    fun entropyBits(length: Int, poolSize: Int): Int {
        if (poolSize <= 1 || length <= 0) return 0
        return (length * ln(poolSize.toDouble()) / ln(2.0)).roundToInt()
    }

    /** A plain-language reading of the entropy figure. */
    fun strengthLabel(entropyBits: Int): String = when {
        entropyBits < 45 -> "Weak"
        entropyBits < 70 -> "Fair"
        entropyBits < 100 -> "Strong"
        else -> "Very strong"
    }

    private fun filtered(set: String, options: Options): String =
        if (options.avoidAmbiguous) set.filterNot { it in AMBIGUOUS } else set
}
