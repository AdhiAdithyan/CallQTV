package com.softland.callqtv.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.softland.callqtv.utils.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class InstallEvent(val filePath: String)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _downloadStatus = MutableLiveData<DownloadStatus>()
    val downloadStatus: LiveData<DownloadStatus> = _downloadStatus

    private val _installIntentLiveData = MutableLiveData<InstallEvent?>()
    val installIntentLiveData: LiveData<InstallEvent?> = _installIntentLiveData

    private var wakeLock: PowerManager.WakeLock? = null

    fun downloadApk(context: Context, downloadUrl: String, versionName: String) {
        viewModelScope.launch {
            _downloadStatus.value = DownloadStatus(DownloadStatus.StatusType.DOWNLOADING, 0)
            
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallQTV:DownloadWakeLock")
            wakeLock?.acquire(10 * 60 * 1000L)

            val result = withContext(Dispatchers.IO) {
                try {
                    val url = URL(downloadUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connect()

                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        return@withContext "Server returned HTTP ${connection.responseCode}"
                    }

                    val fileLength = connection.contentLength
                    val input = connection.inputStream
                    val outputDir = context.getExternalFilesDir(null)
                    val outputFile = File(outputDir, "CallQTV_Version$versionName.apk")
                    val output = FileOutputStream(outputFile)

                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count.toLong()
                        if (fileLength > 0) {
                            _downloadStatus.postValue(DownloadStatus(DownloadStatus.StatusType.DOWNLOADING, (total * 100 / fileLength).toInt()))
                        }
                        output.write(data, 0, count)
                    }

                    output.flush()
                    output.close()
                    input.close()
                    outputFile.absolutePath
                } catch (e: Exception) {
                    e.message ?: "Unknown download error"
                } finally {
                    wakeLock?.let { if (it.isHeld) it.release() }
                }
            }

            if (result.endsWith(".apk")) {
                _downloadStatus.value = DownloadStatus(DownloadStatus.StatusType.SUCCESS, 100, result, versionName)
            } else {
                _downloadStatus.value = DownloadStatus(DownloadStatus.StatusType.ERROR, 0, result)
            }
        }
    }

    fun triggerApkInstall(versionName: String) {
        _installIntentLiveData.value = InstallEvent(versionName)
    }

    override fun onCleared() {
        super.onCleared()
        wakeLock?.let { if (it.isHeld) it.release() }
    }
}
