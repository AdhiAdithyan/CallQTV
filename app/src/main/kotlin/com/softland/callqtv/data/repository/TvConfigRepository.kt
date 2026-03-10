package com.softland.callqtv.data.repository

import android.content.Context
import com.google.gson.Gson
import com.softland.callqtv.data.model.MappedBroker
import com.softland.callqtv.data.model.TvConfigPayload
import com.softland.callqtv.data.model.TvConfigRequest
import com.softland.callqtv.data.model.TvConfigResponse
import com.softland.callqtv.data.network.RetrofitClient
import com.softland.callqtv.data.local.AppDatabase
import com.softland.callqtv.data.local.MappedBrokerEntity
import com.softland.callqtv.data.local.TvConfigEntity
import com.softland.callqtv.data.local.CounterEntity
import com.softland.callqtv.data.local.AdFileEntity
import com.softland.callqtv.data.local.ConnectedDeviceEntity
import com.softland.callqtv.data.network.ApiService
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.utils.PreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Result of fetching TV config from the API.
 * - Success: config loaded and cached
 * - Pending: device awaiting approval (Response1)
 * - Error: API error or limit reached (Response3, Response7, etc.)
 */
sealed class TvConfigResult {
    data class Success(val entity: TvConfigEntity) : TvConfigResult()
    data class Pending(val message: String) : TvConfigResult()
    data class Error(val message: String, val licenceStatus: String? = null) : TvConfigResult()
}

class TvConfigRepository(private val context: Context) {

    private val api: ApiService = RetrofitClient.getApiLicenseService()
    private val authPrefs = context.getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
    private val dao = AppDatabase.getInstance(context).tvConfigDao()
    private val mappedBrokerDao = AppDatabase.getInstance(context).mappedBrokerDao()
    private val counterDao = AppDatabase.getInstance(context).counterDao()
    private val adFileDao = AppDatabase.getInstance(context).adFileDao()
    private val connectedDeviceDao = AppDatabase.getInstance(context).connectedDeviceDao()
    private val gson = Gson()

    /**
     * Fetch TV configuration from backend and cache it in Room.
     * Returns TvConfigResult: Success (cached entity), Pending (awaiting approval), or Error (with message and optional licence_status).
     */
    suspend fun fetchAndCacheTvConfig(
        macAddress: String,
        customerId: String
    ): TvConfigResult = withContext(Dispatchers.IO) {
        val fcmToken = PreferenceHelper.getFcmToken(context)
        val request = TvConfigRequest(
            macAddress = macAddress,
            customerId = customerId,
            flag = "TV",
            fcmToken = fcmToken.ifEmpty { null }
        )

        // Dynamically construct URL from saved base URL
        val savedBaseUrl = authPrefs.getString(PreferenceHelper.base_url, "https://py.softlandindia.net/CallQ/") ?: "https://py.softlandindia.net/CallQ/"
        val baseUrl = if (savedBaseUrl.endsWith("/")) savedBaseUrl else "$savedBaseUrl/"
        val url = "${baseUrl}config/api/android/config"

        val body: TvConfigResponse? = try {
            api.fetchTvConfig(url, request)
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            val logMsg = "HTTP Error ${e.code()} at $url Response: $errorBody"
            com.softland.callqtv.utils.FileLogger.logError(context, "TvConfigRepo", logMsg, e)

            // Attempt to parse the error body to check for "pending" status
            try {
                val errResp = gson.fromJson(errorBody, TvConfigResponse::class.java)
                val msg = errResp.message ?: errResp.error ?: ""
                
                if (errResp.status?.lowercase() == "pending" || msg.contains("awaiting approval", ignoreCase = true)) {
                    return@withContext TvConfigResult.Pending(msg.ifBlank { "Device awaiting approval" })
                }
                if (msg.isNotBlank()) return@withContext TvConfigResult.Error(msg)
            } catch (jsonEx: Exception) {
                // Not a valid JSON or different format
            }

            return@withContext TvConfigResult.Error("HTTP error ${e.code()}")
        } catch (e: Exception) {
            com.softland.callqtv.utils.FileLogger.logError(context, "TvConfigRepo", "Request failed at $url", e)
            return@withContext TvConfigResult.Error(e.message ?: "Request failed")
        }

        if (body == null) {
            return@withContext TvConfigResult.Error("Empty response from configuration server")
        }

        val messageText = body.message ?: body.error ?: ""
        
        if (body.status?.lowercase() == "pending" || messageText.contains("awaiting approval", ignoreCase = true)) {
            return@withContext TvConfigResult.Pending(
                messageText.ifBlank { "Device awaiting approval" }
            )
        }

        if (body.status?.lowercase() == "error") {
            return@withContext TvConfigResult.Error(messageText, body.licenceStatus)
        }

        // Response3: max devices reached (message in body)
        if (messageText.contains("maximum number of devices", ignoreCase = true)) {
            return@withContext TvConfigResult.Error(messageText)
        }

        // Success: require tv_config and device_id
        if (body.status != "success" || body.tvConfig == null || body.deviceId == null) {
            return@withContext TvConfigResult.Error(
                body.message ?: body.error ?: "TV configuration is not available"
            )
        }

        val entity = try {
            body.toEntity(macAddress = macAddress, customerId = customerId)
        } catch (e: Exception) {
            com.softland.callqtv.utils.FileLogger.logError(context, "TvConfigRepo", "Failed to map response to entity", e)
            return@withContext TvConfigResult.Error("Configuration parsing error: ${e.message}")
        }
        
        val db = AppDatabase.getInstance(context)

        try {
            db.runInTransaction {
                dao.upsert(entity)

                body.toMappedBrokerEntityOrNull(macAddress, customerId)?.let {
                    mappedBrokerDao.upsert(it)
                }

                val counterEntities = body.toCounterEntities(macAddress, customerId)
                if (counterEntities.isNotEmpty()) {
                    counterDao.deleteByDeviceAndCustomer(entity.deviceId, macAddress, customerId)
                    counterDao.insertAll(counterEntities)
                }

                val adFileEntities = body.toAdFileEntities(macAddress, customerId)
                if (adFileEntities.isNotEmpty()) {
                    adFileDao.deleteByDeviceAndCustomer(entity.deviceId, macAddress, customerId)
                    adFileDao.insertAll(adFileEntities)
                }

                val connectedDeviceEntities = body.toConnectedDeviceEntities(macAddress, customerId)
                if (connectedDeviceEntities.isNotEmpty()) {
                    connectedDeviceDao.deleteByDeviceAndCustomer(entity.deviceId, macAddress, customerId)
                    connectedDeviceDao.insertAll(connectedDeviceEntities)
                }
            }
        } catch (e: Exception) {
            com.softland.callqtv.utils.FileLogger.logError(context, "TvConfigRepo", "Database transaction failed", e)
            return@withContext TvConfigResult.Error("Database error: ${e.message}")
        }

        TvConfigResult.Success(entity)
    }

    suspend fun getCachedConfigByDeviceId(deviceId: Int): TvConfigEntity? =
        withContext(Dispatchers.IO) {
            dao.getByDeviceId(deviceId)
        }

    suspend fun getCachedConfig(
        macAddress: String,
        customerId: String
    ): TvConfigEntity? = withContext(Dispatchers.IO) {
        dao.getByMacAndCustomer(macAddress, customerId)
    }

    /**
     * Get the list of counters for a given device (by mac + customer).
     */
    suspend fun getCounters(
        macAddress: String,
        customerId: String
    ): List<CounterEntity> = withContext(Dispatchers.IO) {
        counterDao.getByMacAndCustomer(macAddress, customerId)
    }

    /**
     * Get the list of ad files for a given device (by mac + customer).
     */
    suspend fun getAdFiles(
        macAddress: String,
        customerId: String
    ): List<AdFileEntity> = withContext(Dispatchers.IO) {
        val config = dao.getByMacAndCustomer(macAddress, customerId) ?: return@withContext emptyList()
        adFileDao.getByMacAndCustomer(config.macAddress, config.customerId)
    }

    /**
     * Get the list of connected devices from local DB.
     */
    suspend fun getConnectedDevices(
        macAddress: String,
        customerId: String
    ): List<ConnectedDeviceEntity> = withContext(Dispatchers.IO) {
        connectedDeviceDao.getByMacAndCustomer(macAddress, customerId)
    }

    /**
     * Get the mapped broker configuration from a cached config entity.
     * Returns null if no broker is mapped or if deserialization fails.
     */
    fun getMappedBroker(entity: TvConfigEntity?): MappedBroker? {
        return try {
            val json = entity?.mappedBrokerJson ?: return null
            gson.fromJson(json, MappedBroker::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get all mapped broker rows from the dedicated table.
     */
    suspend fun getMappedBrokerEntities(
        macAddress: String,
        customerId: String
    ): List<MappedBrokerEntity> = withContext(Dispatchers.IO) {
        mappedBrokerDao.getAllByMacAndCustomer(macAddress, customerId)
    }

    /**
     * Get a single mapped broker row (for backward compatibility).
     */
    suspend fun getMappedBrokerEntity(
        macAddress: String,
        customerId: String
    ): MappedBrokerEntity? = withContext(Dispatchers.IO) {
        mappedBrokerDao.getByMacAndCustomer(macAddress, customerId)
    }

    private fun TvConfigResponse.toEntity(
        macAddress: String,
        customerId: String
    ): TvConfigEntity {
        val cfg = requireNotNull(this.tvConfig) { "toEntity must be called only when tvConfig is non-null" }
        val deviceId = requireNotNull(this.deviceId) { "toEntity must be called only when deviceId is non-null" }
        val adFilesJson = gson.toJson(cfg.adFiles ?: emptyList<String>())
        val mappedBrokerJson = if (this.mappedBroker != null) {
            gson.toJson(this.mappedBroker)
        } else {
            null
        }
        val countersJson = gson.toJson(this.counters ?: emptyList<Any>())
        val shiftDetailsJson = this.shiftDetails
            ?.takeIf { !it.isJsonNull }
            ?.let { gson.toJson(it) }

        val currentProfileJson = this.currentProfile?.let { gson.toJson(it) }
        val connectedDevicesJson = this.connectedDevices?.let { gson.toJson(it) }
        val sc = this.scrollConfig
        val scrollTextLinesJson = sc?.scrollTextLines?.let { gson.toJson(it) }

        return TvConfigEntity(
            deviceId = deviceId,
            serialNumber = this.serialNumber.orEmpty(),
            macAddress = macAddress,
            customerId = customerId,
            companyName = this.companyName.orEmpty(),
            status = this.status.orEmpty(),
            message = this.message,
            audioLanguage = cfg.audioLanguage,
            showAds = cfg.showAds, // String type handled
            adInterval = cfg.adInterval,
            orientation = cfg.orientation,
            layoutType = cfg.layoutType,
            saveAudioExternal = cfg.saveAudioExternal,
            enableCounterAnnouncement = cfg.enableCounterAnnouncement,
            enableTokenAnnouncement = cfg.enableTokenAnnouncement,
            enableCounterPrefix = cfg.enableCounterPrefix,
            tokenAudioUrl = cfg.tokenAudioUrl,
            tokenMusicUrl = cfg.tokenMusicUrl,
            displayRows = cfg.displayRows,
            displayColumns = cfg.displayColumns,
            counterTextColor = cfg.counterTextColor,
            tokenTextColor = cfg.tokenTextColor,
            fontSize = cfg.fontSize,
            tokenFontSize = cfg.tokenFontSize,
            counterFontSize = cfg.counterFontSize,
            currentTokenColor = cfg.currentTokenColor,
            previousTokenColor = cfg.previousTokenColor,
            blinkCurrentToken = cfg.blinkCurrentToken,
            tokenFormat = cfg.tokenFormat,
            tokensPerCounter = cfg.tokensPerCounter,
            noOfCounters = cfg.noOfCounters,
            adPlacement = cfg.adPlacement,
            adFilesJson = adFilesJson,
            countersJson = countersJson,
            shiftDetailsJson = shiftDetailsJson,
            mappedBrokerJson = mappedBrokerJson,
            blinkSeconds = cfg.blinkSeconds,
            currentProfileJson = currentProfileJson,
            connectedDevicesJson = connectedDevicesJson,
            scrollEnabled = sc?.scrollEnabled,
            noOfTextFields = sc?.noOfTextFields,
            scrollTextLinesJson = scrollTextLinesJson
        )
    }

    private fun TvConfigResponse.toCounterEntities(
        macAddress: String,
        customerId: String
    ): List<CounterEntity> {
        val deviceIdLocal = this.deviceId ?: return emptyList()
        val list = this.counters ?: emptyList()
        if (list.isEmpty()) return emptyList()

        return list.mapNotNull { c ->
            if (c == null) return@mapNotNull null
            CounterEntity(
                deviceId = deviceIdLocal,
                macAddress = macAddress,
                customerId = customerId,
                counterId = c.counterId,
                defaultName = c.defaultName,
                defaultCode = c.defaultCode,
                sourceDevice = this.deviceSerial,
                buttonIndex = c.buttonIndex,
                name = c.name,
                code = c.code,
                rowSpan = c.rowSpan,
                colSpan = c.colSpan,
                isEnabled = c.isEnabled,
                audioUrl = c.audioUrl,
                audioName = c.audioName,
                counterConfigId = c.counterConfigId,
                maxTokenNumber = c.maxTokenNumber,
                dispenserSerialNumber = c.dispenserSerialNumber,
                dispenserTokenType = c.dispenserTokenType,
                dispenserDisplayName = c.dispenserDisplayName,
                rawJson = gson.toJson(c)
            )
        }
    }

    private fun TvConfigResponse.toMappedBrokerEntityOrNull(
        macAddress: String,
        customerId: String
    ): MappedBrokerEntity? {
        val deviceIdLocal = this.deviceId ?: return null
        val broker = this.mappedBroker ?: return null
        val brokerId = broker.id ?: return null

        val rawJson = gson.toJson(broker)
        val cfg = broker.config

        return MappedBrokerEntity(
            deviceId = deviceIdLocal,
            macAddress = macAddress,
            customerId = customerId,
            brokerId = brokerId,
            serialNumber = broker.serialNumber,
            deviceType = broker.deviceType,
            ssid = cfg?.ssid,
            password = cfg?.password,
            host = cfg?.host,
            port = cfg?.port,
            topic = cfg?.topic,
            mappedBrokerJson = rawJson
        )
    }

    private fun TvConfigResponse.toAdFileEntities(
        macAddress: String,
        customerId: String
    ): List<AdFileEntity> {
        val deviceIdLocal = this.deviceId ?: return emptyList()
        val cfg: TvConfigPayload = this.tvConfig ?: return emptyList()
        val list = cfg.adFiles ?: emptyList()
        if (list.isEmpty()) return emptyList()

        return list.mapIndexedNotNull { index, path ->
            if (path == null) return@mapIndexedNotNull null
            AdFileEntity(
                deviceId = deviceIdLocal,
                macAddress = macAddress,
                customerId = customerId,
                position = index,
                filePath = path,
                rawJson = gson.toJson(path)
            )
        }
    }

    private fun TvConfigResponse.toConnectedDeviceEntities(
        macAddress: String,
        customerId: String
    ): List<ConnectedDeviceEntity> {
        val deviceIdLocal = this.deviceId ?: return emptyList()
        val list = this.connectedDevices ?: emptyList()
        if (list.isEmpty()) return emptyList()

        return list.mapNotNull { d ->
            if (d == null) return@mapNotNull null
            ConnectedDeviceEntity(
                deviceId = deviceIdLocal,
                macAddress = macAddress,
                customerId = customerId,
                remoteDeviceId = d.id,
                serialNumber = d.serialNumber,
                deviceType = d.deviceType,
                name = d.name,
                status = d.status,
                isActive = d.isActive,
                configJson = d.config?.let { gson.toJson(it) }
            )
        }
    }
}
