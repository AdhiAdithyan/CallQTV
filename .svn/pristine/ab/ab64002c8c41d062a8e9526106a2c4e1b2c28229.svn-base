package com.softland.callqtv.data.model

class Message {

    var id: String? = null
    var text: String? = null
    var senderId: String? = null
    var senderName: String? = null
    var timestamp: Long = 0L
    var audioUrl: String? = null
    var audioDuration: Int = 0

    constructor()

    constructor(
        id: String?,
        text: String?,
        senderId: String?,
        senderName: String?,
        timestamp: Long
    ) {
        this.id = id
        this.text = text
        this.senderId = senderId
        this.senderName = senderName
        this.timestamp = timestamp
    }

    constructor(
        id: String?,
        text: String?,
        senderId: String?,
        senderName: String?,
        timestamp: Long,
        audioUrl: String?,
        audioDuration: Int
    ) {
        this.id = id
        this.text = text
        this.senderId = senderId
        this.senderName = senderName
        this.timestamp = timestamp
        this.audioUrl = audioUrl
        this.audioDuration = audioDuration
    }

    constructor(messageText: String?, currentUserUid: String?, senderName: String?) {
        this.text = messageText
        this.senderId = currentUserUid
        this.senderName = senderName
    }
}
