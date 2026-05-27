package com.softland.callqtv.viewmodel.mqtt

import com.softland.callqtv.utils.SemanticMqttParser
import com.softland.callqtv.viewmodel.KeypadPayloadParser
import com.softland.callqtv.viewmodel.MqttCounterRouteKeys
import com.softland.callqtv.viewmodel.MqttViewModel

/** Parses verified keypad payloads and enqueues UI token events. */
internal class MqttVerifiedMessageParser(
    private val keypadRegistry: MqttKeypadSerialRegistry,
    private val identityResolver: MqttCounterIdentityResolver,
    private val host: Host,
) {
    interface Host {
        fun shouldSuppressRepeatedPayload(payload: String): Boolean
        fun enqueueTokenUpdate(event: MqttViewModel.TokenUiEvent)
        fun enqueueTokenReplace(event: MqttViewModel.TokenUiEvent)
        suspend fun saveTokenRecord(message: String, counterKey: String, token: String)
        fun markKeypadSeen(buttonKey: String)
        fun markDispenseSeen(buttonKey: String)
        fun shouldSkipQueuedToken(key: String, now: Long): Boolean
        fun recordQueuedToken(key: String, now: Long)
    }

    /** Parses semantic/fixed payloads and emits token UI events plus persistence side effects. */
    suspend fun parse(topic: String, message: String) {
        try {
            val payloadSerial = KeypadPayloadParser.extractKeypadSerial(message)
                ?.let { MqttCounterRouteKeys.normalizeKeypadSerial(it) }
            if (payloadSerial.isNullOrBlank() || !keypadRegistry.isRegistered(payloadSerial)) {
                android.util.Log.d(
                    "MQTT_PAYLOAD_IN",
                    "parseMqttMessage skipped: keypad serial not registered (${payloadSerial ?: "missing"})",
                )
                return
            }
            val fixed = SemanticMqttParser.parseFixedPayload(message)
            if (fixed != null) {
                val fixedSerial = MqttCounterRouteKeys.normalizeKeypadSerial(fixed.serial)
                if (fixedSerial != payloadSerial || !keypadRegistry.isRegistered(fixedSerial)) {
                    android.util.Log.w(
                        "MQTT_PAYLOAD_IN",
                        "parseMqttMessage skipped: fixed-protocol serial mismatch payload=$payloadSerial fixed=$fixedSerial",
                    )
                    return
                }
                val identity = identityResolver.resolve(fixed.serial) ?: run {
                    android.util.Log.w(
                        "MQTT_PAYLOAD_IN",
                        "No counter for keypad_sn=${MqttCounterRouteKeys.normalizeKeypadSerial(fixed.serial)} — UI not updated",
                    )
                    return
                }
                val routedCounter = identity.storageKey
                val token = if (fixed.action == SemanticMqttParser.PayloadAction.REPLACE_COUNTER) {
                    identityResolver.resolveButtonStringValue(fixed.serial, fixed.buttonStringId, fixed.token)
                        ?: fixed.token
                } else {
                    fixed.token
                }
                host.saveTokenRecord(message, identity.counterLabel, token)
                when (fixed.action) {
                    SemanticMqttParser.PayloadAction.DB_ONLY -> return
                    SemanticMqttParser.PayloadAction.REPLACE_COUNTER -> {
                        if (host.shouldSuppressRepeatedPayload(message)) return
                        host.enqueueTokenReplace(
                            MqttViewModel.TokenUiEvent(routedCounter, token, message),
                        )
                        return
                    }
                    SemanticMqttParser.PayloadAction.NORMAL -> {
                        if (host.shouldSuppressRepeatedPayload(message)) return
                        host.enqueueTokenUpdate(
                            MqttViewModel.TokenUiEvent(
                                routedCounter,
                                token,
                                message,
                                isVipEmergency = fixed.isVipEmergency,
                            ),
                        )
                        return
                    }
                }
            }
            val result = SemanticMqttParser.parse(message, topic)
            if (result != null) {
                val (_, token) = result
                if (token == "0" || token.isBlank()) return
                val identity = identityResolver.resolve(payloadSerial) ?: return
                host.markKeypadSeen(identity.storageKey)
                host.markDispenseSeen(identity.storageKey)
                val key = "${identity.storageKey.trim()}_${token.trim()}"
                val now = System.currentTimeMillis()
                if (host.shouldSkipQueuedToken(key, now)) return
                host.recordQueuedToken(key, now)
                host.saveTokenRecord(message, identity.counterLabel, token)
                if (host.shouldSuppressRepeatedPayload(message)) return
                host.enqueueTokenUpdate(
                    MqttViewModel.TokenUiEvent(
                        counter = identity.storageKey,
                        token = token,
                        payload = message,
                    ),
                )
            }
        } catch (e: Exception) {
            android.util.Log.w(
                "MQTT_PAYLOAD_IN",
                "Failed to parse MQTT message '$message' on topic '$topic': ${e.message}",
            )
        }
    }
}
