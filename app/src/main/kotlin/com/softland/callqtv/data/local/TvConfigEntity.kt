package com.softland.callqtv.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached TV configuration for a device.
 *
 * One row per device (deviceId from backend).
 */
@Entity(tableName = "tv_config")
data class TvConfigEntity(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: Int,

    @ColumnInfo(name = "serial_number")
    val serialNumber: String,

    @ColumnInfo(name = "mac_address")
    val macAddress: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "company_name")
    val companyName: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "message")
    val message: String?,

    // Flattened tv_config fields
    @ColumnInfo(name = "audio_language")
    val audioLanguage: String?,
    @ColumnInfo(name = "show_ads")
    val showAds: Boolean?,
    @ColumnInfo(name = "ad_interval")
    val adInterval: Int?,
    @ColumnInfo(name = "orientation")
    val orientation: String?,
    @ColumnInfo(name = "layout_type")
    val layoutType: String?,
    @ColumnInfo(name = "save_audio_external")
    val saveAudioExternal: Boolean?,
    @ColumnInfo(name = "enable_counter_announcement")
    val enableCounterAnnouncement: Boolean?,
    @ColumnInfo(name = "enable_token_announcement")
    val enableTokenAnnouncement: Boolean?,
    @ColumnInfo(name = "token_audio_url")
    val tokenAudioUrl: String?,
    @ColumnInfo(name = "token_music_url")
    val tokenMusicUrl: String?,
    @ColumnInfo(name = "display_rows")
    val displayRows: Int?,
    @ColumnInfo(name = "display_columns")
    val displayColumns: Int?,
    @ColumnInfo(name = "counter_text_color")
    val counterTextColor: String?,
    @ColumnInfo(name = "token_text_color")
    val tokenTextColor: String?,
    @ColumnInfo(name = "font_size")
    val fontSize: Int?,
    @ColumnInfo(name = "token_font_size")
    val tokenFontSize: Int?,
    @ColumnInfo(name = "counter_font_size")
    val counterFontSize: Int?,
    @ColumnInfo(name = "current_token_color")
    val currentTokenColor: String?,
    @ColumnInfo(name = "previous_token_color")
    val previousTokenColor: String?,
    @ColumnInfo(name = "blink_current_token")
    val blinkCurrentToken: Boolean?,
    @ColumnInfo(name = "token_format")
    val tokenFormat: String?,
    @ColumnInfo(name = "tokens_per_counter")
    val tokensPerCounter: Int?,
    @ColumnInfo(name = "no_of_counters")
    val noOfCounters: Int?,
    @ColumnInfo(name = "ad_placement")
    val adPlacement: String?,

    // Raw JSON of ad_files list, for future use (optional)
    @ColumnInfo(name = "ad_files_json")
    val adFilesJson: String?,

    // Raw JSON arrays/objects for counters and shift details (Response5)
    @ColumnInfo(name = "counters_json")
    val countersJson: String?,
    @ColumnInfo(name = "shift_details_json")
    val shiftDetailsJson: String?,

    // Raw JSON of mapped_broker object (can be null or contain broker config)
    @ColumnInfo(name = "mapped_broker_json")
    val mappedBrokerJson: String?,

    @ColumnInfo(name = "blink_seconds")
    val blinkSeconds: Int?,

    @ColumnInfo(name = "current_profile_json")
    val currentProfileJson: String?,

    @ColumnInfo(name = "connected_devices_json")
    val connectedDevicesJson: String?,

    @ColumnInfo(name = "scroll_enabled")
    val scrollEnabled: String?,

    @ColumnInfo(name = "no_of_text_fields")
    val noOfTextFields: Int?,

    @ColumnInfo(name = "scroll_text_lines_json")
    val scrollTextLinesJson: String?
)
