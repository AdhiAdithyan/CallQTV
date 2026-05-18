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
    if (candidate == null) return false
    val right = routeIndex.trim()
    if (right.isBlank()) return false
    if (candidate.toString() == right) return true
    val rightNumber = right.toIntOrNull()
    return rightNumber != null && candidate == rightNumber
}

/** For legacy token map keys that still use `keypad_index` strings. */
internal fun mqttRouteMatchesKeypadIndex(candidate: String?, routeIndex: String): Boolean {
    val left = candidate?.trim().orEmpty()
    val right = routeIndex.trim()
    if (left.isBlank() || right.isBlank()) return false
    if (left.equals(right, ignoreCase = true)) return true
    val leftNumber = left.toIntOrNull()
    val rightNumber = right.toIntOrNull()
    return leftNumber != null && rightNumber != null && leftNumber == rightNumber
}
