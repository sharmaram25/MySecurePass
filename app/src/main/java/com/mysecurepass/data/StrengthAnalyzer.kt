package com.mysecurepass.data

import com.mysecurepass.core.strength.AttackScenario
import com.mysecurepass.core.strength.CrackTime
import com.nulabinc.zxcvbn.Zxcvbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PatternPart(val token: String, val kind: String)

data class Analysis(
    val score: Int,
    val guessesLog10: Double,
    val warning: String?,
    val suggestions: List<String>,
    val parts: List<PatternPart>,
    val crackTimes: List<Pair<AttackScenario, String>>,
    val length: Int,
)

/**
 * Realistic strength estimation for human-chosen passwords using zxcvbn (Dropbox's estimator):
 * dictionary words, names, common passwords, keyboard walks, dates, repeats, l33t substitutions.
 * Runs fully offline; dictionaries ship inside the app.
 */
class StrengthAnalyzer {
    private val zxcvbn by lazy { Zxcvbn() }

    /** Loads dictionaries ahead of time so the first keystroke is instant. */
    suspend fun warmUp() = withContext(Dispatchers.Default) { zxcvbn.measure("warmup") ; Unit }

    suspend fun analyze(password: String): Analysis = withContext(Dispatchers.Default) { analyzeBlocking(password) }

    fun score(password: String): Int {
        val s = zxcvbn.measure(password)
        return s.score.also { s.wipe() }
    }

    fun analyzeBlocking(password: String): Analysis {
        val s = zxcvbn.measure(password)
        try {
            val feedback = s.feedback
            val log10 = s.guessesLog10
            return Analysis(
                score = s.score,
                guessesLog10 = log10,
                warning = feedback.warning?.takeIf { it.isNotBlank() },
                suggestions = feedback.suggestions.orEmpty(),
                parts = s.sequence.map { m -> PatternPart(m.token.toString(), describe(m.pattern.name)) },
                crackTimes = AttackScenario.entries.map { it to CrackTime.format(CrackTime.secondsForLog10Guesses(log10, it)) },
                length = password.length,
            )
        } finally {
            s.wipe()
        }
    }

    private fun describe(pattern: String) = when (pattern.lowercase()) {
        "dictionary" -> "Common word"
        "spatial" -> "Keyboard pattern"
        "repeat" -> "Repetition"
        "sequence" -> "Sequence"
        "regex" -> "Recognisable pattern"
        "date" -> "Date"
        else -> "Random"
    }
}
