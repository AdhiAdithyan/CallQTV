package com.softland.callqtv.data.model

import com.google.gson.annotations.SerializedName

class CheckDeviceStatusResponse {

    @SerializedName("status")
    var status: Int = 0

    @SerializedName("Message")
    var message: String? = null

    @SerializedName("LicenceActiveTo")
    var licenceActiveTo: String? = null

    @SerializedName("LicenceActiveFrom")
    var licenceActiveFrom: String? = null

    @SerializedName("CustomerName")
    var customerName: String? = null

    @SerializedName("CustomerContactPerson")
    var customerContactPerson: String? = null

    @SerializedName("CustomerContact")
    var customerContact: String? = null

    @SerializedName("NumberOfLicence")
    var numberOfLicence: Int = 0

    @SerializedName("IsMandatoryUpdate")
    var isMandatoryUpdate: String? = null

    @SerializedName("APKVersion")
    var apkVersion: String? = null

    @SerializedName("APKVersionCode")
    var apkVersionCode: String? = null

    @SerializedName("DownloadURL")
    var downloadUrl: String? = null

    @SerializedName("MandDataSync")
    var mandDataSync: Int = 0

    @SerializedName("TotalCount")
    var totalCount: String? = null

    @SerializedName("ProjectCode")
    var projectCode: String? = null

    @SerializedName("PreviousDisplayRowCount")
    var previousDisplayRowCount: String? = null

    @SerializedName("CurrentTokenFontSize")
    var currentTokenFontSize: String? = null

    @SerializedName("WebLoginCount")
    var webLoginCount: String? = null

    @SerializedName("PreviousTokenFontSize")
    var previousTokenFontSize: String? = null

    @SerializedName("CustomerHeaderView")
    var customerHeaderView: String? = null

    @SerializedName("AndroidTvCount")
    var androidTvCount: String? = null

    @SerializedName("AndroidApkCount")
    var androidApkCount: String? = null

    @SerializedName("KeypadDeviceCount")
    var keypadDeviceCount: String? = null

    @SerializedName("LedDisplayCount")
    var ledDisplayCount: String? = null

    @SerializedName("OutletCount")
    var outletCount: String? = null

    @SerializedName("Locations")
    var locations: String? = null // Raw JSON string
}
