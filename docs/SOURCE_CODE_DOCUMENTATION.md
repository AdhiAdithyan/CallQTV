# CallQTV — Source Code Guide

Developer index of packages, classes, persistence, and tests. For product behavior see [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md).

**Package root:** `com.softland.callqtv`  
**App version (Gradle):** `1.0.1` (`versionCode` 2)  
**Last aligned with source:** May 2026

---

## 1. Source tree overview

| Area | Path | Kotlin files (approx.) |
|------|------|------------------------|
| Application | `app/src/main/kotlin/com/softland/callqtv/` | `MyApplication.kt`, `StoragePathProvider.kt` |
| UI | `…/ui/` | 6 (+ large `TokenDisplayActivity.kt` ~6.4k lines) |
| ViewModels | `…/viewmodel/` | 11 |
| Data local | `…/data/local/` | 18 |
| Data model | `…/data/model/` | 22 |
| Data network | `…/data/network/` | 2 |
| Data repository | `…/data/repository/` | 11 |
| Utils | `…/utils/` | 16 |
| Unit tests | `app/src/test/kotlin/com/softland/callqtv/` | 7 test classes |

**Main module:** `app/` — AGP 8.7.x, Kotlin 2.1.0, Compose BOM `2024.12.01`, `compileSdk` / `targetSdk` **35**, `minSdk` **26**, Java **17**.

---

## 2. UI (`ui/`)

| File | Purpose |
|------|---------|
| `TokenDisplayActivity.kt` | Main TV display: Compose host, settings, **`AdArea`** / **`MediaEngine`**, MQTT collectors, theme, chime/TTS, diagnostics |
| `AdUnifiedPlayer.kt` | Routes `AdMediaType` → image (Coil), video (ExoPlayer), YouTube/WebView |
| `AdViewportSizing.kt` | Pane pixel sizing, Coil decode targets, ExoPlayer track/bitrate caps |
| `SplashScreenActivity.kt` | Splash; applies `ThemeColorManager` / `theme_color` from prefs |
| `CustomerIdActivity.kt` | Customer ID entry, registration flow, APK update install |
| `OnOrderClickListener.kt` | Legacy callback interface (`com.softland.callqtv.interfaces`) |

**Key composables / symbols inside `TokenDisplayActivity.kt`:**

| Symbol | Purpose |
|--------|---------|
| `TokenDisplayScreen` | Primary screen; `LaunchedEffect` collectors on `tokenUpdateChannel` / `tokenReplaceChannel` |
| `TokenDisplayContent` / `TokenDisplayFooter` | Header, body, footer layout |
| `ScrollingFooter` / `SeamlessTickerView` | Marquee; `getTickerStripBackgroundBrush` |
| `CountersArea` / `CounterBoard` / `TokenCard` | Counter grid and token cells (incl. special-message layout) |
| `PresetColorDialog` / `PresetColorSwatchTile` | TV-focusable theme/background pickers |
| `NotificationSoundDialog` | Notification chime picker |
| `playTokenChime` / `playSystemTone` | Chime playback |
| `runWithAdvertisementAudioDuckedForSpeech` | Duck ads → `awaitSynthesisPrimeIfNeeded` → announce; passes `skipSynthesisPrime` |
| `MediaEngine` | Dual ExoPlayer pool, `updateViewport`, 500MB cache, ducking, adaptive track selector |
| `AdArea` | Round-robin loop, preload `candidateAd`, `BoxWithConstraints` → `viewportPx` |
| `AdVideoPlayer` / `YouTubeAdPlayer` | Texture fit-center; WebView YouTube kiosk |
| `AdMediaType` | `YouTube`, `Video`, `Image`, `Web` (enum in `TokenDisplayActivity.kt`) |
| `TokenAnnouncementAdAudio` | YouTube WebView volume duck during TTS |
| `getTokensForCounter` | Resolves token list by `button_index`, `keypad_index`, id, name aliases |
| `findCounterEntityForMqttRoute` | From `MqttCounterRouting.kt` — button_index, then keypad_index |

### 2.1 Advertisement pipeline

```
tv_config.ad_files[] → AdFileEntity (Room)
  → TokenDisplayViewModel / TokenDisplayBody (show_ads, ad_weight 40–50%)
  → AdArea (BoxWithConstraints → AdViewportPx)
      → MediaEngine.updateViewport(w, h)
      → resolveAdMediaType(path)
      → AdUnifiedPlayer(viewport, mediaType)
          → Image: Coil @ decodeTarget + preload
          → Video: ExoPlayer + applyVideoTrackConstraints
          → YouTube/Web: shared WebView
      → onReady → visible; onEnded/error → round-robin / skip
```

**Key APIs:** `isAdImage`, `isAdVideo`, `isLikelyLiveStreamUrl`, `applyAdaptiveVideoTrackCap`, `applyFitCenterTransform`.

---

## 3. ViewModels (`viewmodel/`)

| Class / file | Purpose |
|--------------|---------|
| `MqttViewModel.kt` | Multi-broker MQTT, keypad validation, `parseMqttMessage`, CLR, token map, channels, `TokenUiProcessResult`, payload log save, config refresh signals |
| `MqttCounterRouting.kt` | **`findCounterEntityForMqttRoute`**, `mqttRouteMatchesButtonIndex`, `mqttRouteMatchesKeypadIndex` |
| `KeypadPayloadParser.kt` | Keypad serial extraction (fixed, `000-` CLR, short wrapper) |
| `TokenDisplayViewModel.kt` | Cache-first `loadData`, ads, `warmTokenAnnouncerIfEnabled`, consumer of `configRefreshRequests` |
| `RegistrationViewModel.kt` | Device registration / customer ID |
| `ServiceUrlViewModel.kt` | Service URL discovery |
| `LicenseCheckViewModel.kt` | License validation |
| `DownloadViewModel.kt` | APK download / install intent |
| `NetworkViewModel.kt` / `NetworkLiveData.kt` | Connectivity observation |

### 3.1 MQTT counter identity (important)

Two layers cooperate; do not confuse **`keypad_index`** (config / CLR route digit) with **`button_index`** (token map / UI storage key):

| Stage | Location | Behavior |
|-------|----------|----------|
| Payload → storage key | `MqttViewModel.resolveCounterIdentityFromSerial` | **Normal tokens:** match **`keypad_sn` only** in `keypadsJson` (fixed-frame index 18 is **not** `keypad_index`). Pick counter row (single row, or first matching Room entity). **CLR:** optional route digit before `CLR` → match **`keypad_index`** when non-blank. Builds **`storageKey`** preferring **`button_index`**. |
| Storage key → UI row | `findCounterEntityForMqttRoute` | Matches `CounterEntity` by **`button_index` first**, then **`keypad_index`** (so storage keys that are only keypad indices still resolve) |
| Token list lookup | `getTokensForCounter` | Reads map by button_index, keypad_index, counter id/name, or fuzzy route match |
| On-screen label | `formatTokenByPattern` in `TokenDisplayActivity.kt` | `token_format` **`T1`/`T2`** = pad digits only (no literal `T`); optional **`{code}-`** prefix when `enable_counter_prefix` is on |

**Key types (`MqttViewModel.kt`):**

```kotlin
data class TokenUiEvent(
    val counter: String,      // storage route key (usually button_index string)
    val token: String,
    val payload: String,
    val isVipEmergency: Boolean = false,
)

data class TokenUiProcessResult(
    val playCueUi: Boolean,
    val speakTokenAnnouncement: Boolean,
)

private data class ResolvedCounterIdentity(
    val storageKey: String,   // internalTokenMap key
    val counterLabel: String, // logs / token_records
)
```

**Channels:** `tokenUpdateChannel`, `tokenReplaceChannel`, `configRefreshRequests` (`TvConfigRefreshSignal` with `forceImmediate`).

---

## 4. Data layer (`data/`)

### 4.1 Room — `AppDatabase` (version **17**)

| Entity | Table | Notes |
|--------|-------|-------|
| `TvConfigEntity` | `tv_config` | Includes `keypads_json`, scroll colors, announcement flags |
| `CounterEntity` | `counters` | `keypad_index`, `button_index`, dispenser fields |
| `MappedBrokerEntity` | mapped brokers | MQTT broker mapping |
| `AdFileEntity` | ad files | Offline/cached ads |
| `TokenHistoryEntity` | `token_history` | Newest-first history per counter key |
| `ConnectedDeviceEntity` | connected devices | KEYPAD / BROKER rows |
| `TokenRecordEntity` | token records | Daily cleanup; server audit |
| `MqttPayloadLogEntity` | `mqtt_payload_logs` | Received/displayed/uploaded flags (v16→17 migration) |

**DAOs:** `TvConfigDao`, `CounterDao`, `MappedBrokerDao`, `AdFileDao`, `TokenHistoryDao`, `ConnectedDeviceDao`, `TokenRecordDao`, `MqttPayloadLogDao`.

**Migrations registered:** `10→11`, `13→14`, `15→16`, `16→17`. Dev builds also use `fallbackToDestructiveMigration()`.

### 4.2 API models (`data/model/`)

| File | Purpose |
|------|---------|
| `TvConfigModels.kt` | `TvConfigResponse`, `CounterConfig`, `KeypadConfig`, `ButtonStringItem`, scroll config |
| `FlexibleIntDeserializer.kt` | Gson: JSON number **or** string → `Int?` on counter index fields |
| `MqttPayloadUploadModels.kt` | `TokenReportRequest`, `GenericApiResponse` |
| `DeviceRegistration*.kt`, `Login*.kt`, … | Registration / license / service URL DTOs |

### 4.3 Network (`data/network/`)

| File | Purpose |
|------|---------|
| `ApiService.kt` | Retrofit: login, service URL, license, device registration/mapping, **`fetchTvConfig`**, **`uploadMqttPayloadLogs`** |
| `RetrofitClient.kt` | Client factory, retries |

**Upload URL pattern:** `{base_url}api/external/token-report` (see `MqttPayloadLogRepository`).

### 4.4 Repositories (`data/repository/`)

| Repository | Purpose |
|------------|---------|
| `TvConfigRepository` | Fetch/map/persist TV config transactionally; persists `keypadsJson` |
| `MqttClientManager.kt` | Eclipse Paho wrapper; listeners → `MqttViewModel` |
| `MqttPayloadLogRepository` | Save incoming, `markDisplayed`, debounced sync, 2-day cleanup on **uploaded** rows only |
| `TokenHistoryRepository` | Persist/load/clear per-counter token history |
| `TokenRecordRepository` | Detailed token records for reporting |
| `AuthRepository`, `ProjectRepository`, `ServiceUrlRepository`, … | Registration and discovery |

---

## 5. Utils (`utils/`)

| File | Purpose |
|------|---------|
| `SemanticMqttParser.kt` | Fixed `$...*` frame: types `A`–`E`, `B` transferred, `C`, `D`, `-`; legacy regex parsers |
| `TokenAnnouncer.kt` | TTS singleton: `warmUp`, `awaitReady`, `awaitSynthesisPrimeIfNeeded`, quiet prime `"wellcome"`, `announceMessage` / `announceTokenCall`, heartbeat, normalization, multi-language |
| `ThemeColorManager.kt` | `ThemePrefs`, gradients, `notificationSoundOptions`, blink mode, brush caches |
| `KeypadPayloadParser` | Lives in **`viewmodel/`** (not utils) — see §3 |
| `DiagnosticsExporter.kt` | Settings → export ZIP snapshot |
| `PublicCallqtvConfigStorage.kt` | MediaStore / public config tree copy for diagnostics |
| `FileLogger.kt` | Rotating file logs |
| `DatabaseBackup.kt` | DB backup helper |
| `AdDownloader.kt` | Ad file download/cache |
| `PreferenceHelper.kt` | Auth/customer shared prefs keys |
| `Variables.kt` | MAC id, API path constants |
| `NetworkUtil.kt` | Connectivity check |
| `UnsafeOkHttpClient.kt` | TLS client for legacy brokers |
| `AnimatedLoadingOverlay.kt` | Loading UI helper |
| `Event.kt`, `DownloadStatus.kt`, `KeyboardUtils.kt` | Small helpers |

---

## 6. Application entry

| File | Purpose |
|------|---------|
| `MyApplication.kt` | Global uncaught handler; suppresses non-fatal MIUI/GMS/integrity noise; `FileLogger.logCrash` |
| `StoragePathProvider.kt` | `FileProvider` for APK install paths |

**Launcher flow:** `SplashScreenActivity` → `CustomerIdActivity` (if needed) → `TokenDisplayActivity`.

---

## 7. Local preferences (`ThemePrefs`)

| Key | Description |
|-----|-------------|
| `theme_color` | Solid or `GRADIENT:#hex1,#hex2,…` |
| `counter_bg_color` / `token_bg_color` | Counter/token area backgrounds |
| `notification_sound_key` | Chime id (`notificationSoundOptions`, ~51 entries) |
| `token_blink_mode` | `tile` or `text` |
| `enable_ad_sound` | Ad audio; enables TTS ducking when on |
| `use_24_hour_format` | Clock |
| YouTube / offline ad toggles | See settings in `TokenDisplayActivity` |

---

## 8. Unit tests (`app/src/test/`)

| Test class | Covers |
|------------|--------|
| `SemanticMqttParserTest` | Fixed types `-`, `D`, `B` (DB-only) |
| `KeypadPayloadParserTest` | Serial extraction, CLR `000-` frames |
| `MqttCounterRoutingTest` | `findCounterEntityForMqttRoute` button + keypad fallback |
| `TvConfigParsingTest` | Gson counter indices as strings (`FlexibleIntDeserializer`) |
| `TokenAnnouncerSpeechTest` | TTS string normalization |
| `TokenFormatTest` | `token_format` padding (`T1`/`T2` without literal `T` prefix) |
| `ExampleUnitTest` | Placeholder |

Run: `./gradlew test` or `./gradlew testCallQTVDebugUnitTest`.

---

## 9. Where to change common features

| Feature | Primary files |
|---------|----------------|
| MQTT fixed/CLR parsing | `SemanticMqttParser.kt` (no `routeIndex` on `FixedPayload`), `KeypadPayloadParser.kt`, `MqttViewModel.kt` |
| On-screen token label | `formatTokenByPattern`, `CounterTokenSlot` in `TokenDisplayActivity.kt` |
| Counter route → UI row | `MqttCounterRouting.kt`, `MqttViewModel.resolveCounterIdentityFromSerial` |
| Token UI update / chime / TTS | `TokenDisplayActivity.kt` (`TokenDisplayScreen` collectors) |
| Token map / history / CLR keys | `MqttViewModel.kt` (`processTokenUpdateForKeys`, `clearTokensForResolvedCounter`) |
| Config refresh on CLR | `MqttViewModel.requestConfigRefresh`, `TokenDisplayViewModel.loadData` |
| Payload audit upload | `MqttPayloadLogRepository.kt`, `ApiService.uploadMqttPayloadLogs` |
| Token records (server) | `MqttViewModel.saveTokenRecord`, `TokenRecordRepository.kt` |
| Chime sounds | `ThemeColorManager.notificationSoundOptions`, `playSystemTone` |
| TTS phrasing / prime / duck order | `TokenDisplayActivity.kt` (`runWithAdvertisementAudioDuckedForSpeech`), `TokenAnnouncer.kt` |
| TTS early warm on config load | `TokenDisplayViewModel.kt` (`warmTokenAnnouncerIfEnabled`) |
| Theme / gradients | `ThemeColorManager.kt`, `TokenDisplayActivity` theme state |
| TV color/sound pickers | `PresetColorDialog`, `PresetColorSwatchTile`, `NotificationSoundDialog` |
| Ads / viewport sizing | `AdArea`, `AdViewportSizing.kt`, `MediaEngine`, `AdUnifiedPlayer.kt`, `AdDownloader.kt` |
| Support export | `DiagnosticsExporter.kt`, `PublicCallqtvConfigStorage.kt` |
| TV config API → Room | `TvConfigRepository.kt`, `TvConfigModels.kt` |

---

## 10. Related documentation

| Document | Use for |
|----------|---------|
| [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) | Full behavior, MQTT examples, changelog |
| [ARCHITECTURE_AND_WORKFLOW.md](./ARCHITECTURE_AND_WORKFLOW.md) | Layer diagrams and workflows |
| [QA_VALIDATION_CHECKLIST.md](./QA_VALIDATION_CHECKLIST.md) | Device acceptance tests |
| [REBUILD_PROMPT.md](./REBUILD_PROMPT.md) | Greenfield handoff |

---

*Update this file when packages, DB version, or primary classes change. Prefer updating [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) first for behavior.*
