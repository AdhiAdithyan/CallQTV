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
                        tts?.setSpeechRate(0.90f) // Slow down for clarity
                        
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
                        scheduleHeartbeat(context.applicationContext)
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
            // If TTS is not ready, initialize and then speak in the init callback
            if (tts == null || !isInitialized) {
                initialize(context, audioLanguage) { success ->
                    if (success) {
                        setupLanguage(audioLanguage)
                        speakNow(counterName, tokenLabel, onDone)
                    } else {
                        onDone?.invoke()
                    }
                }
            } else {
                setupLanguage(audioLanguage)
                speakNow(counterName, tokenLabel, onDone)
            }
        }
    }

    private var heartbeatJob: Job? = null
    private var lastHeartbeatErrorCount = 0

    private fun scheduleHeartbeat(context: Context) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                // Heartbeat every 8 seconds to prevent both TTS and Audio Hardware sleep
                delay(8000L)
                synchronized(this@TokenAnnouncer) {
                    if (isInitialized && tts != null) {
                        try {
                            val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                tts?.playSilentUtterance(10, TextToSpeech.QUEUE_ADD, "heartbeat")
                            } else {
                                @Suppress("DEPRECATION")
                                tts?.speak(" ", TextToSpeech.QUEUE_ADD, null)
                            }

                            if (result == TextToSpeech.ERROR) {
                                lastHeartbeatErrorCount++
                                Log.w(TAG, "TTS Heartbeat failed ($lastHeartbeatErrorCount)")
                            } else {
                                lastHeartbeatErrorCount = 0
                            }

                            // If we fail multiple times, the service is likely dead/disconnected
                            if (lastHeartbeatErrorCount >= 3) {
                                Log.e(TAG, "TTS service unresponsive. Re-initializing...")
                                reinitialize(context.applicationContext)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Heartbeat error", e)
                        }
                    }
                }
            }
        }
    }

    private fun reinitialize(context: Context) {
        val oldLang = currentLanguage
        shutdown()
        initialize(context, oldLang)
    }

    private fun speakNow(counter: String, token: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet. Skipping announcement.")
            onDone?.invoke()
            return
        }
        
        val lang = currentLanguage?.lowercase() ?: "en"

        // 1. Format the token string (Natural reading)
        val tokenText = token.replace(Regex("[^a-zA-Z0-9]"), " ")

        // 2. Select Phrase based on Language
        val phrase = when (lang) {
            "hi", "hindi" -> {
                if (counter.isNotBlank()) "Token $tokenText,$counter vahaan jaen"
                else "Token number $tokenText"
            }
            "ta", "tamil" -> {
                if (counter.isNotBlank()) "Token $tokenText, $counter ange Cellavum"
                else "Token number $tokenText"
            }
            "ml", "malayalam" -> {
                if (counter.isNotBlank()) "Token $tokenText, $counter ilekku varuka"
                else "Token number $tokenText"
            }
            else -> {
                if (counter.isNotBlank()) "Token $tokenText,Counter $counter"
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
        heartbeatJob = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
        currentLanguage = null
        lastHeartbeatErrorCount = 0
    }
}
