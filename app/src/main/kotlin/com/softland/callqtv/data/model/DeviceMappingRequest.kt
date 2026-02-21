package com.softland.callqtv.data.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class DeviceMappingRequest {

    @SerializedName("token")
    @Expose
    var token: String? = null

    @SerializedName("customer_id")
    @Expose
    var customerId: String? = null

    @SerializedName("mac_address")
    @Expose
    var macAddress: String? = null

    @SerializedName("apk_version")
    var apkVersion: String? = null

    @SerializedName("manager_id")
    var managerId: Int = 0
}
