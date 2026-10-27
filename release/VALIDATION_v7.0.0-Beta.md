# Yomira v7.0.0-Beta validation

- Date: 2026-10-27
- Task: TASK_V7_007 / WBS 7.9 / PHASE_RELEASE_VERIFICATION
- Workspace: existing, uncommitted v7 work; no application/test source changed by this verification.
- **Release gate: NOT PASSED.** APK builds pass, but one unit test fails and the target device is unavailable.

## Unit tests

Initial requested command: `./gradlew.bat testDebugUnitTest` — FAILED (app test failure).

Complete Android debug + JVM-only module run (forced execution, continuing across failures):

```powershell
.\gradlew.bat testDebugUnitTest :core:model:test :core:network:test :domain:classifier:test :domain:extension:test :data:metadata:test :data:extension:test :data:sync:test --continue --rerun-tasks --console=plain
```

Result: **532 tests, 531 passed, 1 failed, 0 errors, 0 skipped**, across 17 modules and 89 XML suites. Gradle: BUILD FAILED in 1m 11s; 276 actionable tasks, all executed. `feature:journey:testDebugUnitTest` reports NO-SOURCE (no tests), not an extra passing suite. The postponed desktop module is not included in settings and was not tested. Android instrumentation tests are not included in these unit counts.

| Module | Tests | Failures |
|---|---:|---:|
| app | 97 | 1 |
| core:database | 7 | 0 |
| core:model | 24 | 0 |
| core:network | 32 | 0 |
| core:ui | 35 | 0 |
| data:extension | 73 | 0 |
| data:metadata | 21 | 0 |
| data:sync | 19 | 0 |
| domain:classifier | 18 | 0 |
| domain:extension | 35 | 0 |
| domain:journey | 57 | 0 |
| domain:sync | 18 | 0 |
| feature:details | 2 | 0 |
| feature:home | 16 | 0 |
| feature:reader | 49 | 0 |
| feature:settings | 10 | 0 |
| feature:vault | 19 | 0 |
| **Total** | **532** | **1** |

Reproduced in both runs:

- Test: `com.yomira.app.catalog.MangaDexSourceBridgeTest.productionFallbackResolvesSelectedLanguageAndLoadsRealPages`
- Assertion: `assertTrue(match is SmartMatchResult.Ready)` at `app/src/test/java/com/yomira/app/catalog/MangaDexSourceBridgeTest.kt:23`.
- This uses MockWebServer; no live MangaDex outage is established by this failure. Root cause was not diagnosed or patched in this verification task.
- Evidence: `app/build/test-results/testDebugUnitTest/TEST-com.yomira.app.catalog.MangaDexSourceBridgeTest.xml`; HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Complete rerun log: `C:\Users\paele\AppData\Local\Temp\opencode\yomira-v7-tests.log` (local UTF-16 PowerShell log).

## APK builds

Command: `./gradlew.bat assembleDebug assembleRelease --console=plain`.

**PASS:** BUILD SUCCESSFUL in 3m 26s (802 actionable tasks: 18 executed, 1 from cache, 783 up-to-date). An earlier foreground invocation exceeded the tool's 120-second timeout; the subsequent invocation completed successfully with a longer timeout.

| Variant | APK | Bytes | Approx. MiB |
|---|---|---:|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 33,433,975 | 31.89 |
| Release | `app/build/outputs/apk/release/app-release.apk` | 19,206,334 | 18.32 |

Both APKs independently inspected with SDK 35.0.0 `aapt dump badging`:

- applicationId: `com.yomira.app`
- versionName: `7.0.0-Beta`
- versionCode: `12`
- minimum SDK: `26`; target SDK: `35`

SHA-256:

```text
app-debug.apk   4035EE5B68AED555898192953FE0B4987512A05D7730FF0F5D886E6760543C4A
app-release.apk C5B1727E51F98226115EB2C3D3B1810C4403C1DFC436C50A79687E42646E563D
```

Build log: `C:\Users\paele\AppData\Local\Temp\opencode\yomira-v7-build.log` (local UTF-16 PowerShell log).

Non-blocking warnings include the experimental Android path override, deprecated non-auto-mirrored icons, experimental coroutine/Coil opt-in warnings, and native libraries packaged without symbol stripping. Successful assembly is not a claim of warning-free compilation or full lint coverage.

## Xiaomi Mi 10T live-device checks

Target: `b4bc1593`; ADB: `C:\Users\paele\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

Repeated `adb devices -l` checks returned only `List of devices attached` with no devices. Model query, installation and launch attempts each returned `adb.exe: device 'b4bc1593' not found`.

| Check | Result |
|---|---|
| Install debug APK with `install -r` | BLOCKED — target not found |
| Launch `com.yomira.app/.MainActivity` | BLOCKED — target not found |
| Installed version/build | NOT VERIFIED — APK metadata verified only |
| Home | NOT VERIFIED live |
| Library and new Updates tab | NOT VERIFIED live |
| Settings and Stealth Shield | NOT VERIFIED live |
| Vault format separation | NOT VERIFIED live |
| New screenshots in `art/screenshots/` | NOT CAPTURED — device unavailable |

Existing screenshots were not modified, reused as new evidence, or represented as v7 validation. Passing unit tests for settings/home/vault/network do not substitute for live UI verification.

## Required follow-up

1. Diagnose/fix the reproducible MangaDex bridge test failure and rerun the complete unit command above.
2. Reconnect/unlock the Xiaomi Mi 10T, enable USB debugging and authorize this host; confirm serial `b4bc1593` appears as `device`.
3. Rebuild if code has changed, install the debug APK with `-r` (no app-data wipe), launch and inspect the requested screens/version. Capture new v7 screenshots under `art/screenshots/` and update this report with actual observations.
4. Do not mark WBS 7.9 or release verification complete until both blockers are cleared.
