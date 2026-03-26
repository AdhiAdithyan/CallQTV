# CallQTV Master Summary - Product, UX, and Runtime Flows

This document reflects the current implementation in source code.

## 1) Product Overview
CallQTV is an Android TV queue display app with:
- Real-time token updates over MQTT
- Config-driven layout/theme/audio behavior from backend
- Token chime + optional TTS announcements
- Advertisement playback (online first, offline sync optional)
- License-based registration and validation

## 2) Current User-Facing Behavior
- Cached configuration loads first to minimize startup waiting.
- Configuration API sync runs in background and updates Room + UI.
- TTS initialization uses a separate dialog (`Preparing voice engine...`).
- Settings tabs order is: `Display`, `Audios`, `Other`, `System`.
- Offline ads default to enabled (`PreferenceHelper.isOfflineAdsEnabled` default `true`).
- Info labels in System tab are single-line and readability-tuned.

## 3) Advertisement Behavior (Current)
- Ads are sorted by configured position.
- Media type is resolved via:
  - URL extension checks
  - MIME detection fallback for extension-less/signed/local files
- Media Rendering path:
  - YouTube -> YouTube WebView-based player with Kiosk CSS (Clean UI)
  - Video -> ExoPlayer (Standard local/remote video)
  - Image/GIF/WebP -> Coil image renderer
- Ad area is visual-only: it does not accept focus, clicks, or user interaction.
- **YouTube Automated Looping**:
  - Auto-plays via injected JS (`video.play()`), with periodic re-check for DOM/video replacement.
  - Detects completion via JS `ended` event + JS bridge callback (`CallQTVBridge.onAdEnded`).
  - Title fallback (`AD_ENDED`) is retained as a secondary path.
  - Safety auto-advance timeout prevents ad-loop stalls if YouTube callbacks do not fire.
- **Ad Sound Control**:
  - A single setting (`enable_ad_sound`) controls ad audio for both ExoPlayer video ads and YouTube ads.
  - When disabled, YouTube playback is forced muted and volume is kept at zero through JS re-application.
- In offline mode, ad files are synced to local storage (`Downloads/CALLQ_ADV`) and switched after sync.
- If offline/no network, downloads are skipped safely and current list is retained.

## 4) Core Runtime Flows

### 4.1 Startup + Config + TTS
```mermaid
flowchart TD
    A[App launch] --> B[Load cached config/counters/ads/devices]
    B --> C{Cache available?}
    C -->|Yes| D[Render cached UI immediately]
    C -->|No| E[Show config loading overlay]
    D --> F[Call config API in background]
    E --> F
    F --> G{Network available?}
    G -->|No| H[Skip API call, keep cache]
    G -->|Yes| I[Fetch config, map, store transactionally]
    I --> J[Refresh UI from DB]
    J --> K[Initialize TTS by language]
    K --> L[Show separate TTS initialization dialog]
```

### 4.2 MQTT Token Processing + Announcement
```mermaid
flowchart TD
    A[MQTT token message] --> B[Validate counter mapping]
    B -->|Invalid| C[Drop and log]
    B -->|Valid| D[Update token history]
    D --> E[Trigger blink/re-call indicator]
    E --> F[Play notification chime]
    F --> G{Token announcement enabled?}
    G -->|Yes| H[Speak token via TokenAnnouncer]
    G -->|No| I[Skip TTS]
```

### 4.3 Ad Rotation
```mermaid
flowchart TD
    A[Read ad list] --> B[Sort by position]
    B --> C[Resolve media type]
    C --> D{YouTube?}
    D -->|Yes| E[YouTube player]
    D -->|No| F{Video?}
    F -->|Yes| G[ExoPlayer]
    F -->|No| H[Coil image renderer]
    E --> I[JS ended/safety timeout -> Next ad]
    G --> I
    H --> I[Interval-based Next ad]
```

## 5) Acceptance Criteria
- Cached UI appears without waiting for long config API calls.
- API and ad-download calls are skipped when network is unavailable.
- TTS setup is presented separately from config loading.
- MQTT token updates continue even during background config sync.
- Ad playback supports mixed formats and degrades safely on failures.
