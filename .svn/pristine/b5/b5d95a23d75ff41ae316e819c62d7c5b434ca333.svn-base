# CallQTV – Gradle & Plugin Upgrade Summary (IoT/TV)

Upgrade applied with **factual** version numbers from official sources. Project targets **Android TV / IoT** token display.

---

## 1. Gradle & Plugin Versions

| Component | Before | After | Notes |
|-----------|--------|-------|------|
| **Gradle** | 8.0.1 | **8.9** | Required for AGP 8.7.x ([Gradle compatibility](https://docs.gradle.org/current/userguide/compatibility.html)) |
| **Android Gradle Plugin (AGP)** | 8.0.1 | **8.7.2** | Stable; supports compileSdk 34 |
| **Kotlin** | 1.9.24 | **1.9.25** | Compatible with Compose compiler 1.5.15 |
| **Kotlin Compose Compiler** | 1.5.14 | **1.5.15** | Matched to Kotlin 1.9.25 |
| **Google Play Services** | 4.4.2 | 4.4.2 | Unchanged (root) |

---

## 2. Files Changed

- **`gradle/wrapper/gradle-wrapper.properties`**  
  `distributionUrl` → `gradle-8.9-bin.zip`

- **`build.gradle` (root)**  
  AGP `8.0.1` → `8.7.2`; Kotlin `1.9.24` → `1.9.25`. Comment added for TV/IoT.

- **`app/build.gradle`**  
  - `kotlinCompilerExtensionVersion` → `1.5.15`  
  - Dependencies updated (see table below)  
  - ExoPlayer → **Media3** (ExoPlayer successor)  
  - **BuildConfig:** `BUILD_TYPE_DEVICE = "android_tv"` for IoT/TV targeting  

- **`gradle.properties`**  
  - `org.gradle.parallel=true` enabled  
  - `org.gradle.caching=true` added  
  - `android.suppressUnsupportedCompileSdk=34` commented out (AGP 8.7.2 supports SDK 34)

---

## 3. App Dependency Upgrades (Factual / IoT-Relevant)

| Dependency | Before | After |
|------------|--------|-------|
| kotlin-stdlib | 1.9.24 | **1.9.25** |
| Compose BOM | 2024.05.00 | **2024.08.00** |
| activity-compose | 1.9.0 | **1.9.2** |
| appcompat | 1.6.1 | **1.7.0** |
| play-services-ads | 8.4.0 | **23.0.0** |
| play-services-auth | 8.4.0 | **21.2.0** |
| play-services-maps | 8.4.0 | **19.0.0** |
| play-services-location | 8.4.0 | **21.2.0** |
| Retrofit | 2.9.0 | **2.11.0** |
| converter-gson | 2.9.0 | **2.11.0** |
| OkHttp logging-interceptor | 4.11.0 | **4.12.0** |
| lifecycle-* | 2.6.1 | **2.7.0** |
| kotlinx-coroutines-android | 1.7.3 | **1.8.1** |
| ZXing core | 3.4.1 | **3.5.3** |
| Room | 2.6.1 | 2.6.1 (unchanged) |
| core | 1.6.0 | **core-ktx 1.15.0** |
| ExoPlayer 2.x | 2.19.1 | **Media3 1.4.1** (exoplayer → media3-exoplayer, media3-ui) |
| ML Kit barcode-scanning | 17.2.0 | **17.3.0** |
| Lottie | 6.0.0 | **6.5.1** |

---

## 4. IoT / Android TV Alignment

- **BuildConfig:** `BuildConfig.BUILD_TYPE_DEVICE = "android_tv"` for runtime checks or analytics.  
- **Comments** in root and app `build.gradle` state the project is for **Android TV / IoT** token display.  
- **Stack** remains IoT-friendly: MQTT (Paho), Room (config cache), Retrofit (TV config API), Compose (TV UI), Media3 (playback if needed).

---

## 5. Build Instructions

1. **First build after upgrade** (downloads Gradle 8.9 once):  
   `.\gradlew.bat --version`  
   then  
   `.\gradlew.bat assembleCallQTVDebug`

2. If the wrapper fails to create the Gradle distribution dir (e.g. in restricted envs), run with permissions that allow writing to the user `.gradle` directory, or open the project in Android Studio and use **File → Sync Project with Gradle Files**.

3. **JDK:** AGP 8.7.2 and Gradle 8.9 work with **JDK 17** (already in use).

---

## 6. Media3 vs ExoPlayer

ExoPlayer dependencies were replaced with **AndroidX Media3**:

- `com.google.android.exoplayer:exoplayer*` → `androidx.media3:media3-exoplayer:1.4.1`  
- `com.google.android.exoplayer:exoplayer-ui` → `androidx.media3:media3-ui:1.4.1`

No ExoPlayer/Media3 API usage was found in app source; this is a dependency-only change. If you add playback code later, use the [Media3 package names](https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide).

---

*Summary generated after Gradle and plugin upgrade for CallQTV (Android TV / IoT).*
