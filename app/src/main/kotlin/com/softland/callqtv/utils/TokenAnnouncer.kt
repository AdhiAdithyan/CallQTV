package com.softland.callqtv.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.LinkedList
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
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentLanguage: String? = null
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val completionCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun initialize(context: Context, audioLanguage: String? = null, onInitComplete: (Boolean) -> Unit = {}) {
        synchronized(this) {
            if (tts == null) {
                tts = TextToSpeech(context.applicationContext) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        Log.i(TAG, "TTS Initialized successfully")
                        tts?.setSpeechRate(0.80f) // Slow down for clarity
                        
                        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                utteranceId?.let { id ->
                                    completionCallbacks.remove(id)?.invoke()
                                }
                            }
                            override fun onError(utteranceId: String?) {
                                utteranceId?.let { id ->
                                    completionCallbacks.remove(id)?.invoke()
                                }
                            }
                        })
                        
                        setupLanguage(audioLanguage)
                        scheduleHeartbeat()
                        onInitComplete(true)
                    } else {
                        Log.e(TAG, "TTS Initialization failed")
                        onInitComplete(false)
                    }
                }
            } else {
                setupLanguage(audioLanguage)
                onInitComplete(true)
            }
        }
    }

    private fun setupLanguage(audioLanguage: String?) {
        if (currentLanguage == audioLanguage && isInitialized) return
        
        val targetLocale = when (audioLanguage?.lowercase()) {
            "hi", "hindi" -> Locale("hi", "IN")
            "ta", "tamil" -> Locale("ta", "IN")
            "ml", "malayalam" -> Locale("ml", "IN")
            else -> Locale.US
        }
        
        try {
            tts?.language = targetLocale
            
            // Attempt to select specific requested voices
            val availableVoices = tts?.voices
            if (availableVoices != null) {
                val langCode = targetLocale.language
                var preferredVoiceName: String? = null
                
                if (langCode == "ml") preferredVoiceName = "karthika"
                else if (langCode == "ta") preferredVoiceName = "sentamil"
                
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
        } catch (e: Exception) {
            Log.e(TAG, "Error setting language: $audioLanguage", e)
        }
    }

    fun announceToken(context: Context, audioLanguage: String?, counterName: String, tokenLabel: String, onDone: (() -> Unit)? = null) {
        synchronized(this) {
            if (tts == null) {
                initialize(context, audioLanguage)
            } else {
                setupLanguage(audioLanguage)
            }
            speakNow(counterName, tokenLabel, onDone)
        }
    }

    private var heartbeatJob: Job? = null

    private fun scheduleHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                // Aggressive keep-alive: 45 seconds to prevent ANY sleep
                delay(45 * 1000L) 
                synchronized(this@TokenAnnouncer) {
                    if (isInitialized) {
                        // Play short silence
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                            tts?.playSilentUtterance(500, TextToSpeech.QUEUE_ADD, "heartbeat")
                        } else {
                            tts?.speak(" ", TextToSpeech.QUEUE_ADD, null, "heartbeat")
                        }
                    }
                }
            }
        }
    }

    private fun speakNow(counter: String, token: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet. Skipping announcement.")
            onDone?.invoke()
            return
        }
        
        val lang = currentLanguage?.lowercase() ?: "en"

        // 1. Format the token string (Natural reading)
        // We allow the TTS engine to read numbers naturally (e.g. "33" becomes "Thirty Three").
        // We replace non-alphanumeric characters with spaces to avoid reading punctuation (e.g. "dash").
        val tokenText = token.replace(Regex("[^a-zA-Z0-9]"), " ")

        // 2. Select Phrase based on Language
        // Using transliterated strings which most TTS engines handle reasonably well for these languages
        // independent of script rendering support.
        val phrase = when (lang) {
            "hi", "hindi" -> {
                if (counter.isNotBlank()) "Token number $tokenText, kripya $counter par jayen" 
                else "Token number $tokenText"
            }
            "ta", "tamil" -> {
                if (counter.isNotBlank()) "Token number $tokenText, thayavu seithu $counter sellavum" 
                else "Token number $tokenText"
            }
            "ml", "malayalam" -> {
                if (counter.isNotBlank()) "Token number $tokenText, dayavayi $counter ilekku varuka" 
                else "Token number $tokenText"
            }
            else -> {
                if (counter.isNotBlank()) "Token $tokenText, please proceed to $counter" 
                else "Token $tokenText"
            }
        }

        Log.d(TAG, "Announcing ($lang): $phrase")
        
        val utteranceId = "token_${System.nanoTime()}"
        if (onDone != null) {
            completionCallbacks[utteranceId] = onDone
        }
        
        // Use QUEUE_ADD to ensure each announcement COMPLETES without being "broken" or "interrupted"
        tts?.speak(phrase, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun shutdown() {
        heartbeatJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        currentLanguage = null
    }
}
