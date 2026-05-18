# CallQTV – Android TV Token Display System

**CallQTV** is a premium Android TV application designed for real-time queue token display, advertisement rotations, and automated voice announcements. It is optimized for large screens and IoT environments, providing a professional and responsive experience for clinics, banks, and service centers.

---

## 🚀 Key Features

- **Dynamic Token Display**: Supports multiple counters with a responsive grid layout. Automatically splits into 2 rows or columns when counter count exceeds 4.
- **Smart Announcements**: Integrated Text-to-Speech (TTS) with multi-language support (English, Hindi, Tamil, Malayalam). Features **immediate interruption** for real-time responsiveness.
- **Advertisement Engine**: Image, video (incl. HLS), YouTube, and web ads in a dedicated pane — viewport-sized decode for fast playback at any source resolution.
- **MQTT Connectivity**: Real-time token updates via high-performance MQTT integration.
- **Premium Theming**: App theme, counter/token backgrounds (solids and multi-stop **gradients**), scrolling footer strip matched to theme, and **51** built-in notification chimes — with TV-friendly settings pickers.
- **Robust Offline Support**: Local Room database caching ensures the display stays online even during intermittent network connectivity.
- **Remote Configuration**: Fetches all layout, color, and device settings from a centralized REST API.

---

## 📂 Documentation

| Document | Description |
|----------|-------------|
| [**DOCUMENTATION_INDEX.md**](./DOCUMENTATION_INDEX.md) | Root index — links to all docs. |
| [**docs/MASTER_DOCUMENTATION.md**](./docs/MASTER_DOCUMENTATION.md) | **Canonical reference** — full behavior, architecture, changelog. |
| [**docs/SRS.md**](./docs/SRS.md) | Functional and non-functional requirements. |
| [**docs/WIREFRAMES.md**](./docs/WIREFRAMES.md) | Screen and settings wireframes. |
| [**docs/SRS_FLOWCHARTS_WIREFRAMES.md**](./docs/SRS_FLOWCHARTS_WIREFRAMES.md) | Startup, MQTT, announcement, and picker flows. |
| [**docs/ARCHITECTURE_AND_WORKFLOW.md**](./docs/ARCHITECTURE_AND_WORKFLOW.md) | Layers, components, MQTT→UI pipeline. |
| [**docs/SOURCE_CODE_DOCUMENTATION.md**](./docs/SOURCE_CODE_DOCUMENTATION.md) | Package/class index, Room v17, tests, “where to change” (May 2026). |
| [**docs/QA_VALIDATION_CHECKLIST.md**](./docs/QA_VALIDATION_CHECKLIST.md) | Acceptance test checklist. |
| [**docs/REBUILD_PROMPT.md**](./docs/REBUILD_PROMPT.md) | Greenfield / handoff specification. |

---

## 🛠 Tech Stack

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (100% declarative UI)
- **Language**: Kotlin
- **Database**: Room
- **Networking**: Retrofit & OkHttp
- **Messaging**: Eclipse Paho MQTT
- **Media**: Media3 ExoPlayer (for Video Ads) & Coil (for Image Ads)
- **Architecture**: MVVM (Model-View-ViewModel)

---

## 🛠 Setup & Installation

1. **Prerequisites**: Android Studio Ladybug or later, Android SDK 35, JDK 17.
2. **Clone & Open**: Open the project in Android Studio.
3. **Build**: Run `./gradlew assembleDebug` or use the IDE build button.
4. **Deploy**: Install on an Android TV device or emulator (API 26+).

---

## 🛡 License

© 2026 Softland India. All rights reserved.
