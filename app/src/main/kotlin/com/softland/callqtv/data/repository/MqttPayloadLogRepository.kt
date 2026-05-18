package com.softland.callqtv.data.repository

import android.content.Context
import android.util.Log
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.data.local.MqttPayloadLogEntity
import com.softland.callqtv.data.model.TokenReportRequest
import com.softland.callqtv.data.network.RetrofitClient
import com.softland.callqtv.utils.NetworkUtil
import com.softland.callqtv.utils.PreferenceHelper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MqttPayloadLogRepository(
    private val database: AppDatabase,
    private val context: Context
) {
    private val dao = database.mqttPayloadLogDao()
    private val api = RetrofitClient.getApiLicenseService(context)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    @Volatile
    private var lastCleanupAtMs: Long = 0L

    suspend fun saveIncomingPayload(payload: String) {
        val normalizedPayload = payload.trim()
        if (normalizedPayload.isEmpty()) return
        if (dao.countPendingByPayload(normalizedPayload) > 0) return

        val now = LocalDateTime.now().format(dateTimeFormatter)
        dao.insert(
            MqttPayloadLogEntity(
                messagePayload = normalizedPayload,
                reaceivedTime = now,
                // Display time must remain empty until the UI actually renders the payload.
                displayedTime = "",
                isUploaded = 0
            )
        )
        cleanupUploadedOlderThanDays()
    }

    suspend fun markDisplayed(payload: String) {
        if (payload.isBlank()) return
        val now = LocalDateTime.now().format(dateTimeFormatter)
        dao.markDisplayedByPayload(payload.trim(), now)
    }

    suspend fun syncPendingPayloads(batchSize: Int = 100): Boolean {
        if (!NetworkUtil.isNetworkAvailable(context)) return false

        val pending = dao.getPending(batchSize)
        if (pending.isEmpty()) {
            cleanupUploadedOlderThanDays()
            return true
        }

        val authPrefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val customerIdInt = authPrefs.getInt(PreferenceHelper.customer_id, 0)
        val customerId = authPrefs.getString(PreferenceHelper.customer_id_text, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: String.format(Locale.ROOT, "%04d", customerIdInt)
        val macAddress = com.softland.callqtv.utils.Variables.getMacId(context)
        val savedBaseUrl = authPrefs.getString(PreferenceHelper.base_url, "https://py.softlandindia.net/CallQ/") ?: "https://py.softlandindia.net/CallQ/"
        val baseUrl = if (savedBaseUrl.endsWith("/")) savedBaseUrl else "$savedBaseUrl/"
        val url = "${baseUrl}api/external/token-report"

        return try {
            var uploadedCount = 0
            val uniqueByPayload = pending
                .groupBy { it.messagePayload.trim() }
                .mapNotNull { (_, rows) -> rows.minByOrNull { it.id } }

            uniqueByPayload.forEach { row ->
                val response = api.uploadMqttPayloadLogs(
                    url = url,
                    request = TokenReportRequest(
                        receivedMessage = row.messagePayload,
                        receivedDateTime = toIsoDateTime(row.reaceivedTime),
                        displayedDateTime = toIsoDateTime(row.displayedTime),
                        customerId = customerId,
                        macAddress = macAddress
                    )
                )
                if (response.status.equals("success", ignoreCase = true)) {
                    // Mark all pending duplicates with same payload to prevent re-sending.
                    dao.markPendingByPayloadAsUploaded(row.messagePayload)
                    uploadedCount++
                }
            }
            cleanupUploadedOlderThanDays()
            uploadedCount > 0
        } catch (e: Exception) {
            Log.w("MqttPayloadLogRepo", "Failed to sync pending payload logs: ${e.message}")
            false
        }
    }

    /**
     * Deletes only uploaded MQTT log rows older than [retentionDays] from received time.
     * Unuploaded rows are always preserved.
     */
    suspend fun cleanupUploadedOlderThanDays(retentionDays: Long = 2L) {
        val nowMs = System.currentTimeMillis()
        // Run at most once every 6 hours to avoid redundant DB work.
        if (nowMs - lastCleanupAtMs < CLEANUP_MIN_INTERVAL_MS) return
        lastCleanupAtMs = nowMs

        val cutoff = LocalDateTime.now()
            .minus(retentionDays, ChronoUnit.DAYS)
            .format(dateTimeFormatter)
        try {
            dao.deleteUploadedOlderThan(cutoff)
        } catch (e: Exception) {
            Log.w("MqttPayloadLogRepo", "Failed to clean old uploaded MQTT logs: ${e.message}")
        }
    }

    private fun toIsoDateTime(value: String): String {
        if (value.isBlank()) return ""
        return if (value.contains("T")) value else value.replace(" ", "T")
    }

    companion object {
        private const val CLEANUP_MIN_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
