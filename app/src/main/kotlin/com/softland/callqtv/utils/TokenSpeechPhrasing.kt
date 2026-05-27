package com.softland.callqtv.utils

import java.util.Locale

/** Token/counter phrasing for on-screen labels and TTS (no [TextToSpeech] engine). */
object TokenSpeechPhrasing {
    /** Normalizes TV config `audio_language` values to a short language code. */
    fun normalizeLanguageCode(audioLanguage: String?): String {
        return when (audioLanguage?.trim()?.lowercase()) {
            "hi", "hindi", "hin" -> "hi"
            "ta", "tamil", "tam" -> "ta"
            "ml", "malayalam", "mal" -> "ml"
            "en", "english", "eng" -> "en"
            null, "" -> "en"
            else -> audioLanguage.trim().lowercase()
        }
    }

    /** Localized queue word spoken before the token number (not English "Token" on Indian locales). */
    fun localizedTokenWord(audioLanguage: String?): String {
        return when (normalizeLanguageCode(audioLanguage)) {
            "hi" -> "टोकन"
            "ta" -> "டோக்கன்"
            "ml" -> "ടോക്കൺ"
            else -> "Token"
        }
    }

    /**
     * Maps ASCII digits in token labels to locale-appropriate numerals so TTS does not read
     * "42" with English number pronunciation on hi/ta/ml voices. Leaves letters and punctuation as-is.
     */
    fun localizeTokenForSpeech(text: String, audioLanguage: String?): String {
        if (text.isBlank()) return text
        val lang = normalizeLanguageCode(audioLanguage)
        if (lang == "en") return text
        val digitMap = NATIVE_DIGIT_TABLE[lang] ?: return text
        return buildString(text.length) {
            text.forEach { ch ->
                if (ch in '0'..'9') {
                    append(digitMap[ch - '0'])
                } else {
                    append(ch)
                }
            }
        }
    }

    private val NATIVE_DIGIT_TABLE = mapOf(
        // Devanagari digits (Hindi)
        "hi" to charArrayOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९'),
        // Tamil digits
        "ta" to charArrayOf('௦', '௧', '௨', '௩', '௪', '௫', '௬', '௭', '௮', '௯'),
        // Malayalam digits
        "ml" to charArrayOf('൦', '൧', '൨', '൩', '൪', '൫', '൬', '൭', '൮', '൯'),
    )

    /**
     * Parsed token for TTS, e.g. `C2-23` → spelled lead `C 2` + localized number `२३`.
     */
    data class ParsedTokenSpeech(
        val spelledLead: String,
        val localizedNumber: String,
    )

    /** Keeps letters, digits, and hyphens (hyphen separates code from queue number). */
    fun sanitizeTokenLabelForSpeech(token: String): String =
        token.trim().filter { it.isLetterOrDigit() || it == '-' }

    /**
     * Builds the spoken token body: localized "Token" + optional spelled code + localized number
     * + optional counter name. Example (`hi`, `C2-23`, `NEPHROLOGY`):
     * `टोकन C २ २३ NEPHROLOGY`.
     */
    fun buildTokenAnnouncementBody(
        tokenText: String,
        audioLanguage: String?,
        counterDisplayName: String = "",
        includeTokenWord: Boolean = true,
    ): String {
        val sanitized = sanitizeTokenLabelForSpeech(tokenText)
        val parsed = parseHyphenatedTokenForSpeech(sanitized, audioLanguage)
        return buildString {
            if (includeTokenWord) {
                append(localizedTokenWord(audioLanguage))
            }
            when {
                parsed.spelledLead.isNotBlank() || parsed.localizedNumber.isNotBlank() -> {
                    if (isNotEmpty()) append(' ')
                    if (parsed.spelledLead.isNotBlank()) {
                        append(parsed.spelledLead)
                    }
                    if (parsed.localizedNumber.isNotBlank()) {
                        if (parsed.spelledLead.isNotBlank()) append(' ')
                        append(parsed.localizedNumber)
                    }
                }
                sanitized.isNotBlank() -> {
                    val plain = formatPlainTokenForSpeech(sanitized, audioLanguage)
                    if (plain.isNotBlank()) {
                        if (isNotEmpty()) append(' ')
                        append(plain)
                    }
                }
            }
            val counter = counterDisplayName.trim()
            if (counter.isNotBlank()) {
                if (isNotEmpty()) append(' ')
                append(counter)
            }
        }.trim()
    }

    /**
     * Splits on the last `-` when the suffix is numeric: `C2-23` → lead `C2`, number `23`.
     * `NEU-C2-23` → lead `NEU-C2`, number `23`.
     */
    fun parseHyphenatedTokenForSpeech(token: String, audioLanguage: String?): ParsedTokenSpeech {
        val raw = token.trim()
        if (raw.isEmpty()) return ParsedTokenSpeech("", "")
        val hyphen = raw.lastIndexOf('-')
        if (hyphen > 0) {
            val suffix = raw.substring(hyphen + 1)
            val lead = raw.substring(0, hyphen)
            if (suffix.isNotEmpty() && suffix.all { it.isDigit() }) {
                return ParsedTokenSpeech(
                    spelledLead = spellAlphanumericTokenLead(lead, audioLanguage),
                    localizedNumber = localizeTokenForSpeech(suffix, audioLanguage),
                )
            }
        }
        return ParsedTokenSpeech(
            spelledLead = formatPlainTokenForSpeech(raw, audioLanguage),
            localizedNumber = "",
        )
    }

    /** Spells each letter; single digits in the lead use locale numerals (e.g. `C2` → `C २`). */
    fun spellAlphanumericTokenLead(lead: String, audioLanguage: String?): String {
        if (lead.isBlank()) return ""
        return lead.split('-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { segment ->
                segment.map { ch ->
                    when {
                        ch.isLetter() -> ch.toString()
                        ch.isDigit() -> localizeTokenForSpeech(ch.toString(), audioLanguage)
                        else -> ""
                    }
                }.filter { it.isNotEmpty() }.joinToString(" ")
            }
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun formatPlainTokenForSpeech(token: String, audioLanguage: String?): String {
        val cleaned = token.filter { it.isLetterOrDigit() }
        if (cleaned.isEmpty()) return ""
        return when {
            cleaned.all { it.isDigit() } -> localizeTokenForSpeech(cleaned, audioLanguage)
            cleaned.any { it.isLetter() } -> spellAlphanumericTokenLead(cleaned, audioLanguage)
            else -> localizeTokenForSpeech(cleaned, audioLanguage)
        }
    }
}
