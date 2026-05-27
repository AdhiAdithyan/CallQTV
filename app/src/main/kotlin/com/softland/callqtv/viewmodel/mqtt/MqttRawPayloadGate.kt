package com.softland.callqtv.viewmodel.mqtt

import com.softland.callqtv.viewmodel.KeypadPayloadParser

/**
 * Fast filters applied to raw MQTT payloads before queueing for background processing.
 */
internal object MqttRawPayloadGate {

    /** Fast shape-check to drop non-MQTT-token payloads before background routing. */
    fun shouldEnqueueForBackgroundProcessing(trimmed: String): Boolean {
        if (!trimmed.startsWith("$")) return false
        val extractedKeypadSerial = KeypadPayloadParser.extractKeypadSerial(trimmed)
        val containsClr = trimmed.contains("CLR", ignoreCase = true)
        if (trimmed.length < 24 && extractedKeypadSerial == null && !containsClr) return false
        return true
    }

    /** Detects refresh-only payload form where 17th character is zero. */
    fun isRefreshOnlySeventeenthCharZero(trimmed: String): Boolean =
        trimmed.length > 16 && trimmed[16] == '0'
}
