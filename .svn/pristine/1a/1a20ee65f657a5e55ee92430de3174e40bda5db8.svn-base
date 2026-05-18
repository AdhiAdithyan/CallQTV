package com.softland.callqtv.data.model

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * Request body for TV configuration API.
 */
data class TvConfigRequest(
    @SerializedName("mac_address") val macAddress: String,
    @SerializedName("customer_id") val customerId: String,
    @SerializedName("Flag") val flag: String = "TV",
    @SerializedName("fcm_token") val fcmToken: String? = null
)

/**
 * Response from TV configuration API.
 */
data class TvConfigResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("device_id") val deviceId: Int? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("company_name") val companyName: String? = null,
    @SerializedName("tv_config") val tvConfig: TvConfigPayload? = null,
    @SerializedName("current_profile") val currentProfile: JsonElement? = null,
    @SerializedName("mapped_broker") val mappedBroker: MappedBroker? = null,
    @SerializedName("connected_devices") val connectedDevices: List<ConnectedDevice>? = null,
    @SerializedName("counters") val counters: List<CounterConfig>? = null,
    @SerializedName("shift_details") val shiftDetails: JsonElement? = null,
    @SerializedName("scroll_config") val scrollConfig: ScrollConfig? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("device_serial") val deviceSerial: String? = null,
    @SerializedName("licence_status") val licenceStatus: String? = null
)

data class MappedBroker(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("config") val config: BrokerConfig? = null
)

data class BrokerConfig(
    @SerializedName("ssid") val ssid: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("host") val host: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("topic") val topic: String? = null
)

data class ConnectedDevice(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("device_type") val deviceType: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("is_active") val isActive: Boolean? = null,
    @SerializedName("config") val config: JsonElement? = null
)

data class CounterConfig(
    @SerializedName("counter_id") val counterId: String? = null,
    @SerializedName("default_code") val defaultCode: String? = null,
    @SerializedName("button_index") val buttonIndex: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("code") val code: String? = null,
    @SerializedName("row_span") val rowSpan: Int? = null,
    @SerializedName("col_span") val colSpan: Int? = null,
    @SerializedName("is_enabled") val isEnabled: Boolean? = null,
    @SerializedName("counter_config_id") val counterConfigId: Int? = null,
    @SerializedName("max_token_number") val maxTokenNumber: Int? = null,
    @SerializedName("dispenser_serial_number") val dispenserSerialNumber: String? = null,
    @SerializedName("dispenser_token_type") val dispenserTokenType: String? = null,
    @SerializedName("dispenser_display_name") val dispenserDisplayName: String? = null,
    // Legacy fields kept for compatibility if needed
    @SerializedName("default_name") val defaultName: String? = null,
    @SerializedName("audio_url") val audioUrl: String? = null,
    @SerializedName("audio_name") val audioName: String? = null
)

data class TvConfigPayload(
    @SerializedName("audio_language") val audioLanguage: String? = null,
    @SerializedName("orientation") val orientation: String? = null,
    @SerializedName("layout_type") val layoutType: String? = null,
    @SerializedName("save_audio_external") val saveAudioExternal: Boolean? = null,
    @SerializedName("enable_counter_announcement") val enableCounterAnnouncement: Boolean? = null,
    @SerializedName("enable_token_announcement") val enableTokenAnnouncement: Boolean? = null,
    @SerializedName("enable_counter_prifix") val enableCounterPrefix: Boolean? = null,
    @SerializedName("token_audio_url") val tokenAudioUrl: String? = null,
    @SerializedName("token_music_url") val tokenMusicUrl: String? = null,
    @SerializedName("display_rows") val displayRows: Int? = null,
    @SerializedName("display_columns") val displayColumns: Int? = null,
    @SerializedName("counter_text_color") val counterTextColor: String? = null,
    @SerializedName("token_text_color") val tokenTextColor: String? = null,
    @SerializedName("token_font_size") val tokenFontSize: Int? = null,
    @SerializedName("counter_font_size") val counterFontSize: Int? = null,
    @SerializedName("tokens_per_counter") val tokensPerCounter: Int? = null,
    @SerializedName("no_of_counters") val noOfCounters: Int? = null,
    @SerializedName("current_token_color") val currentTokenColor: String? = null,
    @SerializedName("previous_token_color") val previousTokenColor: String? = null,
    @SerializedName("blink_current_token") val blinkCurrentToken: Boolean? = null,
    @SerializedName("blink_seconds") val blinkSeconds: Int? = null,
    @SerializedName("token_format") val tokenFormat: String? = null,
    
    // Legacy / Ad fields (Optional)
    // Using String for show_ads because JSON may return "on"/"off"
    @SerializedName("show_ads") val showAds: String? = null,
    @SerializedName("ad_interval") val adInterval: Int? = null,
    @SerializedName("ad_files") val adFiles: List<String>? = null,
    @SerializedName("ad_placement") val adPlacement: String? = null,
    @SerializedName("font_size") val fontSize: Int? = null
)

data class ScrollConfig(
    @SerializedName("scroll_enabled") val scrollEnabled: String? = null,
    @SerializedName("no_of_text_fields") val noOfTextFields: Int? = null,
    @SerializedName("scroll_text_lines") val scrollTextLines: List<String>? = null
)
