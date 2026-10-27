# Yomira v8.0.1-Beta validation

- Task: TASK_V801_001 / WBS 10.1 / PHASE_FACTORY_RESET_AND_PATCH_RELEASE
- Version: `8.0.1-Beta`, versionCode `15`.

## Tests and builds — PASS

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease --console=plain
```

`BUILD SUCCESSFUL in 1m 53s`; 815 actionable tasks, 10 executed, 805 up-to-date.
App XML results: **104 tests**, settings XML results: **10 tests**; **0 failures, 0 errors,
0 skipped**. Six added tests cover reset dispatch, duplicates, maintenance exclusion, retries,
preference defaults/onboarding, owner failures and platform refusal. Preference reset tests use
production store wrappers and Preferences.edit/clear with in-memory DataStore backing; they do
not certify Android disk behavior. Initial Windows FileStorage tests hit rename-over-existing
errors; replacing their backing with deterministic in-memory storage resolved this host-only issue.

The build retains existing experimental path-check / Coil API warnings. No full lint,
instrumentation or device compatibility certification is claimed.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 33,428,122 |
| Signed release | `app/build/outputs/apk/release/app-release.apk` | 19,151,317 |

SDK 35.0.0 `aapt dump badging` confirms both packages are `com.yomira.app`, versionCode `15`,
versionName `8.0.1-Beta`. `apksigner verify --print-certs` passes for both with `CN=Yomira Release`,
certificate SHA-256 `0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.
APKs remain in build outputs, not committed to Git. The existing tag workflow builds and publishes
the release APK on GitHub; its completion is not implied by a successful local build or tag push.

SHA-256:

```text
app-debug.apk   4E41247F615037C910BACDE7EEC98F1440DE23FAC43CC28B7AEDF91FB1E51076
app-release.apk 089D8DCD14A3A2B8B6335D9329543BBC59BD842C951A9C57DCFFFA4FB8CF751C
```

## Factory reset device check — NOT RUN

`adb devices -l` returned no devices. Confirmation-dialog behavior and Android's actual
clear-application-data process termination/relaunch have not been exercised on a phone.

When a test device is available:

1. Install an APK, finish onboarding, populate a library/read history and configure extensions.
2. Open Settings → Storage → Factory Reset. Cancel and verify all data remains.
3. Confirm reset. Expect the app to close (Android clears application data and stops the process).
4. Relaunch. Expect fresh onboarding, secure defaults, no old library/history/extensions or consent.
5. Confirm Drive backups and exported files outside app-owned storage are unaffected.

This is intentionally a destructive test; use disposable test data, not a user's live library.
