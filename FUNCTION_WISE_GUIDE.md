# CallQTV – Function-Wise Logic Guide

This document captures the primary functional flows of the application, explaining how core features are implemented and the logic they follow.

---

## 1. Device Registration & License Check
**Objective**: Secure the application and fetch client-specific configuration.

1.  **MAC Address Extraction**: The app identifies the device using its Wi-Fi/Ethernet MAC address.
2.  **4-Digit Validation**: Users enter a 4-digit Customer ID which is validated against the backend.
3.  **State Machine**:
    - **`UNREGISTERED`**: Prompts for Customer ID.
    - **`REGISTERED`**: Fetches Service URL and then TV Configuration.
    - **`INVALID`**: Displays license error.
4.  **Automatic Provisioning**: Once validated, the app automatically fetches the project-specific `base_url` for all future API calls.

---

## 2. TV Configuration & Offline Fallback
**Objective**: Ensure the display is always active with the correct layout.

1.  **Dynamic Fetch**: Upon launch, `TvConfigRepository` calls the `GetTvConfig` API.
2.  **Room Persistence**: All fetched data (counters, ads, layout, colors) is saved into Room.
3.  **Sync Logic**: If the network is unavailable, the repository queries the local cache using the MAC and Customer ID as a primary key.
4.  **Real-Time Refresh (FCM)**: When a push notification is received, the app re-executes the fetch to update the display immediately.

---

## 3. MQTT Token Processing & Announcements
**Objective**: Low-latency token updates with high-quality voice guidance.

1.  **Connection Management**: `MqttClientManager` maintains a persistent TCP/IP connection with auto-reconnect logic.
2.  **Message Parsing**:
    - **Fixed Protocol**: `$<15-char-serial><1-char-counter><1-char-separator><4-char-token><1-char-padding>*`.
    - **Regex Fallback**: Scans for numbers if the fixed format fails.
3.  **Keypad Validation**: The message serial is checked against the list of `KEYPAD` devices authorized for this display.
4.  **Atomic Flow**: `processTokenUpdateForKeys` ensures that if multiple messages arrive (e.g., from different counters), the UI and TTS are updated sequentially without race conditions.
5.  **Deduplication**: The `TokenAnnouncer` checks if a (Counter, Token) pair was just announced to prevent redundant voice calls.

---

## 4. Continuous MQTT Heartbeat
**Objective**: Keep the broker/server aware of the device status.

1.  **The Loop**: A `Timer` or `Flow`-based loop runs every 5 seconds.
2.  **Payload**: `$SERIAL000000#`.
3.  **Condition**: The loop is active as long as the MQTT client is connected.
4.  **Topic**: Publishes to the fixed "fr/status" topic to signal keypad/TV availability to the management system.

---

## 5. Dynamic Ad Rotation Engine
**Objective**: High-impact visual communication during idle times.

1.  **Mixed Media**: Supports both static images (via Coil) and video (via ExoPlayer).
2.  **Sequence Control**: Ads are sorted by `position`.
3.  **Wait Logic**:
    - **Images**: Wait for `ad_interval` (seconds).
    - **Videos**: Listen for `PLAYER_STATE_ENDED` before advancing to the next ad.
4.  **Layout Weighting**: If few counters are configured, the `AdArea` expands to fill the available screen space.

---

## 6. Dynamic Theming (HSL)
**Objective**: Instant branding customization.

1.  **Hue/Saturation/Intensity**: The user selects a primary hue (0-360), vibrancy, and background darkness.
2.  **Global Provider**: `ThemeColorManager` calculates the `primary`, `background`, and `on-color` values.
3.  **Activity Recreation**: When colors are updated in Settings, the Activity is recreated to apply the new `MaterialTheme` color scheme across all Composables.

---
*Generated for CallQTV Documentation Suite – March 2026.*
