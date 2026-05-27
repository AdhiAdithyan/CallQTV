package com.softland.callqtv.viewmodel

import com.softland.callqtv.data.local.CounterEntity

/**
 * Resolves the on-screen [CounterEntity] for a routed **button_index** string from [MqttViewModel]
 * (token map / [TokenUiEvent.counter] keys use `button_index`, not `keypad_index`).
 */
internal fun findCounterEntityForMqttRoute(
    counters: List<CounterEntity>,
    buttonIndexRoute: String,
): CounterEntity? {
    val route = buttonIndexRoute.trim()
    if (route.isEmpty()) return null
    return counters.firstOrNull { mqttRouteMatchesButtonIndex(it.buttonIndex, route) }
        ?: counters.firstOrNull { mqttRouteMatchesKeypadIndex(it.keypadIndex, route) }
}

internal fun mqttRouteMatchesButtonIndex(candidate: Int?, routeIndex: String): Boolean {
    return mqttRouteMatchesString(candidate?.toString(), routeIndex)
}

/** For legacy token map keys that still use `keypad_index` strings. */
internal fun mqttRouteMatchesKeypadIndex(candidate: String?, routeIndex: String): Boolean {
    return mqttRouteMatchesString(candidate, routeIndex)
}

/** Shared route equality for keypad_index, button_index strings, and CLR route digits. */
internal fun mqttRouteMatchesString(candidate: String?, routeIndex: String): Boolean {
    val left = candidate?.trim().orEmpty()
    val right = routeIndex.trim()
    if (left.isBlank() || right.isBlank()) return false
    if (left.equals(right, ignoreCase = true)) return true
    val leftNumber = left.toIntOrNull()
    val rightNumber = right.toIntOrNull()
    return leftNumber != null && rightNumber != null && leftNumber == rightNumber
}
