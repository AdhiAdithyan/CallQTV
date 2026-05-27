package com.softland.callqtv.viewmodel.mqtt

internal data class MqttConnectionDetails(
    val serverUri: String,
    val clientId: String,
    val username: String?,
    val password: String?,
    val topic: String,
    val qos: Int,
)
