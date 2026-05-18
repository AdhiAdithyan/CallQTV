package com.softland.callqtv.data.model

import com.google.gson.annotations.SerializedName

data class TokenReportRequest(
    @SerializedName("received_message") val receivedMessage: String,
    @SerializedName("received_dateTime") val receivedDateTime: String,
    @SerializedName("displayed_dateTime") val displayedDateTime: String,
    @SerializedName("customerId") val customerId: String,
    @SerializedName("mac_address") val macAddress: String,
)

data class GenericApiResponse(
    @SerializedName("status") val status: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("id") val id: Long? = null
)
