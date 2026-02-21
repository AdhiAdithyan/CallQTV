## CallQTV – Architecture & Function‑Wise Workflow

This document describes the current Kotlin/Compose TV codebase, the main modules, and the function‑level workflows.

---

## 1. High‑Level Architecture

- **UI Layer (Activities + Compose)**
  - `SplashScreenActivity` – startup flow, license validity check, navigation.
  - `CustomerIdActivity` – customer ID capture, license validation, APK download/install, theme selection.
  - `TokenDisplayActivity` – TV token display, advertisements, MQTT token updates, theme UI.
- **Domain / ViewModels**
  - `MqttViewModel` – MQTT connection, subscription, message parsing, LiveData for UI.
  - Other ViewModels (license, download, service URL, network) used inside `CustomerIdActivity` and `SplashScreenActivity`.
- **Data Layer**
  - `TvConfigRepository` – calls TV config API, maps JSON → Room entities, exposes cached config, counters, ads, mapped broker.
  - Retrofit client + `ApiService` – HTTP layer for REST APIs.
  - Room database (`AppDatabase`) – tables: `TvConfigEntity`, `MappedBrokerEntity`, `CounterEntity`, `AdFileEntity`, and their DAOs.
- **Utilities**
  - `ThemeColorManager` – dynamic theme (primary color + background intensity).
  - `TokenAnnouncer` – TextToSpeech queue for token announcements.
  - `Variables`, `NetworkUtil`, `PreferenceHelper`, `AnimatedLoadingOverlay`, etc.

Data & control flow is:

1. `SplashScreenActivity` checks license + network and navigates to `CustomerIdActivity`.
2. `CustomerIdActivity` validates license with backend and stores `CustomerID`.
3. `TokenDisplayActivity` reads MAC + `CustomerID`, fetches TV config via `TvConfigRepository`, saves to Room, reads counters/ads/mapped broker, and connects MQTT via `MqttViewModel`.
4. MQTT messages trigger `TokenAnnouncer` and UI updates in `TokenDisplayActivity`.

---

## 2. `TokenDisplayActivity` & Token Display Workflow

### 2.1 `TokenDisplayActivity.onCreate`

- **Purpose**: Set up the themed Compose UI for the token display screen.
- **Workflow**:
  - Gets selected theme color via `ThemeColorManager.getSelectedThemeColor(this)`.
  - Builds dark color scheme via `ThemeColorManager.createDarkColorScheme(themeColor)`.
  - Calls `setContent { MaterialTheme { Surface { TokenDisplayScreen() } } }`.

### 2.2 `@Composable fun TokenDisplayScreen()`

**Purpose**: Entry composable that loads TV config, counters, ads, MQTT, and drives the main state for the token UI.

**Key state variables**:
- `isLoading`, `errorMessage`.
- `config: TvConfigEntity?`, `counters: List<CounterEntity>`, `adFiles: List<AdFileEntity>`.
- MQTT‑related `mqttMessage`, `mqttConnected`, `mqttError`, `latestTokenPair`.
- `macAddress`, `appVersion`, `isNetworkAvailable`.
- `showThemeDialog` – flag to open color picker dialog.

**Major workflows**:

1. **Load TV configuration & cache** (`LaunchedEffect(Unit)`):
   - Builds `TvConfigRepository(context)`.
   - Reads `CustomerID` from `AppSharedPreferences.AUTHENTICATION`, pads to 4 digits.
   - Calls:
     - `fetchAndCacheTvConfig(macAddress, customerId)` – tries network first.
     - Falls back to `getCachedConfig(macAddress, customerId)` on error.
     - Loads `counters = repo.getCounters(macAddress, customerId)`.
     - Loads `adFiles = repo.getAdFiles(macAddress, customerId)`.
   - If config is missing, sets a human‑readable `errorMessage`.
   - If config exists:
     - Fetches mapped broker row: `repo.getMappedBrokerEntity(macAddress, customerId)`.
     - Builds MQTT connection fields: `host`, `port`, `topic`.
     - Computes `serverUri = "tcp://$host:$mqttPort"` and `clientId = "callqtv_${macAddressWithoutColons}"`.
     - Initializes MQTT:
       - `mqttViewModel.initMqttClient(serverUri, clientId, context)`.
       - `mqttViewModel.connectAndSubscribe(username = ssid, password = password, topic = topic, qos = 1)`.
   - Handles exceptions by:
     - Falling back to cached config & counters.
     - Setting a friendly `errorMessage` if nothing cached.
   - Finally, sets `isLoading = false`.

2. **Network status watcher** (`LaunchedEffect("network_status")`):
   - Every 2 seconds:
     - Calls `NetworkUtil.isNetworkAvailable(context)`.
     - Updates `isNetworkAvailable` for header status text.

3. **MQTT token → TTS announcement** (`LaunchedEffect(latestTokenPair, config)`):
   - Runs when either `latestTokenPair` or `config` changes.
   - If `config.enableTokenAnnouncement == true` and `latestTokenPair != null`:
     - Extracts `(counterName, tokenLabel)`.
     - Calls:
       - `TokenAnnouncer.announceToken(context, config.audioLanguage, counterNameOrFallback, tokenLabel)`.
   - `TokenAnnouncer` manages the TTS queue so rapid tokens are spoken sequentially.

4. **UI state branches**:
   - `isLoading == true` → shows `AnimatedLoadingOverlay("Loading TV configuration...")`.
   - `errorMessage != null` → shows animated error Text in center.
   - `config != null` → shows:
     - Optional MQTT raw message (`mqttMessage`).
     - MQTT error line (`mqttError`).
     - Main layout via `TokenDisplayFromConfig(...)`.
     - Dynamic theme dialog when `showThemeDialog == true`.

### 2.3 `@Composable private fun TokenDisplayFromConfig(...)`

**Purpose**: Render the full token display layout using the loaded TV config, including header, counters, ads, and theme picker.

**Inputs**:
- `config: TvConfigEntity`, `macAddress`, `appVersion`.
- `isMqttConnected`, `isNetworkAvailable`.
- `counters: List<CounterEntity>`, `adFiles: List<AdFileEntity>`.
- `onThemeClick: () -> Unit`.

**Key steps**:

1. **Responsive sizing setup** (with `BoxWithConstraints`):
   - Computes `screenWidth`, `screenHeight`, `scale` based on a 1920x1080 baseline.
   - Sets `isCompactWidth` for smaller screens.
   - Derives responsive paddings, spacing, token heights, header sizes, etc.

2. **Config‑derived values**:
   - `showAds`, `adPlacement`, `rows`, `columns`.
   - `countersCount` uses actual Room counters if available, else config `noOfCounters`.
   - `companyName` (with fallback to `"CALL-Q"`).
   - Dynamic `dateTime` string updated every second via `LaunchedEffect`.
   - Colors parsed from hex strings via `parseColorOrDefault(...)`.
   - Font sizes scaled by `scale`.

3. **Advertisement area** – `val adArea: @Composable RowScope.(Float) -> Unit`
   - Sorts ads by `position`: `orderedAds = adFiles.sortedBy { it.position }`.
   - Maintains `currentAdIndex` and reads `intervalSeconds = (config.adInterval ?: 5).coerceAtLeast(1)`.
   - `LaunchedEffect` loop:
     - Every `intervalSeconds` seconds, increments `currentAdIndex` (wraps).
   - Layout:
     - Allocates width via `.weight(weight)` parameter.
     - If no ads: shows `"No advertisements configured"`.
     - Else uses `Crossfade(targetState = currentAd)` with `tween(600)` for smooth transitions.
     - Loads ad image with Coil:
       - `AsyncImage(model = ImageRequest.Builder(context).data(url).crossfade(true).build(), ...)`.

4. **Reusable counter board** – `val counterBoard: @Composable (Int, Modifier) -> Unit`
   - For a given `counterIndex`:
     - Resolves `counterName` using `CounterEntity` (name or defaultName).
     - Renders a `Card` with rounded corners.
     - Inside:
       - Header Text for counter name.
       - `LazyVerticalGrid` of tokens:
         - `rows * columns` items.
         - Each token is a white `Card` with centered token text.

5. **Two layout types for counters**:

   - **Display Type 1 – horizontal row** (`countersContentType1`):
     - `Row` with equal `weight(1f)` boards for each counter.
     - Matches diagrams where counters fill columns horizontally.

   - **Display Type 2 – vertical stack** (`countersContentType2`):
     - `Column` with equal `weight(1f)` rows for each counter.
     - Matches diagrams where counters stack vertically.

6. **Dynamic space sharing between ads and counters**:

   - Computes:
     ```kotlin
     val hasAds = showAds && adFiles.isNotEmpty()
     val maxConfiguredCounters = (config.noOfCounters ?: countersCount).coerceAtLeast(1)
     val visibleCounters = countersCount.coerceAtMost(maxConfiguredCounters)
     val freeSlots = (maxConfiguredCounters - visibleCounters).coerceAtLeast(0)
     val adWeight = 1f + freeSlots.toFloat()
     val countersWeight = visibleCounters.toFloat().coerceAtLeast(1f)
     val layoutType = config.layoutType ?: "1"
     ```

   - **With ads**:
     - **If `layoutType == "2"`**:
       - `Row` with ad column + vertical counters (`countersContentType2`).
     - **Else (type 1)**:
       - `Row` with ad column + horizontal counters (`countersContentType1`).
     - `adWeight` grows when fewer counters are active → ads take freed counter space.

   - **Without ads**:
     - Shows either `countersContentType1` or `countersContentType2` across full width.

7. **Header & footer**:
   - **Top header**:
     - Left: `companyName`.
     - Right: date/time, MQTT status (`BROKER: Connected/Disconnected` with colored bullets), network status.
     - Far right: rainbow `"🌈"` icon calling `onThemeClick()`.
   - **Middle**:
     - Layout described above (ads + counters).
   - **Bottom**:
     - "Test Call" button → triggers `TokenAnnouncer.announceToken` with sample token and first counter.
     - Footer text: `Device: <MAC> Version: <appVersion>`.

8. **Theme picker dialog**
   - When `showThemeDialog == true`, a Material3 `AlertDialog` is displayed.
   - Contains:
     - Horizontal gradient preview.
     - Sliders for hue, saturation (including white), and intensity (background transparency).
     - Large preview header card to show the effect.
     - "Apply" button:
       - Calls `ThemeColorManager.setSelectedThemeColor(...)` and `setBackgroundIntensity(...)`.
       - Recreates activity to apply theme.

---

## 3. `MqttViewModel` – MQTT & Token Parsing

### 3.1 Fields

- `mqttClientManager: MqttClientManager?` – low‑level client wrapper.
- `receivedMessage: MutableLiveData<String>` – last MQTT payload.
- `latestToken: MutableLiveData<Pair<String, String>?>` – `(counterName, tokenLabel)`.
- `connectionStatus: MutableLiveData<Boolean>` – connected/disconnected.
- `errorMessage: MutableLiveData<String>` – detailed errors.
- `pendingTopic`, `pendingQos` – stored until connection is established.

### 3.2 Public API

- `initMqttClient(serverUri, clientId, context)`:
  - Instantiates `MqttClientManager` and sets this ViewModel as listener.
- `connect(username, password)`:
  - Delegates to `mqttClientManager.connect`.
- `subscribe(topic, qos)`:
  - Stores `pendingTopic`/`pendingQos`.
  - Subscribes immediately if already connected.
- `connectAndSubscribe(username, password, topic, qos)`:
  - Saves `pendingTopic`/`pendingQos`.
  - Calls `connect`.
- `disconnect()`:
  - Disconnects underlying MQTT connection.
- `getReceivedMessage()`, `getConnectionStatus()`, `getErrorMessage()`, `getLatestToken()`:
  - Expose `LiveData` for Compose to observe.

### 3.3 Listener callbacks

- `onMessageReceived(topic, message)`:
  - Updates `receivedMessage`.
  - Calls `parseCounterAndToken(message)`:
    - Tries JSON:
      - Reads `token` from `token_no`/`token`/`token_number`.
      - Reads `counter` from `counter_name`/`counter`/`counter_code`.
      - On success posts `latestToken` as `(counterName, tokenLabel)`.
    - Else:
      - Extracts digits from plain text; if present, posts `("" to tokenDigits)`.

- `onConnectionStatus(isConnected)`:
  - Updates `connectionStatus`.
  - If connected and `pendingTopic` not empty → calls `subscribe(pendingTopic, pendingQos)`.

- `onError(error)`:
  - Posts error to `errorMessage` for UI display.

- `onCleared()`:
  - Disconnects MQTT to avoid leaks.

---

## 4. `TvConfigRepository` – Config, Counters, Ads, Broker

### 4.1 Construction

- Injected with `Context`.
- Uses:
  - `RetrofitClient.getApiLisenceService()` → `ApiService`.
  - `AppDatabase.getInstance(context)` → DAOs:
    - `tvConfigDao`, `mappedBrokerDao`, `counterDao`, `adFileDao`.
  - `Gson` for JSON (de)serialization.

### 4.2 `suspend fun fetchAndCacheTvConfig(macAddress, customerId)`

**Purpose**: Fetch TV configuration from backend, map it, store in Room, and return `TvConfigEntity`.

**Workflow**:
1. Build `TvConfigRequest(macAddress, customerId, flag = "TV")`.
2. Use fixed URL: `"https://py.softlandindia.net/CallQ/config/api/android/config"`.
3. Call `api.fetchTvConfig(url, request).awaitResponse()`.
4. If not successful or body missing, return `null`.
5. If `status != "success"` or `tvConfig == null` or `deviceId == null`, return `null`.
6. Map response to `TvConfigEntity` via `toEntity(...)` and `dao.upsert(entity)`.
7. Store `mapped_broker`:
   - `toMappedBrokerEntityOrNull(...)` → if non‑null, `mappedBrokerDao.upsert(...)`.
8. Store `counters`:
   - `toCounterEntities(...)` → `List<CounterEntity>`.
   - If non‑empty:
     - `counterDao.deleteByDeviceAndCustomer(deviceId, macAddress, customerId)`.
     - `counterDao.insertAll(counterEntities)`.
9. Store `ad_files`:
   - `toAdFileEntities(...)` → `List<AdFileEntity>`.
   - If non‑empty:
     - `adFileDao.deleteByDeviceAndCustomer(deviceId, macAddress, customerId)`.
     - `adFileDao.insertAll(adFileEntities)`.
10. Return `entity`.

### 4.3 Read helpers

- `getCachedConfigByDeviceId(deviceId)` – single config by PK.
- `getCachedConfig(macAddress, customerId)` – config by MAC + customer.
- `getCounters(macAddress, customerId)` – returns `List<CounterEntity>` from `counterDao`.
- `getAdFiles(macAddress, customerId)` – returns sorted `List<AdFileEntity>` from `adFileDao` for that MAC+customer.
- `getMappedBroker(entity: TvConfigEntity?)` – decodes embedded `mappedBrokerJson` to `MappedBroker`.
- `suspend fun getMappedBrokerEntity(macAddress, customerId)` – read from `mappedBroker` table.

### 4.4 Mapping helpers

- `TvConfigResponse.toEntity(macAddress, customerId)`:
  - Maps all `tv_config` fields (`audio_language`, `show_ads`, `ad_interval`, layout, colors, fonts, tokens_per_counter, no_of_counters, ad_placement, etc.) to `TvConfigEntity`.
  - Serializes:
    - `adFilesJson` – JSON list of ad file strings.
    - `countersJson` – JSON array of all counters.
    - `shiftDetailsJson` – JSON object if `shift_details` is not `JsonNull`.
    - `mappedBrokerJson` – JSON of `mapped_broker` if present.

- `TvConfigResponse.toCounterEntities(...)`:
  - For each `CounterConfig` builds `CounterEntity` with:
    - `deviceId`, `macAddress`, `customerId`.
    - All counter fields (`counterId`, `defaultName`, `name`, spans, `audioUrl`, etc.).
    - `rawJson` – original JSON for forward compatibility.

- `TvConfigResponse.toMappedBrokerEntityOrNull(...)`:
  - Converts `mapped_broker` into `MappedBrokerEntity`, including Wi‑Fi / MQTT config (host, port, topic).

- `TvConfigResponse.toAdFileEntities(...)`:
  - Converts `tv_config.ad_files` into one row per entry:
    - `deviceId`, `macAddress`, `customerId`, `position`, `filePath`, `rawJson`.

---

## 5. `CustomerIdActivity` – License & Customer ID Flow

### 5.1 `onCreate`

**Responsibilities**:
- Initialize shared preferences, ViewModels, and download observers.
- Determine if device is TV via `UiModeManager` for UI tuning.
- Initialize `customerId` from stored `CustomerID`.
- Set up Compose UI (`CustomerIdScreen`) with:
  - Black‑theme background gradient controlled by `ThemeColorManager`.
  - Digit‑based customer ID input.
  - License check button.
  - App version, device ID text.
  - Theme change handler (`onThemeChangeClick` → `showThemeSelectionDialog()`).
- Overlay `AnimatedLoadingOverlay` with `progressDialogMessage`.
- Auto‑trigger `checkLicense()` when a pre‑filled `custId` exists.

### 5.2 License check helpers

- `setCustomerIdField(custId: Int)`:
  - Pads integer to 4 digits and sets `customerId`.
  - Calls `validateInput()`.

- `validateInput(): Boolean`:
  - Ensures:
    - 4 digits.
    - All numeric.
  - Updates `customerIdError` and returns validity.

- `checkLicense()`:
  - Verifies input; if valid, shows a simulated delay (2.5s) then:
    - Calls `checkLicenseFromServer(currentId)` (existing legacy flow).
    - Shows success or failure via `showStatusMessage`.

- `showStatusMessage(message, isSuccess)`:
  - Updates `statusMessage`, `statusIsSuccess`.
  - UI reacts by showing a colored status card in `CustomerIdScreen`.

### 5.3 Compose UI – `CustomerIdScreen(...)`

**(Defined in this file, not reprinted here)**:
- Layout features:
  - Themed gradient background from white → theme color (with intensity).
  - Digit boxes for 4‑digit ID (responsive width/spacing).
  - Large button for license check.
  - License status area (AnimatedVisibility with expand/shrink).
  - Footer showing MAC address and app version.
  - Theme icon / color picker support via `onThemeChangeClick`.

---

## 6. `SplashScreenActivity` – Startup & Navigation

### 6.1 `onCreate`

- Sets up Compose `SplashScreenContent()` with theme from `ThemeColorManager`.
- Initializes:
  - `authSharedPrefs`, `loginSharedPrefs`.
  - `networkViewModel`, `licenseCheckViewModel`.
- Reads license expiry date from `PreferenceHelper.product_license_end`.
- Calls `licenseCheckViewModel.checkLicenseValidity(validTillDate)`.
- Observes `getLicenseStatus()`:
  - Checks current network availability via `Variables.isNetworkEnabled`.
  - Resets certain auth fields in `SharedPreferences`.
  - After a 3‑second `Handler` delay:
    - If license invalid → call `navigateToDeviceRegistration()` (CustomerId).
    - Else → navigate directly to `CustomerIdActivity` (current flow).

### 6.2 `SplashScreenContent()`

- Uses `BoxWithConstraints` + `LocalDensity` for responsive logo size.
- Uses `rememberInfiniteTransition` to pulse the logo (`scaleX/scaleY`).
- Background is a vertical gradient `primary.copy(alpha=0.35f..0.75f)` so it clearly reflects the selected theme.
- Centers `callq_tv_logo` with a subtle breathing animation.

---

## 7. `TokenAnnouncer` – TextToSpeech Queue (Summary)

*(File not shown here, summary only.)*

- Singleton `object` implementing `TextToSpeech.OnInitListener`.
- Maintains:
  - One `TextToSpeech` instance.
  - A queue (`ArrayDeque<String>`) of phrases to announce.
  - An `isSpeaking` flag.
- `ensureInitialized(context, audioLanguage)`:
  - Initializes TTS lazily with locale derived from `audioLanguage`.
  - Attaches `UtteranceProgressListener` to:
    - Detect `onDone`/`onError`.
    - Mark `isSpeaking = false` and trigger the next item.
- `announceToken(context, audioLanguage, counterName, tokenLabel)`:
  - Builds phrase like `"Token 10, please proceed to counter Ortho"`.
  - Immediately triggers `TextToSpeech.speak` with `QUEUE_FLUSH`.
  - This ensures any ongoing announcement is interrupted and the latest token is spoken immediately.
- Guarantees alignment between the visual display and audio by prioritizing the most recent token call.

---

## 8. Theme Management – `ThemeColorManager` (Summary)

- Stores theme preferences in `SharedPreferences`.
- Exposes:
  - `getSelectedThemeColor(context): Int`.
  - `setSelectedThemeColor(context, color: Int)`.
  - `getBackgroundIntensity(context): Float`.
  - `setBackgroundIntensity(context, intensity: Float)`.
  - `createDarkColorScheme(primaryColor: Int)`:
    - Builds a `darkColorScheme` with background/surfaces derived from `primaryColor` with different alpha values.
- Used by:
  - `SplashScreenActivity`, `CustomerIdActivity`, `TokenDisplayActivity`.
  - Compose color picker dialog in `TokenDisplayActivity`.

---

## 9. Database Entities (Summary)

- `TvConfigEntity`:
  - One row per device; core TV configuration.
  - Holds fields like `audioLanguage`, `showAds`, `adInterval`, layout, fonts, colors, `noOfCounters`, `adPlacement`, plus JSON blobs for `ad_files`, `counters`, `shift_details`, `mapped_broker`.

- `MappedBrokerEntity`:
  - Broker configuration per device (host, port, topic, ssid/password).

- `CounterEntity`:
  - One row per logical counter.
  - Ties counter configuration (`name`, `defaultName`, spans, audio URL, etc.) to `deviceId`, `macAddress`, `customerId`.

- `AdFileEntity`:
  - One row per advertisement entry.
  - Fields: `deviceId`, `macAddress`, `customerId`, `position`, `filePath`, `rawJson`.

Each entity has a matching DAO providing:
- `insertAll`/`upsert`.
- Query by device ID or by `(macAddress, customerId)`.
- Delete rows for a specific device + customer (used during refresh).

---

## 10. End‑to‑End Runtime Workflow (Step‑By‑Step)

1. **App launch**
   - `SplashScreenActivity` starts.
   - Composable splash UI shows logo and animated background.
   - License validity checked; after a delay, navigates to `CustomerIdActivity`.

2. **Customer ID & license**
   - `CustomerIdActivity` shows 4‑digit ID UI with black theme and responsive layout.
   - User enters ID; `validateInput()` enforces 4 numeric digits.
   - On “Check License”, `checkLicense()` triggers server validation and may download/install APK updates.
   - On success, `CustomerID` is stored in `AppSharedPreferences.AUTHENTICATION`.

3. **Token display setup**
   - `TokenDisplayActivity` starts.
   - `TokenDisplayScreen`:
     - Reads MAC address and `CustomerID`.
     - Uses `TvConfigRepository` to fetch TV configuration and store in Room.
     - Loads counters, ads, and mapped broker rows from Room.
     - Initializes MQTT via `MqttViewModel` with server URI, client ID, and topic.
     - Starts network status polling loop.

4. **Token display rendering**
   - `TokenDisplayFromConfig`:
     - Draws themed background and header (company name, date/time, MQTT/network status, theme icon).
     - Computes layout type (1 or 2) and whether ads exist.
     - Allocates horizontal space between ad column and counters dynamically, giving any freed counter space to ads.
     - Displays counters either in a horizontal row (type 1) or vertical stack (type 2).
     - Rotates through `adFiles` based on `adInterval`, ordered by `position`, with smooth crossfade animations.

5. **MQTT token updates & TTS**
   - MQTT client receives payloads via `MqttClientManager`.
   - `MqttViewModel.onMessageReceived` parses counter name + token and posts `latestToken`.
   - `TokenDisplayScreen` observes `latestToken` and, when enabled by config, calls `TokenAnnouncer.announceToken`.
   - `TokenAnnouncer` uses a queue to speak each token sequentially, respecting `audioLanguage`.

6. **Dynamic theming**
   - At any moment, user can open theme dialog from `TokenDisplayActivity` header.
   - Adjusting hue/saturation/intensity updates preview.
   - On apply:
     - New primary color and background intensity are stored by `ThemeColorManager`.
     - Activity is recreated and all screens pick up the new theme and gradients.

This document should give you a function‑wise understanding of the current source code and how data flows from backend APIs and MQTT through Room and ViewModels into the Compose UIs on the TV. 

