# CallQTV – Software Requirements Specification (SRS)

**Document Version:** 2.0  
**Application:** CallQTV (Android TV Token Display)  
**Last Updated:** March 2026

---

## 1. Introduction

### 1.1 Purpose

This Software Requirements Specification (SRS) describes the functional and non-functional requirements for **CallQTV**, an Android application designed for Android TV and similar devices. The application displays queue tokens for multiple counters, plays advertisements, receives token updates via MQTT, and announces tokens using on-device Text-to-Speech (TTS).

### 1.2 Scope

- **In scope:** Splash screen, Customer ID entry and license validation, TV configuration loading, token display with configurable layouts and orientation, advertisement rotation (image + video), MQTT integration with keypad serial validation, TTS announcements (counter/token/counter-prefix configurable), dynamic theming, Firebase Cloud Messaging (FCM) for remote config refresh, token history persistence, connected devices display, notification sound selection, and responsive UI for various TV screen sizes.
- **Out of scope:** Backend API implementation, MQTT broker setup, content management for ads (content is provided via URLs in configuration).

### 1.3 Definitions and Acronyms

| Term | Definition |
|------|------------|
| **Token** | A queue number assigned to a customer for a specific counter (e.g., 20, A-36). |
| **Counter** | A service point (e.g., counter 1, Ortho) that displays its own token list. |
| **MQTT** | Message Queuing Telemetry Transport; protocol used for real-time token updates. |
| **TTS** | Text-to-Speech; on-device announcement of token numbers and counter names. |
| **TV Config** | Backend configuration (layout, orientation, colors, counters, ads, MQTT broker) fetched per device/customer. |
| **Display Type 1** | Horizontal layout: counters in a row; optional ad column on left/right. |
| **Display Type 2** | Vertical layout: counters stacked in a column; optional ad column on left/right. |
| **Fixed Protocol** | MQTT message format: `$<serial><counter><token>*` (e.g., `$02026bCAL0K00062100020*`). |

### 1.4 References

- Architecture & Workflow: `ARCHITECTURE_AND_WORKFLOW.md`
- Flow Charts & Wireframes: `SRS_FLOWCHARTS_WIREFRAMES.md`
- Android TV Design Guidelines

---

## 2. Product Overview

### 2.1 Product Perspective

CallQTV is a standalone Android application that:

1. Runs on Android TV and compatible devices (minimum SDK 26).
2. Requires a 4-digit Customer ID and license validation via backend.
3. Fetches TV-specific configuration from a REST API and caches it in Room.
4. Displays company name, date/time, multiple counter boards with tokens, and optional advertisements.
5. Connects to an MQTT broker and validates messages against connected keypad device serial (keypad_sl_no_1).
6. Announces called tokens via TTS in a configurable language (en, ml, hi, ta, etc.).
7. Supports configurable orientation (portrait/landscape) and token display with optional counter code prefix.
8. Allows theme customization and notification sound selection.

### 2.2 User Classes

| User Class | Description |
|------------|-------------|
| **TV Operator / Reception** | Views token display and ads; uses Test Call to verify TTS. |
| **Setup / Admin** | Enters Customer ID, checks license, changes theme and notification sound. |

### 2.3 Operating Environment

- **Platform:** Android OS (API 26+), optimized for Android TV.
- **Network:** Internet for license, TV config API, MQTT; HTTPS for APIs; TCP for MQTT.
- **Storage:** Room database; SharedPreferences for theme and auth.

---

## 3. Functional Requirements

### 3.1 Splash Screen

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-S1 | Display splash screen with application logo. | High |
| FR-S2 | Splash background shall use gradient based on selected theme color. | Medium |
| FR-S3 | Logo shall scale responsively with subtle pulse animation. | Medium |
| FR-S4 | Check license validity using stored license end date. | High |
| FR-S5 | After ~3 seconds, navigate to Customer ID or registration flow. | High |

### 3.2 Customer ID & License

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-C1 | Provide 4-digit numeric Customer ID input (digit boxes). | High |
| FR-C2 | Validate input: exactly 4 digits only. | High |
| FR-C3 | Allow user to trigger license check against backend. | High |
| FR-C4 | Display license status (valid/invalid) with messaging. | High |
| FR-C5 | Support optional APK update check and install flow. | Medium |
| FR-C6 | Allow theme selection (color picker) from this screen. | Medium |
| FR-C7 | Background shall reflect selected theme (gradient). | Low |
| FR-C8 | On valid license, store Customer ID and navigate to Token Display. | High |

### 3.3 TV Configuration & Token Display

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-T1 | Fetch TV configuration from REST API (mac_address, customer_id). | High |
| FR-T2 | Cache configuration in Room (config, counters, ads, mapped broker). | High |
| FR-T3 | On network failure, fall back to last cached configuration. | High |
| FR-T4 | Display loading overlay while fetching. | High |
| FR-T5 | Display error when configuration unavailable and no cache exists. | High |
| FR-T6 | Show company name, date/time (1s update), MQTT status, network status. | High |
| FR-T7 | Show counter boards; each has header and token grid (rows × columns). | High |
| FR-T8 | Number of counter boards from config no_of_counters or counters size. | High |
| FR-T9 | **Display Type 1:** Counters in horizontal row; >4 splits into two rows. | High |
| FR-T10 | **Display Type 2:** Counters in vertical column; >4 splits into two columns. | High |
| FR-T11 | Layout type from config (layout_type "1" or "2"). | High |
| FR-T12 | **Orientation:** Layout (portrait/landscape) driven by config.orientation; when set, overrides device orientation for ad placement and token grid. | High |
| FR-T13 | Token grid rows/columns swap based on orientation: portrait = taller grid, landscape = wider. | High |
| FR-T14 | When ads enabled and ad files exist, show ad area (left/right per ad_placement). | High |
| FR-T15 | Ad area rotates through images/videos; videos advance on completion. | High |
| FR-T16 | Display MAC address and app version at bottom. | Medium |
| FR-T17 | Provide "Test Call" button for sample TTS. | Medium |
| FR-T18 | Allow theme picker and apply with preview. | Medium |
| FR-T19 | **Counter Prefix:** When enable_counter_prifix is true, display token with counter code prefix (e.g., A-36). When false, display token only (e.g., 36). | High |
| FR-T20 | Settings dialog shall display: Token Announcement, Counter Announcement, Counter Prefix status (Enabled/Disabled). | Medium |

### 3.4 Advertisements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-A1 | Load ad content from URLs in ad_files. | High |
| FR-A2 | Order ads by position field. | High |
| FR-A3 | Use smooth crossfade between ads. | Medium |
| FR-A4 | Support HTTP/HTTPS; show placeholder on invalid URL. | High |

### 3.5 MQTT & Token Announcements

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-M1 | Connect to MQTT broker from mapped_broker config. | High |
| FR-M2 | Subscribe to configurable topic after connection. | High |
| FR-M3 | Parse fixed protocol: `$<14-char-serial><1-char-counter><4-char-token>*`. | High |
| FR-M4 | Support fallback formats: TOKEN:X, COUNTER:Y; regex; topic fallback. | High |
| FR-M5 | **Keypad validation:** Accept message only if serial (chars 2–15) matches keypad_sl_no_1 of a connected KEYPAD device for this customer/MAC. | High |
| FR-M6 | Expose connection status and error messages in UI. | High |
| FR-M7 | When enable_token_announcement is true, announce new tokens via TTS. | High |
| FR-M8 | Phrasing: "Token [Number], please proceed to [Counter Name]"; counter name when enable_counter_announcement is true. | High |
| FR-M9 | Token label with counter code when enable_counter_prifix is true (e.g., A-36). | High |
| FR-M10 | Only announce when MQTT counter matches configured button_index. | High |
| FR-M11 | Deduplicate: do not announce same (counter, token) twice; mark before TTS. | High |
| FR-M12 | Store token atomically under both canonical key and button_index for reliable UI display. | High |
| FR-M13 | Brief delay (~150ms) before TTS so UI updates first. | Medium |
| FR-M14 | Ignore zero-value tokens. | Medium |
| FR-M15 | TTS shall work without Google Play Services. | High |

### 3.6 Firebase Cloud Messaging (FCM)

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-F1 | Integrate FCM for push notifications. | High |
| FR-F2 | On FCM payload received, trigger TV config refresh. | High |
| FR-F3 | Store and send FCM token in TV config API requests. | High |

### 3.7 Theming & Settings

| ID | Requirement | Priority |
|----|-------------|----------|
| FR-Th1 | Theme color selection (hue, saturation, intensity). | Medium |
| FR-Th2 | Counter and token background color selection. | Medium |
| FR-Th3 | Notification sound selection (ding, chime, bell, etc.). | Medium |
| FR-Th4 | Preview before applying theme. | Medium |
| FR-Th5 | Settings dialog: Company info, Token/Counter Announcement, Counter Prefix, sound, theme, colors. | Medium |

---

## 4. Non-Functional Requirements

### 4.1 Performance

| ID | Requirement |
|----|-------------|
| NFR-P1 | TV config API timeout (e.g., 60s); fallback to cache. |
| NFR-P2 | Async ad loading; crossfade transitions. |
| NFR-P3 | Responsive UI; no main-thread blocking. |

### 4.2 Usability

| ID | Requirement |
|----|-------------|
| NFR-U1 | Responsive layout for different screen sizes. |
| NFR-U2 | Readable token and counter text; configurable font size. |
| NFR-U3 | D-pad/remote navigation support on TV. |

### 4.3 Reliability

| ID | Requirement |
|----|-------------|
| NFR-R1 | Handle null/missing API fields safely. |
| NFR-R2 | Deduplicate token announcements; atomic storage. |

### 4.4 Security

| ID | Requirement |
|----|-------------|
| NFR-S1 | Sensitive data stored per app; API over HTTPS. |
| NFR-S2 | Cleartext only where required (e.g., local MQTT). |

### 4.5 Compatibility

| ID | Requirement |
|----|-------------|
| NFR-C1 | Minimum SDK 26; target SDK 35. |
| NFR-C2 | Optimized for Android TV. |

---

## 5. Use Cases (Summary)

| UC-1 | Splash & Navigate | Actor: System | Show splash → Check license → Navigate to Customer ID or registration. |
| UC-2 | Enter Customer ID & Check License | Actor: User | Enter 4-digit ID → Check license → On success, proceed to Token Display. |
| UC-3 | View Token Display | Actor: User | Load config → Show company, date/time, counters, tokens, ads, status. |
| UC-4 | Receive Token via MQTT & Hear Announcement | Actor: System | Message received → Validate keypad → Parse → Store atomically → Announce (with dedup). |
| UC-5 | Change Theme / Settings | Actor: User | Open settings → Adjust theme, sound, colors → Apply. |
| UC-6 | Test TTS | Actor: User | Tap "Test Call" → Sample announcement. |
| UC-7 | Remote Config Refresh (FCM) | Actor: System | Push received → Fetch TV config → Update display. |

---

## 6. Constraints and Assumptions

- Backend APIs exist; request/response formats fixed.
- MQTT broker provisioned externally; fixed protocol and fallbacks supported.
- Connected devices (KEYPAD) registered with keypad_sl_no_1 in config.
- Ads from URLs; Coil-compatible images/video.
- TTS language from config (audio_language: en, ml, hi, ta, etc.).

---

For flow charts and wireframes, see **SRS_FLOWCHARTS_WIREFRAMES.md**.
