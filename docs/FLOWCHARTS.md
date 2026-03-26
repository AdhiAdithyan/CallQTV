# Logic Flowcharts - CallQTV

This document details the complex logic flows for ad-looping and multi-broker connectivity.

## 1. YouTube Automated Looping Flow

This flow ensures ads play and transition without user intervention.

```mermaid
flowchart TD
    A[Start AdArea rendering] --> B[Load YouTubeAdPlayer]
    B --> C[Init non-interactive WebView with Kiosk CSS]
    C --> D[Inject JS: Find video, apply sound setting, play]
    D --> E[Inject JS bridge + ended listener + periodic checks]
    E --> F{Video Playing?}
    F -->|Yes| G[Wait for completion]
    F -->|No| D
    G -- "Event: ended" --> H[JS bridge: CallQTVBridge.onAdEnded]
    H --> I[Kotlin: trigger latestOnVideoEnded]
    G -.fallback.-> H2[Title signal: AD_ENDED]
    H2 --> I
    I --> J[AdArea: Increment ad counter]
    J --> K{No ended callback?}
    K -->|Yes| L[Safety timeout -> trigger next ad]
    K -->|No| B
    J --> B
```

## 2. Multi-Broker Connectivity & Retry Sync

This flow handles multiple MQTT brokers and ensures the retry UI is accurate.

```mermaid
flowchart TD
    A[Monitor: reachability check per-broker] --> B{ANY reachable?}
    B -->|No| C[Set isBrokerReachable = false]
    B -->|Yes| D[Set isBrokerReachable = true]
    
    E[MQTT: onConnectionStatus] --> F{ANY connected OR recent traffic?}
    F -->|No| G[Set mqttConnected = false]
    F -->|Yes| H[Set mqttConnected = true]

    I[UI: brokerConnected = mqttConnected OR isBrokerReachable]
    I --> J{brokerConnected == false?}
    J -->|Yes| K[Increment reconnectUiSeconds]
    J -->|No| L[Reset reconnectUiSeconds = 0]

    K --> M{reconnectUiSeconds >= 30?}
    M -->|Yes| N[Call mqttViewModel.retryConnect]
    N --> O[ViewModel: Increment retryAttempts map]
    O --> P[ViewModel: _mqttRetryAttempt = max of attempts]
    P --> Q[UI: Badge shows 'Try X']

    R[MQTT message received] --> S{Payload contains CLR?}
    S -->|No| T[Normal processing]
    S -->|Yes + valid keypad serial| U[Debounced config refresh trigger]
```
