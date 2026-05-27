package com.softland.callqtv.viewmodel.mqtt

/**
 * Backoff delays for MQTT reconnect attempts (before/after first successful connect, network tier).
 */
internal object MqttReconnectPolicy {

    /** Returns reconnect backoff delay based on retry stage and network condition. */
    fun reconnectDelayMs(attempt: Int, afterFirstSuccess: Boolean, lowBandwidth: Boolean): Long {
        return if (afterFirstSuccess) {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 500L
                attempt == 2 -> 1_500L
                attempt == 3 -> 3_000L
                else -> 5_000L
            }
        } else if (lowBandwidth) {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 2_000L
                attempt == 2 -> 5_000L
                attempt == 3 -> 10_000L
                attempt == 4 -> 15_000L
                else -> 20_000L
            }
        } else {
            when {
                attempt <= 0 -> 0L
                attempt == 1 -> 1_000L
                attempt == 2 -> 2_000L
                attempt == 3 -> 4_000L
                attempt == 4 -> 6_000L
                else -> 8_000L
            }
        }
    }
}
