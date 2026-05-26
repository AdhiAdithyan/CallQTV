# CallQTV — Logic Flowcharts

Process flows for startup, MQTT, announcements, and payload upload. Pair with [WIREFRAMES.md](./WIREFRAMES.md) for layout.

**Canonical reference:** [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md)

---

## 1. Startup and configuration

```mermaid
flowchart TD
    A[App launch] --> P{Storage permissions granted?}
    P -->|No| P2[Prompt runtime + All files access]
    P2 --> P
    P -->|Yes| B[Splash: license check + navigation]
    B --> C{Registered + valid license?}
    C -->|No| R[CustomerIdActivity]
    C -->|Yes| M[TokenDisplayActivity]
    R --> P3{Storage for navigate to main?}
    P3 -->|No| P2
    P3 -->|Yes| M
    M --> P4{Storage before loadData?}
    P4 -->|No| P2
    P4 -->|Yes| L[Load cached config counters ads devices]
    L --> N{Cache available?}
    N -->|Yes| D[Render cached UI]
    N -->|No| E[Show loading overlay]
    D --> F[Background config sync]
    E --> F
    F --> G{Network available?}
    G -->|No| H[Keep cache]
    G -->|Yes| I[Fetch and store config]
    I --> J[Refresh UI from DB]
    J --> K[Initialize TTS / MQTT]
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

**Normal token flow (detail):** `parseMqttMessage` → `resolveCounterIdentityFromSerial` (**CounterRouteLookupCache** or Room on IO; **keypad SN** from frame; not fixed index 18) → `tokenUpdateChannel` (cap **128**, drop-oldest) → `TokenDisplayScreen` → **`findCounterEntityForMqttRoute`** → `processTokenUpdateForKeys` → announcement path (§3). On-screen label: `formatTokenByPattern` + optional `{code}-` when `enable_counter_prefix`; index 4 **`D`** → **`ER-`** on any slot via `vipEmergencyTokensByKey` (§3.4.1 in [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md)).

---

## 3. Announcement (chime + TTS)

```mermaid
flowchart TD
    A[Queued token or message] --> B[Acquire announcementMutex]
    B --> R[async awaitReady if announcements on]
    R --> C[processTokenUpdateForKeys publishImmediately=false]
    C --> D{playCueUi?}
    D -->|No| Z[Release mutex]
    D -->|Yes| W{willAnnounce?}
    W -->|Yes| AR[Await awaitReady max 12s]
    W -->|No| CH
    AR --> CH[playTokenChime awaited]
    CH --> E[Publish tile + blink at cue start]
    E --> G{enable_token_announcement AND speakTokenAnnouncement?}
    G -->|No| Z2[Release mutex]
    G -->|Yes| H[runWithAdvertisementAudioDuckedForSpeech]
    H --> I{Ad sound on?}
    I -->|Yes| J[Duck Exo + YouTube]
    I -->|No| K[No duck]
    J --> L[awaitSynthesisPrimeIfNeeded if cold]
    K --> L
    L --> M{Special or normal?}
    M -->|Special| N[announceMessage until onDone]
    M -->|Normal| O[announceTokenCall until onDone]
    N --> P[Restore ad audio]
    O --> P
    P --> Z2
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
    E --> F[announcementMutex per token]
    F --> G[chime then publishTokensSnapshot at cue]
    G --> H[optional TTS then release mutex]
    H --> I[tokensPerCounter LiveData]
    I --> J[CountersArea / getTokensForCounter]
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

## 8. Unit tests (JVM)

| Test | Covers |
|------|--------|
| `CounterRouteLookupCacheTest` | Route cache keys, TTL, scope invalidation |
| `MqttCounterRoutingTest` | Counter entity resolution |
| `VipEmergencyTokenPrefixTest` | VIP **ER-** on history slots |
| `KeypadPayloadParserTest`, `SemanticMqttParserTest`, … | MQTT parsing |

Run: `./gradlew testCallQTVDebugUnitTest` (**44** tests).

---

*Derived from CallQTV May 2026 source (app `1.0.1`, `versionCode` 2, Room v17, `minSdk` 21). Permission gate, bounded channels, route cache: [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) §3.1. Announcement mutex + VIP ER: §3.4.1, §3.5–§3.5.2. Code index: [SOURCE_CODE_DOCUMENTATION.md](./SOURCE_CODE_DOCUMENTATION.md).*
