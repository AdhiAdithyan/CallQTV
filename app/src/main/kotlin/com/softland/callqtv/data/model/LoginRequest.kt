package com.softland.callqtv.data.model

import com.google.gson.annotations.SerializedName

class LoginRequest {

    @SerializedName("username")
    var username: String? = null

    @SerializedName("password")
    var password: String? = null

    @SerializedName("role")
    var role: String? = null

    @SerializedName("token")
    var token: String? = null

    @SerializedName("mac_address")
    var macAddress: String? = null

    @SerializedName("customer_id")
    var customerId: Int = 0

    constructor()

    constructor(username: String, password: String, role: String) {
        this.username = username
        this.password = password
        this.role = role
    }
}
