# CallQTV – Software Requirements Specification (SRS)

**Document Version:** 1.0  
**Application:** CallQTV (Android TV Token Display)  
**Last Updated:** As of current codebase

---

## 1. Introduction

### 1.1 Purpose

This Software Requirements Specification (SRS) describes the functional and non-functional requirements for **CallQTV**, an Android application designed for Android TV and similar devices. The application displays queue tokens for multiple counters, plays advertisements, receives token updates via MQTT, and announces tokens using on-device Text-to-Speech (TTS).

### 1.2 Scope

- **In scope:** Splash screen, Customer ID entry and license validation, TV configuration loading, token display with configurable layouts, advertisement rotation, MQTT integration, TTS announcements, dynamic theming, and responsive UI for various TV screen sizes.
- **Out of scope:** Backend API implementation, MQTT broker setup, content management for ads (content is provided via URLs in configuration).

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|-------------|
| **Token** | A queue number assigned to a customer for a specific counter. |
| **Counter** | A service point (e.g., counter 1, Ortho) that displays its own token list. |
| **MQTT** | Message Queuing Telemetry Transport; protocol used for real-time token updates. |
| **TTS** | Text-to-Speech; on-device announcement of token numbers and counter names. |
| **TV Config** | Backend configuration (layout, colors, counters, ads, MQTT broker) fetched per device/customer. |
| **Display Type 1** | Horizontal layout: counters in a row; optional ad column on left/right. |
| **Display Type 2** | Vertical layout: counters stacked in a column; optional ad column on left/right. |

### 1.4 References

- Architecture & Function-Wise Workflow: `ARCHITECTURE_AND_WORKFLOW.md`
- Android TV Design Guidelines
- Jetpack Compose, Room, Retrofit, Paho MQTT documentation

---

## 2. Product Overview

### 2.1 Product Perspective

CallQTV is a standalone Android application that:

1. Runs on Android TV (and compatible devices) with minimum SDK 26.
2. Requires the user to enter a 4-digit Customer ID and validate license via backend.
3. Fetches TV-specific configuration from a REST API and caches it in a local Room database.
4. Displays company name, current date/time, multiple counter boards with tokens, and optionally an advertisement strip/column.
5. Connects to an MQTT broker (configuration from API) and subscribes to a topic to receive token call messages.
6. Announces called tokens using TTS in a configurable language (e.g., English, Malayalam).
7. Allows the user to change the application theme (primary color and background intensity) with a live preview.

### 2.2 User Classes

| User Class | Description |
|------------|-------------|
| **TV Operator / Reception** | Views token display and advertisement area; may use "Test Call" to verify TTS. |
| **Setup / Admin** | Enters Customer ID, checks license, and optionally changes theme. |

### 2.3 Operating Environment

- **Platform:** Android OS (API 26+), optimized for Android TV (UI_MODE_TYPE_TELEVISION).
- **Network:** Internet for license check, TV config API, and MQTT; HTTPS for config API; TCP for MQTT.
- **Storage:** Local Room database for config, counters, ads, and mapped broker; SharedPreferences for theme and auth.

---

## 3. Functional Requirements

### 3.1 Splash Screen

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-S1 | The system shall display a splash screen with the application logo. | High |
| FR-S2 | The splash background shall use a gradient based on the user-selected theme color. | Medium |
| FR-S3 | The logo shall scale responsively (e.g., 1920×1080 baseline) and support a subtle pulse animation. | Medium |
| FR-S4 | The system shall check license validity using the stored license end date. | High |
| FR-S5 | After a defined delay (e.g., 3 seconds), the system shall navigate to Customer ID screen or device registration flow as per license result. | High |

### 3.2 Customer ID & License

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-C1 | The system shall provide a 4-digit numeric Customer ID input (e.g., digit boxes). | High |
| FR-C2 | The system shall validate input: exactly 4 characters, digits only. | High |
| FR-C3 | The system shall allow the user to trigger a license check against the backend. | High |
| FR-C4 | The system shall display license status (valid/invalid) with appropriate messaging. | High |
| FR-C5 | The system shall support optional APK update check and download/install flow. | Medium |
| FR-C6 | The system shall allow the user to open a theme selection (color picker) from this screen. | Medium |
| FR-C7 | The Customer ID screen background shall reflect the selected theme (e.g., white-to-theme gradient). | Low |
| FR-C8 | On successful license validation, the system shall store Customer ID and navigate to Token Display. | High |

### 3.3 TV Configuration & Token Display

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-T1 | The system shall fetch TV configuration from a configurable REST API (mac_address, customer_id). | High |
| FR-T2 | The system shall cache the configuration (and related counters, ad files, mapped broker) in a local Room database. | High |
| FR-T3 | On network failure or timeout, the system shall fall back to the last cached configuration when available. | High |
| FR-T4 | The system shall display a loading overlay while fetching configuration. | High |
| FR-T5 | The system shall display an error message when configuration is unavailable and no cache exists. | High |
| FR-T6 | The token display shall show: company name, current date and time (updating every second), MQTT connection status, and network availability. | High |
| FR-T7 | The token display shall show one or more counter boards; each board has a header (counter name) and a grid of token cells (rows × columns from config). | High |
| FR-T8 | The number of counter boards shall be determined by the actual counters in the database (or config no_of_counters). | High |
| FR-T9 | **Display Type 1:** Counters shall be arranged in a horizontal row; if the number of counters exceeds 4, the UI shall split into two horizontal rows (top and bottom) to maintain readability on large screens. | High |
| FR-T10 | **Display Type 2:** Counters shall be arranged in a vertical column; if the number of counters exceeds 4, the UI shall split into two vertical columns (left and right) to maintain readability on large screens. | High |
| FR-T11 | Layout type shall be selected via configuration (e.g., layout_type "1" or "2"). | High |
| FR-T12 | When advertisements are enabled and ad files exist, an ad area shall be shown (left or right per ad_placement). | High |
| FR-T13 | The ad area shall rotate through ad images or videos from the TV config; images use fixed intervals, while videos advance upon completion. | High |
| FR-T14 | When the number of counters is less than the maximum configured, the freed space shall be allocated to the advertisement area. | Medium |
| FR-T15 | The system shall display device MAC address and application version at the bottom center. | Medium |
| FR-T16 | The system shall provide a "Test Call" button to trigger a sample TTS announcement (e.g., "Token 1, please proceed to Counter 1"). | Medium |
| FR-T17 | The system shall allow the user to open a theme picker (e.g., rainbow icon) and apply a new theme with preview. | Medium |

### 3.4 Advertisements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-A1 | Ad content shall be loaded from URLs provided in the TV config (ad_files). | High |
| FR-A2 | Ads shall be ordered by the position field from the API/database. | High |
| FR-A3 | Transition between ads shall use a smooth animation (e.g., crossfade). | Medium |
| FR-A4 | Image loading shall support HTTP/HTTPS and show a placeholder or message when URL is invalid or empty. | High |

### 3.5 MQTT & Token Announcements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-M1 | The system shall connect to an MQTT broker using host, port, and optional credentials from the mapped_broker configuration. | High |
| FR-M2 | The system shall subscribe to a configurable topic after connection. | High |
| FR-M3 | The system shall parse incoming messages for counter name and token number (JSON or plain). | High |
| FR-M4 | The system shall expose connection status and detailed error messages (e.g., reason code) in the UI. | High |
| FR-M5 | When token announcement is enabled in config, the system shall announce each new token via TTS in the configured audio_language. | High |
| FR-M6 | The system shall announce tokens using the phrasing: "Token [Number], please proceed to [Counter Name]". | High |
| FR-M7 | Each unique token call shall be announced only once per counter. | High |
| FR-M8 | Any new token call shall immediately interrupt any ongoing announcement to ensure real-time visibility and audio alignment. | High |
| FR-M9 | Zero-value tokens (e.g., "0", "00") shall be ignored for announcements. | Medium |
| FR-M10 | TTS shall work without Google Play Services (on-device engine). | High |

### 3.6 Theming

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-Th1 | The user shall be able to select a theme color (e.g., via hue and saturation sliders). | Medium |
| FR-Th2 | The user shall be able to adjust background transparency/intensity for gradients. | Medium |
| FR-Th3 | A preview of the selected theme shall be shown before applying. | Medium |
| FR-Th4 | Applied theme shall affect: Splash, Customer ID screen, Token Display (header, background, accents). | Medium |

---

## 4. Non-Functional Requirements

### 4.1 Performance

| ID | Requirement |
|----|-------------|
| NFR-P1 | TV config API response shall be handled with a timeout (e.g., 60 s); fallback to cache on timeout. |
| NFR-P2 | Ad images shall be loaded asynchronously (e.g., Coil) with optional crossfade. |
| NFR-P3 | UI shall remain responsive on TV (smooth animations, no blocking on main thread). |

### 4.2 Usability

| ID | Requirement |
|----|-------------|
| NFR-U1 | Layout shall scale for different screen sizes (e.g., BoxWithConstraints, responsive dp/sp). |
| NFR-U2 | Token text and counter names shall be readable (e.g., white token cards, configurable font size). |
| NFR-U3 | Theme picker dialog shall have a white background and readable text for accessibility. |

### 4.3 Reliability

| ID | Requirement |
|----|-------------|
| NFR-R1 | Application shall not crash on null or missing API fields (e.g., shift_details JsonNull). |
| NFR-R2 | Global exception handler may suppress known OEM-specific crashes (e.g., IS_MIUI_LITE_VERSION). |

### 4.4 Security

| ID | Requirement |
|----|-------------|
| NFR-S1 | Sensitive data (e.g., MQTT password) shall be stored per app; API over HTTPS. |
| NFR-S2 | Cleartext traffic may be allowed only where required (e.g., local MQTT). |

### 4.5 Compatibility

| ID | Requirement |
|----|-------------|
| NFR-C1 | Minimum SDK 26; target/compile SDK 34. |
| NFR-C2 | Optimized for Android TV; supports D-pad/remote where applicable. |

---

## 5. Use Cases (Summary)

| UC-1 | Splash & Navigate | Actor: System | Precondition: App launched. Flow: Show splash → Check license → Navigate to Customer ID or registration. |
| UC-2 | Enter Customer ID & Check License | Actor: User | Precondition: On Customer ID screen. Flow: Enter 4-digit ID → Validate → Check license → See status → On success, proceed to Token Display. |
| UC-3 | View Token Display | Actor: User (TV viewer) | Precondition: Valid license, on Token Display. Flow: System loads config → Shows company, date/time, counters, tokens, optional ads, status. |
| UC-4 | Receive Token via MQTT & Hear Announcement | Actor: System / Backend | Precondition: MQTT connected, announcements enabled. Flow: Message received → Parse counter + token → Queue TTS → Speak. |
| UC-5 | Change Theme | Actor: User | Precondition: On Customer ID or Token Display. Flow: Open theme picker → Adjust hue/saturation/intensity → Preview → Apply → UI updates. |
| UC-6 | Test TTS | Actor: User | Precondition: On Token Display. Flow: Tap "Test Call" → Sample announcement played. |

---

## 6. Constraints and Assumptions

- **Backend:** TV config API and license/device registration APIs are existing; request/response formats are fixed.
- **MQTT:** Broker is provisioned externally; topic and payload format (e.g., JSON with counter_name, token_no) are agreed.
- **Ads:** Ad URLs point to images (e.g., Coil-compatible); video not required in current scope.
- **Localization:** TTS language is driven by config (e.g., audio_language "en", "ml"); no in-app locale switching beyond that.

---

# Flow Charts

**Notation (ANSI-style):**

| Symbol        | Meaning        | Mermaid  |
|---------------|----------------|----------|
| Rounded box   | Start / End    | `([text])` |
| Rectangle     | Process / Action | `[text]` |
| Diamond       | Decision (Yes/No) | `{text}` |
| Arrow         | Flow direction | `-->`    |
| Label on arrow| Condition      | `-->|Yes|` |

Diagrams use [Mermaid](https://mermaid.js.org/) syntax. Render in GitHub, GitLab, or VS Code (Mermaid extension).

---

## Flow Chart 0: System Context (External Systems & App)

```mermaid
flowchart LR
    subgraph External["External Systems"]
        API[TV Config API\nHTTPS]
        MQTT_B[MQTT Broker\nTCP]
        LICENSE[License / Device API]
    end
    subgraph App["CallQTV App"]
        SPLASH[Splash]
        CUST[Customer ID\nScreen]
        TOKEN[Token Display\nScreen]
    end
    SPLASH --> LICENSE
    CUST --> LICENSE
    CUST --> TOKEN
    TOKEN --> API
    TOKEN --> MQTT_B
    API --> TOKEN
    MQTT_B --> TOKEN
```

---

## Flow Chart 1: Application Lifecycle (High Level)

```mermaid
flowchart TD
    START([START: App Launch]) --> SPLASH[Show Splash Screen]
    SPLASH --> CHECK_LIC[Check License Validity]
    CHECK_LIC --> D1{License valid?}
    D1 -->|No| NAV_CUST[Navigate to Customer ID Screen]
    D1 -->|Yes| NAV_CUST
    NAV_CUST --> CUST_UI[Display Customer ID Screen]
    CUST_UI --> USER_INPUT[User enters 4-digit ID]
    USER_INPUT --> CHECK_BTN[User taps Check License]
    CHECK_BTN --> VALIDATE[Validate input: 4 digits]
    VALIDATE --> D2{Valid?}
    D2 -->|No| SHOW_ERR[Show invalid message]
    SHOW_ERR --> USER_INPUT
    D2 -->|Yes| CALL_API[Call license API]
    CALL_API --> D3{License OK?}
    D3 -->|No| SHOW_FAIL[Show invalid license]
    SHOW_FAIL --> USER_INPUT
    D3 -->|Yes| SAVE_ID[Store Customer ID]
    SAVE_ID --> NAV_TOKEN[Navigate to Token Display]
    NAV_TOKEN --> TOKEN_UI[Show Token Display Screen]
    TOKEN_UI --> END([END: User views tokens])
```

---

## Flow Chart 2: Splash Screen & Navigation (Detail)

```mermaid
flowchart TD
    START([START]) --> ON_CREATE[onCreate: SplashScreenActivity]
    ON_CREATE --> SET_THEME[Apply theme from ThemeColorManager]
    SET_THEME --> SHOW_UI[Show SplashScreenContent]
    SHOW_UI --> LOAD_PREFS[Load auth SharedPreferences]
    LOAD_PREFS --> GET_DATE[Get license end date]
    GET_DATE --> CHECK_VALID[licenseCheckViewModel.checkLicenseValidity]
    CHECK_VALID --> OBSERVE[Observe getLicenseStatus]
    OBSERVE --> D1{License invalid?}
    D1 -->|Yes| DELAY1[Wait 3 seconds]
    D1 -->|No| DELAY2[Wait 3 seconds]
    DELAY1 --> NAV_CUST[Start CustomerIdActivity]
    DELAY2 --> NAV_REG[Start Device Registration / CustomerIdActivity]
    NAV_CUST --> FINISH1[Finish SplashScreen]
    NAV_REG --> FINISH1
    FINISH1 --> END([END])
```

---

## Flow Chart 3: Token Display Load & MQTT Setup (Detail)

```mermaid
flowchart TD
    START([START: TokenDisplayActivity]) --> READ_AUTH[Read MAC address and Customer ID from prefs]
    READ_AUTH --> SHOW_LOAD[Show loading overlay]
    SHOW_LOAD --> FETCH[Call TvConfigRepository.fetchAndCacheTvConfig]
    FETCH --> D1{API success?}
    D1 -->|Yes| SAVE[Save config, counters, ads, mapped broker to Room]
    SAVE --> LOAD_DB[Load counters and ad files from DB]
    D1 -->|No| TRY_CACHE[Try getCachedConfig]
    TRY_CACHE --> D2{Cache exists?}
    D2 -->|No| SHOW_ERR[Show error message]
    SHOW_ERR --> HIDE_LOAD[Hide loading]
    D2 -->|Yes| LOAD_DB
    LOAD_DB --> GET_BROKER[Get MappedBrokerEntity from DB]
    GET_BROKER --> D3{Host and topic present?}
    D3 -->|Yes| BUILD_URI[Build MQTT server URI and client ID]
    BUILD_URI --> INIT_MQTT[Init MQTT client]
    INIT_MQTT --> CONNECT[Connect and subscribe to topic]
    CONNECT --> SHOW_UI[Show Token Display UI]
    D3 -->|No| SHOW_UI
    SHOW_UI --> HIDE_LOAD
    HIDE_LOAD --> POLL[Start network status poll every 2s]
    POLL --> END([END: Screen ready])
```

---

## Flow Chart 4: MQTT Message to TTS Announcement (Detail)

```mermaid
flowchart TD
    START([START: MQTT message received]) --> ON_MSG[MqttClientManager.onMessageReceived]
    ON_MSG --> POST_RAW[Post raw message to receivedMessage LiveData]
    POST_RAW --> PARSE[MqttViewModel.parseCounterAndToken]
    PARSE --> D1{Valid Parsing Result?}
    D1 -->|No| END0([END: Parse Failed])
    D1 -->|Yes| POST_PAIR[Post pair to latestToken LiveData]
    POST_PAIR --> OBSERVE[TokenDisplayScreen LaunchedEffect observes]
    OBSERVE --> D2{enableTokenAnnouncement? AND not zero?}
    D2 -->|No| END1([END: No TTS])
    D2 -->|Yes| MATCH{button_index == MQTT Counter ID?}
    MATCH -->|No| SKIP[END: No button_index match]
    MATCH -->|Yes| BUILD_PHRASE[Build phrase: Token X, proceed to name]
    BUILD_PHRASE --> ENQUEUE[TokenAnnouncer: add to internal queue]
    ENQUEUE --> TTS_PLAY[TTS plays phrase sequentially / with priority]
    TTS_PLAY --> END2([END])
    POST_PAIR --> MARK_ANN[Mark as announced in ViewModel]
```

---

## Flow Chart 5: Advertisement Rotation (Detail)

```mermaid
flowchart TD
    START([START: Ad area composed]) --> SORT[Sort ad files by position field]
    SORT --> D1{adFiles non-empty?}
    D1 -->|No| SHOW_MSG[Show: No advertisements configured]
    SHOW_MSG --> END1([END])
    D1 -->|Yes| INIT_IDX[Set currentAdIndex = 0]
    INIT_IDX --> TIMER[LaunchedEffect: wait ad_interval seconds]
    TIMER --> INC[currentAdIndex = currentAdIndex + 1 mod size]
    INC --> D1_2{Current ad is Video?}
    D1_2 -->|No| TIMER
    D1_2 -->|Yes| WAIT_VIDEO[AdVideoPlayer: Wait for STATE_ENDED]
    WAIT_VIDEO --> INC
    TIMER --> CROSSFADE[Crossfade animation to ad at currentAdIndex]
    CROSSFADE --> LOAD[Ad Area: load URL/Path]
    LOAD --> D2{Load success?}
    D2 -->|Yes| DISPLAY[Display Image or Video]
    D2 -->|No| PLACEHOLDER[Show placeholder or next]
    DISPLAY --> END2([END])
    PLACEHOLDER --> END2
```

---

## Flow Chart 6: Layout Type & Ad Space Allocation (Detail)

```mermaid
flowchart TD
    START([START: TokenDisplayFromConfig]) --> COMPUTE[Compute hasAds = showAds and adFiles not empty]
    COMPUTE --> WEIGHTS[Compute visibleCounters, freeSlots, adWeight, countersWeight]
    WEIGHTS --> D1{hasAds?}
    D1 -->|No| FULL_WIDTH[Use full width for counters]
    FULL_WIDTH --> D2{layoutType == 2?}
    D2 -->|No| D8{Counters > 4?}
    D2 -->|Yes| V1{Counters > 4?}
    D2 -->|No| VERT[Render countersContentType2: Single Column]
    V1 -->|Yes| VSPLIT[Split into 2 columns]
    VSPLIT --> END
    VERT --> END
    D8 -->|Yes| HSPLIT[Split into 2 rows]
    D8 -->|No| HORIZ[Render countersContentType1: Single Row]
    HSPLIT --> END
    HORIZ --> END
    D1 -->|Yes| ROW[Row: Ad column + Counters column]
    ROW --> D3{adPlacement == left?}
    D3 -->|Yes| ORDER1[Ad area first, then counters]
    D3 -->|No| ORDER2[Counters first, then ad area]
    ORDER1 --> D4{layoutType == 2?}
    ORDER2 --> D4
    D4 -->|Yes| D5{Counters > 4?}
    D4 -->|No| D6{Counters > 4?}
    D5 -->|Yes| SPLIT_VERT[Split into 2 columns]
    D5 -->|No| VERT2[countersContentType2: Single Column]
    D6 -->|Yes| SPLIT_HORIZ[Split into 2 rows]
    D6 -->|No| HORIZ2[countersContentType1: Single Row]
    SPLIT_VERT --> END
    VERT2 --> END
    SPLIT_HORIZ --> END
    HORIZ2 --> END
```

---

## Flow Chart 7: Customer ID Validation & License Check (Detail)

```mermaid
flowchart TD
    START([START: User taps Check License]) --> TRIM[Trim customer ID input]
    TRIM --> D1{Length == 4?}
    D1 -->|No| ERR_LEN[Show: Invalid ID]
    ERR_LEN --> END1([END])
    D1 -->|Yes| D2{All digits?}
    D2 -->|No| ERR_NUM[Show: Numbers only]
    ERR_NUM --> END1
    D2 -->|Yes| CLEAR[Clear previous status]
    CLEAR --> SHOW_PROG[Show progress overlay]
    SHOW_PROG --> DELAY[Wait 2.5 s simulated delay]
    DELAY --> CALL_API[checkLicenseFromServer]
    CALL_API --> D3{API returns valid?}
    D3 -->|Yes| MSG_OK[Show: License valid]
    D3 -->|No| MSG_FAIL[Show: License invalid]
    MSG_OK --> HIDE_PROG[Hide progress]
    MSG_FAIL --> HIDE_PROG
    HIDE_PROG --> D4{User proceeds to Token Display?}
    D4 -->|Yes| NAV[Navigate to TokenDisplayActivity]
    NAV --> END2([END])
    D4 -->|No| END2
```

---

# Wireframes

Wireframes below are ASCII/text representations of the main screens. Keys: `+` corners, `-` horizontal, `|` vertical, `[ ]` buttons/inputs, `===` header/footer.

---

## Wireframe 1: Splash Screen

```
+------------------------------------------------------------------+
|                                                                  |
|                    (Theme gradient background)                   |
|                                                                  |
|                         +----------------+                       |
|                         |                |                       |
|                         |   CallQ TV     |   (Logo with pulse)   |
|                         |     Logo       |                       |
|                         |                |                       |
|                         +----------------+                       |
|                                                                  |
|                                                                  |
+------------------------------------------------------------------+
```

- Full-screen gradient (primary color, alpha 0.35 → 0.75).
- Centered logo; size scales with screen (e.g., 150–400 dp).
- No buttons; auto-navigation after ~3 s.

---

## Wireframe 2: Customer ID Screen

```
+------------------------------------------------------------------+
|  (Gradient: White -> Theme color, intensity from ThemeColorManager)|
|                                                                  |
|                      CallQ TV / App Name                         |
|                                                                  |
|              +----+  +----+  +----+  +----+                       |
|              | 0  |  | 0  |  | 0  |  | 0  |   <- 4 digit boxes    |
|              +----+  +----+  +----+  +----+                       |
|                                                                  |
|              [      Check License      ]                         |
|                                                                  |
|              +------------------------------------------+       |
|              |  Status: Valid / Invalid message         |       |
|              +------------------------------------------+       |
|                                                                  |
|              Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0         |
|                                              [Theme icon]        |
+------------------------------------------------------------------+
```

- Digit fields: 4 separate boxes; numeric only.
- Single primary button for license check.
- Status area appears below after check (AnimatedVisibility).
- Footer: device ID, app version; theme icon for color picker.

---

## Wireframe 3: Token Display – Display Type 1, With Ads (Left)

```
+------------------------------------------------------------------+
| Company Name          dd-mm-yyyy HH:mm:ss   ● BROKER  ● Network  🌈|
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+--------+---------+---------+---------+---------+------------------+
|        | Counter1| Counter2| Counter3| Counter4|                  |
|  Adv   +---------+---------+---------+---------+                  |
|  area  | T1  T2  | T5  T6  | T9  T10 | T13 T14 |                  |
|  (img) | T3  T4  | T7  T8  | T11 T12 | T15 T16 |                  |
|        |         |         |         |         |                  |
+--------+---------+---------+---------+---------+------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Top bar: company left; date/time and status right; theme icon far right.
- Row: Ad column (weight from adWeight) | Counter boards (weight from countersWeight).
- Each counter: header + grid of token cells (e.g., 2×4).
- Bottom: MAC and version centered.

---

## Wireframe 4: Token Display – Display Type 1, No Ads

```
+------------------------------------------------------------------+
| Company Name          dd-mm-yyyy HH:mm:ss   ● BROKER  ● Network  🌈|
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+---------+---------+---------+---------+---------------------------+
|Counter1 | Counter2| Counter3| Counter4|                          |
+---------+---------+---------+---------+                          |
| T1  T2  | T5  T6  | T9  T10 | T13 T14 |                          |
| T3  T4  | T7  T8  | T11 T12 | T15 T16 |                          |
+---------+---------+---------+---------+---------------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Same header/footer; full width for horizontal counter row.
- No ad column.

---

## Wireframe 5: Token Display – Display Type 2, With Ads (Left)

```
+------------------------------------------------------------------+
| Company Name          dd-mm-yyyy HH:mm:ss   ● BROKER  ● Network  🌈|
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+--------+----------------------------------------------------------+
|        | Counter1                                                 |
|  Adv   +----------------------------------------------------------+
|  area  | Counter2                                                 |
|  (img) +----------------------------------------------------------+
|        | Counter3                                                 |
|        +----------------------------------------------------------+
|        | Counter4                                                 |
+--------+----------------------------------------------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Ad column left; right side: vertical stack of counter boards (each with header + token grid).

---

## Wireframe 6: Token Display – Display Type 2, No Ads

```
+------------------------------------------------------------------+
| Company Name          dd-mm-yyyy HH:mm:ss   ● BROKER  ● Network  🌈|
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+----------------------------------------------------------+
| Counter1                                                 |
+----------------------------------------------------------+
| Counter2                                                 |
+----------------------------------------------------------+
| Counter3                                                 |
+----------------------------------------------------------+
| Counter4                                                 |
+----------------------------------------------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Full width vertical stack of counter boards; no ad column.

---

## Wireframe 7: Theme Picker Dialog

```
+---------------------------+
|  Choose Theme Color       |
+---------------------------+
| [===== Hue gradient =====]|
| --------o----------------  Hue slider
|
| Color Intensity           |
| ----------o-------------   Saturation
|
| Background Transparency   |
| ----------o-------------   Intensity
|
| Preview                   |
| +------------------------+
| |  (Mini header bar      |
| |   in selected color)   |
| +------------------------+
|
|    [ Cancel ]  [ Apply ]  |
+---------------------------+
```

- White dialog background; black text.
- Hue bar (rainbow); sliders for saturation and background intensity.
- Preview: small header-style bar in selected color.
- Apply: saves theme + intensity, recreates activity.

---

## Wireframe 8: Loading & Error States (Token Display)

**Loading:**

```
+------------------------------------------------------------------+
|                                                                  |
|                    ( Semi-transparent overlay )                   |
|                         Loading TV configuration...              |
|                         [  CircularProgressIndicator  ]          |
|                                                                  |
+------------------------------------------------------------------+
```

**Error (no config, no cache):**

```
+------------------------------------------------------------------+
|                                                                  |
|     TV configuration is not available yet. Device may be         |
|     pending approval or limit reached.                            |
|     (or) Failed to load TV configuration: Read timed out         |
|                                                                  |
+------------------------------------------------------------------+
```

---

## Document Control

| Version | Date       | Author   | Changes                    |
|---------|------------|----------|----------------------------|
| 1.0     | (current)  | CallQTV  | Initial SRS, flowcharts, wireframes |

---

*End of SRS, Flow Charts, and Wireframes*
