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
- **FR7**: YouTube ads shall loop automatically without user intervention.
- **FR8**: The system shall support offline ad synchronization, downloading remote assets to local storage for playback when the network is unstable.
- **FR8a**: The Ad Area shall be display-only and shall not receive focus or click interaction.
- **FR8b**: Ad audio control shall be unified: a single setting shall enable/disable sound for both normal video ads and YouTube ads.

### 2.3 Connectivity & Status
- **FR9**: The system shall display a "Connecting to BLUCON..." status badge when MQTT connection is lost.
- **FR10**: The status badge shall show the current retry attempt (Try X) and a second-based timer.
- **FR11**: The system shall perform a hard reconnect every 30 seconds if the connection remains lost.
- **FR12**: In multi-broker setups, connectivity shall be treated as active when any configured broker is connected, or when recent MQTT traffic is observed.
- **FR13**: The system shall trigger configuration refresh when a valid MQTT payload contains `CLR` for the mapped keypad serial.

## 3. Non-Functional Requirements

### 3.1 Performance
- **NFR1**: The application shall load cached configuration within 2 seconds of launch to provide an "instant-on" feel.
- **NFR2**: Ad transitions shall be smooth, with background preparation of the next ad to minimize black screens.

### 3.2 Reliability
- **NFR3**: The system shall handle background system service exceptions (e.g., Google Play Integrity) without crashing.
- **NFR4**: The system shall gracefully handle network loss, failing over to cached content and local ads.

### 3.3 UI/UX
- **NFR5**: The UI shall be optimized for large screen Android TV displays, with clear typography and high-contrast elements.
- **NFR6**: All interactive elements (e.g., Settings) shall be navigable via a standard TV remote D-pad.
