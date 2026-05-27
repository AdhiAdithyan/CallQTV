package com.softland.callqtv.viewmodel.mqtt

import android.app.Application
import android.content.Context
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.Variables
import java.util.Locale

/** MAC + zero-padded customer id for Room queries scoped to this TV. */
internal object MqttDeviceContext {
    /** Reads and formats customer id as zero-padded 4-digit string. */
    fun customerId(app: Application): String {
        val authPrefs = app.getSharedPreferences(
            AppSharedPreferences.AUTHENTICATION,
            Context.MODE_PRIVATE,
        )
        return String.format(
            Locale.ROOT,
            "%04d",
            authPrefs.getInt(PreferenceHelper.customer_id, 0),
        )
    }

    /** Returns current TV MAC identifier used as device scope key. */
    fun macAddress(app: Application): String = Variables.getMacId(app)
}
