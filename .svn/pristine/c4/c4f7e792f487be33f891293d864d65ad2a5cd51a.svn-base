# Wireframes and Layout - CallQTV

This document provides a visualization of the principal screen layouts and key components in CallQTV.

## 1. Main Display Activity (`TokenDisplayActivity`)

```mermaid
graph TD
    subgraph Screen ["Television Viewport (1080p/4K)"]
        subgraph Header ["Header Area (Top 10-15%)"]
            A[App Logo]
            B[Company Name]
            C[Date/Time & Status Indicators]
        end
        
        subgraph MainBody ["Main Content Area"]
            direction LR
            subgraph TokenSection ["Token History"]
                D1[Token 1 (Call/Blink)]
                D2[Token 2]
                D3[Token 3]
            end
            
            subgraph AdArea ["Advertisement Container (AdArea)"]
                E[YouTube Player / Video / Image]
                E1[Strictly Centered Content]
            end
        end
        
        subgraph Overlays ["Dynamic Overlays"]
            F[Pending Calls Badge - Top Right]
            G[Reconnect Status Badge - Top Center of Main Content]
            H[Settings / Theme Dialogs - Center Modal]
        end
    end
```

## 2. Reconnect Status Badge Layout

This component appears at the top-center of the main content area when the connection is lost.

```mermaid
graph LR
    subgraph Badge ["ReconnectStatusBadge (Surface)"]
        B["Text: 'Connecting to BLUCON... Try X | Ys'"]
    end
```

## 3. Ad Area Constraints

The `AdArea` ensures content is centered and clipped to its bounds.

```mermaid
graph TD
    subgraph Container ["AdArea Container (Box)"]
        subgraph Inner ["Clipped Inner View (Modifier.clip)"]
            subgraph CenterTarget ["Alignment.Center"]
                X["Media Content (YouTube Player / Image)"]
            end
        end
    end
```

## 4. Counter Tile Indicators

Each counter-name tile has two inner-corner status dots:
- Left dot: dispense connectivity
- Right dot: keypad connectivity

```mermaid
graph LR
    subgraph CounterNameTile ["Counter Name Tile"]
        L["Left Dot (Dispense)"]
        N["Counter Name Text"]
        R["Right Dot (Keypad)"]
    end
    L --> C1["RED default / GREEN active"]
    R --> C2["RED default / GREEN active"]
```
