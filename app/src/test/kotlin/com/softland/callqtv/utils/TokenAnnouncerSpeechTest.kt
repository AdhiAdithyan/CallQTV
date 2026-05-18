package com.softland.callqtv.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenAnnouncerSpeechTest {

    @Test
    fun localizeTokenForSpeech_hindi_convertsAsciiDigits() {
        assertEquals("१२३", TokenAnnouncer.localizeTokenForSpeech("123", "hindi"))
        assertEquals("A१०", TokenAnnouncer.localizeTokenForSpeech("A10", "hi"))
    }

    @Test
    fun localizeTokenForSpeech_tamil_convertsAsciiDigits() {
        assertEquals("௪௨", TokenAnnouncer.localizeTokenForSpeech("42", "tamil"))
    }

    @Test
    fun localizeTokenForSpeech_malayalam_convertsAsciiDigits() {
        assertEquals("൭൮", TokenAnnouncer.localizeTokenForSpeech("78", "malayalam"))
    }

    @Test
    fun localizeTokenForSpeech_english_leavesDigitsUnchanged() {
        assertEquals("123", TokenAnnouncer.localizeTokenForSpeech("123", "english"))
        assertEquals("42", TokenAnnouncer.localizeTokenForSpeech("42", null))
    }

    @Test
    fun normalizeLanguageCode_acceptsCommonAliases() {
        assertEquals("hi", TokenAnnouncer.normalizeLanguageCode("Hindi"))
        assertEquals("ta", TokenAnnouncer.normalizeLanguageCode("tam"))
        assertEquals("ml", TokenAnnouncer.normalizeLanguageCode("malayalam"))
    }

    @Test
    fun parseHyphenatedToken_C2_23_splitsLeadAndNumber() {
        val parsed = TokenAnnouncer.parseHyphenatedTokenForSpeech("C2-23", "hindi")
        assertEquals("C २", parsed.spelledLead)
        assertEquals("२३", parsed.localizedNumber)
    }

    @Test
    fun buildTokenAnnouncementBody_C2_23_hindi() {
        val body = TokenAnnouncer.buildTokenAnnouncementBody("C2-23", "hindi", "NEPHROLOGY")
        assertEquals("टोकन C २ २३ NEPHROLOGY", body)
    }

    @Test
    fun buildTokenAnnouncementBody_C2_23_english() {
        val body = TokenAnnouncer.buildTokenAnnouncementBody("C2-23", "english", "Counter 1")
        assertEquals("Token C 2 23 Counter 1", body)
    }

    @Test
    fun sanitizeTokenLabelForSpeech_keepsHyphen() {
        assertEquals("C2-23", TokenAnnouncer.sanitizeTokenLabelForSpeech(" C2-23 "))
    }
}
