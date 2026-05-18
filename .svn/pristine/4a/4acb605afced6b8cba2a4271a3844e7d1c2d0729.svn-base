package com.softland.callqtv.data.repository

import android.content.Context
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables

class DateRepository(context: Context) {

    private val appContext = context.applicationContext

    fun checkAndResetIfDateChanged(): Boolean {
        val currentDate = Variables.getCurentDate()
        val savedDate = PreferenceHelper.getCurrentDate(appContext)

        return if (currentDate != savedDate) {
            PreferenceHelper.clearStringList(appContext)
            PreferenceHelper.saveCurrentDate(appContext, currentDate)
            true
        } else {
            false
        }
    }
}

