# Logic Flowcharts - CallQTV

This document details the complex logic flows for ad-looping and multi-broker connectivity.

## 1. Ad Rotation and YouTube Playback Flow

This flow ensures ads play in strict sequence and transition without user intervention.

```mermaid
flowchart TD
    A[AdArea starts with ordered ad list] --> B[Show visibleAd]
    B --> C{Ad ended or image interval elapsed?}
    C -->|Yes| D[Compute next ad index]
    D --> E{More than one ad?}
    E -->|Yes| F[Select different next ad - no same-ad replay]
    E -->|No| G[Single-ad replay allowed]
    F --> H[Load candidate ad]
    G --> H
    H --> I{Candidate type}
    I -->|Image| J[Hidden preload image]
    I -->|Video or YouTube| K[Single visible-surface path]
    J --> L[Swap when ready]
    K --> L
    L --> B

    subgraph YouTubeDetails [YouTube details]
      Y1[Init kiosk WebView + JS bridge] --> Y2[Play and wait for ended]
      Y2 --> Y3{Main-frame SSL/DNS error?}
      Y3 -->|Yes once| Y4[Try fallback URL host variant]
      Y3 -->|No or exhausted| Y5[Use ended event or long safety timeout]
    end
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

    Z[Initial connect phase] --> Z1[Connect timeout 10s]
    Z1 --> Z2[Initial retry delays: 0,1,2,4,8,12s...]

    R[MQTT message received] --> S{Payload contains CLR?}
    S -->|No| T[Normal processing]
    S -->|Yes + valid keypad serial| U[Debounced config refresh trigger]
```

## 3. Per-Counter Connection Indicators

```mermaid
flowchart TD
    msg[Verified MQTT message] --> parseBtn[Parse counter button index]
    parseBtn --> keypadSeen[Mark keypad seen for button]
    parseBtn -->|"token valid and non-zero"| dispenseSeen[Mark dispense seen for button]
    keypadSeen --> stateMap[Update LiveData maps]
    dispenseSeen --> stateMap
    tick[1s watchdog tick] --> stale{last seen older than 300s}
    stale -->|yes| redState[Set button indicator RED]
    stale -->|no| keepGreen[Keep indicator GREEN]
    redState --> stateMap
    stateMap --> ui[Counter tile left/right dots]
```
