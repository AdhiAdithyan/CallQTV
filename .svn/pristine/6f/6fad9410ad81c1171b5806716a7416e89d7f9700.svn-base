package com.softland.callqtv.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.softland.callqtv.R
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.Variables
import com.softland.callqtv.viewmodel.LicenseCheckViewModel
import com.softland.callqtv.viewmodel.NetworkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashScreenActivity : AppCompatActivity() {
    private val splashDisplayLength = 3000L
    private lateinit var networkViewModel: NetworkViewModel
    private lateinit var licenseCheckViewModel: LicenseCheckViewModel
    private lateinit var authSharedPrefs: SharedPreferences
    private lateinit var loginSharedPrefs: SharedPreferences

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("SplashScreen", "Storage permission ${it.key}: ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStoragePermissionsIfNeeded()

        // Set up Jetpack Compose UI - load theme async to avoid blocking main thread
        setContent {
            var themeColor by remember { mutableStateOf(Color(0xFF2196F3)) }
            LaunchedEffect(Unit) {
                themeColor = withContext(Dispatchers.Default) {
                    ThemeColorManager.getSelectedThemeColor(this@SplashScreenActivity)
                }
            }
            val colorScheme = ThemeColorManager.createDarkColorScheme(themeColor)

            MaterialTheme(colorScheme = colorScheme) {
                SplashScreenContent()
            }
        }

        authSharedPrefs =
            getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        loginSharedPrefs = getSharedPreferences(AppSharedPreferences.Login, Context.MODE_PRIVATE)

        networkViewModel = ViewModelProvider(this)[NetworkViewModel::class.java]
        licenseCheckViewModel = ViewModelProvider(this)[LicenseCheckViewModel::class.java]

        val validTillDate =
            authSharedPrefs.getString(PreferenceHelper.product_license_end, "").orEmpty()
        licenseCheckViewModel.checkLicenseValidity(validTillDate)

        var navigationJob: Job? = null
        licenseCheckViewModel.isLicenseValid.observe(this) { isValid ->
            navigationJob?.cancel()
            navigationJob = lifecycleScope.launch {
                val isNetworkAvailable = withContext(Dispatchers.IO) {
                    Variables.isNetworkEnabled(this@SplashScreenActivity)
                }
                val isLicenseValid = isValid

                if (isNetworkAvailable && !isLicenseValid) {
                    authSharedPrefs.edit().apply {
                        putBoolean("IsValid", false)
                        putString("TokenNo", "")
                        putString("Licence", "")
                        putString("UpdateUrl", "")
                        putString("IsMandatory", "")
                        putString("VersionCode", "")
                        putString("Version", "")
                        putBoolean(PreferenceHelper.TOKEN_VOICE, false)
                        apply()
                    }
                }

                delay(splashDisplayLength)
                if (!isLicenseValid) {
                    PreferenceHelper.UpdateLoginRespDetails(
                        this@SplashScreenActivity,
                        "loginUserName",
                        ""
                    )
                    navigateToDeviceRegistration()
                } else {
                    val mainIntent = Intent(this@SplashScreenActivity, CustomerIdActivity::class.java)
                    startActivity(mainIntent)
                    overridePendingTransition(
                        com.softland.callqtv.R.anim.fade_in,
                        com.softland.callqtv.R.anim.fade_in
                    )
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            Variables.isNetworkEnabled(this@SplashScreenActivity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clear binding reference
    }

    private fun requestStoragePermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= 32 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            storagePermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun navigateToDeviceRegistration() {
        lifecycleScope.launch {
            with(authSharedPrefs.edit()) {
                putString("TokenNo", "")
                apply()
            }
            delay(2500L)
            val intent = Intent(this@SplashScreenActivity, CustomerIdActivity::class.java)
            startActivity(intent)
            overridePendingTransition(
                com.softland.callqtv.R.anim.fade_in,
                com.softland.callqtv.R.anim.fade_in
            )
            finish()
        }
    }
}

@Composable
private fun SplashScreenContent() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current

        // Calculate responsive logo size based on screen dimensions
        // Base size for 1920x1080 (Full HD), scale proportionally
        val baseWidth = 1920.dp
        val baseHeight = 1080.dp
        val baseLogoSize = 262.dp

        val widthScale = with(density) { screenWidth.toPx() / baseWidth.toPx() }
        val heightScale = with(density) { screenHeight.toPx() / baseHeight.toPx() }
        val scale = minOf(widthScale, heightScale).coerceIn(0.5f, 2.0f) // Limit scaling between 0.5x and 2x

        // Responsive logo size
        val logoSize = (baseLogoSize * scale).coerceAtLeast(150.dp).coerceAtMost(400.dp)

        // Simple pulse animation for the logo on the splash screen
        val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
        val pulseScale = infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "splash_scale"
        )

        // Background: clearly tinted gradient based on selected theme color
        val primary = MaterialTheme.colorScheme.primary
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(
                primary.copy(alpha = 0.35f),
                primary.copy(alpha = 0.75f)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            // Centered logo with responsive size
            Box(
                modifier = Modifier
                    .size(logoSize)
                    .graphicsLayer(
                        scaleX = pulseScale.value,
                        scaleY = pulseScale.value
                    )
                    .align(Alignment.Center)
            ) {
//            Image(
//                painter = painterResource(id = R.drawable.icon_bg_round),
//                contentDescription = null,
//                modifier = Modifier.fillMaxSize(),
//                contentScale = ContentScale.Crop
//            )

            Image(
                painter = painterResource(id = R.drawable.callq_tv_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )
        }
    }
}
}
