package com.softland.callqtv.ui

import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.TvConfigEntity

internal const val SPECIAL_MSG_PREFIX = "__MSG__:"
internal const val SPECIAL_COUNTER_MSG_LINE_HEIGHT_EM = 1.42f
internal const val VIP_EMERGENCY_COUNTER_PREFIX = "ER"

internal fun tokenUsesVipEmergencyPrefix(rawToken: String?, vipEmergencyRawTokens: Set<String>): Boolean {
    val trimmed = rawToken?.trim().orEmpty()
    return trimmed.isNotEmpty() && vipEmergencyRawTokens.contains(trimmed)
}

internal fun encodeSpecialMessageToken(value: String): String = SPECIAL_MSG_PREFIX + value

internal fun isSpecialMessageToken(value: String?): Boolean =
    value?.contains("__MSG__", ignoreCase = false) == true

internal fun decodeSpecialMessageToken(value: String?): String? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return when {
        raw.contains("__MSG__:") -> raw.substringAfter("__MSG__:").trim()
        raw.contains("__MSG__") -> raw.substringAfter("__MSG__").trimStart(':', '-', ' ')
        else -> raw
    }.ifBlank { null }
}

internal fun resolveCountersToDisplay(
    counters: List<CounterEntity>,
    config: TvConfigEntity,
): List<CounterEntity> {
    val enabled = counters.filter { it.isEnabled != false }
    if (enabled.isEmpty()) return emptyList()
    return if (config.layoutType.equals("full", ignoreCase = true)) {
        val gridCap = (config.displayRows ?: 1).coerceAtLeast(1) *
            (config.displayColumns ?: 1).coerceAtLeast(1)
        enabled.take(gridCap.coerceAtMost(enabled.size))
    } else {
        val limit = (config.noOfCounters ?: enabled.size).coerceAtLeast(1)
        enabled.take(limit.coerceAtMost(enabled.size))
    }
}

internal fun formatTokenByPattern(token: String?, pattern: String?): String? {
    if (token == null) return null
    if (pattern.isNullOrBlank()) return token

    return try {
        val trimmedPattern = pattern.trim()
        Regex("^T(\\d+)$", RegexOption.IGNORE_CASE).matchEntire(trimmedPattern)?.let { match ->
            val digitLen = match.groupValues[1].toIntOrNull() ?: return@let null
            val num = token.toIntOrNull() ?: return token
            return num.toString().padStart(digitLen, '0')
        }

        if (pattern.all { it == '0' || it == '#' }) {
            val num = token.toIntOrNull()
            if (num != null) {
                val len = pattern.length
                return num.toString().padStart(len, '0')
            }
        }
        if (pattern.contains("0")) {
            val num = token.toIntOrNull()
            if (num != null) {
                val firstZeroIdx = pattern.indexOf('0')
                val lastZeroIdx = pattern.lastIndexOf('0')
                val prefix = pattern.substring(0, firstZeroIdx)
                val formatLen = lastZeroIdx - firstZeroIdx + 1
                return prefix + num.toString().padStart(formatLen, '0')
            }
        }
        token
    } catch (_: Exception) {
        token
    }
}
