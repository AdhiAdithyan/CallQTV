@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.softland.callqtv.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.softland.callqtv.AppBackgroundCoordinator
import com.softland.callqtv.utils.AppUpdateChecker
import com.softland.callqtv.utils.AppUpgradeCoordinator
import com.softland.callqtv.utils.TokenAnnouncer
import com.softland.callqtv.utils.StoragePermissionHelper
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.AnimatedLoadingOverlay
import com.softland.callqtv.viewmodel.MqttViewModel
import com.softland.callqtv.viewmodel.TokenDisplayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.softland.callqtv.ui.ads.MediaEngine

class TokenDisplayActivity : ComponentActivity() {

    private lateinit var viewModel: TokenDisplayViewModel
    private lateinit var mqttViewModel: MqttViewModel
    private var launchInstanceId: Long = 0L

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        StoragePermissionHelper.onRuntimePermissionResult(this)
    }

    @Volatile
    private var hasStartedInitialLoad = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Belt-and-suspenders guard: if a second instance is created in the same task,
        // close it immediately and keep the existing one.
        if (!isTaskRoot && savedInstanceState == null) {
            Log.w(
                "TokenDisplayLaunch",
                "Ignoring duplicate TokenDisplayActivity instance taskId=$taskId action=${intent?.action.orEmpty()}"
            )
            finish()
            return
        }
        launchInstanceId = nextLaunchInstanceId()
        logLaunchOrigin("onCreate", savedInstanceState != null)
        
        // Prevent screen from sleeping while this activity is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        

        
        viewModel = ViewModelProvider(this)[TokenDisplayViewModel::class.java]
        mqttViewModel = ViewModelProvider(this)[MqttViewModel::class.java]
        viewModel.armInitialStartupLoad()
        mqttViewModel.setPayloadUiReady(false)
        AppBackgroundCoordinator.registerTokenDisplaySession(mqttViewModel, viewModel)

        StoragePermissionHelper.runWhenStorageAccessReady(
            this,
            storagePermissionLauncher,
        ) {
            startInitialLoadIfNeeded()
        }

        setContent {
            // Theme State - load async to avoid blocking main thread during composition
            val context = LocalContext.current
            var storageAccessReady by remember {
                mutableStateOf(StoragePermissionHelper.hasFullStorageAccess(this@TokenDisplayActivity))
            }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        storageAccessReady =
                            StoragePermissionHelper.hasFullStorageAccess(this@TokenDisplayActivity)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            var currentThemeHex by remember { mutableStateOf("#2196F3") }
            var counterBgHex by remember { mutableStateOf("#FFFFFF") }
            var tokenBgHex by remember { mutableStateOf("#FFFFFF") }
            LaunchedEffect(Unit) {
                try {
                    withContext(Dispatchers.Default) {
                        currentThemeHex = ThemeColorManager.getSelectedThemeColorHex(context)
                        counterBgHex = ThemeColorManager.getCounterBackgroundColor(context)
                        tokenBgHex = ThemeColorManager.getTokenBackgroundColor(context)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Keep defaults on error
                }
            }
            
            val themeColor = remember(currentThemeHex) {
                ThemeColorManager.colorForMaterialPrimary(currentThemeHex)
            }
            val colorScheme = remember(themeColor) { ThemeColorManager.createDarkColorScheme(themeColor) }
            
            MaterialTheme(colorScheme = colorScheme) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        TokenDisplayScreen(
                            viewModel,
                            mqttViewModel,
                            counterBgHex = counterBgHex,
                            tokenBgHex = tokenBgHex,
                            appThemeHex = currentThemeHex,
                            onThemeChange = { newHex ->
                                ThemeColorManager.setThemeColor(this@TokenDisplayActivity, newHex)
                                currentThemeHex = newHex
                            },
                            onCounterBgChange = { newHex ->
                                ThemeColorManager.setCounterBackgroundColor(this@TokenDisplayActivity, newHex)
                                counterBgHex = newHex
                            },
                            onTokenBgChange = { newHex ->
                                ThemeColorManager.setTokenBackgroundColor(this@TokenDisplayActivity, newHex)
                                tokenBgHex = newHex
                            }
                        )
                    }
                    if (!storageAccessReady) {
                        com.softland.callqtv.utils.AnimatedLoadingOverlay(
                            message = "Storage permission is required. Please grant access to continue.",
                            isVisible = true,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StoragePermissionHelper.onActivityResumed(this, storagePermissionLauncher)
        startInitialLoadIfNeeded()
    }

    /**
     * Performs the one-time initial TV configuration load after storage access is granted.
     *
     * This method is guarded to run only once per activity instance to prevent duplicate
     * config reloads, and it consumes a pending refresh flag after an APK upgrade.
     */
    private fun startInitialLoadIfNeeded() {
        if (hasStartedInitialLoad || isFinishing || isDestroyed) return
        if (!StoragePermissionHelper.hasFullStorageAccess(this)) return
        hasStartedInitialLoad = true
        val refreshAfterApkUpgrade =
            com.softland.callqtv.utils.AppUpgradeCoordinator.consumePendingConfigRefresh(this)
        viewModel.loadData(
            mqttViewModel,
            forceShowOverlay = true,
            clearCacheBeforeFetch = refreshAfterApkUpgrade,
        )
        lifecycleScope.launch {
            val pendingUpdate = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.softland.callqtv.utils.AppUpdateChecker.checkForUpdate(this@TokenDisplayActivity)
            }
            if (pendingUpdate != null && !isFinishing && !isDestroyed) {
                startActivity(
                    Intent(this@TokenDisplayActivity, CustomerIdActivity::class.java).apply {
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_VERSION, pendingUpdate.apkVersion)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_URL, pendingUpdate.downloadUrl)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_MANDATORY, pendingUpdate.isMandatoryUpdate)
                        putExtra(CustomerIdActivity.EXTRA_PENDING_UPDATE_PROJECT, pendingUpdate.projectCode)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        AppBackgroundCoordinator.unregisterTokenDisplaySession()
        android.util.Log.i(
            "TokenDisplayLaunch",
            "onDestroy instance=$launchInstanceId taskId=$taskId isFinishing=$isFinishing isDestroyed=$isDestroyed"
        )
        super.onDestroy()
        MediaEngine.shutdown()
        // Avoid cold TTS after transient activity teardown; full shutdown only when leaving the app.
        if (isFinishing) {
            TokenAnnouncer.shutdown()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logLaunchOrigin("onNewIntent", savedInstanceStateAvailable = false)
    }

    /**
     * Logs how this activity instance was launched (task id, intent action, extras, referrer/caller).
     *
     * Used for diagnosing unexpected launches and distinguishing warm vs recreated instances.
     */
    private fun logLaunchOrigin(event: String, savedInstanceStateAvailable: Boolean) {
        val i = intent
        val extrasKeys = try {
            i?.extras?.keySet()?.joinToString(",").orEmpty()
        } catch (_: Exception) {
            ""
        }
        val referrerValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                referrer?.toString().orEmpty()
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        val callerValue = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                callingPackage.orEmpty()
            } catch (_: Exception) {
                ""
            }
        } else {
            ""
        }
        android.util.Log.i(
            "TokenDisplayLaunch",
            "event=$event instance=$launchInstanceId taskId=$taskId " +
                "flags=0x${Integer.toHexString(i?.flags ?: 0)} action=${i?.action.orEmpty()} " +
                "hasExtras=${(i?.extras != null)} extrasKeys=$extrasKeys " +
                "referrer=$referrerValue caller=$callerValue restored=$savedInstanceStateAvailable"
        )
    }

    companion object {
        private val launchCounter = java.util.concurrent.atomic.AtomicLong(0L)

        /** Returns a monotonically increasing instance id for correlating log lines. */
        private fun nextLaunchInstanceId(): Long = launchCounter.incrementAndGet()
    }
}
