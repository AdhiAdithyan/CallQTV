package com.softland.callqtv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors [formatTokenByPattern] behavior for T2 and numeric patterns.
 */
class TokenFormatTest {

    @Test
    fun t2FormatPadsTokenNumberWithoutLiteralT() {
        assertEquals("05", formatTokenForTest("5", "T2"))
        assertEquals("23", formatTokenForTest("23", "T2"))
        assertEquals("2", formatTokenForTest("2", "T1"))
    }

    private fun formatTokenForTest(token: String, pattern: String): String? {
        val trimmedPattern = pattern.trim()
        Regex("^T(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(trimmedPattern)?.let { match ->
            val digitLen = match.groupValues[1].toIntOrNull() ?: return token
            val num = token.toIntOrNull() ?: return token
            return num.toString().padStart(digitLen, '0')
        }
        return token
    }
}
