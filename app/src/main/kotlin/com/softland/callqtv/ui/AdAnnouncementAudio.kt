package com.softland.callqtv.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.softland.callqtv.ui.ads.MediaEngine
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.TokenAnnouncer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
/**
 * Lowers ExoPlayer-based ad audio and the shared YouTube WebView video element while token TTS runs,
 * then restores prior levels. YouTube reference is registered from [AdArea].
 */
internal object TokenAnnouncementAdAudio {
    @Volatile
    private var youtubeRef: WeakReference<WebView>? = null

    fun setYoutubeWebView(webView: WebView?) {
        youtubeRef = webView?.let { WeakReference(it) }
    }

    fun applyYoutubeDuck(duck: Boolean) {
        val wv = youtubeRef?.get() ?: return
        val js = if (duck) {
            """
            (function(){try{
              var v=document.querySelector('video');
              if(!v) return;
              if(!window._callqtv_tts_duck_saved){
                window._callqtv_tts_duck_saved={vol:v.volume,muted:v.muted};
              }
              v.muted=false;
              v.volume=0.1;
            }catch(e){}})();
            """.trimIndent()
        } else {
            """
            (function(){try{
              var v=document.querySelector('video');
              var s=window._callqtv_tts_duck_saved;
              window._callqtv_tts_duck_saved=null;
              if(!v||!s) return;
              v.volume=s.vol;
              v.muted=s.muted;
            }catch(e){}})();
            """.trimIndent()
        }
        try {
            wv.evaluateJavascript(js, null)
        } catch (_: Exception) {
        }
    }
}

private class AdAnnouncementRestoreOnce(private val appContext: Context) {
    private val finished = AtomicBoolean(false)

    fun run() {
        if (!finished.compareAndSet(false, true)) return
        Handler(Looper.getMainLooper()).post {
            MediaEngine.restoreAfterAnnouncement(appContext)
            TokenAnnouncementAdAudio.applyYoutubeDuck(false)
        }
    }
}

internal suspend fun runWithAdvertisementAudioDuckedForSpeech(
    context: Context,
    block: suspend (skipSynthesisPrime: Boolean, restore: () -> Unit) -> Unit,
) {
    if (!ThemeColorManager.isAdSoundEnabled(context)) {
        block(false) { }
        return
    }
    val restore = AdAnnouncementRestoreOnce(context.applicationContext)
    withContext(Dispatchers.Main.immediate) {
        MediaEngine.duckForAnnouncement()
        TokenAnnouncementAdAudio.applyYoutubeDuck(true)
    }
    if (TokenAnnouncer.needsSynthesisWarmUp()) {
        TokenAnnouncer.awaitSynthesisPrimeIfNeeded()
    }
    try {
        block(true) { restore.run() }
    } finally {
        restore.run()
    }
}
