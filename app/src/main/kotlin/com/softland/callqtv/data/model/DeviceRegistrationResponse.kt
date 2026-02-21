package com.softland.callqtv.data.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class DeviceRegistrationResponse {

    @SerializedName("status")
    @Expose
    var status: String? = null

    @SerializedName("UniqueIDentifier")
    @Expose
    var uniqueIDentifier: String? = null

    @SerializedName("NumberOfLicences")
    @Expose
    var numberOfLicences: Int? = null

    @SerializedName("Message")
    @Expose
    var message: String? = null

    @SerializedName("FTPHost")
    @Expose
    var fTPHost: Any? = null

    @SerializedName("FTPPort")
    @Expose
    var fTPPort: Any? = null

    @SerializedName("ProjectName")
    @Expose
    var projectName: String? = null

    @SerializedName("ProductTypeId")
    @Expose
    var productTypeId: Int? = null

    @SerializedName("DeviceRegistrationId")
    @Expose
    var deviceRegistrationId: Int? = null
}
