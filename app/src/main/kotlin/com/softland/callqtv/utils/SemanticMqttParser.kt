package com.softland.callqtv.utils

import android.util.Log

/**
 * Uses AI (ML Kit) and Regex to extract Token and Counter information from unstructured text.
 */
object SemanticMqttParser {
    private const val TAG = "SemanticParser"

    // Regex fallbacks for performance and offline reliability
    private val tokenRegex = Regex("""(?i)(?:token|no|number)[:\s]*(\d+)""")
    private val counterRegex = Regex("""(?i)(?:counter|desk|point)[:\s]*([a-zA-Z\s\d]+)""")
    private val simplePairRegex = Regex("""(\d+)\s*,\s*([a-zA-Z\s\d]+)""") // "123,Counter A"

    /**
     * Parses a message to find a token and counter.
     * Returns Pair(CounterName, TokenValue)
     */
    fun parse(message: String, topic: String = ""): Pair<String, String>? {
        Log.d(TAG, "Parsing message: $message (topic: $topic)")
        val trimmedMessage = message.trim()

        // 0. Fixed-Length Protocol Check ($...*)
        if (trimmedMessage.startsWith("$") && (trimmedMessage.endsWith("*") || trimmedMessage.length >= 24)) {
            try {
                if (trimmedMessage.length >= 22) {
                    val counterNum = trimmedMessage.substring(16, 17).trim()
                    val tokenNum = trimmedMessage.substring(18, 22).trim()
                    
                    // Return counter ID and the token
                    // We trim leading zeros for display, but "0000" becomes "0"
                    val cleanToken = tokenNum.trimStart('0').ifEmpty { "0" }
                    val cleanCounter = if (counterNum == "0") "" else counterNum
                    return Pair(cleanCounter, cleanToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing fixed protocol", e)
            }
        }

        // 1. Try Key-Value Format (TOKEN: 123, COUNTER: 1)
        if (trimmedMessage.contains("TOKEN:", ignoreCase = true)) {
            val parts = trimmedMessage.split(",")
            var token = ""
            var counter = ""
            parts.forEach { part ->
                val segment = part.trim()
                val kv = segment.split(":", limit = 2)
                if (kv.size > 1) {
                    val key = kv[0].trim().uppercase()
                    val value = kv[1].trim()
                    if (key == "TOKEN") token = value
                    else if (key == "COUNTER" || key == "DESK") counter = value
                }
            }
            if (token.isNotEmpty()) {
                val cleanCounter = if (counter == "0") "" else counter
                return Pair(cleanCounter, token)
            }
        }

        // 2. Try Regex-based Extraction
        val tokenMatch = tokenRegex.find(trimmedMessage)
        val counterMatch = counterRegex.find(trimmedMessage)
        
        if (tokenMatch != null) {
            val token = tokenMatch.groupValues.getOrNull(1) ?: ""
            var counter = counterMatch?.groupValues?.getOrNull(1)?.trim() ?: ""
            if (counter == "0") counter = ""
            return Pair(counter, token)
        }

        // 3. Try "123,Main" format
        val simpleMatch = simplePairRegex.find(trimmedMessage)
        if (simpleMatch != null) {
            var counter = simpleMatch.groupValues[2].trim()
            if (counter == "0") counter = ""
            return Pair(counter, simpleMatch.groupValues[1])
        }

        // 4. Try Topic fallback if message is just a number
        val numericToken = trimmedMessage.toIntOrNull()
        if (numericToken != null && topic.isNotEmpty()) {
            val topicParts = topic.split("/")
            // Try to find a numeric part in topic that could be a counter ID (excluding "0")
            val counterFromTopic = topicParts.findLast { 
                it.isNotEmpty() && it.all { c -> c.isDigit() } && it != trimmedMessage && it != "0" 
            }
            if (counterFromTopic != null) {
                return Pair(counterFromTopic, trimmedMessage)
            }
        }

        // Last resort: If the message IS just a number, treat it as token
        if (numericToken != null) {
            return Pair("", trimmedMessage)
        }

        return null
    }
}
