package com.softland.callqtv.utils

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced TokenAnnouncer for robust cross-device voice support.
 * Uses AI-enhanced phrasing and handles multi-language announcement.
 */
object TokenAnnouncer {
    private const val TAG = "TokenAnnouncer"
    /** Consistent, clear pace for queue token calls (avoid 0.85f — sounds sluggish on many engines). */
    private const val TOKEN_SPEECH_RATE = 1.0f
  /** Slightly brighter pitch so token calls feel alert, not sleepy. */
    private const val TOKEN_SPEECH_PITCH = 1.06f
    /** Longer informational / special messages: still clear, not as slow as legacy 0.85f. */
    private const val INFO_MESSAGE_SPEECH_RATE = 0.96f
    private const val INFO_MESSAGE_SPEECH_PITCH = 1.0f
    /** Keep the engine warm on idle TVs (was 8s — too long; service slept before next token). */
    private const val HEARTBEAT_INTERVAL_MS = 3_000L
    private const val HEARTBEAT_SILENT_MS = 120L
    private const val HEARTBEAT_FAILURES_BEFORE_REINIT = 6

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: String? = null
    private var appContext: Context? = null
    @Volatile
    private var initInProgress = false
    private val pendingInitCallbacks = java.util.concurrent.CopyOnWriteArrayList<(Boolean) -> Unit>()

    /** Mirrors TV config `enable_token_announcement`; when false, no TTS bind or keep-alive. */
    @Volatile
    private var announcementsEnabled = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    private val completionCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun isAnnouncementsEnabled(): Boolean = announcementsEnabled

    /**
     * Enable or disable TTS based on TV config. When disabled, releases the engine and stops keep-alive.
     * When enabled, call [warmUp] to bind the engine (usually right after this).
     */
    fun setAnnouncementsEnabled(enabled: Boolean) {
        if (announcementsEnabled == enabled) return
        announcementsEnabled = enabled
        val apply: () -> Unit = {
            if (enabled) {
                Log.i(TAG, "Token announcements enabled; TTS keep-alive active when engine is warmed")
            } else {
                Log.i(TAG, "Token announcements disabled; releasing TTS engine")
                releaseEngineOnMain()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else mainHandler.post(apply)
    }

    /**
     * Bind and warm the TTS engine on the main thread (required by [TextToSpeech]).
     * No-op when [announcementsEnabled] is false.
     */
    fun warmUp(
        context: Context,
        audioLanguage: String? = null,
        performPoke: Boolean = false,
        onReady: (Boolean) -> Unit = {},
    ) {
        appContext = context.applicationContext
        if (!announcementsEnabled) {
            mainHandler.post { onReady(false) }
            return
        }
        mainHandler.post { ensureEngineReadyOnMain(audioLanguage, performPoke, onReady) }
    }

    fun initialize(context: Context, audioLanguage: String? = null, onInitComplete: (Boolean) -> Unit = {}) {
        warmUp(context, audioLanguage, performPoke = false, onReady = onInitComplete)
    }

    private fun ensureEngineReadyOnMain(
        audioLanguage: String?,
        performPoke: Boolean,
        onReady: (Boolean) -> Unit,
    ) {
        if (!announcementsEnabled) {
            onReady(false)
            return
        }
        val readyNow = synchronized(this) {
            if (isInitialized && tts != null) {
                setupLanguage(audioLanguage)
                true
            } else {
                pendingInitCallbacks.add(onReady)
                if (!initInProgress) {
                    initInProgress = true
                    false
                } else {
                    null
                }
            }
        }
        when (readyNow) {
            true -> {
                if (performPoke) performEnginePoke()
                onReady(true)
            }
            null -> Unit
            false -> startTtsEngineOnMain(audioLanguage)
        }
    }

    private fun startTtsEngineOnMain(audioLanguage: String?) {
        val ctx = appContext
        if (ctx == null) {
            finishInitAttempt(success = false)
            return
        }
        if (tts == null) {
            Log.i(TAG, "Creating TextToSpeech on main thread")
            tts = TextToSpeech(ctx) { status ->
                mainHandler.post { handleTtsInitCallback(status, audioLanguage) }
            }
        } else {
            handleTtsInitCallback(TextToSpeech.SUCCESS, audioLanguage)
        }
    }

    private fun handleTtsInitCallback(status: Int, audioLanguage: String?) {
        synchronized(this) {
            initInProgress = false
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                Log.i(TAG, "TTS initialized successfully")
                applyTokenSpeechStyle()
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id -> completionCallbacks.remove(id)?.invoke() }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id -> completionCallbacks.remove(id)?.invoke() }
                    }
                })
                setupLanguage(audioLanguage)
                appContext?.let { scheduleHeartbeat(it) }
            } else {
                Log.e(TAG, "TTS initialization failed (status=$status)")
                isInitialized = false
            }
        }
        if (status == TextToSpeech.SUCCESS) {
            performEnginePoke()
        }
        finishInitAttempt(status == TextToSpeech.SUCCESS)
    }

    private fun finishInitAttempt(success: Boolean) {
        val callbacks = pendingInitCallbacks.toList()
        pendingInitCallbacks.clear()
        callbacks.forEach { it(success) }
    }

    /** Short silent utterance to wake a sleeping TTS / audio path before real speech. */
    private fun performEnginePoke() {
        mainHandler.post { performEnginePokeOnMain() }
    }

    private fun performEnginePokeOnMain() {
        if (!announcementsEnabled) return
        synchronized(this) {
            if (!isInitialized || tts == null) return
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    tts?.playSilentUtterance(
                        HEARTBEAT_SILENT_MS.toLong(),
                        TextToSpeech.QUEUE_ADD,
                        "poke_${System.nanoTime()}",
                    )
                } else {
                    @Suppress("DEPRECATION")
                    tts?.speak(" ", TextToSpeech.QUEUE_ADD, null)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Engine poke failed: ${e.message}")
            }
        }
    }

    private fun runWithReadyEngine(
        context: Context,
        audioLanguage: String?,
        onNotReady: (() -> Unit)?,
        pokeEngine: Boolean = true,
        block: () -> Unit,
    ) {
        if (!announcementsEnabled) {
            onNotReady?.invoke()
            return
        }
        appContext = context.applicationContext

        val speakNow: () -> Unit = {
            if (pokeEngine) performEnginePokeOnMain()
            block()
        }

        val onEngineReady: (Boolean) -> Unit = { ready ->
            if (ready) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    speakNow()
                } else {
                    mainHandler.post { speakNow() }
                }
            } else {
                onNotReady?.invoke()
            }
        }

        val alreadyReady = synchronized(this) {
            if (isInitialized && tts != null) {
                setupLanguage(audioLanguage)
                true
            } else {
                false
            }
        }
        if (alreadyReady) {
            onEngineReady(true)
            return
        }

        warmUp(context, audioLanguage, performPoke = pokeEngine, onReady = onEngineReady)
    }

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

    private fun setupLanguage(audioLanguage: String?) {
        if (currentLanguage == audioLanguage && isInitialized) return
        
        val targetLocale = when (normalizeLanguageCode(audioLanguage)) {
            "hi" -> Locale("hi", "IN")
            "ta" -> Locale("ta", "IN")
            "ml" -> Locale("ml", "IN")
            else -> Locale.US
        }
        
        try {
            val langResult = tts?.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not available for $targetLocale (result=$langResult)")
            }
            
            // Attempt to select specific requested voices
            val availableVoices = tts?.voices
            if (availableVoices != null) {
                val langCode = targetLocale.language
                var preferredVoiceName: String? = null
                
                preferredVoiceName = when (langCode) {
                    "ml" -> "karthika"
                    "ta" -> "sentamil"
                    "hi" -> "hindi"
                    else -> null
                }
                
                if (preferredVoiceName != null) {
                    val targetVoice = availableVoices.find { 
                        it.name.contains(preferredVoiceName, ignoreCase = true) && 
                        it.locale.language == langCode 
                    }
                    
                    if (targetVoice != null) {
                        tts?.voice = targetVoice
                        Log.i(TAG, "Set preferred voice: ${targetVoice.name}")
                    }
                }
            }
            
            currentLanguage = audioLanguage
            applyTokenSpeechStyle()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language: $audioLanguage", e)
        }
    }

    fun announceToken(
        context: Context,
        audioLanguage: String?,
        counterName: String,
        tokenLabel: String,
        includeTokenWord: Boolean = true,
        onDone: (() -> Unit)? = null
    ) {
        runWithReadyEngine(context, audioLanguage, onNotReady = { onDone?.invoke() }) {
            speakNow(counterName, tokenLabel, includeTokenWord, onDone)
        }
    }

    /**
     * Announces a raw message exactly as provided (no "Token" prefix and no token formatting).
     * Used for special informational messages so users can understand full text clearly.
     */
    /**
     * Standard token-call announcement: localized "Token", then optional spelled counter code,
     * then token number + optional counter name. Uses [TOKEN_SPEECH_RATE] / [TOKEN_SPEECH_PITCH].
     */
    fun announceTokenCall(
        context: Context,
        audioLanguage: String?,
        spelledCounterPrefix: String,
        tokenText: String,
        counterDisplayName: String = "",
        onDone: (() -> Unit)? = null,
    ) {
        val tokenWord = localizedTokenWord(audioLanguage)
        val tokenBody = buildTokenAnnouncementBody(
            tokenText = tokenText,
            audioLanguage = audioLanguage,
            counterDisplayName = counterDisplayName,
            includeTokenWord = false,
        )
        val prefixPart = spelledCounterPrefix.trim()
        val bodyPart = tokenBody.trim()
        runWithReadyEngine(
            context,
            audioLanguage,
            onNotReady = { onDone?.invoke() },
            pokeEngine = false,
        ) {
            val phrase = buildString {
                append(tokenWord)
                if (prefixPart.isNotEmpty()) {
                    append(' ')
                    append(prefixPart)
                }
                if (bodyPart.isNotEmpty()) {
                    append(' ')
                    append(bodyPart)
                }
            }.trim()
            speakRawNow(
                message = phrase,
                speechRate = TOKEN_SPEECH_RATE,
                speechPitch = TOKEN_SPEECH_PITCH,
                audioLanguage = audioLanguage,
                onDone = onDone,
                collapseSpelledWords = false,
                queueMode = TextToSpeech.QUEUE_FLUSH,
            )
        }
    }

    private data class ChainedUtterance(
        val text: String,
        val collapseSpelledWords: Boolean,
    )

    private fun speakChainedUtterances(
        audioLanguage: String?,
        parts: List<ChainedUtterance>,
        speechRate: Float,
        speechPitch: Float,
        onDone: (() -> Unit)?,
    ) {
        val nonEmpty = parts.mapNotNull { part ->
            val trimmed = part.text.trim()
            if (trimmed.isEmpty()) null else part.copy(text = trimmed)
        }
        if (nonEmpty.isEmpty()) {
            onDone?.invoke()
            return
        }
        nonEmpty.forEachIndexed { index, part ->
            speakRawNow(
                message = part.text,
                speechRate = speechRate,
                speechPitch = speechPitch,
                audioLanguage = audioLanguage,
                onDone = if (index == nonEmpty.lastIndex) onDone else null,
                collapseSpelledWords = part.collapseSpelledWords,
                queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
            )
        }
    }

    fun announceMessage(
        context: Context,
        audioLanguage: String?,
        message: String,
        speechRate: Float = INFO_MESSAGE_SPEECH_RATE,
        onDone: (() -> Unit)? = null
    ) {
        runWithReadyEngine(
            context,
            audioLanguage,
            onNotReady = { onDone?.invoke() },
            pokeEngine = false,
        ) {
            speakRawNow(
                message,
                speechRate,
                INFO_MESSAGE_SPEECH_PITCH,
                audioLanguage,
                onDone,
                queueMode = TextToSpeech.QUEUE_FLUSH,
            )
        }
    }

    /**
     * Speaks [spelledPrefix] first (typically counter code with spaces between characters),
     * then speaks [tokenWithOptionalCounterSuffix] as a separate utterance so the token is
     * read as a phrase/number, not letter-by-letter.
     */
    fun announceSpelledPrefixAndToken(
        context: Context,
        audioLanguage: String?,
        spelledPrefix: String,
        tokenWithOptionalCounterSuffix: String,
        speechRate: Float = TOKEN_SPEECH_RATE,
        speechPitch: Float = TOKEN_SPEECH_PITCH,
        onDone: (() -> Unit)? = null,
    ) {
        val prefixPart = spelledPrefix.trim()
        val remainderPart = tokenWithOptionalCounterSuffix.trim()
        runWithReadyEngine(context, audioLanguage, onNotReady = { onDone?.invoke() }) {
            when {
                prefixPart.isEmpty() ->
                    speakRawNow(
                        remainderPart,
                        speechRate,
                        speechPitch,
                        audioLanguage,
                        onDone,
                        collapseSpelledWords = true,
                    )
                remainderPart.isEmpty() ->
                    speakRawNow(
                        prefixPart,
                        speechRate,
                        speechPitch,
                        audioLanguage,
                        onDone,
                        collapseSpelledWords = false,
                    )
                else -> {
                    speakRawNow(
                        prefixPart,
                        speechRate,
                        speechPitch,
                        audioLanguage,
                        onDone = null,
                        collapseSpelledWords = false,
                    )
                    speakRawNow(
                        remainderPart,
                        speechRate,
                        speechPitch,
                        audioLanguage,
                        onDone = onDone,
                        collapseSpelledWords = true,
                    )
                }
            }
        }
    }

    private var heartbeatJob: Job? = null
    private var lastHeartbeatErrorCount = 0

    private fun scheduleHeartbeat(context: Context) {
        appContext = context.applicationContext
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            performHeartbeatCycle()
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                performHeartbeatCycle()
            }
        }
    }

    private fun performHeartbeatCycle() {
        if (!announcementsEnabled) return
        mainHandler.post {
            if (!announcementsEnabled) return@post
            synchronized(this@TokenAnnouncer) {
                if (!isInitialized || tts == null) return@post
                try {
                    val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        tts?.playSilentUtterance(
                            HEARTBEAT_SILENT_MS.toLong(),
                            TextToSpeech.QUEUE_ADD,
                            "heartbeat_${System.nanoTime()}",
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        tts?.speak(" ", TextToSpeech.QUEUE_ADD, null)
                    }
                    if (result == TextToSpeech.ERROR) {
                        lastHeartbeatErrorCount++
                        Log.w(TAG, "TTS heartbeat failed ($lastHeartbeatErrorCount)")
                    } else {
                        lastHeartbeatErrorCount = 0
                    }
                    if (lastHeartbeatErrorCount >= HEARTBEAT_FAILURES_BEFORE_REINIT) {
                        Log.e(TAG, "TTS unresponsive; re-binding engine")
                        reinitializeOnMain()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat error", e)
                }
            }
        }
    }

    private fun reinitializeOnMain() {
        if (!announcementsEnabled) return
        val oldLang = currentLanguage
        val ctx = appContext
        lastHeartbeatErrorCount = 0
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
        initInProgress = false
        if (ctx != null) {
            warmUp(ctx, oldLang)
        }
    }

    private fun speakNow(
        counter: String,
        token: String,
        includeTokenWord: Boolean,
        onDone: (() -> Unit)? = null
    ) {
        val deliver = { speakNowOnMain(counter, token, includeTokenWord, onDone) }
        if (Looper.myLooper() == Looper.getMainLooper()) deliver() else mainHandler.post(deliver)
    }

    private fun speakNowOnMain(
        counter: String,
        token: String,
        includeTokenWord: Boolean,
        onDone: (() -> Unit)?,
    ) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not initialized yet. Skipping announcement.")
            onDone?.invoke()
            return
        }

        val lang = normalizeLanguageCode(currentLanguage)
        val phrase = buildTokenAnnouncementBody(
            tokenText = token,
            audioLanguage = currentLanguage,
            counterDisplayName = counter,
            includeTokenWord = includeTokenWord,
        )

        Log.d(TAG, "Announcing ($lang): $phrase")
        
        val utteranceId = "token_${System.nanoTime()}"
        if (onDone != null) {
            completionCallbacks[utteranceId] = onDone
        }
        
        // Use QUEUE_ADD so that announcements are spoken strictly in FIFO order.
        // Older tokens will always be called before newer ones.
        applyTokenSpeechStyle()
        val result = tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            isInitialized = false
            completionCallbacks.remove(utteranceId)
            onDone?.invoke()
            appContext?.let { warmUp(it, currentLanguage) }
        }
    }

    private fun applyTokenSpeechStyle() {
        tts?.setSpeechRate(TOKEN_SPEECH_RATE)
        tts?.setPitch(TOKEN_SPEECH_PITCH)
    }

    /**
     * Strips internal `__MSG__` envelopes, NFC-normalizes, removes control chars, and collapses
     * whitespace so TTS reads continuous phrases (not letter-by-letter from odd payloads).
     */
    private fun normalizeSpeechPhrase(
        raw: String,
        collapseSpelledWords: Boolean,
        audioLanguage: String?,
    ): String {
        var t = raw.trim()
        if (t.isEmpty()) return ""
        when {
            t.contains("__MSG__:") ->
                t = t.substringAfter("__MSG__:").trim()
            t.contains("__MSG__") ->
                t = t.substringAfter("__MSG__").trimStart(':', '-', ' ').trim()
        }
        t = try {
            Normalizer.normalize(t, Normalizer.Form.NFC)
        } catch (_: Exception) {
            t
        }
        return t
            .asSequence()
            .filter { ch ->
                ch != '\u200B' && ch != '\u200C' && ch != '\u200D' && ch != '\uFEFF' &&
                    (!ch.isISOControl() || ch == '\n' || ch == '\t')
            }
            .joinToString("")
            .replace(Regex("[\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { if (collapseSpelledWords) collapseLetterSpacedRuns(it) else it }
            .let { normalizeUppercaseWordsForSpeech(it) }
            .let { if (collapseSpelledWords) localizeTokenForSpeech(it, audioLanguage) else it }
    }

    /**
     * Some MQTT payloads spell a word as "R O U N C E" (spaces between letters). TTS then reads
     * letter-by-letter. Merge runs of 3+ single letters separated by single spaces into one token.
     * Not used for intentionally spaced counter codes (see [collapseSpelledWords]).
     */
    private fun collapseLetterSpacedRuns(s: String): String {
        return letterSpacedRun.replace(s) { m ->
            m.groupValues[1].replace(" ", "")
        }
    }

    private val letterSpacedRun =
        Regex("""(?<!\p{L})(\p{L}(?:\s+\p{L}){2,})(?!\p{L})""")

    /**
     * Some engines spell long ALL-CAPS words (e.g., "ROUNCE") letter-by-letter.
     * For speech only, convert long uppercase words to lowercase while preserving short acronyms.
     */
    private fun normalizeUppercaseWordsForSpeech(s: String): String {
        if (s.isBlank()) return s
        return uppercaseWordRegex.replace(s) { match ->
            val word = match.value
            if (word.length >= 4 && word.any { it.isLetter() }) {
                word.lowercase(Locale.ROOT)
            } else {
                word
            }
        }
    }

    private val uppercaseWordRegex = Regex("""\b\p{Lu}{4,}\b""")

    private fun speakRawNow(
        message: String,
        speechRate: Float,
        speechPitch: Float,
        audioLanguage: String?,
        onDone: (() -> Unit)? = null,
        collapseSpelledWords: Boolean = true,
        queueMode: Int = TextToSpeech.QUEUE_ADD,
    ) {
        val deliver = {
            speakRawNowOnMain(
                message,
                speechRate,
                speechPitch,
                audioLanguage,
                onDone,
                collapseSpelledWords,
                queueMode,
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) deliver() else mainHandler.post(deliver)
    }

    private fun speakRawNowOnMain(
        message: String,
        speechRate: Float,
        speechPitch: Float,
        audioLanguage: String?,
        onDone: (() -> Unit)?,
        collapseSpelledWords: Boolean,
        queueMode: Int,
    ) {
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }
        val phrase = normalizeSpeechPhrase(message, collapseSpelledWords, audioLanguage)
        if (phrase.isEmpty()) {
            onDone?.invoke()
            return
        }
        if (message.contains("__MSG__", ignoreCase = false)) {
            Log.d(TAG, "Special message TTS normalized: '$phrase'")
        }

        val utteranceId = "msg_${System.nanoTime()}"
        if (onDone != null) {
            completionCallbacks[utteranceId] = onDone
        }
        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 1.2f))
        tts?.setPitch(speechPitch.coerceIn(0.5f, 2.0f))
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts?.speak(phrase, queueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "speak() returned ERROR; marking engine cold")
            isInitialized = false
            completionCallbacks.remove(utteranceId)
            onDone?.invoke()
            if (announcementsEnabled) {
                appContext?.let { ctx -> warmUp(ctx, audioLanguage) }
            }
        }
    }

    fun shutdown() {
        mainHandler.post {
            announcementsEnabled = false
            releaseEngineOnMain()
        }
    }

    private fun releaseEngineOnMain() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pendingInitCallbacks.clear()
        initInProgress = false
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing TTS", e)
        }
        tts = null
        isInitialized = false
        currentLanguage = null
        lastHeartbeatErrorCount = 0
    }
}
