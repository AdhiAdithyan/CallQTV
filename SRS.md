# CallQTV – Software Requirements Specification (SRS) Detailed

**Document Version:** 3.0  
**Application:** CallQTV (Android TV Token Display)  
**Last Updated:** March 2026

---

## 1. Introduction

### 1.1 Purpose
This Software Requirements Specification (SRS) provides a comprehensive description of the **CallQTV** application. It is designed to be a definitive reference for developers, testers, and stakeholders, detailing every functional and non-functional aspect of the system.

### 1.2 Product Scope
CallQTV is a high-performance Android TV application for real-time queue management. It integrates MQTT for instant token updates, Text-to-Speech (TTS) for accessible announcements, and a dynamic advertisement engine for visual communication.
### 2. Advertisement Rotation Fix
- **Issue**: Large videos caused a long black screen or occasional "hangs" during buffering.
- **Solution**: Implemented a "Wait Until Ready" preloading strategy and a 10-second watchdog timer.
- **Benefit**: Seamless transitions between ads and 100% reliability in the rotation cycle, even with slow network or large media files.

---

## 2. Visual Identity & UI Design

### 2.1 Splash Screen
The initial entry point of the application, designed for branding and silent license verification.

![Splash Screen Mockup](file:///C:/Users/SILLAP-048/.gemini/antigravity/brain/69565e73-d029-4874-88ef-edbe184adfa9/splash_screen_mockup_1774241243053.png)
*Figure 1: Splash Screen featuring the pulse-animated logo and primary theme gradient.*

- **Requirement**: Logo must pulse with an infinite transition (scale 0.95 to 1.05).
- **Requirement**: Background uses a vertical gradient derived from the `ThemeColorManager` primary color.
- **Requirement**: Automated navigation after 3 seconds to the appropriate landing screen.

### 2.2 Customer ID & License Registration
The secure gateway for device provisioning and license activation.

![Customer ID Mockup](file:///C:/Users/SILLAP-048/.gemini/antigravity/brain/69565e73-d029-4874-88ef-edbe184adfa9/customer_id_mockup_1774241258311.png)
*Figure 2: Registration screen with 4-digit input and license validation status.*

- **Requirement**: User must enter a 4-digit numeric Customer ID.
- **Requirement**: The system validates the ID against the backend using the device's unique MAC address.
- **Requirement**: On successful validation, the system fetches and stores the `product_license_end` date and the project-specific `base_url`.

---

## 3. Functional Requirements Detail

### 3.1 TV Configuration & Data Persistence
- **FR-T1**: The application shall fetch a complete JSON configuration (`TvConfigPayload`) from the REST API using the MAC address and Customer ID.
- **FR-T2**: **Offline First Strategy**: All configuration data, including counters and advertisement files, must be persisted in a local Room database.
- **FR-T3**: Upon network failure, the application shall automatically load and display the last successfully cached configuration.
- **FR-T4**: Supports remote configuration refresh via **Firebase Cloud Messaging (FCM)**.

### 3.2 Token Display Engine
- **FR-D1**: The dashboard must support dual layouts:
  - **Type 1 (Horizontal)**: Counters arranged in rows.
  - **Type 2 (Vertical)**: Counters stacked in columns.
- **FR-D2**: **Dynamic Orientation**: The `config.orientation` field allows overriding the device's physical orientation (Portrait/Landscape) to optimize for specific TV mounts.
- **FR-D3**: **Counter Prefixing**: When `enable_counter_prifix` is true, tokens are displayed with their counter code (e.g., "A-12"). If false, only the number "12" is shown.
- **FR-D4**: **Responsive Scaling**: All UI elements (fonts, cards, paddings) are scaled based on a 1920x1080 design baseline to ensure perfect display on screens from 32" to 85"+ TV panels.

### 3.3 MQTT & Real-Time Sync
- **FR-M1**: Maintain a persistent connection to the configured MQTT broker with automatic exponential backoff on disconnect.
- **FR-M2**: **Fixed Protocol Parsing**:
  - Format: `$<15-char-serial><1-char-counter><1-skip><4-char-token>*`.
  - Messages must be ignored if the 17th character is '0' (protocol status bit).
- **FR-M3**: **Keypad Validation**: Only messages from authorized keypad serial numbers (stored in `connected_devices` table) shall be processed.
- **FR-M4**: **Heartbeat Publishing**: Every 5 seconds, the app must publish a `$SERIAL000000#` payload to the `"fr/status"` topic to maintain visibility in the manage system.

### 3.4 Audio Announcements (TTS)
- **FR-A1**: Use **TextToSpeech.QUEUE_ADD** to ensure sequential, non-overlapping announcements of tokens and counter names.
- **FR-A2**: **Deduplication Logic**: Do not re-announce the same token for the same counter within a 2-second window to prevent echo or noise from duplicate MQTT packets.
- **FR-A3**: Optional Chime: Play a notification sound immediately before the TTS announcement.

### 3.5 Advertisement Rotation
- **FR-Ad1**: Supports mixed media (Images via Coil, Videos via ExoPlayer).
- **FR-Ad2**: Order is strictly dictated by the `position` field in the API.
- **FR-Ad3**: **Seamless Transition (Wait Until Ready)**: The application shall keep the current advertisement visible while the next one (especially video) is preloading in the background. The transition occurs only when the next media is `STATE_READY`.
- **FR-Ad4**: **Hang-Prevention**: If an advertisement fails to load or prepare within 10 seconds, it must be automatically skipped to the next item in the rotation to prevent a frozen or black screen.
- **FR-Ad5**: **Expansion Logic**: If the number of active counters is less than the screen capacity, the advertisement area automatically expands to utilize the "free weight" on the screen.

### 3.6 Media Specifications & Limitations
- **Supported Formats**: MP4, MKV, MOV, 3GP, WEBM (Direct URLs only).
- **Max Video Size**: No hard limit for playback, but local cache is optimized for files up to **100MB**. Videos exceeding this will be streamed directly.
- **YouTube Support**: **Fully Supported**. Both direct `youtube.com` and `youtu.be` links are compatible.
- **Recommended Bitrate**: 2-5 Mbps for 1080p content to ensure smooth buffering on standard TV hardware.

---

## 4. Non-Functional Requirements

### 4.1 Performance & Reliability
- **NFR-P1**: Main thread must never be blocked during MQTT message parsing or database operations.
- **NFR-P2**: Resource management: ExoPlayer instances must be precisely managed using `DisposableEffect` to prevent memory leaks in long-running TV environments.

### 4.2 Accessibility
- **NFR-Ac1**: High-contrast text options via the dynamic theme picker.
- **NFR-Ac2**: Clear, audible TTS guidance for patients/customers in waiting areas.

---
*Produced by CallQTV Development Team – March 2026.*
