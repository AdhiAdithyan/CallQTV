package com.softland.callqtv.viewmodel

import com.softland.callqtv.utils.SemanticMqttParser

/**
 * Extracts keypad serials from both the legacy payload format and the newer fixed protocol.
 *
 * **CLR `000-` frame:** `$` + `000-` + **exactly 11 characters = keypad serial** + one or more token
 * digits + `CLR…` + `*`. The serial never includes the token run after it.
 * Example: `$000-AbCAL0K000303CLR0*` → keypad serial `AbCAL0K0003`, token digits `03`, route index
 * (single character immediately before `CLR`) `3`.
 */
object KeypadPayloadParser {
    /**
     * @param serial The 11-char keypad serial from the frame (e.g. `AbCAL0K0003`), not the trailing token digits.
     * @param routeIndex One character before `CLR` in the full payload (typically the last digit of the token run).
     */
    data class ClearPayloadInfo(
        val serial: String,
        val routeIndex: String
    )

    fun extractKeypadSerial(message: String): String? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("$") || !trimmed.endsWith("*")) return null

        val body = trimmed.substring(1, trimmed.length - 1)

        // Prefer the fixed-protocol parser when the payload carries the trailing "-000x" shape.
        // This avoids truncating 11-char serials like "$0MP-AbCAL0K000111-0004*" through the
        // older short-prefix extraction path.
        if (body.lastIndexOf('-') >= body.length - 8) {
            SemanticMqttParser.parseFixedPayload(trimmed)?.let { fixed ->
                if (fixed.serial.isNotBlank()) return fixed.serial
            }
        }

        // CLR clear payloads: "$000-<11-char keypad SN><token digits>CLR…*" — SN is always body[4..14]
        // (e.g. "$000-AbCAL0K000303CLR0*" → SN "AbCAL0K0003", not "AbCAL0K000303").
        if (body.length >= 19 && body.getOrNull(3) == '-' && body.startsWith("000")) {
            val clrStart = body.indexOf("CLR", ignoreCase = true)
            if (clrStart >= 15 &&
                clrStart + 3 <= body.length &&
                body.regionMatches(clrStart, "CLR", 0, 3, ignoreCase = true)
            ) {
                val serial = body.substring(4, 15).trim()
                if (serial.isNotEmpty()) return serial
            }
        }

        // Current keypad payloads use a short prefix like "0Je-" before the 10-char serial.
        if (body.length >= 14 && body.getOrNull(3) == '-') {
            val serial = body.substring(4, 14).trim()
            if (serial.isNotEmpty()) return serial
        }

        if (body.length < 16) return null

        val serial = body.substring(1, 15).trim()
        return serial.ifEmpty { null }
    }

    fun extractClearPayloadInfo(message: String): ClearPayloadInfo? {
        val trimmed = message.trim()
        if (!trimmed.startsWith("$") || !trimmed.endsWith("*")) return null

        val clrIndex = trimmed.indexOf("CLR", ignoreCase = true)
        if (clrIndex <= 1) return null

        val serial = extractKeypadSerial(trimmed) ?: return null
        val routeIndex = trimmed.getOrNull(clrIndex - 1)?.toString()?.trim().orEmpty()
        if (routeIndex.isBlank()) return null

        return ClearPayloadInfo(
            serial = serial,
            routeIndex = routeIndex
        )
    }
}
