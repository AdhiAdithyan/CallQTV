# CallQTV — Architecture and Workflow

Technical architecture, component responsibilities, MQTT semantics, and data flows.

**Canonical reference:** [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md)  
**Flowcharts:** [SRS_FLOWCHARTS_WIREFRAMES.md](./SRS_FLOWCHARTS_WIREFRAMES.md)  
**Requirements:** [SRS.md](./SRS.md)

---

## 1. Technology stack

| Layer | Technology |
|-------|------------|
| UI | Jetpack Compose, Material 3 |
| Language | Kotlin |
| Architecture | MVVM |
| DB | Room |
| API | Retrofit, OkHttp |
| Messaging | Eclipse Paho MQTT |
| Media | Media3 ExoPlayer, Coil, YouTube WebView |
| minSdk / targetSdk | **21** / 35 (API 21–25 via `*Compat` helpers) |

---

## 2. Layered architecture

### 2.1 `ui`

- `SplashScreenActivity`, `CustomerIdActivity`, `TokenDisplayActivity` (all gate on `StoragePermissionHelper` before next step / `loadData`)
- Compose: `TokenDisplayScreen`, `CountersArea`, **`AdArea`** (`BoxWithConstraints`), `ScrollingFooter`, settings dialogs
- `AdUnifiedPlayer.kt`, **`AdViewportSizing.kt`**
- `PresetColorDialog`, `NotificationSoundDialog`, `PresetColorSwatchTile`
- `playTokenChime`, `playSystemTone`, **`MediaEngine`** (`updateViewport`), `TokenAnnouncementAdAudio`

### 2.2 `viewmodel`

| Class | Responsibility |
|-------|----------------|
| `TokenDisplayViewModel` | Cache-first `loadData`, ads, `configRefreshRequests` consumer; `forceImmediate` bypasses 30s MQTT refresh throttle when true |
| `MqttViewModel` | Multi-broker lifecycle, token map, channels, `TokenUiProcessResult`; delegates parse/route/CLR to `viewmodel/mqtt/*` |
| `mqtt/*` helpers | `MqttInboundPayloadRouter`, `MqttVerifiedMessageParser`, `MqttCounterIdentityResolver`, `MqttClrTokenOperations`, `MqttKeypadSerialRegistry` |
| `MqttCounterRouteCache.kt` | `CounterRouteLookupCache`, `MqttCounterRouteKeys`, `ResolvedCounterIdentity` |
| `MqttCounterRouting.kt` | `findCounterEntityForMqttRoute` (button_index, then keypad_index) |
| `KeypadPayloadParser` | Serial extraction (fixed, CLR, short wrapper) |
| Registration VMs | Device/customer flows |

### 2.3 `data`

- Room **v17** entities/DAOs: TV config, counters, ads, devices, **`mqtt_payload_logs`**, token history, token records
- Repositories: `TvConfigRepository`, `MqttPayloadLogRepository`, `TokenHistoryRepository`, `TokenRecordRepository`, etc.
- Retrofit `ApiService` (`fetchTvConfig`, **`uploadMqttPayloadLogs`** → `api/external/token-report`, registration)
- Gson **`FlexibleIntDeserializer`** on API counter index fields (`TvConfigModels`)

### 2.4 `utils`

| Utility | Role |
|---------|------|
| `SemanticMqttParser` | Fixed `$...*` types A–E, actions |
| `KeypadPayloadParser` | Serial extraction (fixed, CLR, short wrapper) |
| `TokenAnnouncer` | TTS bind/warm, idle synthesis keep-warm (15s), announce APIs, 3s silent heartbeat, normalization |
| `ThemeColorManager` | ThemePrefs, brushes, chimes, blink mode |
| `DiagnosticsExporter` | Support ZIP export |
| `StoragePermissionHelper` | Runtime storage, notifications, all-files access; startup/main gate |
| `NetworkCompat` / `WebViewErrorCompat` / `ProcessCompat` | API 21+ safe network/WebView/process helpers |

---

## 3. Core components

### 3.1 `TokenDisplayActivity`

Main surface: counter grid, ads, scrolling footer, overlays (reconnect, pending calls), settings. Hosts `MaterialTheme` from `theme_color` pref.

### 3.2 `TokenDisplayViewModel`

- `loadData(mqttViewModel, forceShowOverlay)` — full config sync; **`activeConfigLoadId`** prevents stale cancelled jobs from clearing loading; **`forceShowOverlay`** sets `_isLoading` before cancelling prior job
- Subscribes to `MqttViewModel.configRefreshRequests` (`TvConfigRefreshSignal`)
- CLR / Retry / Settings Refresh: `forceShowOverlay = true` → **`AnimatedLoadingOverlay`** on top of error states

### 3.3 `MqttViewModel`

- `internalTokenMap` + `_tokensPerCounter` LiveData
- `vipEmergencyTokensByKey` — `Set` of raw VIP token values per counter key (`getVipEmergencyTokensByKey()`)
- `tokenUpdateChannel` / `tokenReplaceChannel` (`TokenUiEvent`, capacity **128**, drop-oldest)
- `configRefreshRequests` (capacity **16**, drop-oldest; CLR uses `forceImmediate = true`, no 15s debounce gate)
- `CounterRouteLookupCache` — 5 min TTL for serial+route → `ResolvedCounterIdentity`
- `processTokenUpdateForKeys` → `TokenUiProcessResult(playCueUi, speakTokenAnnouncement)`
- `replaceTokenForKeys` — type `C` full replace
- CLR clears map + history + dedupe; always posts snapshot; immediate config refresh

### 3.4 Repositories

- **`TvConfigRepository`**: fetch/map/persist TV config; adaptive retries + `FileLogger` telemetry
- **`ProjectRepository`**, **`ServiceUrlRepository`**: registration/discovery with retries
- **`MqttPayloadLogRepository`**: received/displayed timestamps, upload with per-row retries, 2-day cleanup on uploaded only
- **`TokenHistoryRepository`**: newest-first per counter key; clear per counter or all

---

## 4. MQTT → UI pipeline

```
MqttClientManager.onMessageReceived
  → rawMessageQueue (bounded)
  → Default dispatcher: `MqttInboundPayloadRouter` → CLR / refresh / `MqttVerifiedMessageParser`
  → SemanticMqttParser
  → tokenUpdateChannel | tokenReplaceChannel (bounded 128, drop-oldest if UI lags)
  → `MqttCounterIdentityResolver.resolve` (CounterRouteLookupCache, else Room on IO)
  → TokenDisplayScreen LaunchedEffect (announcementMutex)
  → resolveCounterIdentityFromSerial(keypad SN; CLR may use route-before-CLR)
  → findCounterEntityForMqttRoute (button_index, then keypad_index)
  → announcementMutex: async awaitReady (if announcements on) → processTokenUpdateForKeys (publishImmediately=false)
  → playTokenChime → publishTokensSnapshot + blink at cue start (current token)
  → runWithAdvertisementAudioDuckedForSpeech → TTS until onDone (next token blocked on mutex)
  → tokensPerCounter → CountersArea (getTokensForCounter)
```

---

## 5. Fixed protocol (index 4)

| Type | UI |
|------|-----|
| `A`, `E` | DB-only |
| `B` | Transferred token, DB-only |
| `C` | Replace token area, special message |
| `D`, `-`, normal | Standard flow; **`D`** → VIP/emergency, always **`ER-`** on tile + TTS |

---

## 6. CLR workflow

1. Validate keypad serial against mapped devices.
2. Parse route = character before `CLR` in `000-` frame (SN = `body[4..14]`).
3. Build key set: `ResolvedCounterIdentity` + all `keypadsJson` aliases for SN.
4. Clear `internalTokenMap`, `token_history`, `vipEmergencyTokensByKey`, dedupe keys.
5. `publishTokensSnapshot()`.
6. `requestConfigRefresh(forceImmediate = true)`.

If route does not match any `counters[]` row, clear **all** counters under that keypad SN.

---

## 7. Announcement workflow

- **`playCueUi`**: map changed, VIP overlay changed, or primary re-call after >10s → chime + UI + blink.
- **`speakTokenAnnouncement`**: primary-key move/re-call rules → TTS if `enable_token_announcement`.
- **`announcementMutex`**: one token at a time; mutex released only after TTS `onDone` (when speaking) so the **next** tile cannot update mid-announcement.
- **Current token** UI: `publishTokensSnapshot` at chime cue (`onAudioStart` + fallback), not after speech.
- Chime: counter URL → global URL → `notification_sound_key` system tone; `playTokenChime` is **awaited** (not fire-and-forget).
- **Chime → TTS:** `async { awaitReady() }` before map update; **`withTimeoutOrNull(12s)`** before chime when speaking; then duck (if ad sound) → `awaitSynthesisPrimeIfNeeded()` (if needed) → `announceMessage` / `announceTokenCall`.
- **Engine bind vs prime:** `finishInitAttempt(true)` on bind; synthesis prime async so token path is not blocked.
- **Idle keep-warm:** heartbeat **3 s** (silent bind) + quiet prime **15 s** (`keepSynthesisWarmDuringIdle`).
- **Early warm:** `warmTokenAnnouncerIfEnabled` → `warmUp(performPoke=true)`; UI `LaunchedEffect` → `launch { awaitReady }` (background).
- **VIP/emergency TTS:** index 4 **`D`** → always spell **`ER`** (`pair.isVipEmergency`), independent of `enable_counter_prefix`.

## 7.1 Token display (`token_format`)

- **`formatTokenByPattern`:** `T1` / `T2` → zero-pad only (no literal `T`).
- **`enable_counter_prefix`:** `{code}-{token}` on counter tiles (e.g. `NU-2`).
- **VIP/emergency (`D`):** `MqttViewModel.markVipEmergencyToken` adds raw token to `vipEmergencyTokensByKey`; `CounterTokenSlot` shows **`ER-{token}`** on **any** slot where `tokenUsesVipEmergencyPrefix` is true (including previous tokens after a normal token arrives).

---

## 8. Local preferences workflow

- User changes theme/counter/token hex in `PresetColorDialog` → `ThemePrefs` → `MaterialTheme` / brushes / footer strip update.
- `getBackgroundBrush` (vertical) for counters/tokens; `getTickerStripBackgroundBrush` (horizontal) for footer.
- Footer **`SeamlessTickerView`**: continuous marquee (no loop pause). Counter **`CounterNameTickerView`**: 3s pause at loop start for readability.
- Settings dialog TV-focus orchestration:
  - First actionable content control is focused per tab (not default `Close`)
  - Portal + Help details use explicit focusable tiles with focus ring
  - Details grids use adaptive columns (`GridCells.Adaptive(minSize = 220.dp)`) so large screens show 3+ columns when space allows

---

## 9. REST API (conceptual)

- TV configuration POST → Room
- Token payload report upload (`/api/external/token-report` suffix pattern)
- Device registration / license endpoints per deployment
- Service discovery via composite URL query where configured

---

## 10. MQTT payload examples

| Example | Expected behavior |
|---------|-------------------|
| `$000-AbCAL0K000101CLR0*` | Clear counter route 1 for SN `AbCAL0K0001`; immediate config refresh |
| `$000-AbCAL0K000303CLR0*` | Clear route 3 for SN `AbCAL0K0003` |
| `$0PA-AeCAL0K0001lo-0008*` | Token `8` on mapped counter |
| `$0KJ-AbCAL0K000111-0015*` | Token `15` after prior CLR |
| Type `B` fixed frame | DB-only, no tile change |
| `$0AGCAdCAL0K0004u5-0001*` | Special message replace + TTS |

---

## 11. Advertisement workflow

```
ad_files (API) → Room AdFileEntity
  → TokenDisplayBody: adWeight 40% or 50%, ad_placement
  → AdArea measures pane (AdViewportPx)
  → MediaEngine.updateViewport + Coil preload (images)
  → visible AdUnifiedPlayer + hidden preload slot
  → Video: dual ExoPlayer; Image: viewport-sized Coil; YouTube: WebView
  → onReady / onEnded → round-robin (ad_interval for static types)
```

See [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) §3.10 for supported formats, pane sizing, and limitations.

---

## 12. Unit tests (JVM)

| Test | Module |
|------|--------|
| `SemanticMqttParserTest` | Fixed protocol types |
| `KeypadPayloadParserTest` | Serial + CLR frames |
| `MqttCounterRoutingTest` | Counter entity resolution |
| `CounterRouteLookupCacheTest` | Route cache TTL, scope, keys |
| `TvConfigParsingTest` | API JSON / flexible ints |
| `TokenAnnouncerSpeechTest`, `TokenFormatTest`, `VipEmergencyTokenPrefixTest` | Speech / display / VIP ER |

Run: `./gradlew testCallQTVDebugUnitTest` (**44** tests).

---

**`TokenAnnouncer` (idle / prime):** `HEARTBEAT_INTERVAL_MS` 3s · `IDLE_SYNTHESIS_KEEP_WARM_INTERVAL_MS` 15s · `SPEECH_WAKE_IDLE_MS` 60s · `PRIME_VOLUME` 0.01 · phrase `wellcome`.

*Derived from CallQTV May 2026 source (app `1.0.1` / `versionCode` 2, Room v17, `minSdk` 21, permission gate, bounded channels, route cache). Update when [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) changes.*
