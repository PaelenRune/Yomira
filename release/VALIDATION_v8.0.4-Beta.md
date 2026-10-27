# Yomira v8.0.4-Beta validation

- Task: TASK_V804_001 / WBS 13.1 / PHASE_GOOGLE_DRIVE_AUTH_AND_V804_RELEASE
- Version: `8.0.4-Beta`, versionCode `18`.

## Tests and builds — PASS

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease
```

- `BUILD SUCCESSFUL in 3m 23s`; 815 actionable tasks: 85 executed, 1 from cache,
  729 up-to-date.
- App: **114 tests**; settings feature: **10 tests**. Total **124 tests**,
  **0 failures, 0 errors, 0 skipped** in the targeted debug unit-test XML results.
- Coverage includes disconnected sync gating, automatic opt-in, restored email,
  successful/cancelled/failed consent callbacks, local disconnect after revocation
  failure, and disabling automatic sync after lost authorization.
- Release R8 shrinking and release vital lint tasks completed successfully.
- Warnings: existing experimental path-check/coroutines usage and Google's deprecated
  Sign-In compatibility APIs. The compatibility API choice and migration boundary
  are documented in `docs/GOOGLE_DRIVE_AUTH.md`.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 34,158,810 |
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 19,481,781 |

SDK 35.0.0 `aapt dump badging` confirms `com.yomira.app`, versionCode `18`,
versionName `8.0.4-Beta` for both APKs. `apksigner verify --print-certs` passes
for both with `CN=Yomira Release`, certificate SHA-256:
`0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.

SHA-256:

```text
app-debug.apk   59D98413202FBFAE443E4E6DB8736E279789C5E92003CCDE30207DAC6DB66EA3
app-release.apk 43BA188F7E197DCFB2CBD49758D299BF3A1426376482745039677ABAF23893EC
```

APKs remain in build outputs, not committed to Git. The existing tag workflow
publishes the release APK; local validation does not certify remote workflow completion.

## Scope and limitations

No live Google account sign-in, real Drive upload/restore, device UI/instrumentation,
full lint, or all-module unit-test certification is claimed. OAuth Cloud provisioning
is external and was not changed or verified. A connected Android device was detected,
but no APK was installed and no real account consent was requested. See the Cloud
setup requirements and device checklist in [GOOGLE_DRIVE_AUTH.md](../docs/GOOGLE_DRIVE_AUTH.md).
