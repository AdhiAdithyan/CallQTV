# CallQTV — Source Code Guide

Developer index of packages, classes, persistence, and tests. For product behavior see [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md).

**Package root:** `com.softland.callqtv`  
**App version (Gradle):** `1.0.1` (`versionCode` 2)  
**Last aligned with source:** May 2026 (app `1.0.1`, Room v17, `minSdk` 21, storage permission gate, bounded MQTT channels, counter route cache, VIP ER on all history slots, config retry overlay, network retries/telemetry)

---

## 1. Source tree overview

| Area | Path | Kotlin files (approx.) |
|------|------|------------------------|
| Application | `app/src/main/kotlin/com/softland/callqtv/` | `MyApplication.kt`, `AppBackgroundCoordinator.kt`, `StoragePathProvider.kt` |
| UI | `…/ui/` | Activities + `ui/settings/`, `ui/theme/`, `ui/display/` reusable modules |
| UI settings | `…/ui/settings/` | `AppearanceSettingsDialog`, `AppearanceSettingsLauncher`, pickers, registration/status dialogs |
| UI theme | `…/ui/theme/` | `CallQtvSettingsColors`, `CallQtvDimens`, `ColorParsing` |
| UI display | `…/ui/display/` | `TokenDisplayOverlays.kt` (`TokenDisplayBlockingOverlays`), status chips |
| ViewModels | `…/viewmodel/` | **21** (8 VMs + `viewmodel/mqtt/*` helpers, routing, parsers) |
| Data local | `…/data/local/` | **18** |
| Data model | `…/data/model/` | **19** |
| Data network | `…/data/network/` | **2** |
| Data repository | `…/data/repository/` | **10** (+ `MqttClientManager.kt`) |
| Utils | `…/utils/` | **29** (incl. `TokenAnnouncer` / `TokenTtsEngine` / `TokenSpeechPhrasing`) |
| **Main `app` module total** | `…/kotlin/com/softland/callqtv/` | **~139** `.kt` files |
| Unit tests | `app/src/test/kotlin/com/softland/callqtv/` | **10** test classes |

**Main module:** `app/` — AGP 8.7.x, Kotlin 2.1.0, Compose BOM `2024.12.01`, `compileSdk` / `targetSdk` **35**, `minSdk` **21** (API 23+ calls via `*Compat` helpers), Java **17**.

---

## 2. UI (`ui/`)

| File | Purpose |
|------|---------|
| `TokenDisplayActivity.kt` | Activity shell: theme `setContent`, storage gate, `loadData`, `MediaEngine.shutdown()` |
| `TokenDisplayScreen.kt` | `TokenDisplayScreen` — MQTT token collectors, TTS/chime, blocking overlays |
| `TokenDisplayLayout.kt` | `TokenDisplayContent` / body / footer / header badges |
| `TokenDisplayCounters.kt` | `CountersArea`, `CounterBoard`, `CounterTokenSlot`, token grid layout |
| `TokenDisplayTicker.kt` | `ScrollingFooter`, `SeamlessTickerView`, `CounterNameTickerView` |
| `TokenFormatting.kt` | VIP/special tokens, `formatTokenByPattern`, `resolveCountersToDisplay` |
| `TokenCounterLookup.kt` | `getTokensForCounter`, `counterStorageLookupKey` |
| `AdPlayback.kt` | **`AdArea`**, round-robin orchestration, texture host |
| `ui/ads/AdYouTubePlayer.kt` | YouTube WebView player + fallbacks, `WebViewWarmup` |
| `ui/ads/AdExoVideoPlayer.kt` | Exo texture video player, bandwidth helpers |
| `ui/ads/MediaEngine.kt` | Shared ExoPlayer pool, viewport caps, announcement ducking |
| `ui/ads/AdMediaResolver.kt` | **`AdMediaType`**, path/MIME classification, YouTube max duration helper |
| `AdAnnouncementAudio.kt` | TTS ducking for ads (`runWithAdvertisementAudioDuckedForSpeech`) |
| `ui/widget/MarqueeScrollAnimator.kt` | Shared footer/counter-name marquee scroll |
| `viewmodel/mqtt/MqttReconnectPolicy.kt` | MQTT reconnect backoff delays |
| `viewmodel/mqtt/MqttRawPayloadGate.kt` | Fast raw-payload filters before background queue |
| `viewmodel/mqtt/MqttBrokerListenerFactory.kt` | `MqttClientManager.MqttListener` from `MqttBrokerCallbacks` |
| `viewmodel/mqtt/MqttBrokerConnector.kt` | Reattach vs replace existing broker manager |
| `viewmodel/mqtt/MqttDeviceContext.kt` | MAC + customer id for Room scope |
| `viewmodel/mqtt/MqttKeypadSerialRegistry.kt` | Registered keypad SN cache + validation gate |
| `viewmodel/mqtt/MqttCounterIdentityResolver.kt` | Serial/route → storage key; CLR key sets |
| `viewmodel/mqtt/MqttInboundPayloadRouter.kt` | CLR / refresh-only / verified payload routing |
| `viewmodel/mqtt/MqttVerifiedMessageParser.kt` | Fixed + legacy semantic → token UI channels |
| `viewmodel/mqtt/MqttClrTokenOperations.kt` | CLR token-map clear orchestration |
| `TokenFormatting.kt` / `TokenCounterLookup.kt` | VIP/special tokens, `formatTokenByPattern`, counter token lookup |
| `ui/display/TokenDisplayStatusIndicators.kt` | BLUCON / network header chips (`CallQtvStatusColors`) |
| `ui/settings/SettingsHelpDialog.kt` | Reusable settings help bullets |
| `ui/settings/TvDelayedFocusEffect.kt` | TV dialog focus after delay |
| `ui/settings/SettingsComponents.kt` | `GridSettingsItem`, `PortalConfigurationGrid`, adaptive details grid columns |
| `AdUnifiedPlayer.kt` | Routes `AdMediaType` → image (Coil), video (ExoPlayer), YouTube/WebView |
| `AdViewportSizing.kt` | Pane pixel sizing, Coil decode targets, ExoPlayer track/bitrate caps |
| `SplashScreenActivity.kt` | Splash; **`StoragePermissionHelper.runWhenStorageAccessReady`** before license/navigation; `onResume` re-prompts |
| `CustomerIdActivity.kt` | Customer ID entry, registration flow, **`AppearanceSettingsLauncher`** (same settings as main display); APK update; storage gate |
| `OnOrderClickListener.kt` | Legacy callback interface (`com.softland.callqtv.interfaces`) |

**Key composables / symbols (token display package `ui/`):**

| Symbol | Purpose |
|--------|---------|
| `TokenDisplayScreen` | Primary screen; `LaunchedEffect` collectors on `tokenUpdateChannel` / `tokenReplaceChannel` |
| `TokenDisplayContent` / `TokenDisplayFooter` | Header, body, footer layout |
| `ScrollingFooter` / `SeamlessTickerView` | Footer marquee (continuous loop, no inter-loop pause); `CounterNameTickerView` uses `COUNTER_NAME_MARQUEE_RESTART_PAUSE_MS` (3s) for long counter names only |
| `TokenDisplayBlockingOverlays` | TTS prep, config load, approval, license, config unavailable, MQTT retry (`ui/display/`) |
| `AppearanceSettingsLauncher` | Single entry to full settings dialog (registration + main header) |
| `AnimatedLoadingOverlay` | Config load progress; drawn on top of error dialogs when `isLoading` |
| `CountersArea` / `CounterBoard` / `TokenCard` | Counter grid and token cells (incl. special-message layout) |
| `PresetColorDialog` | TV-focusable theme/background pickers (`ui/settings/`) |
| `NotificationSoundDialog` | Notification chime picker |
| Settings focus requesters | Auto-focus first-content only when dialogs open (Display/Audios/Other/Portal/System + Help tabs) — tab-to-tab D-pad navigation is preserved |
| `TokenChimePlayer.playTokenChime` | Awaited chime (`utils/`); `onAudioStart` triggers `publishTokenTile` at cue start |
| `ThemeColorManager` | **`ThemePrefs`** getters/setters (theme, colors, 24h, ad/YouTube flags, blink mode) |
| `publishTokenTile` | `publishTokensSnapshot` + blink at chime; mutex blocks next token until TTS `onDone` |
| `withTimeoutOrNull(12_000)` | Caps per-token `awaitReady` wait (bind) so announce path is not blocked; speech starts after chime-tail delay |
| `runWithAdvertisementAudioDuckedForSpeech` | Duck ads → `awaitSynthesisPrimeIfNeeded` → announce; passes `skipSynthesisPrime` |
| `MediaEngine` | Dual ExoPlayer pool, `updateViewport`, 500MB cache, ducking, adaptive track selector |
| `AdArea` | Round-robin loop, preload `candidateAd`, `BoxWithConstraints` → `viewportPx` |
| `AdVideoPlayer` / `YouTubeAdPlayer` | Texture fit-center; WebView YouTube kiosk |
| `AdMediaType` | `YouTube`, `Video`, `Image`, `Web` (enum in `ui/ads/AdMediaResolver.kt`) |
| `TokenAnnouncementAdAudio` | YouTube WebView volume duck during TTS |
| `getTokensForCounter` | Resolves token list by `button_index`, `keypad_index`, id, name aliases |
| `findCounterEntityForMqttRoute` | From `MqttCounterRouting.kt` — button_index, then keypad_index |
| `VIP_EMERGENCY_COUNTER_PREFIX` | `"ER"` — fixed-protocol index 4 `D`; display + TTS always |
| `tokenUsesVipEmergencyPrefix` | Any slot: raw token in `vipEmergencyRawTokens` set → **`ER-{token}`** |
| `CounterTokenSlot` / `CounterTokenSlotsGrid` | Token grid cells; VIP ER prefix on current **and** previous slots |

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
| `MqttViewModel.kt` | Multi-broker MQTT (~1.5k lines), token map, channels, broker lifecycle; delegates to `viewmodel/mqtt/*` |
| `MqttCounterRouteCache.kt` | **`CounterRouteLookupCache`**, **`MqttCounterRouteKeys`**, **`ResolvedCounterIdentity`** — TTL route cache (5 min), channel capacity constants |
| `MqttCounterRouting.kt` | **`findCounterEntityForMqttRoute`**, `mqttRouteMatchesButtonIndex`, `mqttRouteMatchesKeypadIndex` |
| `KeypadPayloadParser.kt` | Keypad serial extraction (fixed, `000-` CLR, short wrapper) |
| `TokenDisplayViewModel.kt` | Cache-first `loadData` (`activeConfigLoadId`, sync `_isLoading` before job cancel on retry), ads, `warmTokenAnnouncerIfEnabled`, consumer of `configRefreshRequests` |
| `RegistrationViewModel.kt` | Device registration / customer ID |
| `ServiceUrlViewModel.kt` | Service URL discovery |
| `LicenseCheckViewModel.kt` | License validation |
| `DownloadViewModel.kt` | APK download / install intent |
| `NetworkViewModel.kt` / `NetworkLiveData.kt` | Connectivity observation |

### 3.1 MQTT counter identity (important)

Two layers cooperate; do not confuse **`keypad_index`** (config / CLR route digit) with **`button_index`** (token map / UI storage key):

| Stage | Location | Behavior |
|-------|----------|----------|
| Payload → storage key | **`MqttCounterIdentityResolver.resolve`** → **`CounterRouteLookupCache`** (5 min TTL; invalidated with keypad cache / history clear) | **Normal tokens:** match **`keypad_sn` only** in `keypadsJson` (fixed-frame index 18 is **not** `keypad_index`). Pick counter row (single row, or first matching Room entity). **CLR:** optional route digit before `CLR` → match **`keypad_index`** when non-blank. Builds **`storageKey`** preferring **`button_index`**. Uncached path: Room read on IO. |
| Inbound routing | `MqttInboundPayloadRouter` → `MqttVerifiedMessageParser` | CLR / refresh-only / verified payloads; parser enqueues `TokenUiEvent` on bounded channels |
| Storage key → UI row | `findCounterEntityForMqttRoute` | Matches `CounterEntity` by **`button_index` first**, then **`keypad_index`** (so storage keys that are only keypad indices still resolve) |
| Token list lookup | `getTokensForCounter` (`TokenCounterLookup.kt`) | Reads map by button_index, keypad_index, counter id/name, or fuzzy route match |
| On-screen label | `formatTokenByPattern` (`TokenFormatting.kt`), `CounterTokenSlot` in `TokenDisplayCounters.kt` | `T1`/`T2` pad digits only; **`{code}-`** when `enable_counter_prefix`; VIP **`D`** → **`ER-`** on any slot via `tokenUsesVipEmergencyPrefix` + `getVipEmergencyTokensByKey()` |

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

// ResolvedCounterIdentity lives in MqttCounterRouteCache.kt
```

**Channels (bounded, `DROP_OLDEST`):** `tokenUpdateChannel` / `tokenReplaceChannel` — capacity **128** (`MqttCounterRouteKeys.TOKEN_UI_CHANNEL_CAPACITY`); dropped oldest decrements `announcementQueueSize`. `configRefreshRequests` — capacity **16**; `TvConfigRefreshSignal` with `forceImmediate`. **`enqueueTokenUiEvent`** uses `trySend` (non-blocking).

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
| `TvConfigRepository` | Fetch/map/persist TV config; adaptive retries + backoff; `FileLogger` network telemetry (`TvConfigRepoNet`) |
| `MqttClientManager.kt` | Eclipse Paho wrapper; listeners → `MqttViewModel` |
| `MqttPayloadLogRepository` | Save incoming, `markDisplayed`, debounced sync, per-row upload retries, 2-day cleanup on **uploaded** rows only; telemetry tag `MqttPayloadLogRepoNet` |
| `ProjectRepository` | License/registration APIs with per-endpoint retries + http/https failover |
| `ServiceUrlRepository` | Service URL fetch with retries |
| `TokenHistoryRepository` | Persist/load/clear per-counter token history |
| `TokenRecordRepository` | Detailed token records for reporting |
| `AuthRepository`, `ProjectRepository`, `ServiceUrlRepository`, … | Registration and discovery |

---

## 5. Utils (`utils/`)

| File | Purpose |
|------|---------|
| `SemanticMqttParser.kt` | Fixed `$...*` frame: types `A`–`E`, `B` transferred, `C`, `D`, `-`; legacy regex parsers |
| `TokenAnnouncer.kt` | Public facade (~80 lines) delegating to `TokenTtsEngine` / `TokenSpeechPhrasing` |
| `TokenTtsEngine.kt` | TTS bind/warm/announce/shutdown, heartbeat, ducking hooks; timing table below |
| `TokenSpeechPhrasing.kt` | Language normalization, hyphenated tokens, `buildTokenAnnouncementBody` |
| `ThemeColorManager.kt` | `ThemePrefs`, gradients, `notificationSoundOptions`, blink mode, brush caches |
| `KeypadPayloadParser` | Lives in **`viewmodel/`** (not utils) — see §3 |
| `DiagnosticsExporter.kt` | Settings → export ZIP snapshot |
| `PublicCallqtvConfigStorage.kt` | MediaStore / public config tree copy for diagnostics |
| `FileLogger.kt` | Rotating file logs |
| `DatabaseBackup.kt` | DB backup helper |
| `AdDownloader.kt` | Ad file download/cache |
| `PreferenceHelper.kt` | Auth/customer shared prefs keys |
| `Variables.kt` | MAC id, API path constants |
| `StoragePermissionHelper.kt` | Runtime storage + notifications (API 33+); **All files access** (API 30+); `runWhenStorageAccessReady`, `onActivityResumed` |
| `ApkUpdateHelper.kt` | APK install via `FileProvider`; **install unknown apps** settings when needed |
| `NetworkUtil.kt` | Connectivity check (delegates to `NetworkCompat`) |
| `NetworkCompat.kt` | API 21+ network availability / low-bandwidth heuristics |
| `WebViewErrorCompat.kt` | API 23+ `WebResourceError` fields with safe fallbacks |
| `ProcessCompat.kt` | `Process.waitFor` with timeout on API 26+ |
| `UnsafeOkHttpClient.kt` | TLS client for legacy brokers |
| `TokenChimePlayer.kt` | `playTokenChime` + system tones for token preview and MQTT announcements |
| `AnimatedLoadingOverlay.kt` | Loading UI helper |
| `Event.kt`, `DownloadStatus.kt`, `KeyboardUtils.kt` | Small helpers |

**`TokenTtsEngine` timing constants (source of truth):**

| Constant | Value | Role |
|----------|-------|------|
| `HEARTBEAT_INTERVAL_MS` | 3000 | Silent bind keep-alive |
| `IDLE_SYNTHESIS_KEEP_WARM_INTERVAL_MS` | 15000 | Quiet `wellcome` prime while idle |
| `SPEECH_WAKE_IDLE_MS` | 60000 | Token-path re-prime if keep-warm missed |
| `PRIME_DEBOUNCE_MS` | 4000 | Min gap between primes |
| `PRIME_VOLUME` | 0.01 | Nearly silent synthesis prime |
| `SYNTHESIS_PRIME_PHRASE` | `wellcome` | Voice-load warm-up text |

**Bind vs prime:** `handleTtsInitCallback` calls **`finishInitAttempt(true)`** as soon as the engine binds, then runs **`primeSpeechSynthesisOnMain` async**. `warmUp(performPoke=true)` invokes **`onReady(true)`** without waiting for prime. Token collector uses **`withTimeoutOrNull(12_000)`** around `awaitReady`.

---

## 6. Application entry

| File | Purpose |
|------|---------|
| `MyApplication.kt` | Global uncaught handler; suppresses non-fatal MIUI/GMS/integrity noise; `FileLogger.logCrash` |
| `AppBackgroundCoordinator.kt` | App background → stops MQTT/TTS (`TokenAnnouncer.shutdown`) |
| `StoragePathProvider.kt` | `FileProvider` for APK install paths |

**Launcher flow:** `SplashScreenActivity` → (storage permissions) → `CustomerIdActivity` (if needed) → `TokenDisplayActivity` → (storage permissions) → `loadData` / MQTT.

**Android manifest (declared):** `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, storage (`READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` by API), `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`. **Not declared:** camera, microphone, location, boot receiver, foreground service (WebView denies capture requests).

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
| `CounterRouteLookupCacheTest` | Route cache keys, TTL, scope invalidation, negative cache |
| `TvConfigParsingTest` | Gson counter indices as strings (`FlexibleIntDeserializer`) |
| `TokenAnnouncerSpeechTest` | TTS string normalization |
| `TokenFormatTest` | `token_format` padding (`T1`/`T2` without literal `T` prefix) |
| `VipEmergencyTokenPrefixTest` | `tokenUsesVipEmergencyPrefix` for current/previous VIP slots |
| `LicenseDateUtilsTest` | License date parsing |
| `ExampleUnitTest` | Placeholder |

Run: `./gradlew test` or `./gradlew testCallQTVDebugUnitTest`.

---

## 9. Where to change common features

| Feature | Primary files |
|---------|----------------|
| MQTT fixed/CLR parsing | `SemanticMqttParser.kt`, `KeypadPayloadParser.kt`, `MqttInboundPayloadRouter.kt`, `MqttClrTokenOperations.kt` |
| On-screen token label / VIP ER prefix | `formatTokenByPattern`, `CounterTokenSlot`, `tokenUsesVipEmergencyPrefix`, `VIP_EMERGENCY_COUNTER_PREFIX` |
| VIP/emergency MQTT flag | `SemanticMqttParser.FixedPayload.isVipEmergency`, `TokenUiEvent.isVipEmergency`, `vipEmergencyTokensByKey` / `getVipEmergencyTokensByKey()` |
| Config error Retry / loading overlay | `TokenDisplayViewModel.loadData` (`activeConfigLoadId`, `forceShowOverlay`), `TvConfigurationUnavailableScreen`, `AnimatedLoadingOverlay` |
| Footer ticker scroll | `SeamlessTickerView` (continuous); `CounterNameTickerView` (3s pause at loop start) |
| Counter route → UI row | `MqttCounterRouting.kt`, `MqttCounterIdentityResolver.kt`, `MqttCounterRouteCache.kt` |
| Storage / permissions | `StoragePermissionHelper.kt`, `SplashScreenActivity`, `TokenDisplayActivity`, `CustomerIdActivity` |
| Bounded MQTT UI queue | `MqttViewModel` (`createTokenUiChannel`, `enqueueTokenUiEvent`) |
| Token UI update / chime / TTS | `TokenDisplayScreen.kt` (MQTT collectors) |
| Token map / history / CLR keys | `MqttViewModel.kt` (`processTokenUpdateForKeys`); CLR clear via `MqttClrTokenOperations.kt` |
| Config refresh on CLR | `MqttViewModel.requestConfigRefresh`, `TokenDisplayViewModel.loadData` |
| Payload audit upload | `MqttPayloadLogRepository.kt`, `ApiService.uploadMqttPayloadLogs` |
| Token records (server) | `MqttViewModel.saveTokenRecord`, `TokenRecordRepository.kt` |
| Chime sounds | `ThemeColorManager.notificationSoundOptions`, `playSystemTone` |
| TTS phrasing / prime / duck order | `AdAnnouncementAudio.kt` (`runWithAdvertisementAudioDuckedForSpeech`), `TokenAnnouncer.kt` → `TokenTtsEngine.kt` |
| TTS early warm on config load | `TokenDisplayViewModel.kt` (`warmTokenAnnouncerIfEnabled` → `warmUp`); `TokenDisplayScreen` `LaunchedEffect` → `launch { awaitReady }` |
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
