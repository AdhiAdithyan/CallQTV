package com.softland.callqtv.viewmodel.mqtt

import android.app.Application
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.softland.callqtv.data.local.ConnectedDeviceDao
import com.softland.callqtv.data.local.TvConfigDao
import com.softland.callqtv.data.model.KeypadConfig
import com.softland.callqtv.viewmodel.KeypadPayloadParser
import com.softland.callqtv.viewmodel.MqttCounterRouteKeys
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory set of registered keypad serials (connected_devices + keypadsJson).
 * Thread-safe via [stateMutex] passed from [com.softland.callqtv.viewmodel.MqttViewModel].
 */
internal class MqttKeypadSerialRegistry(
    private val app: Application,
    private val connectedDeviceDao: ConnectedDeviceDao,
    private val tvConfigDao: TvConfigDao,
    private val gson: Gson,
    private val stateMutex: Mutex,
) {
    private val keypadSerials = mutableSetOf<String>()

    @Volatile
    private var keypadSerialsLoaded = false

    /** Clears cached serial set so it is reloaded from DB on next access. */
    fun invalidate() {
        stateMutex.tryLock()?.let { lockAcquired ->
            try {
                if (lockAcquired) {
                    keypadSerials.clear()
                    keypadSerialsLoaded = false
                }
            } finally {
                if (lockAcquired) stateMutex.unlock()
            }
        } ?: run {
            keypadSerialsLoaded = false
        }
    }

    /** Returns true when a normalized keypad serial exists in registered serial set. */
    suspend fun isRegistered(serial: String): Boolean {
        val normalized = MqttCounterRouteKeys.normalizeKeypadSerial(serial)
        if (normalized.isBlank()) return false
        stateMutex.withLock {
            ensureLoadedLocked()
            return keypadSerials.contains(normalized)
        }
    }

    /** Returns a snapshot list of currently cached registered keypad serials. */
    suspend fun registeredSerialsSnapshot(): List<String> =
        stateMutex.withLock {
            ensureLoadedLocked()
            keypadSerials.toList()
        }

    /** Validates whether a payload belongs to a known keypad serial for this TV context. */
    suspend fun isValidMessage(message: String): Boolean {
        val serialInMessage = KeypadPayloadParser.extractKeypadSerial(message) ?: return false
        val valid = isRegistered(serialInMessage)
        if (!valid) {
            android.util.Log.d(
                "MqttViewModel",
                "Keypad serial not in database: ${MqttCounterRouteKeys.normalizeKeypadSerial(serialInMessage)} " +
                    "payload=${message.take(80)}",
            )
        }
        return valid
    }

    /** Lazy-loads registered keypad serials from connected_devices and keypadsJson sources. */
    private suspend fun ensureLoadedLocked() {
        if (keypadSerialsLoaded) return
        try {
            val customerId = MqttDeviceContext.customerId(app)
            val macAddress = MqttDeviceContext.macAddress(app)
            val devices = connectedDeviceDao.getByMacAndCustomer(macAddress, customerId)
            val tvConfig = tvConfigDao.getByMacAndCustomer(macAddress, customerId)
            keypadSerials.clear()
            devices.forEach { device ->
                if (device.deviceType?.trim().equals("KEYPAD", ignoreCase = true)) {
                    device.serialNumber
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { keypadSerials.add(MqttCounterRouteKeys.normalizeKeypadSerial(it)) }
                    val cfg = device.configJson
                    if (!cfg.isNullOrBlank()) {
                        val regex = "20[0-9A-Za-z]{11}".toRegex()
                        regex.findAll(cfg).forEach { m ->
                            keypadSerials.add(MqttCounterRouteKeys.normalizeKeypadSerial(m.value))
                        }
                    }
                }
            }
            tvConfig?.keypadsJson?.let { json ->
                val type = object : TypeToken<List<KeypadConfig>>() {}.type
                val keypads = gson.fromJson<List<KeypadConfig>>(json, type) ?: emptyList()
                keypads.forEach { keypad ->
                    keypad.keypadSn
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { keypadSerials.add(MqttCounterRouteKeys.normalizeKeypadSerial(it)) }
                }
            }
            keypadSerialsLoaded = true
            android.util.Log.i(
                "MqttViewModel",
                "Keypad serial cache loaded (${keypadSerials.size} SNs) for MAC=$macAddress customer=$customerId",
            )
        } catch (e: Exception) {
            android.util.Log.w("MqttViewModel", "Failed to build keypad serial cache: ${e.message}")
        }
    }
}
