# CallQTV — Greenfield rebuild specification (master prompt)

Copy this document into another AI session or hand it to a team to implement an application **functionally equivalent** to CallQTV. It consolidates product intent, architecture, MQTT semantics, REST behavior, and QA expectations aligned with this repository (`docs/MASTER_DOCUMENTATION.md`, `README.md`, Gradle, manifest).

---

## 1) Product identity and goals

- **Product:** CallQTV-style Android client — **queue token display** and **digital signage** for clinics, banks, service centers (large TV / IoT screens).
- **Package reference:** `com.softland.callqtv` (`applicationId` / namespace).
- **Core capabilities:**
  - Real-time **MQTT** token updates with a fixed `$...*` protocol plus **`000-` CLR** frames and short-wrapper payloads.
  - **REST** remote configuration, device registration / license validation, MQTT payload log upload.
  - **Room** offline cache: config, counters, ads, devices, payload logs, token history — **cached-first startup**.
  - **Compose** main UI: counters, token history, ad area, overlays (reconnect, pending calls), settings/theme.
  - **TTS + chime** announcements with sequencing, dedupe, and optional **ad audio ducking**.
  - **Ads:** image, GIF/WebP, video (ExoPlayer), YouTube (WebView kiosk), offline sync, round-robin.
  - **Diagnostics** and support export; local error logging.

---

## 2) Platform and build baseline

| Item | Value |
|------|--------|
| minSdk | 21 |
| compileSdk / targetSdk | 35 |
| Java / Kotlin | 17 |
| UI | Jetpack Compose (Material 3), MVVM |
| Main display | Landscape; launcher → splash → registration/customer flows → **singleTask**-style main display activity |

**Libraries (parity):** Room (KTX + kapt/KSP), Retrofit 2 + Gson + OkHttp (logging, retry on 5xx / IO), Eclipse Paho MQTT v3, Media3 ExoPlayer, Coil Compose, Lottie, Lifecycle ViewModel, Coroutines, ZXing (if QR flows required).

**Reference versions in repo:** Compose BOM `2024.12.01`, Kotlin `2.1.0`, Room `2.7.0-alpha12` (or newer compatible), Media3 `1.5.0`, AGP as in root `build.gradle.kts`.

---

## 3) Architecture layers

1. **`ui`** — `SplashScreenActivity`, `CustomerIdActivity`, `TokenDisplayActivity` (main surface: counters, tokens, ads, overlays, settings dialogs).
2. **`viewmodel`** — `TokenDisplayViewModel` (cache-first config, background sync, ad prep, **`configRefreshRequests`** / **`TvConfigRefreshSignal`** for MQTT-driven and **CLR-immediate** `loadData`); `MqttViewModel` (multi-broker lifecycle, validation, fixed/CLR routing, **`resolveCounterIdentityFromSerial`**, announcement queue, CLR clearing, **`TokenUiProcessResult`** `playCueUi` / `speakTokenAnnouncement`, payload log save); **`MqttCounterRouting.kt`** (`findCounterEntityForMqttRoute`); **`KeypadPayloadParser`**; registration VMs.
3. **`data`** — Room **v17** (incl. `mqtt_payload_logs`), entities/DAOs (`AdFileEntity` from `ad_files`), `ApiService` (`fetchTvConfig`, **`uploadMqttPayloadLogs`**), repositories: `TvConfigRepository`, `MqttPayloadLogRepository`, `TokenHistoryRepository`, `TokenRecordRepository`, `AdDownloader`, etc.; **`FlexibleIntDeserializer`** on counter index JSON fields.
4. **`utils`** — `SemanticMqttParser`, `KeypadPayloadParser`, `TokenAnnouncer`, `ThemeColorManager` / **`ThemePrefs`**, **`DiagnosticsExporter`**, `AdDownloader`, logging, media helpers. **`ui`:** `AdViewportSizing`, `AdUnifiedPlayer`, `MediaEngine` in `TokenDisplayActivity`.

---

## 4) REST API behavior

- **Retrofit** with configurable **BASE_URL**; some calls use **`@Url` full URL** (service discovery pattern: composite JSON query for backend URL).
- **Endpoints (conceptual):** login/auth as needed; **device registration / status / mapping**; **`POST` TV config** (JSON → Room transaction); **`POST` upload MQTT logs** to path equivalent to **`/api/external/token-report`** (base + suffix).
- **Payload logging:** Save with **received** timestamp; set **displayed** when UI actually renders; debounced upload; **delete uploaded rows older than 2 days only**; **never delete unuploaded rows**.
- **License client:** separate base URL (live vs demo) from main API if product requires it.

---

## 5) MQTT validation and protocol

### 5.1 Keypad serial extraction (`KeypadPayloadParser`)

- **Fixed:** e.g. `"$0PA-AeCAL0K0001lo-0008*"`.
- **`000-` CLR:** e.g. `"$000-AbCAL0K000101CLR0*"` — after `$000-`, **11-char keypad SN = `body[4..14]`**. **`CLR` position is dynamic** (variable-length token digits before `CLR`). Route digit = **single character immediately before** `CLR`.
- **Short wrapper:** e.g. `"$0Je-AdCAL0k0071010001*"`.

### 5.2 Serial validation

Match extracted serial against **DB-mapped** keypad / `connected_devices` cache; drop invalid payloads.

### 5.3 Fixed payload type (index **4** in fixed frame — `SemanticMqttParser.parseFixedPayload`)

| Type | Behavior |
|------|----------|
| `A`, `E` | DB-only; **no** live token UI update |
| `B` | **Transferred token** — same as A/E (**DB-only**, not on token UI) |
| `C` | Special message: `button_strings` text; **replaces** token area; blink; dedicated TTS path |
| `D`, `-`, normal | Standard token flow; **`D`** = VIP/emergency → always **`ER-{token}`** + TTS **`ER`** (even if counter prefix off) |

### 5.4 UI routing vs persistence

- **Normal fixed / legacy token payloads:** resolve counter from **`keypad_sn` in the frame** (`KeypadPayloadParser` + `SemanticMqttParser.parseFixedPayload`). **Do not** use fixed-frame **index 18** (1-based) as `keypad_index` — it lies inside the 11-character serial.
- **`resolveCounterIdentityFromSerial(serial)`** (no route) → `keypadsJson` entry for that SN → `counters[]` row(s) → **`storageKey`** = prefer **`button_index`**.
- **`CLR`:** route digit = character **immediately before `CLR`**; match `keypad_index` / `button_index` when route is known; else clear all counters under that SN.
- Persistence uses **canonical per-counter keys** so history stays stable across aliases.

### 5.5 On-screen token format

- **`tv_config.token_format`**: `T1`, `T2`, `00`, `000`, etc. Patterns **`T{n}`** pad the numeric token to **n** digits **without** a literal `T` (e.g. `2` + `T1` → `2`; `5` + `T2` → `05`).
- With **`enable_counter_prefix`**, show **`{counter.code}-{formattedToken}`** (e.g. `NU-2`).
- **VIP/emergency (index 4 = `D`):** always **`ER-{formattedToken}`** on **any** slot for that raw token (including previous/history) and spell **`ER`** in TTS, even when **`enable_counter_prefix`** is off (`SemanticMqttParser.isVipEmergency` → `vipEmergencyTokensByKey` / `markVipEmergencyToken` → `tokenUsesVipEmergencyPrefix` → `CounterTokenSlot`).

---

## 6) `CLR` behavior (`MqttViewModel`)

On **valid CLR** (serial passes validation):

1. Build key set: `ResolvedCounterIdentity`, all plausible **`internalTokenMap` keys** for counters under that keypad SN in **`tv_config.keypadsJson`**, plus Room counter aliases.
2. **Route match:** `keypadsJson` → `counters[]` where **`keypad_index` OR `button_index`** matches route digit; fallback to `CounterEntity` fields.
3. **If no row matches route:** clear **all counters listed for that keypad SN** in config (avoid stuck UI).
4. Clear **live map** keys, **`token_history`**, **`vipEmergencyTokensByKey`**, **dedupe / queued-payload** state (route-aware when route known).
5. **Always** post `tokensPerCounter` (or equivalent) after CLR attempt.
6. **TV configuration after CLR:** emit **`TvConfigRefreshSignal(forceImmediate = true)`** on `configRefreshRequests` so the display VM runs **`loadData(..., forceShowOverlay = true)`** immediately (same full config API as Settings **Refresh**), **bypassing** the display VM’s **30s** MQTT-refresh throttle. Non-CLR MQTT refresh uses `forceImmediate = false` and remains subject to **15s** debounce on `MqttViewModel` and **30s** min interval on the collector.
7. Run clear when serial known and CLR validates **even if** structured CLR parse returns null in edge cases.

---

## 7) Runtime UX

- **Startup:** Load cache → render immediately; background sync; loading overlay only if no cache.
- **Config Retry:** `forceShowOverlay` sets loading synchronously; `activeConfigLoadId` avoids race with cancelled jobs; overlay on top of error dialogs.
- **TTS:** “Preparing voice engine…” when audio language changes; early `warmUp` from cached config during `loadData` when announcements enabled.
- **Connectivity:** Multiple brokers; “connected” if **any broker connected** OR **recent MQTT traffic**; reconnect badge (“Connecting to BLUCON…”, try count, timer); **~30s** reconnect cycle.
- **Counter tiles:** Left dot = **dispense**, right = **keypad**; red default → green on activity → red after **~5 min** idle.
- **Grid:** When counter count **> 4**, split into **2 rows or columns**.
- **Special message card:** Multiline: extra padding (scale-aware), **line height ~1.42×** font size, auto-fit uses same line height.

---

## 8) Announcements (`TokenAnnouncer` + `TokenUiProcessResult`)

- **Serialized** under `announcementMutex`; mutex held through TTS **`onDone`** so the **next** token cannot update its tile until the previous announcement finishes.
- **Current token** tile + blink at **chime cue start** (`publishTokenTile` via `onAudioStart` + fallback).
- **`playTokenChime`**: awaited; starts after **`withTimeoutOrNull(12s) { awaitReady() }`** when speech is required (timeout logs warning; **TTS still attempted**). `async { awaitReady() }` begins **before** `processTokenUpdateForKeys` when announcements are enabled.
- **`awaitReady`**: wait for engine **bind**, then optional **`awaitSynthesisPrimeIfNeeded()`** (`primeSynthesis` default true). **`warmUp(performPoke=true)`** returns immediately; prime is async.
- **`warmTokenAnnouncerIfEnabled`**: `warmUp` only (non-blocking on config load); does not block the token collector.
- **Config `LaunchedEffect`**: `launch { awaitReady }` in background for “Preparing voice engine…” overlay.
- **`processTokenUpdateForKeys`** returns **`TokenUiProcessResult`**:
  - **`playCueUi`**: chime + snapshot + blink when map/VIP changed or primary re-call after **>10s**.
  - **`speakTokenAnnouncement`**: TTS only when primary-key announce rules pass and `enable_token_announcement` is on.
- **Dedupe:** identical raw payload within **10s** → single channel event; **re-call after 10s** allowed with fresh blink/TTS.
- **Normal:** space-separated — “Token”, optional **spelled counter prefix**, token label, optional counter name when `enable_counter_announcement` on.
- **VIP/emergency (`D`):** always spell **`ER`** before the token in TTS; on-screen **`ER-{token}`** on current and previous slots regardless of `enable_counter_prefix`.
- **Special (`C` / `__MSG__`):** message + optional counter name (space, no comma).
- **TTS:** NFC normalize, strip controls, collapse whitespace; optional letter-spaced collapse; long ALL-CAPS handling for special messages.
- **Ad sound (`enable_ad_sound`):** `runWithAdvertisementAudioDuckedForSpeech` ducks Exo + YouTube **before** `awaitSynthesisPrimeIfNeeded` and real TTS; restore on end/cancel; skip duck if ad sound off.
- **Synthesis prime:** `SYNTHESIS_PRIME_PHRASE` = **`wellcome`** at **`PRIME_VOLUME` 0.01**. **`finishInitAttempt(true)`** when engine binds; prime **async** (must not block waiters — first token hung otherwise). `synthesisPrimeInFlight` + `pendingAfterPrime` queue overlapping primes. Idle keep-warm **15s**; token re-prime after **60s** idle.
- **`publishTokenTile`:** single publish per token (atomic guard); at chime cue, not after TTS.

---

## 9) Local preferences (`ThemePrefs`)

Device-local (not in server `tv_config` JSON): theme/counter/token colors, notification sound, 24h clock, YouTube/offline ad toggles, etc. SharedPreferences name **`ThemePrefs`**. **`ThemeColorManager`** centralizes most keys; **`ThemeColorManager.colorForMaterialPrimary`** maps stored **`theme_color`** (solid `#…` or **`GRADIENT:#…`**) to a single Compose **`Color`** for Material3 **`primary`** (gradients → **first stop**); **`getBackgroundBrush`** paints **vertical** gradients on counter/token surfaces; **`getTickerStripBackgroundBrush`** paints **horizontal** gradients (or darkened solid blend) on the **scrolling footer** ticker.

**Notification chimes:** `notificationSoundOptions` (~51 keys) + `playSystemTone` in `TokenDisplayActivity`; prefs key **`notification_sound_key`** (default `ding`).

**Settings pickers:** `PresetColorDialog` — brush cache, throttled background warm, TV **`PresetColorSwatchTile`** focus (gold ring / white selected), grid keys **`name_index`** (never hex-only — duplicate hex crash). **`NotificationSoundDialog`** — same focus pattern.

**Token blink mode** (`token_blink_mode`): **`WHOLE_TILE`** (default) vs **`TEXT_ONLY`** — both gated by server **`blink_current_token`**.

---

## 10) Advertisements

- Sort by position; **strict round-robin** for multiple ads (`AdArea`).
- **`AdMediaType`:** Image (Coil), Video (ExoPlayer), YouTube (WebView), Web (WebView or Exo for live).
- **`AdViewportSizing.kt`:** Measure pane with `BoxWithConstraints`; decode images and select video tracks at pane resolution; `MediaEngine.updateViewport`.
- Pane share: **40%** of body (50% if ≤2 counters); `ad_placement` left/right/top/bottom.
- Display: **fit** inside pane (letterboxing OK). No fixed creative resolution required.
- Preload next ad; dual ExoPlayer for video handoff; Coil preload for images.
- YouTube: kiosk, hide chrome, pin video id, SSL/DNS/embed fallbacks; optional disable in settings.
- **Offline ad sync** (`AdDownloader` → `CALLQTV_ADV`).
- Known gaps: DRM streams, blocked YouTube, exotic codecs, broken URLs → skip via watchdog.

---

## 10a) Diagnostics snapshot export (`DiagnosticsExporter`)

- **Entry:** Settings → System → export from `TokenDisplayActivity` (background thread).
- **Staging:** `cacheDir/CALLQTV_EXPORT/snapshot_<timestamp>/` then ZIP; includes DB, logs, config mirrors per current Kotlin implementation.
- **ZIP write order:** (1) **Removable** `getExternalFilesDirs(DIRECTORY_DOWNLOADS)` → `…/CALLQTV_EXPORT/<zip>` when SD/USB is removable and writable; (2) **API 29+** MediaStore `Downloads/CALLQTV_EXPORT/<zip>`; (3) legacy `getExportParent` then **`getAppScopedExportRoot`** with **`tryWriteSnapshotZip`** retries.
- **UX:** Toast shows path; system “read-only” copy errors when moving ZIP to another disk are outside the app.

---

## 11) Android manifest (parity)

Manifest permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `POST_NOTIFICATIONS` (runtime API 33+), `WAKE_LOCK` (APK download), storage (`READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` by API level), `MANAGE_EXTERNAL_STORAGE` (All files access, API 30+), `REQUEST_INSTALL_PACKAGES` (APK update). Runtime gating via `StoragePermissionHelper`; install-unknown-apps via `ApkUpdateHelper`. WebView ads deny camera/mic — not declared. `FileProvider` for APK install and export; `largeHeap`, cleartext per deployment.

---

## 12) MQTT → UI (reference)

- Incoming message → `rawMessageQueue` (max **2000**) → coroutine → **`MqttInboundPayloadRouter`** / **`MqttVerifiedMessageParser`** → **`MqttCounterIdentityResolver.resolve`** (`CounterRouteLookupCache`, 5 min TTL) → **`tokenUpdateChannel`** / **`tokenReplaceChannel`** (capacity **128**, drop-oldest) → Compose **`collect`** + **`announcementMutex`** → chime → **`publishTokensSnapshot()`** (cue start) → TTS → next event → **`tokensPerCounter`** → **`getTokensForCounter`** in grid.
- **`configRefreshRequests`**: capacity **16**, drop-oldest; CLR uses `forceImmediate`.

---

## 13) QA checklist (acceptance)

- Debug build; cached-first startup; early TTS warm when announcements enabled.
- Multi-broker MQTT + reconnect badge behavior.
- CLR: `"$000-AbCAL0K000101CLR0*"`, `"$000-AbCAL0K000303CLR0*"` — correct SN/route clearing; `keypadsJson` must list SN + `counters[]` for predictable resolution.
- `"$0PA-AeCAL0K0001lo-0008*"` → token `8`.
- After CLR, `"$0KJ-AbCAL0K000111-0015*"` → token `15`.
- Type `B` → **no** token tile change (DB-only).
- Ad sound on → duck before prime + real TTS; restore after speech; prime **wellcome** not over full-volume ads.
- Special message multiline readability.
- Payload log retention: uploaded >2 days removed; pending preserved.
- Token blink: whole-tile vs text-only when server blink on; **gradient** app theme: primary accents vs full counter/token brushes vs **horizontal** footer strip.
- Chime on all **`playCueUi`** paths; TTS only when **`speakTokenAnnouncement`**.
- Back-to-back tokens: second tile must not appear until first TTS completes (mutex).
- Idle 2+ min with announcements on: next token speaks without multi-second cold delay (15s idle prime).
- VIP payload (index 4 = `D`) with counter prefix **off**: tile shows **ER-{token}** and TTS spells **ER**; after a normal token, previous slot still **ER-{vip}**.
- Config unavailable Retry: loading overlay visible for full fetch.
- Footer ticker: continuous scroll (no 3s loop pause on footer strip).
- Storage denied on splash/main: no navigation / `loadData` until granted; re-prompt on resume.
- `./gradlew testCallQTVDebugUnitTest`: 44 tests including `CounterRouteLookupCacheTest`.
- Color/sound pickers: no ANR; TV focus on current selection; visible focus ring while navigating.
- Support export / diagnostics; error log file behavior; **CLR** forces **immediate** config `loadData` (verify via network or server state); export ZIP appears on **removable** path when SD/USB mounted, else Downloads / app-scoped per Toast.

---

## 14) Deliverables from implementer

1. Short architecture note (threading, MQTT lifecycle, DB schema).
2. Assumed REST contracts for config, token-report, registration.
3. MQTT spec with examples (fixed, CLR, short).
4. Runnable Android project passing the QA checklist.

---

## 15) Operational note

If `keypadsJson` omits a keypad SN or has empty `counters`, CLR may log **no map keys** — fix server config. Prefer **HTTPS**; use cleartext only when explicitly required.

---

*Generated for CallQTV repository maintenance. Keep in sync with `docs/MASTER_DOCUMENTATION.md` when behavior changes.*
