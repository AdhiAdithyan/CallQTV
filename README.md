# CallQTV – Android TV Token Display System

**CallQTV** is a premium Android TV application designed for real-time queue token display, advertisement rotations, and automated voice announcements. It is optimized for large screens and IoT environments, providing a professional and responsive experience for clinics, banks, and service centers.

---

## 🚀 Key Features

- **Dynamic Token Display**: Supports multiple counters with a responsive grid layout. Automatically splits into 2 rows or columns when counter count exceeds 4.
- **Smart Announcements**: Integrated Text-to-Speech (TTS) with multi-language support (English, Hindi, Tamil, Malayalam). Features **immediate interruption** for real-time responsiveness.
- **Advertisement Engine**: Seamless rotation of image and video advertisements with smooth crossfade transitions.
- **MQTT Connectivity**: Real-time token updates via high-performance MQTT integration.
- **Premium Theming**: Live theme picker with hue, saturation, and background intensity controls for a branded experience.
- **Robust Offline Support**: Local Room database caching ensures the display stays online even during intermittent network connectivity.
- **Remote Configuration**: Fetches all layout, color, and device settings from a centralized REST API.

---

## 📂 Documentation

Detailed documentation is available in the following files:

`docs/` is now intentionally reduced to only 3 files:
- `docs/DOCUMENTATION_INDEX.md`
- `docs/MASTER_PRODUCT_AND_FLOWS.md`
- `docs/MASTER_ENGINEERING_AND_QA.md`

| Document | Description |
|----------|-------------|
| [**DOCUMENTATION_INDEX.md**](./DOCUMENTATION_INDEX.md) | Root documentation index (consolidated). |
| [**docs/MASTER_PRODUCT_AND_FLOWS.md**](./docs/MASTER_PRODUCT_AND_FLOWS.md) | Product requirements, UX intent, and flowcharts. |
| [**docs/MASTER_ENGINEERING_AND_QA.md**](./docs/MASTER_ENGINEERING_AND_QA.md) | Engineering architecture, build baseline, and QA guidance. |
| [**SRS.md**](./SRS.md) | Software Requirements Specification, detailing core functional and non-functional requirements. |
| [**WIREFRAMES.md**](./WIREFRAMES.md) | Standalone UI wireframes for all main screens. |
| [**SRS_FLOWCHARTS_WIREFRAMES.md**](./SRS_FLOWCHARTS_WIREFRAMES.md) | Comprehensive logic flows (Mermaid), UI wireframes, and architectural diagrams. |
| [**ARCHITECTURE_AND_WORKFLOW.md**](./ARCHITECTURE_AND_WORKFLOW.md) | Technical deep-dive into the codebase, module responsibilities, and data flows. |
| [**SOURCE_CODE_DOCUMENTATION.md**](./SOURCE_CODE_DOCUMENTATION.md) | Developer guide for classes, interfaces, and utilities. |
| [**VALIDATION_REPORT.md**](./VALIDATION_REPORT.md) | Recent build and code quality validation results. |

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
