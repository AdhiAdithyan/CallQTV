# CallQTV – Function-Wise Working Guide

This document provides a comprehensive breakdown of the application's core functions, detailing the internal logic, data flow, and technical implementation for each major feature.

---

## 1. Startup & Splash Flow (`SplashScreenActivity`)

**Objective**: Initialize the environment and determine the correct entry point (Registration vs. Main Display).

### ⚙️ Logic Flow:
1.  **Theme Injection**: Immediately as `onCreate` starts, the `ThemeColorManager` fetches the stored primary color and background intensity. It creates a `darkColorScheme` which is applied to the root `MaterialTheme`.
2.  **License Verification**: 
    - The `SplashScreenActivity` retrieves the `product_license_end` date from `SharedPreferences`.
    - It passes this date to the `LicenseCheckViewModel`.
    - If the date is in the past OR missing, it prepares to navigate to the Registration flow.
3.  **Logo Animation**: While checks are running, a pulse animation is triggered on the logo using `rememberInfiniteTransition`, scaling between `0.95f` and `1.05f`.
4.  **Intelligent Navigation**: After a 3-second delay (to ensure branding visibility), the app checks the license status:
    - **Invalid/Expired**: Navigates to `CustomerIdActivity`.
    - **Valid**: Navigates to `CustomerIdActivity` (where it often auto-verifies and proceeds to `TokenDisplayActivity`).

---

## 2. Global Registration & Licensing Flow (`RegistrationViewModel`)

**Objective**: Securely register the device and fetch the environment-specific Service URL.

### ⚙️ Logic Flow:
1.  **Stage 1: Product Authentication**:
    - Sends MAC Address and the user-entered 4-digit Customer ID to the backend.
    - If status is `Approve`, it moves to Stage 2.
2.  **Stage 2: Device Registration**:
    - Sends the Device Identifier (MAC) and Product Registration ID.
    - Transitions to Stage 3 if the status is `Success`. If `Block`, it pauses for admin approval.
3.  **Stage 3: Check Device Status**:
    - Verifies the software version. If a mismatch is found, it triggers the `UpdateAvailable` state with a download URL.
    - Saves the `licenceActiveTo` date locally for the next Splash check.
4.  **Stage 4: Service URL Fetching**:
    - If Stage 3 succeeds, it hits the `GetServiceUrl` endpoint using the `projectCode`.
    - This dynamically updates the `base_url` for all future TV Configuration calls.

---

## 3. Configuration & Cache Logic (`TvConfigRepository`)

**Objective**: Ensure the TV display is always functional, even without a persistent internet connection.

### ⚙️ Logic Flow:
1.  **Fetch & Sync**: When `TokenDisplayActivity` starts, it calls `fetchAndCacheTvConfig`.
2.  **Local Persistence**: 
    - The API response is mapped into Room Database entities (`TvConfigEntity`, `CounterEntity`, etc.).
    - Existing counters and ads for that specific device are deleted and refreshed to maintain data integrity.
3.  **Fallback Strategy**:
    - If the network call fails (timeout/no-internet), the repository queries the local Room database using the MAC Address + Customer ID.
    - It returns the last successfully cached configuration, ensuring the TV never shows a blank screen.

---

## 4. Token Call & MQTT Processing (`MqttViewModel`)

**Objective**: Real-time processing of messages and conversion into visual/audio updates.

### ⚙️ Logic Flow:
1.  **Client Management**: `MqttClientManager` maintains a persistent TCP connection. On disconnection, it uses an auto-retry mechanism with exponential backoff (mapped in `MqttViewModel`).
2.  **Message Parsing**:
    - **JSON Path**: Tries to find `token`, `token_no`, or `token_number` fields.
    - **Plain Path**: If JSON fails, it uses regex to extract all digits from the raw string.
3.  **Strict button_index Filtering**:
    - Once a valid token is parsed, `TokenDisplayScreen` checks if the `counterId` matches the `button_index` of a configured counter.
    - **Match Logic**: `counter_id_from_mqtt == button_index`.
    - **No Match**: The system logs the mismatch and **skips the audio announcement** entirely. This ensures that only authorized service points trigger voice guidance.
4.  **Audio Construction**:
    - If a match is found, it retrieves the `name` (or `defaultName`) of that counter.
    - `TokenAnnouncer.announceToken(...)` is called with the name and the queue number.
5.  **Robust Sequential Playback**:
    - **Event Streaming**: Instead of state observation, the system uses `SharedFlow` (`latestTokenFlow`). This ensures that if 5 tokens arrive in 100ms, all 5 are captured and queued.
    - **Non-Breaking Audio**: The `TokenAnnouncer` uses `TextToSpeech.QUEUE_ADD`. Each phrase plays in its entirety. This prevents the "choppy" or "glitchy" sound of multi-token bursts while guaranteeing that every counter's call is heard.

---

## 5. Responsive Layout & Advertisement Engine

**Objective**: Dynamically adapt to screen sizes and content availability.

### ⚙️ Layout Logic:
- **Display Type 1 (Horizontal)**: Use `Row` weights. If > 4 counters, split into two horizontal rows (top and bottom).
- **Display Type 2 (Vertical)**: Use `Column` weights. If > 4 counters, split into two vertical columns (left and right).
- **Space Allocation**: If `noOfCounters` in the API is less than the physical slots, the "freed" weight is automatically added to the `AdArea` to make the advertisements larger.

### ⚙️ Ad Rotation Logic:
1.  **Sequence**: Ad files are sorted by their `position` field from the API.
2.  **Type Detection**: The extension is checked (`.mp4`, `.mov` vs `.jpg`, `.png`).
3.  **Stability & Leak Prevention**: 
    - The rotation uses a **stable index** with modular safety (`index % size`) to prevent crashes during list updates.
    - **Listener Management**: `DisposableEffect` ensures that ExoPlayer listeners are added and removed precisely, preventing "multiple-increment" bugs where ads would skip.
4.  **Rotation Control**:
    - **Images**: Advance based on `adInterval` (default 5s) using a `LaunchedEffect` timer.
    - **Videos**: The `AdVideoPlayer` (ExoPlayer) listens for `STATE_ENDED`. When the video finishes, it manually triggers the next index, bypassing the timer.

---

## 6. Real-Time Dynamic Theming (`ThemeColorManager`)

**Objective**: Allow instant visual branding without app restarts.

### ⚙️ Logic Flow:
1.  **Color Picker**: The user adjusts Hue (rainbow bar), Saturation (vibrancy), and Intensity (background darkness).
2.  **Atomic Update**:
    - Saves the `primaryColor` and `backgroundIntensity` to `SharedPreferences`.
    - Calls `activity.recreate()`.
3.  **Re-Application**: Upon recreation, the root `MaterialTheme` color scheme is rebuilt, and every Composable (from Headers to Token Cards) reacts to the new color constants via the `MaterialTheme.colorScheme` provider.

---
*Document produced by Antigravity AI – Optimized for CallQTV Maintenance & Development.*
