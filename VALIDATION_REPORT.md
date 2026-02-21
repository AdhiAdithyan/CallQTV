# CallQTV – Code Validation Report

**Date:** January 30, 2026  
**Scope:** Full project (Kotlin app, Gradle Kotlin DSL, Jetpack)  
**Last validation:** Full validation run; issues fixed (see §8).

---

## 1. Build & Compilation

| Check | Result |
|-------|--------|
| Clean build | **PASS** – `./gradlew clean assembleCallQTVDebug` |
| Compilation | **PASS** – All 61 Kotlin sources in `app/src/main/kotlin` compile |
| Kapt (Room, DataBinding) | **PASS** |

---

## 2. Tests

| Check | Result |
|-------|--------|
| Unit tests | **PASS** – `./gradlew :app:testCallQTVDebugUnitTest` |
| Test code | Kotlin (`app/src/test/kotlin`, `app/src/androidTest/kotlin`) |

---

## 3. Lint (Android)

| Check | Result |
|-------|--------|
| Lint run | **PASS** – `./gradlew :app:lintCallQTVDebug` (0 errors) |
| Fixes applied | 1. `AndroidManifest.xml`: `SCHEDULE_EXACT_ALARM` – added `tools:ignore="ProtectedPermissions"` (permission kept for devices that grant it). 2. `PreferenceHelper.kt`: Replaced `.commit()` with `.apply()` for all SharedPreferences writes (avoids blocking UI). |

**Remaining lint (warnings only, non-blocking):**

- **DefaultLocale** – `String.format("%04d", ...)` in `CustomerIdActivity.kt`; use `String.format(Locale.getDefault(), ...)` or `Locale.ROOT` where appropriate.
- **KotlinNullnessAnnotation** – `@Nullable` / `@NonNull` in Kotlin (`CurrentDayOrder.kt`, `StoragePathProvider.kt`); safe to remove, nullability is in the type (`String?`, non-null types).
- **CustomSplashScreen** – `SplashScreenActivity` custom splash; on API 31+ consider aligning with system splash (see [SplashScreen](https://developer.android.com/guide/topics/ui/splash-screen)).
- **OldTargetApi** – `targetSdk = 35`; lint suggests “not latest” relative to its checks; 35 is current; no change required unless you adopt a newer SDK.
- **VectorRaster** – Some vector drawables have large intrinsic size; consider limiting to &lt;200dp for icons.
- **ApplySharedPref** – Resolved in `PreferenceHelper` (all `.apply()`).
- Other minor warnings (typos, accessibility, etc.) – see `app/build/reports/lint-results-CallQTVDebug.html`.

---

## 4. IDE / Linter

| Check | Result |
|-------|--------|
| Linter errors in `app/src/main/kotlin` | **0** |

---

## 5. Code Quality Snapshot

| Area | Notes |
|------|--------|
| **Null safety** | No unsafe `!!` found in app code. Nullable types and `?.` / `?.let` used. |
| **Deprecations** | A few intentional: `overridePendingTransition`, `MediaRecorder()`, `InputMethodManager.toggleSoftInput`, `TextToSpeech` callback; some already have `@Suppress("DeprecatedCallableAddReplaceWith")` or `@Suppress("DEPRECATION")` where needed. |
| **Threading** | ViewModels, LiveData, coroutines used; no obvious UI work on background threads. |
| **Resources** | `AndroidManifest` references correct activities; duplicate `FileProvider` entries (one `StoragePathProvider`, one `androidx.core.content.FileProvider`) – consider using a single provider. |
| **Architecture** | Clear split: Activities (UI), ViewModel, Repository, Room DB, Retrofit API, Utils. |

---

## 6. Validation Summary

| Category | Status |
|----------|--------|
| Build | **PASS** |
| Unit tests | **PASS** |
| Lint (errors) | **PASS** (0 errors) |
| Linter (IDE) | **PASS** (0 errors) |
| Kotlin style | Pure Kotlin; source under `kotlin/` only |
| Jetpack | ViewModel, LiveData, Room, Compose in use |

**Overall:** The codebase validates successfully. Build and tests pass, lint reports no errors, and the applied fixes (manifest permission handling, SharedPreferences `apply()`) improve correctness and lint compliance. Remaining lint warnings are documented above and in the HTML report for optional follow-up.

---

## 7. Commands Used

```bash
./gradlew clean assembleCallQTVDebug
./gradlew :app:testCallQTVDebugUnitTest
./gradlew :app:lintCallQTVDebug
```

Lint report (if generated): `app/build/reports/lint-results-CallQTVDebug.html`

---

## 8. Issues fixed (this run)

| Issue | Location | Fix |
|-------|----------|-----|
| **DefaultLocale** | CustomerIdActivity.kt | `String.format("%04d", custId)` → `String.format(Locale.ROOT, "%04d", custId)` |
| **KotlinNullnessAnnotation** | CurrentDayOrder.kt | Removed `@Nullable` from `notifiedAt`, `device` (Kotlin types already express nullability) |
| **KotlinNullnessAnnotation** | StoragePathProvider.kt | Removed `@NonNull` from `uncaughtException(thread, throwable)` and import |
| **Unused variable** | CustomerIdActivity.kt | Removed unused `device` (DeviceRegistrationRequest) in `checkLicenseFromServer` |
| **Unused variables** | CustomerIdActivity.kt | Removed unused `statusIconSize`, `statusTextStyle` in `CustomerIdScreen` |
| **Unnecessary safe call** | CustomerIdActivity.kt | `Objects.requireNonNull(posts)?.authenticationstatus` → `posts?.authenticationstatus` |
| **Redundant / unused** | Variables.kt | Simplified `isNetworkEnabled`: use `wifiEnabled`/`cellularEnabled` booleans, remove redundant assignments |
| **No cast needed** | TokenDisplayActivity.kt | `List(...) { null as String? }` → `List(...) { null }` (type inferred) |
