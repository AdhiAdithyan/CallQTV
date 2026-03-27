# CallQTV Master Summary - Engineering, Build, and QA

This document reflects the current codebase implementation and validation approach.

## 1) Architecture Snapshot
- Pattern: MVVM (`ui`, `viewmodel`, `data`, `utils`)
- Persistence: Room (`TvConfig`, counters, ad files, connected devices, mapped broker)
- Networking: Retrofit + OkHttp
  - Shared client for general APIs
  - Dedicated license client with longer timeouts
- Messaging: Eclipse Paho MQTT
- Media: Media3 ExoPlayer + Coil + WebView-based YouTube playback

## 2) Key Components and Responsibilities
- `TokenDisplayActivity`
  - Compose UI host, loading dialogs, settings dialog, ad rendering area
- `TokenDisplayViewModel`
  - Cached-first config loading, background API refresh, ad sync orchestration
- `TvConfigRepository`
  - Config API call, mapping, transactional Room updates, perf logs
- `MqttViewModel` / `MqttClientManager`
  - Broker connection lifecycle, token stream handling, multi-broker reachability mapping
  - Manual retry attempt synchronization via global 30s timeout
  - Faster initial connect path (10s connect timeout + aggressive first-connect retry cadence)
  - Per-counter keypad/dispense indicator maps derived from MQTT activity (button-index keyed)
  - Config refresh trigger when valid MQTT payload contains `CLR`
- `MyApplication`
  - Global uncaught exception handling and system service (Integrity) suppression
- `ProjectRepository` / `RegistrationViewModel`
  - License authentication, registration, and status checks
- `AdDownloader`
  - Offline ad sync, filename/content-type handling, safe download fallback

## 3) Guardrails Implemented in Code
- Network checks via `NetworkUtil.isNetworkAvailable(...)` before:
  - Config API call (`TvConfigRepository`)
  - License API calls (`ProjectRepository`)
  - Offline ad downloads (`AdDownloader`)
- Cached-first rendering to reduce perceived startup/API delay.
- Separate TTS initialization dialog (not mixed with config loading overlay).
- Bounded/truncated response logging for large config payloads.
- Split performance logs for API, mapping, and DB transaction sections.
- Strict layout clipping (`clipToBounds`) and centered rendering for AdArea.
- Ad area is display-only (non-focusable and non-clickable); no remote/touch interaction is required.
- JavaScript bridge + kiosk-mode YouTube playback with autoplay and automatic next-ad transition.
- Ad loop sequencing is strict round-robin when multiple ads exist (no same-ad repeat).
- Candidate ad preloading is image-only; YouTube/video use single visible-surface playback for GPU stability.
- YouTube URL fallback retry (one-shot) on SSL/DNS main-frame failures before skipping.
- Ad sound setting (`enable_ad_sound`) controls both ExoPlayer video ads and YouTube WebView ads.
- MQTT connected status aggregates `any broker connected OR recent MQTT traffic`.
- Counter-name tiles render two inner-corner connectivity dots (left=dispense, right=keypad) with 5-minute stale-to-red watchdog.

## 4) Build and Environment (Current)
From current Gradle files:
- `compileSdk = 35`
- `targetSdk = 35`
- Java/Kotlin target: 17 (`sourceCompatibility`, `targetCompatibility`, `jvmTarget`)
- `minSdk = 26`

## 5) QA Checklist (Current)
1. Build:
   - `./gradlew :app:assembleCallQTVDebug`
2. Static checks:
   - `./gradlew :app:lint`
3. Runtime validation:
   - Cached-first startup with slow API
   - TTS init dialog separation
   - MQTT reconnect and token announcement path
  - Mixed ad format playback (video/image/gif/webp + YouTube)
  - Verify multi-ad loop plays all ads once per cycle without same-ad repetition
  - Verify reconnect badge position (top-center of main content) and Try counter increments
  - Verify per-counter indicator behavior: default RED, GREEN on MQTT activity, and RED after 5-minute inactivity
   - Offline ad sync with/without network
   - License API behavior in offline mode

## 6) Known Technical Notes
- Current `app/build.gradle.kts` still uses older AGP patterns (`applicationVariants`, `base.archivesName`) that may be deprecated in newer AGP versions.
- Gradle properties include several deprecated Android flags; they currently build but should be cleaned up in a planned maintenance pass.

## 7) Reference Files for This Summary
- `app/src/main/kotlin/com/softland/callqtv/ui/TokenDisplayActivity.kt`
- `app/src/main/kotlin/com/softland/callqtv/viewmodel/TokenDisplayViewModel.kt`
- `app/src/main/kotlin/com/softland/callqtv/data/repository/TvConfigRepository.kt`
- `app/src/main/kotlin/com/softland/callqtv/data/repository/ProjectRepository.kt`
- `app/src/main/kotlin/com/softland/callqtv/utils/AdDownloader.kt`
- `app/src/main/kotlin/com/softland/callqtv/utils/AnimatedLoadingOverlay.kt`
- `app/src/main/kotlin/com/softland/callqtv/utils/NetworkUtil.kt`
- `app/build.gradle.kts`
- `gradle.properties`
