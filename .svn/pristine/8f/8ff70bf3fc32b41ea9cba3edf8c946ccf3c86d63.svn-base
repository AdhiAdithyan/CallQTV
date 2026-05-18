package com.softland.callqtv.data.model

import com.google.gson.annotations.SerializedName

class DailyOrderResponse() {

    @SerializedName("message")
    var message: String? = null

    @SerializedName("count")
    var count: Int = 0

    @SerializedName("unread")
    var unread: Int = 0

    @SerializedName("delivered")
    var delivered: Int = 0

    @SerializedName("ready")
    var ready: Int = 0

    @SerializedName("preparing")
    var preparing: Int = 0

    @SerializedName("created")
    var created: Int = 0

    @SerializedName("cancelled")
    var cancelled: Int = 0

    @SerializedName("detail")
    var orderDetails: List<CurrentDayOrder>? = null

    constructor(
        message: String,
        count: Int,
        unread: Int,
        delivered: Int,
        ready: Int,
        preparing: Int,
        created: Int,
        cancelled: Int,
        orderDetails: List<CurrentDayOrder>
    ) : this() {
        this.message = message
        this.count = count
        this.unread = unread
        this.delivered = delivered
        this.ready = ready
        this.preparing = preparing
        this.created = created
        this.cancelled = cancelled
        this.orderDetails = orderDetails
    }

    constructor(
        message: String,
        count: Int,
        orderDetails: List<CurrentDayOrder>
    ) : this() {
        this.message = message
        this.count = count
        this.orderDetails = orderDetails
    }
}
