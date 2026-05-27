package com.softland.callqtv.viewmodel.mqtt

import com.softland.callqtv.utils.SemanticMqttParser
import com.softland.callqtv.viewmodel.KeypadPayloadParser
import com.softland.callqtv.viewmodel.ResolvedCounterIdentity
import com.softland.callqtv.viewmodel.mqttRouteMatchesString

/** Validates that a CLR payload maps to the same serial/route as resolved counter identity. */
internal fun mqttClrPayloadMatchesResolvedCounter(
    payload: String,
    serial: String,
    routeIndex: String,
): Boolean {
    val payloadSerial = KeypadPayloadParser.extractKeypadSerial(payload) ?: return false
    if (!payloadSerial.trim().equals(serial.trim(), ignoreCase = true)) return false
    KeypadPayloadParser.extractClearPayloadInfo(payload)?.let { clearInfo ->
        return mqttRouteMatchesString(clearInfo.routeIndex, routeIndex)
    }
    if (SemanticMqttParser.parseFixedPayload(payload) != null) return true
    return true
}

/** Clears in-memory token state for CLR payloads. */
internal class MqttClrTokenOperations(
    private val identityResolver: MqttCounterIdentityResolver,
    private val host: Host,
) {
    interface Host {
        suspend fun resolveIdentity(serial: String, routeIndex: String): ResolvedCounterIdentity?
        suspend fun applyClear(keysToClear: Set<String>, serial: String, routeIndex: String)
    }

    /** Resolves all alias keys for CLR and clears token state across matching map keys. */
    suspend fun clearTokensForResolvedCounter(serial: String, routeIndex: String) {
        val trimmedRoute = routeIndex.trim()
        val resolved =
            if (trimmedRoute.isNotEmpty()) host.resolveIdentity(serial, trimmedRoute) else null
        val storageKey = resolved?.storageKey?.trim().orEmpty()
        val counterLabel = resolved?.counterLabel?.trim().orEmpty()
        val aliasKeys =
            if (trimmedRoute.isNotEmpty()) identityResolver.resolveClrInternalMapAliasKeys(serial, trimmedRoute)
            else emptySet()
        val keypadKeys = identityResolver.collectTokenMapKeysForClrKeypad(serial, trimmedRoute)
        val keysToClear = linkedSetOf<String>().apply {
            add(storageKey)
            add(counterLabel)
            if (trimmedRoute.isNotEmpty()) add(trimmedRoute)
            addAll(aliasKeys)
            addAll(keypadKeys)
        }.map { it.trim() }.filter { it.isNotBlank() }.distinct().toSet()

        if (keysToClear.isEmpty()) {
            android.util.Log.d(
                "MqttViewModel",
                "CLR: no token map keys for serial='$serial' route='$trimmedRoute' (check tv_config keypads for this SN)",
            )
            return
        }
        host.applyClear(keysToClear, serial, trimmedRoute)
    }
}
