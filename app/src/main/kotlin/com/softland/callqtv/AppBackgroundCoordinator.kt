@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.ui.SplashScreenActivity
import com.softland.callqtv.utils.TokenAnnouncer
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import java.lang.ref.WeakReference

/**
 * When the app leaves the foreground, stops MQTT, media, TTS, and other background work.
 * When the user returns, relaunches [SplashScreenActivity] instead of resuming the previous screen.
 */
object AppBackgroundCoordinator {

    private const val TAG = "AppBackground"

    @Volatile
    private var resumeToSplashOnNextForeground = false

    private var mqttViewModelRef: WeakReference<MqttViewModel>? = null
    private var tokenDisplayViewModelRef: WeakReference<TokenDisplayViewModel>? = null

    fun install(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    onAppMovedToBackground()
                }

                override fun onStart(owner: LifecycleOwner) {
                    onAppMovedToForeground(application)
                }
            },
        )
    }

    fun registerTokenDisplaySession(
        mqttViewModel: MqttViewModel,
        tokenDisplayViewModel: TokenDisplayViewModel,
    ) {
        mqttViewModelRef = WeakReference(mqttViewModel)
        tokenDisplayViewModelRef = WeakReference(tokenDisplayViewModel)
    }

    fun unregisterTokenDisplaySession() {
        mqttViewModelRef = null
        tokenDisplayViewModelRef = null
    }

    private fun onAppMovedToBackground() {
        resumeToSplashOnNextForeground = true
        Log.i(TAG, "App moved to background — stopping background work")
        stopAllBackgroundWork()
    }

    private fun onAppMovedToForeground(application: Application) {
        if (!resumeToSplashOnNextForeground) return
        resumeToSplashOnNextForeground = false

        Log.i(TAG, "App returned to foreground — restarting from splash")
        val intent = Intent(application, SplashScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        application.startActivity(intent)
    }

    private fun stopAllBackgroundWork() {
        mqttViewModelRef?.get()?.stopAllBackgroundWork()
        tokenDisplayViewModelRef?.get()?.stopBackgroundWork()
        MediaEngine.shutdown()
        TokenAnnouncer.shutdown()
    }
}
