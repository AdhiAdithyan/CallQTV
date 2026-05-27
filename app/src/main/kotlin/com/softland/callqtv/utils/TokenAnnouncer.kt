package com.softland.callqtv.utils

import android.content.Context

/** Public entry for token TTS; engine in [TokenTtsEngine], phrasing in [TokenSpeechPhrasing]. */
object TokenAnnouncer {
    fun isAnnouncementsEnabled(): Boolean = TokenTtsEngine.isAnnouncementsEnabled()

    fun setAnnouncementsEnabled(enabled: Boolean) = TokenTtsEngine.setAnnouncementsEnabled(enabled)

    fun warmUp(
        context: Context,
        audioLanguage: String? = null,
        performPoke: Boolean = false,
        onReady: (Boolean) -> Unit = {},
    ) = TokenTtsEngine.warmUp(context, audioLanguage, performPoke, onReady)

    fun initialize(context: Context, audioLanguage: String? = null, onInitComplete: (Boolean) -> Unit = {}) =
        TokenTtsEngine.initialize(context, audioLanguage, onInitComplete)

    suspend fun awaitReady(
        context: Context,
        audioLanguage: String? = null,
        performPoke: Boolean = true,
        primeSynthesis: Boolean = true,
    ): Boolean = TokenTtsEngine.awaitReady(context, audioLanguage, performPoke, primeSynthesis)

    suspend fun awaitSynthesisPrimeIfNeeded() = TokenTtsEngine.awaitSynthesisPrimeIfNeeded()

    fun needsSynthesisWarmUp(): Boolean = TokenTtsEngine.needsSynthesisWarmUp()

    fun enterTokenAnnouncementCycle() = TokenTtsEngine.enterTokenAnnouncementCycle()

    fun exitTokenAnnouncementCycle() = TokenTtsEngine.exitTokenAnnouncementCycle()

    suspend fun prepareForNextTokenAnnouncement() = TokenTtsEngine.prepareForNextTokenAnnouncement()

    fun announceToken(
        context: Context,
        audioLanguage: String?,
        counterName: String,
        tokenLabel: String,
        includeTokenWord: Boolean = true,
        onDone: (() -> Unit)? = null,
    ) = TokenTtsEngine.announceToken(context, audioLanguage, counterName, tokenLabel, includeTokenWord, onDone)

    fun announceTokenCall(
        context: Context,
        audioLanguage: String?,
        spelledCounterPrefix: String,
        tokenText: String,
        counterDisplayName: String = "",
        onDone: (() -> Unit)? = null,
        skipSynthesisPrime: Boolean = false,
        pokeEngine: Boolean = !skipSynthesisPrime,
    ) = TokenTtsEngine.announceTokenCall(
        context,
        audioLanguage,
        spelledCounterPrefix,
        tokenText,
        counterDisplayName,
        onDone,
        skipSynthesisPrime,
        pokeEngine,
    )

    fun announceMessage(
        context: Context,
        audioLanguage: String?,
        message: String,
        speechRate: Float = 0.96f,
        onDone: (() -> Unit)? = null,
        skipSynthesisPrime: Boolean = false,
        pokeEngine: Boolean = !skipSynthesisPrime,
    ) = TokenTtsEngine.announceMessage(
        context,
        audioLanguage,
        message,
        speechRate,
        onDone,
        skipSynthesisPrime,
        pokeEngine,
    )

    fun announceSpelledPrefixAndToken(
        context: Context,
        audioLanguage: String?,
        spelledPrefix: String,
        tokenText: String,
        onDone: (() -> Unit)? = null,
    ) = TokenTtsEngine.announceSpelledPrefixAndToken(context, audioLanguage, spelledPrefix, tokenText, onDone = onDone)

    fun shutdown() = TokenTtsEngine.shutdown()

    fun normalizeLanguageCode(audioLanguage: String?) = TokenSpeechPhrasing.normalizeLanguageCode(audioLanguage)

    fun localizedTokenWord(audioLanguage: String?) = TokenSpeechPhrasing.localizedTokenWord(audioLanguage)

    fun localizeTokenForSpeech(text: String, audioLanguage: String?) =
        TokenSpeechPhrasing.localizeTokenForSpeech(text, audioLanguage)

    fun sanitizeTokenLabelForSpeech(token: String) = TokenSpeechPhrasing.sanitizeTokenLabelForSpeech(token)

    fun buildTokenAnnouncementBody(
        tokenText: String,
        audioLanguage: String?,
        counterDisplayName: String = "",
        includeTokenWord: Boolean = true,
    ) = TokenSpeechPhrasing.buildTokenAnnouncementBody(tokenText, audioLanguage, counterDisplayName, includeTokenWord)

    fun parseHyphenatedTokenForSpeech(token: String, audioLanguage: String?) =
        TokenSpeechPhrasing.parseHyphenatedTokenForSpeech(token, audioLanguage)

    fun spellAlphanumericTokenLead(lead: String, audioLanguage: String?) =
        TokenSpeechPhrasing.spellAlphanumericTokenLead(lead, audioLanguage)
}
