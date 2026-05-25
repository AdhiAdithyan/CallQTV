# CallQTV — Software Requirements Specification

**Product:** CallQTV Android TV client (`com.softland.callqtv`)  
**Audience:** Product, QA, integration  
**Canonical reference:** [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) (full behavior and architecture)

---

## 1. Purpose

CallQTV is an Android TV application for **real-time queue token display** and **digital signage** in clinics, banks, and service centers. It receives tokens over **MQTT**, loads layout and branding from a **REST** TV configuration API, plays **advertisements**, and announces tokens with **chime + TTS**.

---

## 2. Functional requirements

### 2.1 Startup and configuration

| ID | Requirement |
|----|-------------|
| FR-01 | On launch, load cached TV config, counters, ads, and devices from Room before blocking the UI. |
| FR-02 | If cache exists, render the main display immediately while background sync continues. |
| FR-03 | If cache is absent, show a loading overlay until minimum data is available. |
| FR-04 | Initialize TTS in a separate phase from config loading (“preparing voice engine”). |
| FR-05 | Support device registration, customer ID, and license validation flows before main display. |

### 2.2 MQTT token display

| ID | Requirement |
|----|-------------|
| FR-10 | Receive token updates from one or more MQTT brokers. |
| FR-11 | Validate payloads by extracting keypad serial and matching DB-mapped keypad/device records. |
| FR-12 | Route fixed payloads to UI by mapped **button index** first; persist tokens under canonical per-counter keys. |
| FR-13 | Support fixed `$...*` payloads, **`000-` CLR** frames (variable token digits before `CLR`), and supported short-wrapper shapes. |
| FR-14 | Fixed protocol index 4: **`A`/`E`** and transferred **`B`** → DB-only, **no** live token UI. |
| FR-15 | Type **`C`** → special message replacing token area; blink; dedicated TTS path. |
| FR-16 | Types **`D`**, **`-`**, normal → standard token flow; **`D`** (index 4) = VIP/emergency with fixed **`ER`** prefix on display and TTS. |
| FR-17 | Display current and historical tokens per counter per server layout (`display_rows` / `display_columns`). |
| FR-18 | When counter count **> 4**, use split grid layout (2 rows or columns). |
| FR-19 | Suppress duplicate handling of **identical raw payload** within **10 seconds**. |
| FR-20 | Allow **re-call** of same top token after **10 seconds** (fresh blink/chime/TTS per rules). |
| FR-21 | Log payloads with **received** timestamp; set **displayed** when UI actually renders. |
| FR-22 | Upload payload logs to backend; delete **uploaded** rows older than **2 days** only; never delete unuploaded rows. |

### 2.3 CLR (clear)

| ID | Requirement |
|----|-------------|
| FR-30 | On valid CLR, clear live token state and persisted `token_history` for resolved counter key set. |
| FR-31 | Resolve route from digit **immediately before** `CLR` in `000-` frames; match `keypadsJson` `keypad_index` or `button_index`. |
| FR-31a | For **normal** fixed/legacy token payloads, resolve counter from **keypad serial in the frame** only; **do not** treat fixed-frame index 18 (1-based) as `keypad_index`. |
| FR-32 | If route match is ambiguous, clear **all counters** listed for that keypad SN in config. |
| FR-33 | Always post `tokensPerCounter` after a CLR attempt. |
| FR-34 | Trigger **immediate** full TV configuration fetch on CLR (same as Settings **Refresh**), bypassing 30s display-VM MQTT refresh throttle. |
| FR-35 | Reset dedupe / queued-payload state for affected serial (route-aware when known). |

### 2.4 Announcements (chime + TTS)

| ID | Requirement |
|----|-------------|
| FR-40 | Serialize token UI events under `announcementMutex` so chime/TTS do not overlap; hold mutex through TTS `onDone` when speaking. |
| FR-41 | **`playCueUi`**: play notification chime, publish token snapshot, and blink when map/VIP changes or primary re-call after >10s. |
| FR-42 | **`speakTokenAnnouncement`**: speak TTS only when primary-key announce rules pass and `enable_token_announcement` is on. |
| FR-43 | Publish **current** token tile at **chime cue start** (`onAudioStart` + fallback); **next** token must not publish until previous announcement completes. |
| FR-44 | Normal TTS: `Token`, optional spelled counter prefix (when `enable_counter_prefix`), token label, optional counter name (`enable_counter_announcement`). |
| FR-44a | VIP/emergency (`D`): TTS always spells **`ER`** before the token, even when `enable_counter_prefix` is off. |
| FR-45 | Special TTS: message text then optional counter name (single space). |
| FR-46 | When `enable_ad_sound` is on, duck ExoPlayer and YouTube WebView video during TTS; restore after speech. |
| FR-47 | Support configurable notification chime (~51 system tones in `ThemeColorManager.notificationSoundOptions`). |
| FR-48 | Optional per-counter or global custom chime URL before system tone. |
| FR-49 | When speech is required, start `async { awaitReady() }` before map update; await bind (12s timeout) before TTS; do not block speech on full custom chime duration; attempt TTS even if warm times out. |
| FR-49a | TTS engine bind (`finishInitAttempt`) must not wait for synthesis prime to finish (prime runs async). |
| FR-50 | Warm TTS from cached/fresh `tv_config` during config load when `enable_token_announcement` is on (`warmTokenAnnouncerIfEnabled`). |
| FR-51 | Play quiet synthesis prime (`SYNTHESIS_PRIME_PHRASE` **wellcome**, `PRIME_VOLUME` 0.01) on init, `warmUp(performPoke=true)`, `awaitReady`, when cold, and every **15s** idle via heartbeat while announcements are on. |
| FR-52 | When `enable_ad_sound` is on, run synthesis prime **only after** ad ducking, not over full-volume ads. |
| FR-53 | Keep TTS engine bound with silent heartbeat every **3s**; re-bind after repeated heartbeat failures. |

### 2.5 Token display

| ID | Requirement |
|----|-------------|
| FR-55 | Apply `token_format` for on-screen padding; patterns `T1`/`T2` must **not** insert a literal `T` before the number. |
| FR-56 | When counter prefix is enabled, display `{counter.code}-{formattedToken}` (e.g. `NU-2`). |
| FR-57 | VIP/emergency (index 4 = **`D`**): display **`ER-{formattedToken}`** on the primary slot even when counter prefix is disabled. |

### 2.6 Connectivity and status

| ID | Requirement |
|----|-------------|
| FR-60 | Treat connectivity as active if **any** broker is connected **or** recent MQTT traffic observed. |
| FR-61 | Show reconnect badge with “Connecting to BLUCON…”, retry attempt, and timer (~30s cycle). |
| FR-62 | Per counter tile: left dot = dispense, right dot = keypad; red default, green on activity, red after **5 min** idle. |

### 2.7 Advertisements

| ID | Requirement |
|----|-------------|
| FR-70 | Play **image** (jpg/png/gif/webp/bmp/svg), **video** (mp4, HLS `.m3u8`, DASH `.mpd`, …), **YouTube**, and generic **http(s) web** URLs in the ad area (`AdMediaType`). |
| FR-71 | Ad area is display-only (non-interactive). |
| FR-72 | Strict **round-robin** when multiple ads configured. |
| FR-73 | Support offline ad sync (`AdDownloader` → `CALLQTV_ADV`). |
| FR-74 | Single setting controls ad sound for video and YouTube. |
| FR-75 | Scale decode/playback to **measured ad pane** (`AdViewportSizing`, `MediaEngine.updateViewport`); Coil images at pane size with preload. |
| FR-76 | Video track selection and bitrate capped to pane area (not fixed 1080×1920); faster buffer for first-frame start. |
| FR-77 | Ad pane uses **40%** (or **50%** when ≤2 counters) of main body; placement via `ad_placement`. |

### 2.8 Local settings (`ThemePrefs`)

| ID | Requirement |
|----|-------------|
| FR-80 | Persist on device: app theme, counter background, token background (solid or `GRADIENT:#…`), notification sound, 24h clock, blink style, ad/YouTube toggles. |
| FR-81 | Material3 `primary` from first gradient stop when theme is `GRADIENT:`; counter/token use full vertical gradient brushes. |
| FR-82 | Scrolling footer background follows `theme_color` via horizontal `getTickerStripBackgroundBrush`. |
| FR-83 | Token blink: whole tile vs text-only (`token_blink_mode`), gated by server `blink_current_token`. |
| FR-84 | Settings color pickers: grid of presets; TV D-pad focus on current selection; gold focus ring; white marker for saved selection; no ANR on open. |
| FR-85 | Settings notification sound picker: preview chime; focus on current selection. |

### 2.9 Diagnostics

| ID | Requirement |
|----|-------------|
| FR-90 | Export logs/config snapshot ZIP from Settings → System. |
| FR-91 | Prefer removable storage `Android/data/.../Download/CALLQTV_EXPORT/`, else MediaStore Downloads, else app-scoped fallback. |

---

## 3. Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-01 | **minSdk** 26, **targetSdk** 35, Kotlin/Java **17**. |
| NFR-02 | Main display optimized for **landscape** Android TV. |
| NFR-03 | Cached-first startup; usable on intermittent network. |
| NFR-04 | MQTT processing off main thread; UI must remain responsive during color picker open (throttled brush warm). |
| NFR-05 | `KEEP_SCREEN_ON` during main display. |
| NFR-06 | Large heap enabled where configured for media/WebView. |

---

## 4. Server configuration dependencies

- **`tv_config`**: counters, layout, ads, MQTT brokers, announcement flags, scroll text, etc.
- **`keypadsJson`**: keypad SN → `counters[]` with `keypad_index` / `button_index` — required for reliable CLR and routing.
- **UI resolution**: `MqttViewModel` maps **keypad SN** → storage key (prefer `button_index`); **CLR** may use route digit before `CLR`. `findCounterEntityForMqttRoute` binds UI rows by `button_index` then `keypad_index`.
- **`token_format`**: `T1`/`T2` pad digits only (no literal `T` on screen); combine with counter code prefix when enabled.
- **Room v17**: `mqtt_payload_logs` for received/displayed/upload audit; see `MqttPayloadLogRepository`.
- **Tests**: JVM suite includes `MqttCounterRoutingTest`, `KeypadPayloadParserTest`, `SemanticMqttParserTest` — run `./gradlew test`.
- Misconfigured keypad SN or empty `counters[]` may cause CLR to log “no map keys”; fix on server.

---

## 5. Out of scope (client)

- Editing MQTT broker credentials without config API (read from server).
- Interactive ad area (touch/click on ads).
- DRM-protected streams, exotic codecs, or blocked YouTube embeds (may skip after timeout).
- Fixed required creative resolution (app accepts any size; scales with fit + viewport decode).
- Custom user-defined hex entry in pickers (preset grids only).

---

*Derived from CallQTV May 2026 source (app `1.0.1`, Room v17). Update when [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) changes.*
