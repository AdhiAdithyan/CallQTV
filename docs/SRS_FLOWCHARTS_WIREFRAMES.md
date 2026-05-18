# CallQTV — Logic Flowcharts

Process flows for startup, MQTT, announcements, and payload upload. Pair with [WIREFRAMES.md](./WIREFRAMES.md) for layout.

**Canonical reference:** [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md)

---

## 1. Startup and configuration

```mermaid
flowchart TD
    A[App launch] --> B[Load cached config counters ads devices]
    B --> C{Cache available?}
    C -->|Yes| D[Render cached UI]
    C -->|No| E[Show loading overlay]
    D --> F[Background config sync]
    E --> F
    F --> G{Network available?}
    G -->|No| H[Keep cache]
    G -->|Yes| I[Fetch and store config]
    I --> J[Refresh UI from DB]
    J --> K[Initialize TTS]
```

---

## 2. MQTT token and CLR

```mermaid
flowchart TD
    A[Incoming MQTT payload] --> B[Extract keypad serial]
    B --> C{Mapped keypad valid?}
    C -->|No| D[Drop payload]
    C -->|Yes| E[Save received payload log]
    E --> F{CLR or route-marker 0?}
    F -->|CLR| G[Build key set: resolved identity + keypadsJson aliases + route]
    G --> H[Clear live token map keys]
    H --> I[Clear persisted token_history keys]
    I --> J[Reset dedupe / queued payloads]
    J --> K[Post tokensPerCounter snapshot]
    K --> L[Immediate TV config API on CLR TvConfigRefreshSignal else debounced MQTT refresh]
    F -->|route-marker 0| L
    F -->|No| M{Payload type}
    M -->|A/B/E incl B transferred| N[DB only no UI]
    M -->|C| O[Special-message replace flow]
    M -->|D/-/normal| P[Normal token flow]
```

**Normal token flow (detail):** `parseMqttMessage` → `resolveCounterIdentityFromSerial` (**keypad SN** from frame; not fixed index 18) → `tokenUpdateChannel` → `TokenDisplayScreen` → **`findCounterEntityForMqttRoute`** → `processTokenUpdateForKeys` → announcement path (§3). On-screen label: `formatTokenByPattern` + optional counter code prefix (§3 note).

---

## 3. Announcement (chime + TTS)

```mermaid
flowchart TD
    A[Queued token or message] --> B[Acquire announcement turn]
    B --> C[processTokenUpdateForKeys or replaceTokenForKeys]
    C --> D{playCueUi?}
    D -->|No| Z[Skip UI/audio]
    D -->|Yes| E[Publish UI + blink at cue start]
    E --> F[Play chime]
    F --> F2[Short lead-in 80-180ms]
    F2 --> G{Ad sound enabled?}
    G -->|Yes| H[Duck ExoPlayer + YouTube WebView audio]
    G -->|No| I[Skip ducking]
    H --> J{enable_token_announcement AND speakTokenAnnouncement?}
    I --> J
    J -->|No| K[Restore ad audio if ducked]
    J -->|Yes special| L[Speak message + optional counter name]
    J -->|Yes normal| M[Speak Token + prefix + token + optional counter name]
    L --> K
    M --> K
```

---

## 4. MQTT payload upload

```mermaid
flowchart TD
    A[Save incoming payload] --> B{Pending duplicate exists?}
    B -->|Yes| C[Skip insert]
    B -->|No| D[Insert pending row]
    D --> E[Debounced sync]
    E --> F{Network available?}
    F -->|No| G[Keep pending]
    F -->|Yes| H[Upload unique payloads]
    H --> I[Mark matching pending rows uploaded]
    I --> J[Delete uploaded rows older than 2 days]
    J --> K[Keep unuploaded rows untouched]
```

---

## 5. MQTT → UI pipeline (reference)

```mermaid
flowchart LR
    A[MqttClientManager] --> B[rawMessageQueue]
    B --> C[parseMqttMessage]
    C --> D[tokenUpdateChannel or tokenReplaceChannel]
    D --> E[TokenDisplayScreen collect]
    E --> F[announcementMutex]
    F --> G[publishTokensSnapshot]
    G --> H[tokensPerCounter LiveData]
    H --> I[CountersArea / getTokensForCounter]
```

---

## 6. Settings color picker open

```mermaid
flowchart TD
    A[User opens PresetColorDialog] --> B[Warm first 35 brushes on background thread]
    B --> C[Show grid]
    C --> D[Scroll to selectedHex index]
    D --> E[Request focus on selected swatch]
    E --> F[User D-pad moves focus]
    F --> G[focusedIndex updates gold ring]
    G --> H{User OK / click?}
    H -->|Yes| I[Save hex to ThemePrefs + dismiss]
    H -->|Close| J[Dismiss without change]
```

---

## 7. Advertisement rotation

```mermaid
flowchart TD
    A[ad_files from config] --> B[AdArea round-robin]
    B --> C[BoxWithConstraints measures pane]
    C --> D[AdViewportSizing + MediaEngine.updateViewport]
    D --> E{AdMediaType}
    E -->|Image| F[Coil decode at pane size + preload]
    E -->|Video| G[ExoPlayer track <= pane + preload slot]
    E -->|YouTube/Web| H[WebView]
    F --> I[onReady show]
    G --> I
    H --> I
    I --> J{Ended or interval?}
    J -->|Yes| B
    J -->|Error| K[skipVisibleAd]
    K --> B
```

---

*Derived from CallQTV May 2026 source (app 1.0.1, Room v17, `AdViewportSizing`). See [SOURCE_CODE_DOCUMENTATION.md](./SOURCE_CODE_DOCUMENTATION.md) and [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) §3.10.*
