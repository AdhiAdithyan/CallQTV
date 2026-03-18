# CallQTV: Architecture & Implementation Guide

This document provides a detailed technical overview of the CallQTV Android application, explaining the core components, their interactions, and the specialized logic used for high-reliability token announcements.

---

## 1. System Architecture (MVVM)
The app follows a standard **Model-View-ViewModel (MVVM)** architecture using Jetpack Compose for the UI and Kotlin Coroutines for asynchronous operations.

### Key Layers:
- **View**: [TokenDisplayActivity](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/ui/TokenDisplayActivity.kt#100-182) (Jetpack Compose).
- **ViewModels**: 
    - [MqttViewModel](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/viewmodel/MqttViewModel.kt#22-755): Manages MQTT connections, message parsing, and token history.
    - [TokenDisplayViewModel](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/viewmodel/TokenDisplayViewModel.kt#23-261): Manages TV configuration, license status, and clock/date updates.
- **Repository**:
    - [TvConfigRepository](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/data/repository/TvConfigRepository.kt#36-437): Handles API calls to fetch/cache the TV configuration.
    - `TokenHistoryRepository`: Persists the last 15 tokens per counter in Room DB.
- **Data Source**:
    - [AppDatabase](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/data/local/AppDatabase.kt#10-61) (Room): Local persistence for configurations, counters, ad files, and token history.
    - `RetrofitClient`: Optimized OkHttp/Retrofit client with automatic retries and service caching.

---

## 2. MQTT Communication System

### [MqttClientManager.kt](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/data/repository/MqttClientManager.kt)
Wraps the Paho MQTT Android Service to provide a robust, state-aware interface.
- **Connection Guard**: Implements [isConnecting](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/viewmodel/MqttViewModel.kt#87-88) and [isConnected()](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/data/repository/MqttClientManager.kt#300-301) checks to prevent redundant socket openings.
- **Auto-Reconnect**: Configured with `isAutomaticReconnect = true` and `isCleanSession = false` to ensure zero message loss during brief network hiccups.
- **Deduplication**: Logic in [MqttViewModel](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/viewmodel/MqttViewModel.kt#22-755) ensures only one manager exists per broker URI.

### [SemanticMqttParser.kt](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/utils/SemanticMqttParser.kt)
A hybrid parser that extracts token/counter data from unstructured strings.
- **Fixed Protocol**: Fast-path for payloads like `$02026bCAL0K00071100030*`.
- **Regex Fallback**: Uses pre-compiled patterns to find "Token: X" or "Counter: Y" in plain text.
- **Topic Fallback**: If the payload is just a number (e.g., "35"), it extracts the counter ID from the MQTT topic structure.

---

## 3. Token Processing & History

### Continuous Publishing Heartbeat
Modified to ensure high availability on the broker status board.
- **Topic**: Always publishes to `fr/status`.
- **Payload**: `$<SERIAL>000000#`.
- **Execution**: Runs every 5 seconds unconditionally in `MqttViewModel.startContinuousPublishLoop`.

### Token History Logic
- **Storage**: Top 15 unique tokens per counter.
- **Complexity**: Optimized as O(N) memory operations.
- **Persistence**: Tokens are saved to the Room DB asynchronously via [persistToken](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/viewmodel/MqttViewModel.kt#709-718).
- **Announcement Trigger**: Returns `true` only if a token is new or its position has changed (e.g., re-calling a token moves it to the top).

---

## 4. UI & Performance Optimizations

### Recomposition Filtering
To maintain 60FPS on TV hardware during high-load MQTT traffic:
- **Helper**: [getTokensForCounter](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/ui/TokenDisplayActivity.kt#2236-2257) extracts only the tokens needed for a specific board.
- **Parent Extraction**: [TokenDisplayContent](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/ui/TokenDisplayActivity.kt#658-887) pre-computes these lists using `remember(tokensPerCounter, counter)`.
- **Result**: Only the single [CounterBoard](file:///f:/I%20Drive/Adithyan/CallQTV/app/src/main/kotlin/com/softland/callqtv/ui/TokenDisplayActivity.kt#1307-1495) affected by a message recomposes; the rest of the dashboard remains stationary.

### Connection Feedback
- **BLUCON Indicator**: Defaults to Red (Error). Turns Green only on active connection.
- **Overlay**: `AnimatedLoadingOverlay` shows "Connecting to BLUCON..." whenever the connection state is lost, providing immediate visual feedback.

---

## 5. Announcement Engine (`TokenAnnouncer`)
- **TTS Library**: Uses `Android TextToSpeech` with `QUEUE_ADD` for sequential announcements.
- **Heartbeat Mechanism**: Sends a 10ms silent utterance every 8 seconds to prevent the system audio hardware from entering a sleep/power-saving state (which causes "clipped" starts on the first word).
- **Language Selection**: Dynamically selects Hindi/Tamil/Malayalam/English voices based on the TV configuration.

---

## 6. Network Reliability
- **RetryInterceptor**: Implements exponential backoff (2s, 4s, 6s) for 5xx errors and timeouts.
- **Service Caching**: `RetrofitClient.getApiService()` caches the reflective service instance to avoid performance penalties on repeated calls.
- **SSL**: Uses `UnsafeOkHttpClient` for internal broker environments that utilize self-signed certificates.

---

## 7. Configuration Flow
1. **Fetch**: `TokenDisplayViewModel.loadData()` calls the backend with MacID.
2. **Cache**: Results mapped to Room (`TvConfigEntity`, `CounterEntity`).
3. **Mqtt Init**: ViewModels initialize connections for each broker found in the config.
4. **Display**: UI subscribes to LiveData streams and renders the "Counter Board" grid.
