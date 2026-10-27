# Yomira v7.1.0-Beta validation

- Date: 2026-10-27
- Task: TASK_V71_007 / WBS 8.7 / PHASE_RELEASE_AND_DEVICE_TEST
- Version: `7.1.0-Beta`, versionCode `13`.
- **Live-device release gate: BLOCKED.** The requested Xiaomi Mi 10T (`b4bc1593`) is not connected to ADB. Build and unit results below do not substitute for phone verification.

## Builds and unit tests

Build command: `.\gradlew.bat assembleDebug assembleRelease`.

Unit command:

```powershell
.\gradlew.bat testDebugUnitTest :core:model:test :core:network:test :domain:classifier:test :domain:extension:test :data:metadata:test :data:extension:test :data:sync:test --continue --console=plain
```

**Build PASS:** BUILD SUCCESSFUL in 27s; 802 actionable tasks, 8 executed and 794 up-to-date.

**Unit command PASS:** BUILD SUCCESSFUL in 30s; 276 actionable tasks, 2 executed and 274 up-to-date. App and Vault test tasks executed; the other test tasks reused Gradle's up-to-date results. This was not a forced fresh execution of every test.

XML reports contain **569 tests, 0 failures, 0 errors, 0 skipped**, across 17 modules and 94 suites:

| Module | Tests |
|---|---:|
| app | 98 |
| core:database | 9 |
| core:model | 28 |
| core:network | 32 |
| core:ui | 35 |
| data:extension | 73 |
| data:metadata | 21 |
| data:sync | 19 |
| domain:classifier | 26 |
| domain:extension | 43 |
| domain:journey | 57 |
| domain:sync | 18 |
| feature:details | 7 |
| feature:home | 22 |
| feature:reader | 49 |
| feature:settings | 10 |
| feature:vault | 22 |

The previously failing MangaDex bridge test is passing in the current app results. `feature:journey:testDebugUnitTest` is NO-SOURCE, not an additional passing suite. Instrumentation tests and the postponed desktop module were not run.

| Variant | APK | Bytes | MiB |
|---|---|---:|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 33,482,959 | 31.93 |
| Signed release | `app/build/outputs/apk/release/app-release.apk` | 19,222,546 | 18.33 |

SDK 35.0.0 `aapt dump badging` independently confirms both APKs have applicationId `com.yomira.app`, versionCode `13`, and versionName `7.1.0-Beta`. `apksigner verify --print-certs` passes for both; the release APK verifies using APK Signature Scheme v2. Both use `CN=Yomira Release`, certificate SHA-256 `0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.

APK SHA-256:

```text
app-debug.apk   8204BE8441D4DF393894A21F65966A609F7F0B33AA8F9956169C0949DA2612C7
app-release.apk F0C70E9932229F97BF6025C92AC235F51AFD11092E744755F8A6DF0EF2B5CD49
```

The experimental `android.overridePathCheck=true` warning remains; successful assembly is not a full lint or device-compatibility certification. APKs remain in build output directories and are not added to Git.

## Xiaomi Mi 10T live verification

ADB: `C:\Users\paele\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

`adb devices -l` returned only `List of devices attached`, with no device entries. `adb -s b4bc1593 get-state` returned `error: device 'b4bc1593' not found`. After the successful build, the requested `install -r app\build\outputs\apk\debug\app-debug.apk` and `shell am start -n com.yomira.app/.MainActivity` were both attempted and returned `adb.exe: device 'b4bc1593' not found`.

| Stage | Result |
|---|---|
| Install debug APK with `install -r` | BLOCKED — target unavailable |
| Launch `com.yomira.app/.MainActivity` | BLOCKED — target unavailable |
| Home and globally popular hits | NOT VERIFIED on phone |
| Series details: no chapter download buttons | NOT VERIFIED on phone |
| Toggle chapter read and confirm 38% dimming | NOT VERIFIED on phone |
| Unread-only chapter filter | NOT VERIFIED on phone |
| Settings: blacklist chips and Stealth Shield | NOT VERIFIED on phone |
| New device screenshots in `art/screenshots/` | NOT CAPTURED — target unavailable |

Source inspection confirms the chapter row applies `.alpha(if (entry.read) 0.38f else 1.0f)` and the Settings screen includes blacklist chips and Stealth Shield. These are source checks, not evidence that the UI renders or persists correctly on the device. Existing screenshots were not reused or presented as new v7.1 evidence.

## Required follow-up

Reconnect and unlock the Xiaomi, enable USB debugging, authorize this host, and confirm serial `b4bc1593` appears as `device`. Install the built debug APK without wiping application data, launch the app, perform every phone check above, capture new screenshots under `art/screenshots/`, and update this report with actual observations. Do not mark WBS 8.7 live verification complete before those checks pass.
