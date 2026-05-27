package com.softland.callqtv.utils

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Enhanced TokenAnnouncer for robust cross-device voice support.
 * Uses AI-enhanced phrasing and handles multi-language announcement.
 */
internal object TokenTtsEngine {
    private const val TAG = "TokenAnnouncer"
    /** Consistent, clear pace for queue token calls (avoid 0.85f — sounds sluggish on many engines). */
    private const val TOKEN_SPEECH_RATE = 1.0f
  /** Slightly brighter pitch so token calls feel alert, not sleepy. */
    private const val TOKEN_SPEECH_PITCH = 1.06f
    /** Longer informational / special messages: still clear, not as slow as legacy 0.85f. */
    private const val INFO_MESSAGE_SPEECH_RATE = 0.96f
    private const val INFO_MESSAGE_SPEECH_PITCH = 1.0f
    /** Keep the engine bound on idle TVs (was 8s — too long; service slept before next token). */
    private const val HEARTBEAT_INTERVAL_MS = 3_000L
    private const val HEARTBEAT_SILENT_MS = 120L
    private const val HEARTBEAT_FAILURES_BEFORE_REINIT = 6
    /**
     * Silent heartbeats keep the engine bound; many TV stacks still unload voice synthesis after
     * ~30–60s without an audible utterance. Re-prime on this interval while idle.
     */
    private const val IDLE_SYNTHESIS_KEEP_WARM_INTERVAL_MS = 15_000L
    /** Token path may still re-prime if idle keep-warm was skipped (e.g. during long speech). */
    private const val SPEECH_WAKE_IDLE_MS = 60_000L
    private const val PRIME_DEBOUNCE_MS = 4_000L
    private const val PRIME_TIMEOUT_MS = 4_500L
    /** Failsafe when [UtteranceProgressListener.onDone] is never delivered (common on some TV engines). */
    private const val UTTERANCE_COMPLETION_TIMEOUT_MS = 45_000L
    /** Nearly silent; loads the active voice without a noticeable announcement. */
    private const val PRIME_VOLUME = 0.01f
    /** Quiet warm-up phrase (not the localized token word). */
    private const val SYNTHESIS_PRIME_PHRASE = "wellcome"

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
    private val utteranceTimeoutRunnables =
        java.util.concurrent.ConcurrentHashMap<String, Runnable>()

    @Volatile
    private var lastRealSpeechAtMs = 0L
    @Volatile
    private var lastPrimeAtMs = 0L
    @Volatile
    private var synthesisPrimeInFlight = false
    private val pendingAfterPrime = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    /**
     * While > 0, heartbeats / idle primes / engine pokes are suppressed so queued token speech
     * is not corrupted by silent utterances on many TV TTS engines.
     */
    @Volatile
    private var activeTokenAnnouncementCycles = 0

    fun isAnnouncementsEnabled(): Boolean = announcementsEnabled

    fun enterTokenAnnouncementCycle() {
        activeTokenAnnouncementCycles++
    }

    fun exitTokenAnnouncementCycle() {
        activeTokenAnnouncementCycles = (activeTokenAnnouncementCycles - 1).coerceAtLeast(0)
    }

    private fun isInsideTokenAnnouncementCycle(): Boolean = activeTokenAnnouncementCycles > 0

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

    /**
     * Suspends until the TTS engine is bound and warmed, or returns false when announcements are
     * disabled or initialization fails. Use before the first spoken token on cold start.
     */
    suspend fun awaitReady(
        context: Context,
        audioLanguage: String? = null,
        performPoke: Boolean = true,
        primeSynthesis: Boolean = true,
    ): Boolean {
        if (!announcementsEnabled) return false
        val ready = suspendCancellableCoroutine { cont ->
            warmUp(context, audioLanguage, performPoke = false) { bound ->
                if (!bound) {
                    if (cont.isActive) cont.resume(false)
                    return@warmUp
                }
                val finish: () -> Unit = {
                    if (performPoke) performEnginePokeOnMain()
                    if (cont.isActive) cont.resume(true)
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    finish()
                } else {
                    mainHandler.post { finish() }
                }
            }
        }
        if (!ready) return false
        if (primeSynthesis) {
            awaitSynthesisPrimeIfNeeded()
        }
        return true
    }

    /**
     * Runs the quiet [SYNTHESIS_PRIME_PHRASE] warm-up when synthesis is cold. Call from
     * [runWithAdvertisementAudioDuckedForSpeech] (after ads are ducked) before the real announcement.
     */
    suspend fun awaitSynthesisPrimeIfNeeded() {
        if (!announcementsEnabled) return
        suspendCancellableCoroutine { cont ->
            val run = {
                val finish: () -> Unit = {
                    if (cont.isActive) cont.resume(Unit)
                }
                if (!needsSpeechWake()) {
                    finish()
                } else if (synthesisPrimeInFlight) {
                    pendingAfterPrime.add(finish)
                } else {
                    primeSpeechSynthesisOnMain(finish)
                }
                Unit
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                run()
            } else {
                mainHandler.post { run() }
            }
        }
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
                appContext?.let { ensureHeartbeatScheduled(it) }
                if (performPoke) {
                    performEnginePokeOnMain()
                    primeSpeechSynthesisOnMain {}
                }
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
                    override fun onStart(utteranceId: String?) {
                        utteranceId ?: return
                        when {
                            utteranceId.startsWith("token_") || utteranceId.startsWith("msg_") ->
                                lastRealSpeechAtMs = System.currentTimeMillis()
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        utteranceId?.let { id -> completeUtterance(id) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        utteranceId?.let { id -> completeUtterance(id) }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.w(TAG, "TTS utterance error id=$utteranceId code=$errorCode")
                        utteranceId?.let { id -> completeUtterance(id) }
                    }
                })
                setupLanguage(audioLanguage)
                appContext?.let { scheduleHeartbeat(it) }
            } else {
                Log.e(TAG, "TTS initialization failed (status=$status)")
                isInitialized = false
            }
        }
        if (status != TextToSpeech.SUCCESS) {
            finishInitAttempt(false)
            return
        }
        // Release waiters as soon as the engine is bound; prime runs async (blocking here hung first token speech).
        finishInitAttempt(true)
        performEnginePokeOnMain()
        primeSpeechSynthesisOnMain {
            Log.d(TAG, "TTS init synthesis prime complete")
        }
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
        if (!announcementsEnabled || isInsideTokenAnnouncementCycle()) return
        synchronized(this) {
            if (!isInitialized || tts == null) return
            try {
                tts?.playSilentUtterance(
                    HEARTBEAT_SILENT_MS.toLong(),
                    TextToSpeech.QUEUE_ADD,
                    "poke_${System.nanoTime()}",
                )
            } catch (e: Exception) {
                Log.w(TAG, "Engine poke failed: ${e.message}")
            }
        }
    }

    /** True when the engine is bound but voice synthesis has not run (or not recently). */
    private fun needsSpeechWake(): Boolean {
        val lastWake = maxOf(lastRealSpeechAtMs, lastPrimeAtMs)
        if (lastWake == 0L) return true
        return System.currentTimeMillis() - lastWake > SPEECH_WAKE_IDLE_MS
    }

    /**
     * Periodic quiet prime while the TV is idle so the next token does not pay voice-load latency.
     * Silent [playSilentUtterance] heartbeats alone are not enough on many devices.
     */
    private fun keepSynthesisWarmDuringIdle() {
        if (!announcementsEnabled || isInsideTokenAnnouncementCycle()) return
        synchronized(this) {
            if (!isInitialized || tts == null) return
        }
        if (tts?.isSpeaking == true) return
        val now = System.currentTimeMillis()
        if (now - lastPrimeAtMs < IDLE_SYNTHESIS_KEEP_WARM_INTERVAL_MS) return
        primeSpeechSynthesisOnMain {}
    }

    /**
     * Runs [action] on the main thread after a quiet prime utterance when synthesis is cold.
     * Silent heartbeats do not load voice data on many TV TTS engines.
     */
    private fun runOnMainAfterSpeechWake(action: () -> Unit) {
        val run = {
            if (needsSpeechWake()) {
                primeSpeechSynthesisOnMain(action)
            } else {
                action()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run()
        } else {
            mainHandler.post { run() }
        }
    }

    /** True when a synthesis prime is likely needed before the next spoken token. */
    fun needsSynthesisWarmUp(): Boolean = needsSpeechWake()

    /**
     * Stops any in-flight speech and cancels pending token/message callbacks before the next
     * queued announcement (avoids [TextToSpeech.QUEUE_FLUSH] cutting a prior utterance mid-word).
     */
    suspend fun prepareForNextTokenAnnouncement() {
        if (!announcementsEnabled) return
        suspendCancellableCoroutine { cont ->
            mainHandler.post {
                try {
                    synchronized(this) {
                        if (!isInitialized || tts == null) return@synchronized
                        runCatching { tts?.stop() }
                        completionCallbacks.keys
                            .filter { id -> id.startsWith("msg_") || id.startsWith("token_") }
                            .toList()
                            .forEach { cancelUtteranceCompletion(it) }
                    }
                } finally {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    /**
     * Speaks a nearly silent phrase in the configured language so the first user-facing
     * announcement does not pay multi-second voice-load latency.
     */
    private fun primeSpeechSynthesisOnMain(onComplete: () -> Unit) {
        if (!announcementsEnabled) {
            onComplete()
            return
        }
        if (isInsideTokenAnnouncementCycle()) {
            onComplete()
            return
        }
        synchronized(this) {
            if (!isInitialized || tts == null) {
                onComplete()
                return
            }
            val now = System.currentTimeMillis()
            if (synthesisPrimeInFlight) {
                pendingAfterPrime.add(onComplete)
                return
            }
            if (now - lastPrimeAtMs < PRIME_DEBOUNCE_MS) {
                onComplete()
                return
            }
            synthesisPrimeInFlight = true
        }
        val primePhrase = SYNTHESIS_PRIME_PHRASE
        val utteranceId = "prime_${System.nanoTime()}"
        val timeoutRunnable = Runnable {
            if (completionCallbacks.remove(utteranceId) != null) {
                Log.w(TAG, "Synthesis prime timed out after ${PRIME_TIMEOUT_MS}ms")
                completeSynthesisPrime(onComplete)
            }
        }
        mainHandler.postDelayed(timeoutRunnable, PRIME_TIMEOUT_MS)
        completionCallbacks[utteranceId] = {
            mainHandler.removeCallbacks(timeoutRunnable)
            lastPrimeAtMs = System.currentTimeMillis()
            completeSynthesisPrime(onComplete)
        }
        applyTokenSpeechStyle()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, PRIME_VOLUME)
        }
        val result = tts?.speak(primePhrase, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            mainHandler.removeCallbacks(timeoutRunnable)
            completionCallbacks.remove(utteranceId)
            Log.w(TAG, "Synthesis prime speak() returned ERROR")
            completeSynthesisPrime(onComplete)
        } else {
            Log.d(TAG, "Synthesis prime started ($primePhrase)")
        }
    }

    private fun completeUtterance(utteranceId: String) {
        utteranceTimeoutRunnables.remove(utteranceId)?.let { mainHandler.removeCallbacks(it) }
        completionCallbacks.remove(utteranceId)?.invoke()
    }

    private fun armUtteranceCompletion(utteranceId: String, onDone: () -> Unit) {
        val timeoutRunnable = Runnable {
            if (completionCallbacks.remove(utteranceId) != null) {
                utteranceTimeoutRunnables.remove(utteranceId)
                Log.w(TAG, "Utterance timed out after ${UTTERANCE_COMPLETION_TIMEOUT_MS}ms id=$utteranceId")
                onDone()
            }
        }
        utteranceTimeoutRunnables[utteranceId] = timeoutRunnable
        completionCallbacks[utteranceId] = onDone
        mainHandler.postDelayed(timeoutRunnable, UTTERANCE_COMPLETION_TIMEOUT_MS)
    }

    private fun cancelUtteranceCompletion(utteranceId: String) {
        utteranceTimeoutRunnables.remove(utteranceId)?.let { mainHandler.removeCallbacks(it) }
        completionCallbacks.remove(utteranceId)
    }

    private fun completeSynthesisPrime(primary: () -> Unit) {
        synthesisPrimeInFlight = false
        primary()
        val pending = pendingAfterPrime.toList()
        pendingAfterPrime.clear()
        pending.forEach { run ->
            try {
                run()
            } catch (e: Exception) {
                Log.w(TAG, "pendingAfterPrime callback failed: ${e.message}")
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

    private fun setupLanguage(audioLanguage: String?) {
        if (currentLanguage == audioLanguage && isInitialized) return
        
        val targetLocale = when (TokenSpeechPhrasing.normalizeLanguageCode(audioLanguage)) {
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
        skipSynthesisPrime: Boolean = false,
        pokeEngine: Boolean = !skipSynthesisPrime,
    ) {
        val tokenWord = TokenSpeechPhrasing.localizedTokenWord(audioLanguage)
        val tokenBody = TokenSpeechPhrasing.buildTokenAnnouncementBody(
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
            pokeEngine = pokeEngine,
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
                skipSynthesisPrime = skipSynthesisPrime,
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
        onDone: (() -> Unit)? = null,
        skipSynthesisPrime: Boolean = false,
        pokeEngine: Boolean = !skipSynthesisPrime,
    ) {
        runWithReadyEngine(
            context,
            audioLanguage,
            onNotReady = { onDone?.invoke() },
            pokeEngine = pokeEngine,
        ) {
            speakRawNow(
                message,
                speechRate,
                INFO_MESSAGE_SPEECH_PITCH,
                audioLanguage,
                onDone,
                queueMode = TextToSpeech.QUEUE_FLUSH,
                skipSynthesisPrime = skipSynthesisPrime,
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

    private fun ensureHeartbeatScheduled(context: Context) {
        appContext = context.applicationContext
        if (heartbeatJob?.isActive == true) return
        scheduleHeartbeat(context)
    }

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
        if (!announcementsEnabled || isInsideTokenAnnouncementCycle()) return
        mainHandler.post {
            if (!announcementsEnabled || isInsideTokenAnnouncementCycle()) return@post
            synchronized(this@TokenTtsEngine) {
                if (!isInitialized || tts == null) return@post
                if (tts?.isSpeaking == true) return@post
                try {
                    val result = tts?.playSilentUtterance(
                        HEARTBEAT_SILENT_MS.toLong(),
                        TextToSpeech.QUEUE_ADD,
                        "heartbeat_${System.nanoTime()}",
                    )
                    if (result == TextToSpeech.ERROR) {
                        lastHeartbeatErrorCount++
                        Log.w(TAG, "TTS heartbeat failed ($lastHeartbeatErrorCount)")
                    } else {
                        lastHeartbeatErrorCount = 0
                    }
                    if (lastHeartbeatErrorCount >= HEARTBEAT_FAILURES_BEFORE_REINIT) {
                        Log.e(TAG, "TTS unresponsive; re-binding engine")
                        reinitializeOnMain()
                    } else {
                        keepSynthesisWarmDuringIdle()
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
        runOnMainAfterSpeechWake {
            deliverTokenSpeechOnMain(counter, token, includeTokenWord, onDone)
        }
    }

    private fun deliverTokenSpeechOnMain(
        counter: String,
        token: String,
        includeTokenWord: Boolean,
        onDone: (() -> Unit)?,
    ) {
        val lang = TokenSpeechPhrasing.normalizeLanguageCode(currentLanguage)
        val phrase = TokenSpeechPhrasing.buildTokenAnnouncementBody(
            tokenText = token,
            audioLanguage = currentLanguage,
            counterDisplayName = counter,
            includeTokenWord = includeTokenWord,
        )

        Log.d(TAG, "Announcing ($lang): $phrase")
        
        val utteranceId = "token_${System.nanoTime()}"
        onDone?.let { armUtteranceCompletion(utteranceId, it) }

        applyTokenSpeechStyle()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "token speak() returned ERROR")
            isInitialized = false
            cancelUtteranceCompletion(utteranceId)
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
            .let { if (collapseSpelledWords) TokenSpeechPhrasing.localizeTokenForSpeech(it, audioLanguage) else it }
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
        skipSynthesisPrime: Boolean = false,
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
                skipSynthesisPrime,
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
        skipSynthesisPrime: Boolean,
    ) {
        if (!isInitialized || tts == null) {
            onDone?.invoke()
            return
        }
        val deliverSpeech = {
            deliverRawSpeechOnMain(
                message,
                speechRate,
                speechPitch,
                audioLanguage,
                onDone,
                collapseSpelledWords,
                queueMode,
            )
        }
        if (skipSynthesisPrime) {
            deliverSpeech()
        } else {
            runOnMainAfterSpeechWake(deliverSpeech)
        }
    }

    private fun deliverRawSpeechOnMain(
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
        onDone?.let { armUtteranceCompletion(utteranceId, it) }
        tts?.setSpeechRate(speechRate.coerceIn(0.5f, 1.2f))
        tts?.setPitch(speechPitch.coerceIn(0.5f, 2.0f))
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        val result = tts?.speak(phrase, queueMode, params, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.w(TAG, "speak() returned ERROR; marking engine cold")
            isInitialized = false
            cancelUtteranceCompletion(utteranceId)
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
        lastRealSpeechAtMs = 0L
        lastPrimeAtMs = 0L
        synthesisPrimeInFlight = false
        pendingAfterPrime.clear()
        utteranceTimeoutRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        utteranceTimeoutRunnables.clear()
        completionCallbacks.clear()
    }
}
