package com.softland.callqtv.data.model

import com.google.gson.annotations.SerializedName

class LoginResponse {

    @SerializedName("message")
    var message: String? = null

    @SerializedName("access")
    var accessToken: String? = null

    @SerializedName("refresh")
    var refreshToken: String? = null

    @SerializedName("user")
    var user: User? = null

    constructor()

    constructor(message: String, accessToken: String, refreshToken: String, user: User) {
        this.message = message
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.user = user
    }
}
