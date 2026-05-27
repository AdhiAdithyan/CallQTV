package com.softland.callqtv.viewmodel.mqtt

import com.softland.callqtv.viewmodel.KeypadPayloadParser

/** First-hop routing for raw MQTT payloads (CLR, refresh-only, verified token). */
internal class MqttInboundPayloadRouter(
    private val keypadRegistry: MqttKeypadSerialRegistry,
    private val host: Host,
) {
    interface Host {
        suspend fun isValidKeypadMessage(message: String): Boolean
        fun saveIncomingMqttPayload(trimmed: String)
        fun onVerifiedPayload(topic: String, trimmed: String)
        suspend fun clearTokensForClr(serial: String, routeIndex: String)
        fun markPayloadDisplayed(payload: String)
        fun requestConfigRefresh(reason: String, forceImmediate: Boolean = false)
    }

    /** Routes verified payload to CLR clear flow, refresh trigger, or token parser path. */
    suspend fun process(topic: String, trimmed: String) {
        if (!MqttRawPayloadGate.shouldEnqueueForBackgroundProcessing(trimmed)) return
        val extractedKeypadSerial = KeypadPayloadParser.extractKeypadSerial(trimmed)
        val clearInfo = KeypadPayloadParser.extractClearPayloadInfo(trimmed)
        val containsClr = trimmed.contains("CLR", ignoreCase = true)
        val isValidKeypadPayload = keypadRegistry.isValidMessage(trimmed)

        if (containsClr) {
            if (!isValidKeypadPayload) {
                android.util.Log.d(
                    "MQTT_PAYLOAD_IN",
                    "Ignored CLR refresh trigger: Keypad serial mismatch or device not found $trimmed",
                )
                return
            }
            host.saveIncomingMqttPayload(trimmed)
            val clrSerial = clearInfo?.serial ?: extractedKeypadSerial
            if (!clrSerial.isNullOrBlank()) {
                host.clearTokensForClr(clrSerial, clearInfo?.routeIndex.orEmpty())
            } else {
                android.util.Log.w(
                    "MQTT_PAYLOAD_IN",
                    "CLR accepted but could not extract keypad serial from payload: $trimmed",
                )
            }
            host.markPayloadDisplayed(trimmed)
            host.requestConfigRefresh(
                "MQTT refresh trigger detected (payload contains CLR)",
                forceImmediate = true,
            )
            return
        }

        if (MqttRawPayloadGate.isRefreshOnlySeventeenthCharZero(trimmed)) {
            if (isValidKeypadPayload) {
                host.saveIncomingMqttPayload(trimmed)
            }
            host.requestConfigRefresh("MQTT refresh trigger detected (17th char '0')")
            return
        }

        if (!isValidKeypadPayload) {
            android.util.Log.d(
                "MQTT_PAYLOAD_IN",
                "Ignored message: Keypad serial mismatch or device not found $trimmed",
            )
            return
        }

        host.saveIncomingMqttPayload(trimmed)
        android.util.Log.i(
            "MQTT_PAYLOAD_IN",
            "!!! VERIFIED MQTT MESSAGE !!! Topic: [$topic], Payload: $trimmed",
        )
        host.onVerifiedPayload(topic, trimmed)
    }
}
