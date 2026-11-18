package com.example.voice

import kotlin.math.abs

data class WakeWordMatchResult(
    val matched: Boolean,
    val matchedPhrase: String = "",
    val remainingCommand: String = "",
    val isStopCommand: Boolean = false,
    val isPermanentOffCommand: Boolean = false,
    val confidence: Float = 0f
)

/**
 * Custom Wake Word & Stop-Command Detection engine for hands-free SpeechRecognizer transcripts.
 * Supports:
 * - User-defined custom wake phrases (e.g. "MYRA", "Hey Nova", "Suno Myra", "Jarvis")
 * - Built-in Hindi / Hinglish / English phonetic variants when targeting MYRA
 * - Configurable phonetic edit-distance tolerance for noisy microphones
 * - Inline command extraction (e.g. "Hey MYRA open WhatsApp" -> remainingCommand = "open WhatsApp")
 */
object WakeWordDetector {

    private val myraPhoneticAliases = listOf(
        "hey myra",
        "hi myra",
        "ok myra",
        "suno myra",
        "सुनो मायरा",
        "हे मायरा",
        "मायरा",
        "माइरा",
        "myra",
        "maira",
        "mayra",
        "myrah",
        "mira",
        "meera"
    )

    private val stopPhrases = listOf(
        "myra stop",
        "मायरा स्टॉप",
        "stop listening",
        "stop",
        "रुको",
        "चुप",
        "chup"
    )

    private val permanentOffPhrases = listOf(
        "myra off",
        "मायरा बंद",
        "turn off myra",
        "turn off"
    )

    fun evaluate(
        transcript: String,
        customWakeWord: String = "MYRA",
        sensitivity: Float = 0.8f
    ): WakeWordMatchResult {
        val trimmed = transcript.trim()
        if (trimmed.isBlank()) return WakeWordMatchResult(matched = false)

        val lower = normalizeText(trimmed)
        val normalizedWake = normalizeText(customWakeWord).ifBlank { "myra" }

        // 1. Check explicit permanent OFF or temporary STOP commands first
        if (matchesStopOrOff(lower, normalizedWake, permanentOffPhrases)) {
            return WakeWordMatchResult(
                matched = true,
                matchedPhrase = trimmed,
                isStopCommand = true,
                isPermanentOffCommand = true,
                confidence = 1.0f
            )
        }
        if (matchesStopOrOff(lower, normalizedWake, stopPhrases)) {
            return WakeWordMatchResult(
                matched = true,
                matchedPhrase = trimmed,
                isStopCommand = true,
                isPermanentOffCommand = false,
                confidence = 0.95f
            )
        }

        // 2. Build candidate wake phrases (user custom wake phrase + aliases if MYRA)
        val candidates = buildCandidatePhrases(normalizedWake)
        val maxDistance = when {
            sensitivity >= 0.9f -> 0
            sensitivity >= 0.65f -> 1
            else -> 2
        }

        // Exact or prefix/inline phrase match first (longest phrases checked first)
        for (phrase in candidates.sortedByDescending { it.length }) {
            val idx = findPhraseIndex(lower, phrase)
            if (idx >= 0) {
                val remainder = extractRemainingCommand(trimmed, phrase)
                return WakeWordMatchResult(
                    matched = true,
                    matchedPhrase = phrase,
                    remainingCommand = remainder,
                    confidence = 1.0f
                )
            }
        }

        // 3. Phonetic / fuzzy word-level matching for single-word wake targets
        if (maxDistance > 0 && !normalizedWake.contains(' ') && normalizedWake.length >= 4) {
            val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
            for ((index, word) in words.withIndex()) {
                val dist = levenshtein(word, normalizedWake)
                if (dist <= maxDistance && abs(word.length - normalizedWake.length) <= maxDistance) {
                    val remainingWords = words.drop(index + 1).joinToString(" ")
                    val score = (1.0f - (dist * 0.18f)).coerceIn(0.6f, 0.95f)
                    return WakeWordMatchResult(
                        matched = true,
                        matchedPhrase = word,
                        remainingCommand = remainingWords,
                        confidence = score
                    )
                }
            }
        }

        return WakeWordMatchResult(matched = false)
    }

    /**
     * Evaluates N-best recognition hypotheses from SpeechRecognizer to maximize wake-word recall
     * while avoiding false positives.
     */
    fun evaluateHypotheses(
        hypotheses: List<String>,
        customWakeWord: String = "MYRA",
        sensitivity: Float = 0.8f
    ): Pair<String, WakeWordMatchResult>? {
        val cleaned = hypotheses.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return null

        for (candidate in cleaned) {
            val result = evaluate(candidate, customWakeWord, sensitivity)
            if (result.matched) {
                return candidate to result
            }
        }
        return cleaned.first() to WakeWordMatchResult(matched = false)
    }

    private fun buildCandidatePhrases(normalizedWake: String): List<String> {
        val list = linkedSetOf<String>()
        list.add("hey $normalizedWake")
        list.add("hi $normalizedWake")
        list.add("ok $normalizedWake")
        list.add("suno $normalizedWake")
        list.add(normalizedWake)
        if (normalizedWake == "myra" || normalizedWake == "मायरा" || normalizedWake.contains("myra")) {
            list.addAll(myraPhoneticAliases)
        }
        return list.toList()
    }

    private fun matchesStopOrOff(
        lowerTranscript: String,
        normalizedWake: String,
        phrases: List<String>
    ): Boolean {
        if (phrases.any { phrase -> lowerTranscript == phrase || lowerTranscript.contains(phrase) }) {
            return true
        }
        if (lowerTranscript == "$normalizedWake stop" || lowerTranscript == "$normalizedWake off") {
            return true
        }
        return false
    }

    private fun findPhraseIndex(normalizedText: String, phrase: String): Int {
        val escaped = Regex.escape(phrase)
        val regex = Regex("(^|\\s)$escaped(\\s|$)")
        return regex.find(normalizedText)?.range?.first ?: -1
    }

    private fun extractRemainingCommand(originalText: String, matchedPhrase: String): String {
        val tokens = matchedPhrase.split(Regex("\\s+")).filter { it.isNotBlank() }
        val pattern = tokens.joinToString("\\s+") { Regex.escape(it) }
        val regex = Regex("^.*?$pattern[\\s,:;!.-]*", RegexOption.IGNORE_CASE)
        return regex.replaceFirst(originalText, "").trim()
    }

    private fun normalizeText(input: String): String {
        return input
            .lowercase()
            .replace(Regex("[,.!?;:\"'`~()\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    internal fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            System.arraycopy(curr, 0, prev, 0, b.length + 1)
        }
        return prev[b.length]
    }
}
