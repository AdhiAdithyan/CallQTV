package com.softland.callqtv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.softland.callqtv.R
import com.softland.callqtv.data.local.AppSharedPreferences
import com.softland.callqtv.utils.AnimatedLoadingOverlay
import com.softland.callqtv.utils.KeyboardUtils
import com.softland.callqtv.utils.NetworkUtil
import com.softland.callqtv.utils.PreferenceHelper
import com.softland.callqtv.utils.ThemeColorManager
import com.softland.callqtv.utils.Variables
import com.softland.callqtv.viewmodel.DownloadViewModel
import com.softland.callqtv.viewmodel.NetworkViewModel
import com.softland.callqtv.viewmodel.RegistrationViewModel
import com.softland.callqtv.viewmodel.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CustomerIdActivity : AppCompatActivity() {

    private lateinit var registrationViewModel: RegistrationViewModel
    private lateinit var downloadViewModel: DownloadViewModel
    private lateinit var networkViewModel: NetworkViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registrationViewModel = ViewModelProvider(this)[RegistrationViewModel::class.java]
        downloadViewModel = ViewModelProvider(this)[DownloadViewModel::class.java]
        networkViewModel = ViewModelProvider(this)[NetworkViewModel::class.java]
        
        // Prevent screen from sleeping while this activity is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Detect if we are running on an Android TV device
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTv = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        // Pre-fill if exists
        val authSharedPrefs = getSharedPreferences(AppSharedPreferences.AUTHENTICATION, Context.MODE_PRIVATE)
        val custId = authSharedPrefs.getInt(PreferenceHelper.customer_id, 0)
        val licenseEnd = authSharedPrefs.getString(PreferenceHelper.product_license_end, "") ?: ""

        if (custId != 0) {
            registrationViewModel.setCustomerId(String.format(Locale.ROOT, "%04d", custId))
        }

        observeDownloadStatus()

        setContent {
            var currentThemeColor by remember { mutableStateOf(ComposeColor(0xFF2196F3)) }
            var deviceId by remember { mutableStateOf("") }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    currentThemeColor = ThemeColorManager.getSelectedThemeColor(this@CustomerIdActivity)
                    deviceId = Variables.getMacId(this@CustomerIdActivity)
                }
            }
            val colorScheme = ThemeColorManager.createDarkColorScheme(currentThemeColor)
            var showThemeDialog by remember { mutableStateOf(false) }

            MaterialTheme(colorScheme = colorScheme) {
                val customerId by registrationViewModel.customerId.observeAsState("")
                val customerIdError by registrationViewModel.customerIdError.observeAsState(null)
                val registrationState by registrationViewModel.state.observeAsState(RegistrationState.Idle)
                val isNetworkAvailable by networkViewModel.getNetworkLiveData(this).observeAsState(
                    initial = NetworkUtil.isNetworkAvailable(this)
                )

                CustomerIdScreen(
                    customerId = customerId,
                    errorMessage = customerIdError,
                    isChecking = registrationState is RegistrationState.Loading,
                    appVersionText = getString(R.string.app_version),
                    isTv = isTv,
                    isFirstInstall = custId == 0, // Only true on first install
                    deviceId = deviceId,
                    isNetworkAvailable = isNetworkAvailable,
                    onCustomerIdChange = { registrationViewModel.setCustomerId(it) },
                    onCheckLicenseClick = {
                        KeyboardUtils.hideKeyboard(this)
                        registrationViewModel.startRegistrationFlow()
                    },
                    onThemeChangeClick = { showThemeDialog = true }
                )

                HandleRegistrationState(registrationState, deviceId)

                if (showThemeDialog) {
                    ThemeSelectionComposeDialog(
                        onDismiss = { showThemeDialog = false },
                        onThemeSelected = { hexCode ->
                            ThemeColorManager.setThemeColor(this@CustomerIdActivity, hexCode)
                            // Update reactive state, avoids disruptive activity recreation
                            currentThemeColor = ThemeColorManager.getSelectedThemeColor(this@CustomerIdActivity)
                            showThemeDialog = false
                        }
                    )
                }
            }
        }
        
        // Auto trigger if already has ID
        if (custId != 0) {
            if (isLicenseValid(licenseEnd)) {
                // License is valid for more than 0 days, proceed to main activity
                val intent = Intent(this, TokenDisplayActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // License expired or expiring today, check with server
                registrationViewModel.startRegistrationFlow()
            }
        }
    }

    private fun isLicenseValid(licenseEndDateStr: String): Boolean {
        if (licenseEndDateStr.isEmpty()) return false
        
        try {
            // Attempt common formats. If the server uses a specific one, adjust here.
            val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy", "yyyy/MM/dd", "dd/MM/yyyy", "MMM dd, yyyy")
            var expiryDate: Date? = null
            
            for (format in formats) {
                try {
                    expiryDate = SimpleDateFormat(format, Locale.ROOT).parse(licenseEndDateStr)
                    if (expiryDate != null) break
                } catch (e: Exception) { continue }
            }

            if (expiryDate == null) return false

            val calendar = Calendar.getInstance()
            // Reset current time to start of day for accurate day comparison
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val today = calendar.time

            // Compare only dates. If expiryDate is AFTER today, it's valid for at least tomorrow.
            // If expiryDate is EQUAL to today, it's 0 days left, so we check license.
            return expiryDate.after(today)
        } catch (e: Exception) {
            return false
        }
    }

    @Composable
    private fun HandleRegistrationState(state: RegistrationState, deviceId: String) {
        val context = LocalContext.current
        
        when (state) {
            is RegistrationState.Loading -> {
                AnimatedLoadingOverlay(message = state.message, isVisible = true)
            }
            is RegistrationState.Error -> {
                AlertDialog(
                    modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
                    onDismissRequest = { registrationViewModel.resetState() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    title = { Text("Device License Registration error") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { registrationViewModel.retryRegistrationFlow() }) { Text("Retry") }
                    },
                    dismissButton = {
                        TextButton(onClick = { registrationViewModel.resetState() }) { Text("Close") }
                    }
                )
            }
            is RegistrationState.ProductAuthRequired -> {
                AlertDialog(
                    modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
                    onDismissRequest = { registrationViewModel.resetState() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    title = { Text("License status") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.status,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { registrationViewModel.startRegistrationFlow() }) { Text("Refresh") }
                    },
                    dismissButton = {
                        TextButton(onClick = { registrationViewModel.resetState() }) { Text("Cancel") }
                    }
                )
            }
            is RegistrationState.DeviceApprovalRequired -> {
                AlertDialog(
                    modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
                    onDismissRequest = { registrationViewModel.resetState() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    title = { Text("Device approval required") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.status,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { registrationViewModel.startRegistrationFlow() }) { Text("Refresh") }
                    },
                    dismissButton = {
                        TextButton(onClick = { registrationViewModel.resetState() }) { Text("Cancel") }
                    }
                )
            }
            is RegistrationState.LicenseExpired -> {
                AlertDialog(
                    modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
                    onDismissRequest = { registrationViewModel.resetState() },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    title = { Text("License expired") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.status,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { registrationViewModel.startRegistrationFlow() }) { Text("Refresh") }
                    },
                    dismissButton = {
                        TextButton(onClick = { registrationViewModel.resetState() }) { Text("Cancel") }
                    }
                )
            }
            is RegistrationState.UpdateAvailable -> {
                AlertDialog(
                    modifier = Modifier.widthIn(min = 280.dp, max = 900.dp),
                    onDismissRequest = { },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                    title = { Text("Update available") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "A new version is available. Please update to continue.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Device ID: $deviceId",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            downloadViewModel.downloadApk(this, state.downloadURL, state.apkVersion)
                        }) { Text("Update") }
                    },
                    dismissButton = if (state.isMandatoryUpdate == 0) {
                        {
                            TextButton(onClick = {
                                registrationViewModel.fetchServiceUrl(state.projectCode)
                            }) { Text("Later") }
                        }
                    } else null
                )
            }
            is RegistrationState.NavigateToMain -> {
                LaunchedEffect(Unit) {
                    val intent = Intent(this@CustomerIdActivity, TokenDisplayActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            else -> {}
        }
    }

    private fun observeDownloadStatus() {
        downloadViewModel.downloadStatus.observe(this) { status ->
            when (status.statusType) {
                com.softland.callqtv.utils.DownloadStatus.StatusType.SUCCESS -> {
                    downloadViewModel.triggerApkInstall(status.versionOrResult.orEmpty())
                }
                com.softland.callqtv.utils.DownloadStatus.StatusType.ERROR -> {
                    Toast.makeText(this, "Download failed: ${status.filePathOrError}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        downloadViewModel.installIntentLiveData.observe(this) { event ->
            if (event != null) {
                installApk(event.filePath)
            }
        }
    }

    private fun installApk(versionName: String) {
        try {
            val apkFile = File(
                Environment.getExternalStorageDirectory(),
                "CallQTV_Version$versionName.apk"
            )

            if (!apkFile.exists()) {
                Toast.makeText(this, "APK file not found: ${apkFile.absolutePath}", Toast.LENGTH_LONG).show()
                return
            }

            val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", apkFile)
            } else {
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun CustomerIdScreen(
    customerId: String,
    errorMessage: String?,
    isChecking: Boolean,
    appVersionText: String,
    isTv: Boolean,
    isFirstInstall: Boolean,
    deviceId: String,
    isNetworkAvailable: Boolean,
    onCustomerIdChange: (String) -> Unit,
    onCheckLicenseClick: () -> Unit,
    onThemeChangeClick: () -> Unit
) {
    var localCustomerId by rememberSaveable { mutableStateOf(customerId) }

    // Keep local and external state in sync
    if (localCustomerId != customerId) {
        localCustomerId = customerId
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current

        val baseWidth = 1920.dp
        val baseHeight = 1080.dp

        val widthScale = with(density) { screenWidth.toPx() / baseWidth.toPx() }
        val heightScale = with(density) { screenHeight.toPx() / baseHeight.toPx() }
        val scale = minOf(widthScale, heightScale).coerceIn(0.5f, 2.0f)

        val horizontalPadding = if (isTv) (48.dp * scale).coerceAtLeast(16.dp) else 16.dp
        val verticalPadding = if (isTv) (40.dp * scale).coerceAtLeast(24.dp) else 24.dp
        val maxContentWidthFraction = if (isTv) 0.6f else 1f
        val titleFontSize = if (isTv) (28f * scale).coerceAtLeast(22f).sp else 22.sp
        val digitFieldWidth = (72.dp * scale).coerceAtLeast(56.dp)
        val digitFieldSpacing = (16.dp * scale).coerceAtLeast(10.dp)
        val cardPadding = (20.dp * scale).coerceAtLeast(16.dp)
        val buttonHeight = if (isTv) (56.dp * scale).coerceAtLeast(48.dp) else 48.dp

        val primary = MaterialTheme.colorScheme.primary
        val context = LocalContext.current
        val bgIntensity = remember { ThemeColorManager.getBackgroundIntensity(context) }
        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(
                ComposeColor(0xFF121212),
                primary.copy(alpha = bgIntensity)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        ) {
            val networkIconSize = if (isTv) (28.dp * scale).coerceAtLeast(20.dp) else 24.dp
            val networkIconRes = if (isNetworkAvailable) R.drawable.ic_network_available else R.drawable.ic_network_unavailable
            val networkIconColor = if (isNetworkAvailable) ComposeColor(0xFF2E7D32) else ComposeColor(0xFFB71C1C)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = networkIconRes),
                        contentDescription = if (isNetworkAvailable) "Network available" else "Network unavailable",
                        modifier = Modifier.size(networkIconSize),
                        colorFilter = ColorFilter.tint(networkIconColor)
                    )
                    Text(
                        text = if (isNetworkAvailable) "Online" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = networkIconColor,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enter Customer ID",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = titleFontSize,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(maxContentWidthFraction),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ComposeColor(0xFF121212)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    val digitStates = remember {
                        mutableStateListOf(
                            TextFieldValue(""),
                            TextFieldValue(""),
                            TextFieldValue(""),
                            TextFieldValue("")
                        )
                    }

                    LaunchedEffect(customerId) {
                        if (customerId.length == 4) {
                            customerId.forEachIndexed { index, c ->
                                if (index < digitStates.size) {
                                    val text = c.toString()
                                    digitStates[index] = TextFieldValue(text = text, selection = TextRange(text.length))
                                }
                            }
                        }
                    }

                    val digitFocusRequesters = remember { List(4) { FocusRequester() } }
                    val scope = rememberCoroutineScope()

                    fun updateDigit(index: Int, input: TextFieldValue) {
                        val filtered = input.text.filter { it.isDigit() }
                        val newText = filtered.takeLast(1)

                        if (index !in 0..3) return

                        if (newText.isEmpty() && digitStates[index].text.isNotEmpty()) {
                            digitStates[index] = TextFieldValue("")
                            val combined = digitStates.joinToString(separator = "") { it.text }
                            onCustomerIdChange(combined)
                            if (index > 0) {
                                scope.launch {
                                    delay(0)
                                    digitFocusRequesters[index - 1].requestFocus()
                                }
                            }
                        } else if (newText.isNotEmpty()) {
                            digitStates[index] = TextFieldValue(text = newText, selection = TextRange(newText.length))
                            val combined = digitStates.joinToString(separator = "") { it.text }
                            onCustomerIdChange(combined)
                            if (index < 3) {
                                scope.launch {
                                    delay(0)
                                    digitFocusRequesters[index + 1].requestFocus()
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(cardPadding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LaunchedEffect(isTv) {
                            if (isTv) {
                                delay(0)
                                digitFocusRequesters[0].requestFocus()
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(digitFieldSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (0..3).forEach { index ->
                                OutlinedTextField(
                                    value = digitStates[index],
                                    onValueChange = { updateDigit(index, it) },
                                    singleLine = true,
                                    isError = errorMessage != null,
                                    textStyle = LocalTextStyle.current.copy(
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = titleFontSize * 0.6f
                                    ),
                                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .width(digitFieldWidth)
                                        .focusRequester(digitFocusRequesters[index])
                                )
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onCheckLicenseClick,
                            enabled = !isChecking,
                            modifier = Modifier.fillMaxWidth().height(buttonHeight),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(text = "Checking...", color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(text = "Check license", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Device ID: $deviceId",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Version: $appVersionText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                if (isTv && isFirstInstall) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onThemeChangeClick, shape = RoundedCornerShape(10.dp)) {
                        Text(text = "Change Theme")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSelectionComposeDialog(
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val currentHex = remember { ThemeColorManager.getSelectedThemeColorHex(context) }
    val options = ThemeColorManager.themeColorOptions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme Color") },
        text = {
            // Use a scrollable grid to handle many color options on TV screens
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options.size) { index ->
                    val option = options[index]
                    val isSelected = option.hexCode == currentHex
                    val optionColor = ComposeColor(android.graphics.Color.parseColor(option.hexCode))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(option.hexCode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(optionColor, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
