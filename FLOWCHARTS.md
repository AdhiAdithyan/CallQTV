# CallQTV – Flow Charts

**Document Version:** 2.0  
**Application:** CallQTV (Android TV Token Display)  
**Last Updated:** March 2026

---

## Notation (Mermaid)

| Symbol        | Meaning           | Mermaid   |
|---------------|-------------------|-----------|
| Rounded box   | Start / End       | `([text])` |
| Rectangle     | Process / Action  | `[text]`  |
| Diamond       | Decision (Y/N)    | `{text}`  |
| Arrow         | Flow direction    | `-->`     |

Render in GitHub, GitLab, or VS Code (Mermaid extension).

---

## Flow Chart 0: System Context

```mermaid
flowchart LR
    subgraph External["External Systems"]
        API[TV Config API]
        MQTT_B[MQTT Broker]
        LICENSE[License API]
        FCM[FCM]
    end
    subgraph App["CallQTV"]
        SPLASH[Splash]
        CUST[Customer ID]
        TOKEN[Token Display]
    end
    SPLASH --> LICENSE
    CUST --> LICENSE
    CUST --> TOKEN
    TOKEN --> API
    TOKEN --> MQTT_B
    API --> TOKEN
    MQTT_B --> TOKEN
    FCM --> TOKEN
```

---

## Flow Chart 1: Application Lifecycle

```mermaid
flowchart TD
    START([App Launch]) --> SPLASH[Show Splash]
    SPLASH --> CHECK[Check License]
    CHECK --> D1{Valid?}
    D1 --> NAV[Navigate to Customer ID]
    NAV --> CUST[Display Customer ID Screen]
    CUST --> INPUT[User enters 4-digit ID]
    INPUT --> CHECK_BTN[Tap Check License]
    CHECK_BTN --> VAL[Validate 4 digits]
    VAL --> D2{Valid?}
    D2 -->|No| ERR[Show error]
    ERR --> INPUT
    D2 -->|Yes| API[Call License API]
    API --> D3{OK?}
    D3 -->|No| FAIL[Show invalid]
    FAIL --> INPUT
    D3 -->|Yes| SAVE[Store Customer ID]
    SAVE --> TOKEN[Token Display]
    TOKEN --> END([View Tokens])
```

---

## Flow Chart 2: Splash & Navigation

```mermaid
flowchart TD
    START([START]) --> ON_CREATE[SplashScreenActivity onCreate]
    ON_CREATE --> THEME[Apply theme]
    THEME --> SHOW[Show Splash UI]
    SHOW --> LOAD[Load license end date]
    LOAD --> CHECK[checkLicenseValidity]
    CHECK --> D1{Invalid?}
    D1 -->|Yes| D3[Wait 3s]
    D1 -->|No| D3
    D3 --> NAV[Start CustomerIdActivity]
    NAV --> FINISH[Finish Splash]
    FINISH --> END([END])
```

---

## Flow Chart 3: Token Display Load & MQTT

```mermaid
flowchart TD
    START([TokenDisplayActivity]) --> AUTH[Read MAC, Customer ID]
    AUTH --> LOAD_UI[Show loading]
    LOAD_UI --> FETCH[fetchAndCacheTvConfig]
    FETCH --> D1{Success?}
    D1 -->|Yes| SAVE[Save to Room]
    SAVE --> DB[Load counters, ads]
    D1 -->|No| CACHE{Cache?}
    CACHE -->|No| ERR[Show error]
    CACHE -->|Yes| DB
    DB --> BROKER[Get MappedBroker]
    BROKER --> D2{Broker?}
    D2 -->|Yes| MQTT[Connect MQTT, subscribe]
    D2 -->|No| UI
    MQTT --> UI[Show Token Display]
    ERR --> HIDE[Hide loading]
    UI --> HIDE
    HIDE --> END([Ready])
```

---

## Flow Chart 4: MQTT Message to TTS (Detailed)

```mermaid
flowchart TD
    START([MQTT Message]) --> RECV[onMessageReceived]
    RECV --> FILTER{Contains CAL0K?}
    FILTER -->|No| DROP0([Discard])
    FILTER -->|Yes| VALIDATE[isValidKeypadMessage]
    VALIDATE --> D1{Serial matches keypad_sl_no_1?}
    D1 -->|No| DROP1([Discard])
    D1 -->|Yes| PARSE[SemanticMqttParser.parse]
    PARSE --> D2{Valid?}
    D2 -->|No| DROP2([Discard])
    D2 -->|Yes| SEND[Send to tokenUpdateChannel]
    SEND --> COLLECT[LaunchedEffect collects]
    COLLECT --> D3{Match button_index?}
    D3 -->|No| DROP3([Drop token])
    D3 -->|Yes| ATOMIC[processTokenUpdateForKeys]
    ATOMIC --> D4{Should announce?}
    D4 -->|No| END1([END])
    D4 -->|Yes| DEDUP{Already announced?}
    DEDUP -->|Yes| END2([Skip TTS])
    DEDUP -->|No| MARK[Mark as announced]
    MARK --> DELAY[Delay 150ms]
    DELAY --> CHIME[Play chime]
    CHIME --> TTS[TTS announce]
    TTS --> END3([END])
```

---

## Flow Chart 5: Orientation & Layout

```mermaid
flowchart TD
    START([Compose]) --> DEV[Device: screenWidth, screenHeight]
    DEV --> DEV_P{Height > Width?}
    DEV_P -->|Yes| DEV_PORT[deviceIsPortrait = true]
    DEV_P -->|No| DEV_LAND[deviceIsPortrait = false]
    DEV_PORT --> CONFIG
    DEV_LAND --> CONFIG[Read config.orientation]
    CONFIG --> D1{orientation set?}
    D1 -->|portrait| USE_PORT[usePortraitLayout = true]
    D1 -->|landscape| USE_LAND[usePortraitLayout = false]
    D1 -->|null/auto| USE_DEV[usePortraitLayout = deviceIsPortrait]
    USE_PORT --> LAYOUT
    USE_LAND --> LAYOUT
    USE_DEV --> LAYOUT[Compute layout]
    LAYOUT --> ROWS{usePortraitLayout?}
    ROWS -->|Yes| SWAP[rows=displayColumns, cols=displayRows]
    ROWS -->|No| NORM[rows=displayRows, cols=displayColumns]
    SWAP --> ADS{hasAds?}
    NORM --> ADS
    ADS -->|Yes| AD_LAYOUT[Ad + Counters by orientation]
    ADS -->|No| COUNTERS[Full width counters]
    AD_LAYOUT --> END([Render])
    COUNTERS --> END
```

---

## Flow Chart 6: Advertisement Rotation

```mermaid
flowchart TD
    START([Ad Area]) --> SORT[Sort ad files by position]
    SORT --> D1{Non-empty?}
    D1 -->|No| MSG[Show: No ads]
    D1 -->|Yes| IDX[currentAdIndex = 0]
    IDX --> TIMER[Wait ad_interval]
    TIMER --> INC[Increment index mod size]
    INC --> D2{Video?}
    D2 -->|Yes| WAIT[Wait STATE_ENDED]
    WAIT --> INC
    D2 -->|No| CROSS[Crossfade]
    CROSS --> LOAD[Load URL]
    LOAD --> DISPLAY[Display]
    MSG --> END([END])
    DISPLAY --> END
```

---

## Flow Chart 7: Customer ID Validation

```mermaid
flowchart TD
    START([Check License]) --> TRIM[Trim input]
    TRIM --> D1{Length 4?}
    D1 -->|No| ERR1[Invalid ID]
    D1 -->|Yes| D2{All digits?}
    D2 -->|No| ERR2[Numbers only]
    D2 -->|Yes| PROG[Show progress]
    PROG --> API[checkLicenseFromServer]
    API --> D3{Valid?}
    D3 -->|Yes| OK[License valid]
    D3 -->|No| FAIL[License invalid]
    OK --> HIDE[Hide progress]
    FAIL --> HIDE
    HIDE --> NAV{Navigate?}
    NAV -->|Yes| TOKEN[Token Display]
    NAV -->|No| END([END])
    TOKEN --> END
```

---

## Related Documents

- **SRS.md** – Software Requirements Specification
- **WIREFRAMES.md** – UI Wireframes
