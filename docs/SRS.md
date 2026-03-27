# Software Requirements Specification (SRS) - CallQTV

## 1. Introduction
CallQTV is a specialized Android TV application designed for queue management and digital signage. It provides real-time token updates and high-quality advertisement playback.

## 2. Functional Requirements

### 2.1 Token Management
- **FR1**: The system shall receive real-time token updates via MQTT from one or more brokers.
- **FR2**: The system shall display the current token and counter information prominently on the screen.
- **FR3**: The system shall play a notification chime and optional Text-to-Speech (TTS) announcement for every new token.
- **FR4**: The system shall support "re-call" functionality, restarting the visual blink and audio announcement if the same token is called again after 10 seconds.

### 2.2 Advertisement Playback
- **FR5**: The system shall play a sequence of advertisements (Images, Videos, YouTube) in the designated Ad Area.
- **FR6**: YouTube ads shall be displayed in a "Kiosk Mode" (clean UI, no headers/footers) and centered perfectly within the Ad Area.
- **FR7**: YouTube ads shall play until natural end when possible; if no ended event is received, a long safety timeout shall advance to the next ad.
- **FR8**: The system shall support offline ad synchronization, downloading remote assets to local storage for playback when the network is unstable.
- **FR8a**: The Ad Area shall be display-only and shall not receive focus or click interaction.
- **FR8b**: Ad audio control shall be unified: a single setting shall enable/disable sound for both normal video ads and YouTube ads.
- **FR8c**: When more than one ad exists, ad rotation shall be strict round-robin and shall not repeatedly replay the same ad.
- **FR8d**: Offscreen preloading shall be limited to image ads; YouTube/video ads shall use a single visible surface path for stability.
- **FR8e**: On YouTube SSL/DNS failures, one fallback URL retry may be attempted before skipping the ad.

### 2.3 Connectivity & Status
- **FR9**: The system shall display a "Connecting to BLUCON..." status badge when MQTT connection is lost.
- **FR10**: The status badge shall show the current retry attempt (Try X) and a second-based timer.
- **FR11**: The system shall perform a hard reconnect every 30 seconds if the connection remains lost.
- **FR12**: In multi-broker setups, connectivity shall be treated as active when any configured broker is connected, or when recent MQTT traffic is observed.
- **FR13**: The system shall trigger configuration refresh when a valid MQTT payload contains `CLR` for the mapped keypad serial.
- **FR14**: Initial BLUCON connection retries shall use an aggressive early retry cadence to reduce first-connect delay.
- **FR15**: Each counter-name tile shall show two connection indicators: left for dispense and right for keypad.
- **FR16**: Indicator state shall be tracked per counter button index.
- **FR17**: Indicators shall default RED and turn GREEN when relevant MQTT heartbeat/message is observed.
- **FR18**: If no relevant MQTT signal is observed for 5 minutes, indicators shall revert to RED.

## 3. Non-Functional Requirements

### 3.1 Performance
- **NFR1**: The application shall load cached configuration within 2 seconds of launch to provide an "instant-on" feel.
- **NFR2**: Ad transitions shall be smooth, minimizing black screens while avoiding excessive offscreen media surface churn.

### 3.2 Reliability
- **NFR3**: The system shall handle background system service exceptions (e.g., Google Play Integrity) without crashing.
- **NFR4**: The system shall gracefully handle network loss, failing over to cached content and local ads.

### 3.3 UI/UX
- **NFR5**: The UI shall be optimized for large screen Android TV displays, with clear typography and high-contrast elements.
- **NFR6**: All interactive elements (e.g., Settings) shall be navigable via a standard TV remote D-pad.
