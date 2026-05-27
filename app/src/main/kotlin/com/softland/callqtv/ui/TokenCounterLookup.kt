package com.softland.callqtv.ui

import com.softland.callqtv.data.local.CounterEntity

internal fun counterStorageLookupKey(counter: CounterEntity): String {
    val btn = counter.buttonIndex?.toString()?.trim()?.takeIf { it.isNotBlank() }
    if (!btn.isNullOrBlank()) return btn
    return counter.keypadIndex?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.counterId?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.name?.trim()?.takeIf { it.isNotBlank() }
        ?: counter.defaultName?.trim()?.takeIf { it.isNotBlank() }
        ?: ""
}

internal fun getTokensForCounter(
    counter: CounterEntity,
    tokensPerCounter: Map<String, List<String>>,
): List<String> {
    val storageKey = counterStorageLookupKey(counter)
    val btnKey = counter.buttonIndex?.toString()?.trim().orEmpty()
    val lookupKeys = linkedSetOf<String>()
    if (storageKey.isNotBlank()) lookupKeys.add(storageKey)
    counter.counterId?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    counter.name?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    counter.defaultName?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    if (btnKey.isBlank()) {
        counter.keypadIndex?.trim()?.takeIf { it.isNotBlank() }?.let { lookupKeys.add(it) }
    }

    var rawList: List<String>? = null
    for (key in lookupKeys) {
        val candidate = tokensPerCounter[key]
        if (!candidate.isNullOrEmpty()) {
            rawList = candidate
            break
        }
    }
    if (rawList == null && storageKey == "__default__") {
        rawList = tokensPerCounter["__default__"]
    }
    rawList = rawList ?: emptyList()

    return rawList
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
}
