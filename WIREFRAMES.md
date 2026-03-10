# CallQTV – UI Wireframes

**Document Version:** 2.0  
**Application:** CallQTV (Android TV Token Display)  
**Last Updated:** March 2026

---

## Overview

ASCII wireframes for the main screens of CallQTV. Layout adapts to **orientation** (config.orientation: portrait/landscape) and **display type** (layout_type 1 or 2).

**Notation:** `+` corners, `-` horizontal, `|` vertical, `[ ]` buttons/inputs.

---

## Wireframe 1: Splash Screen

```
+------------------------------------------------------------------+
|                                                                  |
|                    (Theme gradient background)                   |
|                                                                  |
|                         +----------------+                       |
|                         |                |                       |
|                         |   CallQ TV     |   (Logo, pulse)       |
|                         |     Logo       |                       |
|                         |                |                       |
|                         +----------------+                       |
|                                                                  |
+------------------------------------------------------------------+
```

- Full-screen gradient (primary color)
- Centered logo; responsive size
- Auto-navigate after ~3 seconds

---

## Wireframe 2: Customer ID Screen

```
+------------------------------------------------------------------+
|  (Gradient: White -> Theme color)                                |
|                                                                  |
|                      CallQ TV / App Name                         |
|                                                                  |
|              +----+  +----+  +----+  +----+                       |
|              | 0  |  | 0  |  | 0  |  | 0  |   <- 4 digit boxes   |
|              +----+  +----+  +----+  +----+                       |
|                                                                  |
|              [      Check License      ]                         |
|                                                                  |
|              +------------------------------------------+       |
|              |  Status: Valid / Invalid message         |       |
|              +------------------------------------------+       |
|                                                                  |
|              Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0         |
|                                              [Theme icon]        |
+------------------------------------------------------------------+
```

- 4 digit boxes; Check License; status area; theme icon

---

## Wireframe 3: Token Display – Type 1, Ads Left (Landscape)

```
+------------------------------------------------------------------+
| Company Name     dd-mm-yyyy HH:mm:ss   ● MQTT  ● Net  🌈         |
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+--------+---------+---------+---------+---------+------------------+
|        | Counter1| Counter2| Counter3| Counter4|                  |
|  Adv   +---------+---------+---------+---------+                  |
|  area  | 20  36  | T5  T6  | T9  T10 | T13 T14 |  (or A-20, A-36  |
| (img/  | T3  T4  | T7  T8  | T11 T12 | T15 T16 |   if prefix on)  |
| video) |         |         |         |         |                  |
+--------+---------+---------+---------+---------+------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Header: Company, date/time, MQTT, Network, Theme
- Ad column (left) + horizontal counter row
- Token format: plain (e.g. 20) or with prefix (A-20) per enable_counter_prifix

---

## Wireframe 4: Token Display – Type 1, No Ads

```
+------------------------------------------------------------------+
| Company Name     dd-mm-yyyy HH:mm:ss   ● MQTT  ● Net  🌈         |
+------------------------------------------------------------------+
|                    [ Test Call ]                                   |
+---------+---------+---------+---------+---------------------------+
|Counter1 | Counter2| Counter3| Counter4|                          |
+---------+---------+---------+---------+                          |
| T1  T2  | T5  T6  | T9  T10 | T13 T14 |                          |
| T3  T4  | T7  T8  | T11 T12 | T15 T16 |                          |
+---------+---------+---------+---------+---------------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Full width counters; >4 counters split into 2 rows

---

## Wireframe 5: Token Display – Type 2, Ads Left (Portrait)

```
+------------------------------------------------------------------+
| Company Name     dd-mm-yyyy HH:mm:ss   ● MQTT  ● Net  🌈         |
+------------------------------------------------------------------+
|                    [ Test Call ]                                 |
+--------+----------------------------------------------------------+
|        | Counter1 |    token metrix display                       |
|  Adv   +----------------------------------------------------------+
|  area  | Counter2 |     token metrix display                      |
|        +----------------------------------------------------------+
|        | Counter3 |     token metrix display                      |
|        +----------------------------------------------------------+
|        | Counter4 |     token metrix display                      |
+--------+----------------------------------------------------------+
|        Device: XX:XX:XX:XX:XX:XX    Version: 1.0.0               |
+------------------------------------------------------------------+
```

- Ad left; counters stacked vertically

---

## Wireframe 6: Token Display – Portrait Orientation

When config.orientation = "portrait", layout uses vertical stacking even on wide screens. Token grid per counter is taller (more rows, fewer columns).

```
+------------------------------------------------------------------+
|                                                                  |
|  (Portrait layout: ads top/bottom, counters stacked vertically)   |
|                                                                  |
|  Token grid per counter: e.g. 4 rows x 3 cols (taller)           |
|  vs landscape: 3 rows x 4 cols (wider)                           |
|                                                                  |
+------------------------------------------------------------------+
```

---

## Wireframe 7: Settings Dialog

```
+-----------------------------------------------+
|  Settings                                     |
+-----------------------------------------------+
|  +------------------------------------------+ |
|  | Company Name                             | |
|  | Company ID: 0001                         | |
|  | Device ID: XX:XX:XX:XX:XX:XX             | |
|  | App Version: 1.0.0                       | |
|  | License: Expires in 30 days              | |
|  | Token Announcement:    Enabled          | |
|  | Counter Announcement:  Disabled          | |
|  | Counter Prefix:        Enabled          | |
|  +------------------------------------------+ |
|  | Appearance                              | |
|  | Notification sound: [Ding ▼]             | |
|  | [Theme] [Counter BG] [Token BG]         | |
|  | [Clear Token History & Refresh]         | |
|  +------------------------------------------+ |
|                    [ Close ]                  |
+-----------------------------------------------+
```

- Left: Company info, Token/Counter Announcement, Counter Prefix (Enabled/Disabled)
- Right: Sound, theme, colors, clear history

---

## Wireframe 8: Theme Picker Dialog

```
+---------------------------+
|  Choose Theme Color       |
+---------------------------+
| [===== Hue gradient =====]|
| --------o----------------  Hue
|
| Color Intensity           |
| ----------o-------------   Saturation
|
| Background Transparency   |
| ----------o-------------   Intensity
|
| Preview                   |
| +------------------------+
| |  (Mini header bar)     |
| +------------------------+
|
|    [ Cancel ]  [ Apply ]   |
+---------------------------+
```

---

## Wireframe 9: Loading & Error States

**Loading:**
```
+------------------------------------------------------------------+
|              (Semi-transparent overlay)                            |
|                  Loading TV configuration...                      |
|                  [ CircularProgressIndicator ]                    |
+------------------------------------------------------------------+
```

**MQTT Error Dialog:**
```
+------------------------------------------------------------------+
|  The display could not connect to the messaging server.          |
|  Please check your network or broker settings, then tap Retry.    |
|                                                                  |
|              [ Close ]         [ Retry Connection ]              |
+------------------------------------------------------------------+
```

---

## Related Documents

- **SRS.md** – Software Requirements Specification
- **FLOWCHARTS.md** – Flow Charts (Mermaid)
