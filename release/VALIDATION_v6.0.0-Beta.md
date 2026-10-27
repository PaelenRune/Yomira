# Yomira v6.0.0-Beta — release validation

Date: 2026-10-27 · Task: TASK_006 / WBS 1.5 / PHASE_RELEASE_TEST

## Scope and evidence standard

This is a **simulated ten-minute mobile reader journey**, backed by executable JVM/Android local unit tests and inspection of production navigation/state/persistence code. Timeline labels below represent a user's ten-minute session, **not ten minutes of recorded device interaction**. No emulator/UI instrumentation, physical touch/gesture test, biometric/PIN entry, live-source reading session, or airplane-mode device test was performed. A connected Xiaomi M2007J3SY was detected with ADB, but its installed app, credentials, connectivity settings, and personal data were not modified.

`PASS — unit` means executable local tests cover the stated logic; `SOURCE` means production wiring was inspected but not exercised by a UI test. Neither substitutes for device acceptance testing. No performance multiplier, frame-rate, or ten-minute crash-free device claim is made.

## Build and version

- `app/build.gradle.kts`: versionName **`6.0.0-Beta`**, versionCode **`11`** (previously `5.4.1-beta` / `10`).
- `assembleDebug`: **BUILD SUCCESSFUL** (43 seconds with JVM module tests; final standalone build after the fresh suite: **4 seconds**).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Size: **25,293,873 bytes**.
- SHA-256: `d89db2a3300f559c0ca64fd4b400209ee822e8d963c1bb20f767c3c624496c64`.
- `aapt dump badging`: `com.yomira.app`, `6.0.0-Beta` / `11`, minSdk **26**, targetSdk **35**, debuggable.
- `apksigner verify --verbose --print-certs`: **verified**, APK Signature Scheme **v2**, signer `CN=Yomira Release`.
- Certificate SHA-256: `0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.
- This is a debug APK, not a verified minified release build. No APK upload, tag, commit, or GitHub release was performed.

## Commands

Run from the repository root on Windows with JDK 17 and Android SDK 35:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :core:model:test :core:network:test :domain:classifier:test :domain:extension:test :data:extension:test :data:metadata:test :data:sync:test assembleDebug
.\gradlew.bat testDebugUnitTest :core:model:test :core:network:test :domain:classifier:test :domain:extension:test :data:extension:test :data:metadata:test :data:sync:test --rerun-tasks
.\gradlew.bat assembleDebug
```

The separate `:test` tasks are essential: `testDebugUnitTest` does not run the JVM-only modules. Desktop is excluded from `settings.gradle.kts` and is not an active build target. `feature:journey` has no local unit-test sources; its domain tests run under `domain:journey`.

The first Android unit-test invocation passed in 24 seconds. The final forced rerun completed **BUILD SUCCESSFUL in 1 minute 2 seconds**, with **276 tasks executed** (not reused from cache). Fresh XML results span `2026-10-27T12:26:44`–`12:27:35` UTC.

### Final unit-test totals

**491 tests in 81 suites across 17 modules: 0 failures, 0 errors, 0 skipped.** Android local unit tests account for 288 tests; JVM-only modules account for 203.

| Module | Suites | Tests |
| --- | ---: | ---: |
| app | 15 | 94 |
| core:database | 3 | 7 |
| core:model | 4 | 22 |
| core:network | 4 | 23 |
| core:ui | 8 | 34 |
| data:extension | 12 | 72 |
| data:metadata | 2 | 21 |
| data:sync | 2 | 19 |
| domain:classifier | 1 | 18 |
| domain:extension | 4 | 28 |
| domain:journey | 8 | 47 |
| domain:sync | 2 | 18 |
| feature:details | 1 | 2 |
| feature:home | 3 | 12 |
| feature:reader | 9 | 49 |
| feature:settings | 1 | 10 |
| feature:vault | 2 | 15 |
| **Total** | **81** | **491** |

Counts were read from `*/build/test-results/testDebugUnitTest/TEST-*.xml` and JVM-module `*/build/test-results/test/TEST-*.xml`. Human-readable reports are under each module's corresponding `build/reports/tests/` directory.

## Ten-minute simulated journey

| Simulated time | Actions / variants | Result and evidence |
| --- | --- | --- |
| 00:00–01:00 | Fresh install; select Turkish; complete onboarding; restart. Repeat with English and both languages; simulate failed repository refresh and unreadable storage. | **PASS — unit.** `OnboardingViewModelTest`, `DataStoreOnboardingStoreTest`, `OnboardingWizardManagerTest`, and `ReadingLanguageTest` cover stored selection, restart restoration, offline setup, and failure gating. **SOURCE:** `MainActivity` applies TR/EN UI locale after setup. UI language and reading-language preference are distinct; changing UI locale must not silently rewrite the saved content language. |
| 01:00–02:00 | Browse the public hero and shelves; horizontally navigate rails; open a card; switch Home / Discover / Library; change grid density. | **PASS — unit (data policies); SOURCE (touch/navigation).** `HomeShelfTest`, `HomeRailsTest`, `DynamicShelvesTest`, `CatalogIsolationTest`, and `AdaptiveNavigationTest` cover stable seven-shelf selection, deterministic order, deduplication, genre purity, explicit-content exclusion, and navigation policy. `MainActivity` and `HomeScreen` wire cards/routes/columns. Real horizontal scrolling, layout clipping, and accessibility focus were not device-tested. |
| 02:00–03:00 | Open an uncached series directly. Turkish preference must not choose English-only sources; English preference must drop Turkish-only sources. Exercise completeness, recency, latency, missing runtime, no match, and aliases. | **PASS — unit; SOURCE (automatic launch).** `SmartSourceMatcherTest`, `MangaDexSourceBridgeTest`, `SourceMatchActivatorTest`, and `MatchedSourceReaderTest` cover language gating, ranking, aliases, chapter insertion, stable selected-source routing, and page-provider handoff. `MainActivity` calls `ensureSource` on detail entry; `YomiraViewModel.findSource` activates `Ready` before publishing and removes alternatives. No selection dialog is wired. Missing/unavailable sources remain a real limitation, not a successful activation. |
| 03:00–04:00 | Open details with Anime, Hentai OVA, K-Drama/live-action, Donghua, and Special relations; inspect badge labels; repeat without connections or with unknown metadata. | **PASS — unit (mapping/copy); SOURCE (rendering).** `AniListConnectionsTest` verifies distinct kinds, Hentai/explicit tags, K-Drama labels, Donghua versus Japanese ONA, deduplication, scores/year/episodes, and readable-manga separation. `DetailCopyTest` covers all labels and custom localization. `MangaDetailScreen` calls `AdaptationBadges` in phone and two-pane layouts; badges are absent for an empty list. K-Drama shares the Drama / Live-action kind; live provider coverage is not guaranteed and no video playback exists. |
| 04:00–05:00 | Read a Webtoon vertically; change visible page; jump with scrubber; zoom without accidentally scrolling the strip; reopen the chapter. | **PASS — unit (progress/seams); SOURCE (Compose gestures).** `ReaderProgressTest`, `WebtoonSeamCleanerTest`, and `SmartZoomTest` verify bounded/restored positions, seam rules, and pan limits. `WebtoonReader` uses `LazyColumn`, tracks first visible page, applies bounded jumps, and disables list scrolling while zoomed. Actual long-image decoding, pinch arbitration, and visual seam quality need device testing. |
| 05:00–06:00 | Switch to Manga RTL; swipe pages; double-tap to 2.5× and reset; pinch toward 1×/5× bounds; pan to edges; resize; compare Western LTR. | **PASS — unit (direction/geometry); SOURCE (gesture dispatch).** `ReaderProgressTest`, `SmartZoomTest`, `SpreadStitcherTest`, and `GuidedViewDetectorTest` cover RTL policy, pan bounds, resized bounds, and page/panel geometry. `PagerReader` forces LTR ambient and sets `reverseLayout` from reading mode; `SmartZoom` clamps pinch scale and focal pan, resets offset on unzoom, and disables paging while zoomed. Physical double-tap/pinch/swipe behavior remains unverified. |
| 06:00–07:00 | Advance to next chapter, choose another chapter, finish a chapter, leave and resume; check independent positions and invalid saves; inspect continue-reading card. | **PASS — unit (persistence/ledger); SOURCE (navigation).** `ReaderProgressTest`, `CompletionSyncTrackerTest`, and journey-domain tests cover positions, independent chapter state, rejected invalid indices, and deduplicated completion. `ReaderOverlay` selects next chapter/picker target; `MainActivity` replaces the reader route; `ReaderViewModel` queues progress writes and restores bounded positions; `SyncDao`/`continueReading` expose the latest position. No full Room instrumentation or UI chapter-navigation test was run. |
| 07:00–08:00 | Reveal hidden Vault from Settings; reject credential, then simulate success; enter locked Vault; cancel unlock; simulate no configured device lock; then authorize access. | **PASS — unit (visibility/verdict); SOURCE (OS gate).** `SettingsViewModelTest` covers denied/verified reveal, inability to bypass reveal with a setter, and persisted privacy toggles. `MainActivity.confirmCredentials` uses `KeyguardManager.createConfirmDeviceCredentialIntent`, refuses a null intent, and unlocks only on `RESULT_OK`. No credentials were entered, no biometric prompt was exercised, and a dedicated `BiometricPrompt` implementation is absent. If the user explicitly disables device-lock protection, Vault entry intentionally does not prompt. |
| 08:00–09:00 | Inspect Vault hero, all four rails, collection; toggle each style twice; include unclassified imports, AllPornComic source identity, duplicate discovery rows, and non-explicit records; press panic; background and return. | **PASS — unit (all surfaces/styles); SOURCE (panic/lifecycle).** `VaultRailsTest` and `VaultIsolationTest` cover four rails, style scoping, toggle deselection, locked empty snapshots, deduplication, rail/hero caps, full collection preservation, and strict explicit-only input. `ContentRatingClassifierTest` covers Western/3D/AllPornComic evidence. `panic()` locks and navigates Home; background lock follows settings; locked adult detail/reader routes are removed. English Vault rail titles currently fall back to Turkish defaults. OS lifecycle and rapid panic/back-stack interaction require device acceptance. |
| 09:00–10:00 | Save a title to Library; restart offline; browse cached catalogue; restore reading position; request a chapter download; try reopening an uncached page with no network. | **PARTIAL — persistence supported; full offline reading unsupported.** DataStore recreation tests and `ReaderProgressTest` exercise stored preferences/positions; `CatalogSeederTest` and `PagedCatalogLoaderTest` cover catalogue behavior. `YomiraViewModel` stores saved ids in SharedPreferences and observes Room independently of network; this saved-library path was source-reviewed, not process-death/device-tested. Download action shows `offline_download_notice` only. `ReaderScreen` disables persistent image disk cache and `ReaderPageRepository` still requires provider/network page resolution. Do not claim downloaded-chapter offline reading passes. |

## Additional regression coverage added

`feature/reader/src/test/java/com/yomira/feature/reader/SmartZoomTest.kt` adds five focused tests: reset at native/sub-native scale; positive/negative pan boundaries at 2.5×; preservation of in-bound focal pan; zero-size layout reset; and resize re-clamping. No production reader behavior was changed.

## Release caveats and outstanding device acceptance

1. **Offline downloads:** UI is explanatory only, not a download queue or stored-image reader. Catalogue/library metadata and positions persisting does not prove offline chapter images work.
2. **Language lock:** unrelated single-language sources are excluded; multilingual fallback is intentionally permitted. Built-in MangaDex requests are explicitly TR/EN-filtered. Other source runtimes require separate provider validation.
3. **Coverage/performance:** a ready match activates automatically, but not every source has a runnable adapter. No “10× faster” benchmark was performed. Existing stored chapters bypass automatic re-matching; this is not a language migration of an existing chapter index.
4. **Adaptations:** K-Drama is represented as live-action metadata when supplied; no separate live-action metadata provider or player exists.
5. **Vault localization:** new rail headings/empty-rail text use Turkish `VaultCopy` defaults because the app's `vaultCopy()` does not provide localized overrides. English style chips are also not translated.
6. **Authentication:** OS PIN/pattern/password integration was source-reviewed; biometric-specific support and real success/cancel/no-screen-lock behavior were not device-tested. Privacy toggles intentionally alter entry and background-lock policy.
7. **Device QA still needed:** fresh-install UI in TR/EN; actual source opening with live chapters; image decode and memory behavior; RTL/pinch/double-tap; next-chapter/resume; credential acceptance/cancellation; background/panic/back-stack privacy; process death; airplane-mode library and Room persistence.

No compilation/test failures were found in the initial runs. The first forced rerun caught an overly strict packed-`Offset` equality assertion in the newly added zero-dimension zoom test: negative and positive floating-point zero render identically but do not have identical packed bits. The test now checks both numeric components with zero tolerance; no production change was needed. The complete rerun then passed all 491 tests. `git diff --check` reported no whitespace errors; Git emitted existing LF/CRLF conversion notices. Gradle reports the existing experimental `android.overridePathCheck=true` setting, deprecated non-auto-mirrored icons, and coroutine/Coil opt-in warnings. These are warnings, not suppressed failures.

## Outcome

All 491 local unit tests pass, version and release notes are prepared, the final debug APK builds and verifies, and the simulated journey documents both covered logic and missing capabilities. This is **not** an unconditional sign-off for all-device UX, biometric authentication, live-provider compatibility, offline chapter downloads, or production release packaging.
