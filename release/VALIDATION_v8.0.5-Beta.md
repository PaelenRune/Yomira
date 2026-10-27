# Yomira v8.0.5-Beta validation

- Task: TASK_V805_001 / WBS 14.1 / PHASE_ENGLISH_DEFAULT_AND_ONBOARDING_LOCALE_SYNC
- Version: `8.0.5-Beta`, versionCode `19`.

## Tests and builds — PASS

```powershell
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease
.\gradlew.bat :feature:home:testDebugUnitTest --rerun :app:testDebugUnitTest --rerun
.\gradlew.bat :feature:home:compileDebugAndroidTestKotlin
```

- Requested tests/builds: `BUILD SUCCESSFUL in 2m 12s`; 815 actionable tasks: 79 executed, 1 from cache, 735 up-to-date.
- Explicit test rerun: `BUILD SUCCESSFUL in 8s`. The initial incremental app report contained only the new locale tests; the rerun confirmed all **117 app tests** and **22 home tests**: **139 tests, 0 failures, 0 errors, 0 skipped**.
- New locale unit coverage verifies missing/unknown languages default to English, Turkish variants map to Turkish, and English/Both map to English.
- Existing onboarding save/restore tests include English, Turkish and Both preferences.
- Updated onboarding instrumentation tests compile successfully, including English default completion and immediate language-selection callbacks. They were not executed on a device.
- New English/Turkish onboarding resource keys passed XML parsing, parity and uniqueness checks.
- Release R8 shrinking and release vital lint completed successfully. Full lint and all-module unit tests were not run.
- Existing warnings: experimental Android path-check override and coroutines opt-in in `YomiraViewModel.kt`.
- `git diff --check` passed (only Git line-ending conversion notices).

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 34,163,878 |
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 19,486,853 |

SDK 35.0.0 `aapt dump badging` confirms `com.yomira.app`, versionCode `19`,
versionName `8.0.5-Beta` for both APKs. `apksigner verify --print-certs` passes
for both with `CN=Yomira Release`, certificate SHA-256:
`0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.

SHA-256:

```text
app-debug.apk   3D791E65830ACB42E0ABF016B9F16912C9229816510EB52F1E883A08A09869FC
app-release.apk CA739BF4E4182AC8BABEAA4EB7399749783B6300EE2181F07D6BF547F50CD702
```

APKs remain in build outputs and are not committed to Git. The tag workflow publishes
the release APK; local validation does not certify remote workflow completion.

## Locale behavior and limitations

- An empty AppCompat locale request resolves to English in application policy. Startup sync explicitly sets English even on Turkish devices; the locale setter does not mistake an unset request for an already-applied English locale.
- Selecting a reading language immediately invokes the host’s full-app locale setter. Turkish selects `tr`; English/Both select `en`. Completion also applies that mapping.
- Activity recreation uses the existing `rememberSaveable` onboarding state; startup synchronization retains the once-per-process guard against Samsung recreation loops.
- All four onboarding steps and the settings survey use localized copy, including age and exclusion controls.
- Device UI behavior, real activity recreation/state restoration, and resource rendering across Home, Discover, Library, Journey and Settings were not exercised on hardware or an emulator. The device checklist is in [the release notes](RELEASE_NOTES_v8.0.5-Beta.md).
