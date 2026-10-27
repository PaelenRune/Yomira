# Yomira v8.0.2-Beta validation

- Task: TASK_V802_001 / WBS 11.1 / PHASE_EXTENSIONS_SCREEN_AND_V802_RELEASE
- Version: `8.0.2-Beta`, versionCode `16`.

## Tests and builds — PASS

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease :data:extension:test --console=plain
```

Final verification: `BUILD SUCCESSFUL in 5s`; 873 actionable tasks, 3 executed,
870 up-to-date. Earlier full successful run: 2m 31s. A subsequent Windows JAR-lock
failure from overlapping verification runs was resolved by running verification serially.
The initial settings refresh method-name compilation error was corrected before successful builds.

App XML results: **109 tests**; extension-engine XML results: **75 tests**.
Both have **0 failures, 0 errors, 0 skipped**. The command also verifies the other
Android modules' debug unit-test tasks.

New coverage includes search/source aliases, language/installed/update filters, offline
and orphaned installations, native adapter protection, repository add/remove, install/update/
uninstall transitions, failed operations, invalid HTTPS URLs and revoked consent. Engine
regressions verify repository provenance, ordinary/chunked downloads, and the APK size cap.
CI now includes the JVM-only `:data:extension:test` task.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 33,576,603 |
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 19,177,597 |

SDK 35.0.0 `aapt dump badging` confirms `com.yomira.app`, versionCode `16`,
versionName `8.0.2-Beta` for both. `apksigner verify --print-certs` passes for both
with `CN=Yomira Release`, certificate SHA-256:
`0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.

SHA-256:

```text
app-debug.apk   B2581CC7866066A2528B6D2C04E16BD109EFDDCF60216B3521F05C29680A549F
app-release.apk C824CD0B2F16E12F2A8FC3AEFAC938538F9E84F14A217E20F399489B846F9720
```

APKs remain in build outputs, not committed to Git. The existing tag workflow publishes
the release APK; local success does not certify completion of the remote workflow.

## Device UI / real repository installation — NOT RUN

`adb devices -l` returned no devices. No phone/emulator UI, live third-party repository
download/install, full lint, or instrumentation certification is claimed.

Manual follow-up on a disposable test device:

1. Open **Extensions** from the top bar and from Settings; check Turkish and English UI.
2. Add a trusted HTTPS index; expand repository controls and inspect the active URL.
3. Search by extension/source, test TR/EN/all and installed/update filters, and inspect NSFW badges.
4. Install an extension, verify the loading indicator then the uninstall action; restart and verify persistence.
5. Use a trusted newer index version to verify Update transitions back to Uninstall.
6. Go offline or remove the repository: installed entries must remain removable.
7. Verify failed downloads do not mark packages installed and built-in MangaDex cannot be removed.
