package com.softland.callqtv.utils

data class DownloadStatus(
    val statusType: StatusType,
    val progress: Int,
    val filePathOrError: String? = null,
    val versionOrResult: String? = null
) {
    enum class StatusType {
        IDLE, DOWNLOADING, SUCCESS, ERROR
    }
}
