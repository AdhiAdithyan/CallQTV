# CallQTV Documentation Index (Root)

## Canonical vs split docs

**[docs/MASTER_DOCUMENTATION.md](./docs/MASTER_DOCUMENTATION.md)** is the single source of truth. The split files below are derived for product, QA, and engineering review; update the master first, then refresh splits when behavior changes.

## Primary documents

| Document | Description |
|----------|-------------|
| [docs/MASTER_DOCUMENTATION.md](./docs/MASTER_DOCUMENTATION.md) | Full architecture, runtime behavior, MQTT/CLR, announcements, theming, settings pickers, ads, diagnostics, flowcharts, QA notes, changelog |
| [docs/REBUILD_PROMPT.md](./docs/REBUILD_PROMPT.md) | Greenfield / handoff specification aligned with the current codebase |

## Split documents (May 2026)

| Document | Description |
|----------|-------------|
| [docs/SRS.md](./docs/SRS.md) | Software requirements (FR/NFR tables) |
| [docs/WIREFRAMES.md](./docs/WIREFRAMES.md) | Main UI and settings/picker wireframes |
| [docs/SRS_FLOWCHARTS_WIREFRAMES.md](./docs/SRS_FLOWCHARTS_WIREFRAMES.md) | Flowcharts: startup, MQTT, announcements, upload, pickers |
| [docs/ARCHITECTURE_AND_WORKFLOW.md](./docs/ARCHITECTURE_AND_WORKFLOW.md) | Technical architecture and workflows |
| [docs/SOURCE_CODE_DOCUMENTATION.md](./docs/SOURCE_CODE_DOCUMENTATION.md) | Source tree and class index |
| [docs/QA_VALIDATION_CHECKLIST.md](./docs/QA_VALIDATION_CHECKLIST.md) | QA acceptance checklist |

## Superseded references

Older paths such as `docs/MASTER_PRODUCT_AND_FLOWS.md`, `docs/MASTER_ENGINEERING_AND_QA.md`, and root-level standalone SRS/wireframe files are **superseded** by the table above unless reintroduced explicitly.

## Last aligned with source

**May 2026** — App `1.0.1` / Room **v17** / 83 main Kotlin files (`ui/` includes `AdViewportSizing.kt`). Highlights: viewport-aware ads (`AdArea` + `MediaEngine.updateViewport`), all `AdMediaType` formats, `MqttCounterRouting`, MQTT payload logs, CLR/config refresh, announcements/theming, diagnostics, JVM tests. Full ad matrix and limitations: [MASTER_DOCUMENTATION.md](./docs/MASTER_DOCUMENTATION.md) §3.10. Class index: [SOURCE_CODE_DOCUMENTATION.md](./docs/SOURCE_CODE_DOCUMENTATION.md).
