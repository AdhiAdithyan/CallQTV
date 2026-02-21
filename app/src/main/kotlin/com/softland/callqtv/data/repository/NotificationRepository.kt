package com.softland.callqtv.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.softland.callqtv.data.model.NotificationModel

class NotificationRepository private constructor() {

    private val notificationLiveData = MutableLiveData<NotificationModel>()

    fun pushNotification(model: NotificationModel) {
        notificationLiveData.postValue(model)
    }

    fun getNotifications(): LiveData<NotificationModel> = notificationLiveData

    companion object {
        @Volatile
        private var instance: NotificationRepository? = null

        @JvmStatic
        fun getInstance(): NotificationRepository =
            instance ?: synchronized(this) {
                instance ?: NotificationRepository().also { instance = it }
            }
    }
}

