# CallQTV – Module-Wise Architecture Guide

This document breaks down the CallQTV application into its core functional modules, explaining the responsibilities and key components of each.

---

## 1. UI Module (`com.softland.callqtv.ui`)
Responsible for the presentation layer, layout adaptations, and user interactions.

- **`SplashScreenActivity`**: Initial branding and license validity check; routes to Registration or Display.
- **`CustomerIdActivity`**: Handles 4-digit Customer ID entry, license validation, and initial configuration.
- **`TokenDisplayActivity`**: The main dashboard. Manages the dual-orientation layout, token grids, and advertisement area using Jetpack Compose.
- **`OnOrderClickListener`**: Interface for handling interactions within the token list or display.

---

## 2. ViewModel Module (`com.softland.callqtv.viewmodel`)
Bridges the UI and Data layers, maintaining state and handling business logic.

- **`MqttViewModel`**: Manages MQTT connection lifecycle, message parsing, and token announcement triggers.
- **`TokenDisplayViewModel`**: Handles TV configuration loading (Room/API), ad rotation state, and counter data management.
- **`RegistrationViewModel`**: Orchestrates the multi-stage device registration and license verification process.
- **`LicenseCheckViewModel`**: Logic for validating the locally stored license expiration date.
- **`NetworkViewModel` / `NetworkLiveData`**: Real-time monitoring of internet connectivity for UI indicators.
- **`DownloadViewModel`**: Manages APK updates and background downloads if a new version is detected.

---

## 3. Data Module (`com.softland.callqtv.data`)
Handles data persistence, remote API communication, and the abstraction of data sources via repositories.

### 📂 Local (`data.local`)
- **`AppDatabase`**: Room database definition.
- **DAOs**: Data Access Objects for `TvConfig`, `Counters`, `Ads`, `TokenHistory`, and `TokenRecords`.

### 📂 Repository (`data.repository`)
- **`TvConfigRepository`**: The central data hub; fetches from API, caches in Room, and provides "Offline First" fallback.
- **`MqttClientManager`**: Low-level Paho MQTT client management (connect, subscribe, publish heartbeats).
- **`TokenHistoryRepository`**: Manages the persistence and cleanup of called token history.
- **`AuthRepository` / `ProjectRepository`**: Handles authentication tokens and project-specific settings.

### 📂 Network (`data.network`)
- **`RetrofitClient`**: Configures the REST API client with dynamic base URLs.
- **`ApiService`**: Interface for licensing, configuration, and service URL endpoints.

---

## 4. FCM Module (`com.softland.callqtv.fcm`)
Handles remote push notifications to trigger immediate actions.

- **`MyFirebaseMessagingService`**: Listens for data payloads (e.g., config refresh) and notifies the `TvConfigRepository` to update the display.

---

## 5. Utils Module (`com.softland.callqtv.utils` / `com.softland.callqtv`)
Cross-cutting concerns and helper components.

- **`ThemeColorManager`**: Stores and applies user-defined HSL color schemes globally.
- **`TokenAnnouncer`**: Orchestrates Text-to-Speech (TTS) announcements with queue management.
- **`AdVideoPlayer`**: Tailored ExoPlayer implementation for seamless video advertisement loops.
- **`StoragePathProvider`**: Manages file paths for ad content and error logs.

---
*Generated for CallQTV Documentation Suite – March 2026.*
