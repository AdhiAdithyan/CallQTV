package com.softland.callqtv.data.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

class ServiceUrlResponse {

    @SerializedName("ProjectDetails")
    @Expose
    var projectDetails: ProjectDetails? = null

    class ProjectDetails {

        @SerializedName("PrjectCode")
        @Expose
        var prjectCode: String? = null

        @SerializedName("ProjectID")
        @Expose
        var projectID: Int = 0

        @SerializedName("ServiceUrl")
        @Expose
        var serviceUrl: String? = null
    }
}
