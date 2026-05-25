# CallQTV — QA Validation Checklist

Acceptance tests aligned with current source (app `1.0.1`, Room v17, May 2026). Full context: [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) §3.4.1 (VIP **ER-**), §3.5–§3.5.2 (announcements); requirements: [SRS.md](./SRS.md).

---

## 1. Build and environment

- [ ] `./gradlew assembleCallQTVDebug` (or project debug variant) succeeds
- [ ] Install on Android TV or emulator **API 26+**
- [ ] `compileSdk` 35 / `targetSdk` 35 / Java 17

---

## 2. Startup and configuration

- [ ] Cached-first: with existing DB, main UI appears without long blocking overlay
- [ ] Cold install: loading overlay until first config available
- [ ] TTS “preparing voice engine” overlay only when audio **language** changes during config load (not for entire config overlay)
- [ ] First announcement after cold start: tile at chime **and speech plays** (not tile-only silence from stuck `awaitReady`)
- [ ] First announcement after long idle (2+ min): no multi-second gap before real speech (idle keep-warm ~15s)
- [ ] Settings **Refresh** fetches latest config (counters, ads, devices)

---

## 3. MQTT and tokens

- [ ] Fixed payload routes by **keypad serial** in frame (e.g. `$0NV-AbCAL0K000625-0002*` → counter for SN `AbCAL0K0006` / configured SN), **not** by index-18 digit inside serial
- [ ] Counters with only **`keypad_index`** in config (no matching `button_index` on event key) still update the correct tile (`findCounterEntityForMqttRoute` keypad fallback)
- [ ] `"$0PA-AeCAL0K0001lo-0008*"` → token **8** on correct counter
- [ ] With `token_format` **`T1`** and counter code **`NU`**, display **`NU-2`** for token `2` (**no** literal `T` after hyphen)
- [ ] `"$0KJ-AbCAL0K000111-0015*"` → token **15** after prior CLR
- [ ] Type **B** transferred payload → **no** token tile change (DB-only)
- [ ] Type **C** → special message replaces area; multiline readable (padding, line height)
- [ ] VIP/emergency (index 4 = `D`) shows **ER-** prefix on tile and in TTS even when `enable_counter_prefix` is off

### 3.1 CLR

- [ ] `"$000-AbCAL0K000101CLR0*"` clears intended counter(s) for SN `AbCAL0K0001`, route **1**
- [ ] `"$000-AbCAL0K000303CLR0*"` clears route **3** for SN `AbCAL0K0003`
- [ ] `keypadsJson` lists keypad SN + `counters[]` for predictable resolution
- [ ] Pre-clear tokens do **not** reappear after app restart
- [ ] Next valid token after CLR appears normally
- [ ] **Immediate** config API after CLR (not only after 30s throttle)

---

## 4. Announcements

- [ ] Chime plays on new token (`playCueUi`)
- [ ] **Current** token tile appears at chime cue (not after TTS ends)
- [ ] **Back-to-back tokens:** second counter tile does **not** update until first token TTS fully finishes
- [ ] TTS engine bind/prime overlaps map update and chime (`async awaitReady` started before `processTokenUpdateForKeys`)
- [ ] If TTS warm exceeds 12s, log `TTS not ready before announce` but **announcement still plays**
- [ ] Real speech after duck/prime path; not blocked on full custom chime clip end
- [ ] Logcat: `TTS initialized successfully` before first token; optional `TTS init synthesis prime complete` shortly after
- [ ] Chime on UI-only updates (e.g. VIP overlay change) even when TTS does not speak
- [ ] TTS only when `enable_token_announcement` and primary announce rules (`speakTokenAnnouncement`)
- [ ] Re-call same top token after **10s** → chime + blink + TTS again
- [ ] Identical raw payload within **10s** → single handling
- [ ] With **ad sound** on: ads duck before speech; quiet **wellcome** prime (if needed) plays **after** duck, then real announcement; volume restores after speech
- [ ] With **ad sound** off: no duck; prime (if needed) immediately before real announcement
- [ ] Idle with announcements on: logcat shows `Synthesis prime started` about every **15s** (keep-warm); skipped while speaking
- [ ] Long special message: announcement completes before next token (no 6s cut-off)

---

## 5. Connectivity and indicators

- [ ] Reconnect badge when broker lost: “Connecting to BLUCON…”, try count, timer
- [ ] Effective “connected” when any broker up or recent traffic
- [ ] Counter dots: green on activity, red after ~5 min idle

---

## 6. Theming and settings UI

- [ ] **App theme**: solid and **gradient** — Material primary = first gradient stop
- [ ] **Counter/token** areas show vertical gradients for `GRADIENT:` values
- [ ] **Scrolling footer** uses horizontal theme strip (`getTickerStripBackgroundBrush`)
- [ ] **Token blink**: whole-tile vs text-only (Settings → Display) when server blink on

### 6.1 Color pickers (TV)

- [ ] Open **App Theme** / **Counter Background** / **Token Background** without ANR
- [ ] Initial D-pad focus on **current** selection (not Close only)
- [ ] **Gold ring** visible on focused swatch while navigating
- [ ] **White border/dot** on saved selection when not focused
- [ ] Select new color → applies and dialog closes
- [ ] No crash when grid contains duplicate hex colors (keys use name+index)

### 6.2 Notification sound

- [ ] **Audios** → Notification sound: preview on select
- [ ] Focus on current sound; gold highlight when navigating
- [ ] Chime matches selected tone on next token

---

## 7. Advertisements

- [ ] Image / video / YouTube rotate round-robin when multiple ads
- [ ] Ad area non-interactive
- [ ] Offline ads sync when enabled
- [ ] YouTube kiosk: minimal chrome, pinned video
- [ ] **Large image** (e.g. 4K JPEG) appears without long stall (viewport-sized Coil decode)
- [ ] **Large video** (1080p/4K MP4 or HLS) starts within a few seconds; no decoder crash on side strip
- [ ] Mixed aspect creatives letterbox correctly (fit, not cropped) in ad pane
- [ ] Side ad on 1920×1080: pane ~40% width; portrait creative looks correct
- [ ] `ad_interval` advances static/image ads; video advances on end

---

## 8. Diagnostics export

- [ ] Settings → System → Export Logs/Config Snapshot completes
- [ ] Toast shows path
- [ ] With **SD/USB** removable: ZIP under `Android/data/.../files/Download/CALLQTV_EXPORT/`
- [ ] Without removable: `Downloads/CALLQTV_EXPORT` or app-scoped fallback

---

## 9. Payload log retention

- [ ] Valid keypad payloads create **`mqtt_payload_logs`** rows with received time
- [ ] **Displayed** time set only after UI renders (not on CLR-only / DB-only paths unless implemented)
- [ ] Upload to **`api/external/token-report`** when network available (debounced sync)
- [ ] Uploaded rows older than **2 days** removed
- [ ] Pending/unuploaded rows retained

---

## 9.1 Unit tests (CI / local)

- [ ] `./gradlew test` passes (`SemanticMqttParserTest`, `KeypadPayloadParserTest`, `MqttCounterRoutingTest`, `TvConfigParsingTest`, …)

---

## 10. Known limitations (not failures)

- Duplicate hex in preset list (e.g. Dark Green / Emerald `#1B5E20`) — first match used for initial focus
- System “read-only” when copying export ZIP via Files app to read-only USB — external to app Toast
- Some Gradle/AGP deprecation warnings — maintenance backlog
- DRM video, blocked/private YouTube, or broken URLs may skip to next ad (not a resolution limit)
- SVG and exotic codecs may fail; WMV uses WebView fallback

---

*Update this checklist when [MASTER_DOCUMENTATION.md](./MASTER_DOCUMENTATION.md) changes.*
