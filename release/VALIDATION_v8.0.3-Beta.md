# Yomira v8.0.3-Beta validation

- Task: TASK_V803_001 / WBS 12.1 / PHASE_ENGLISH_COVERS_AND_V803_RELEASE
- Version: `8.0.3-Beta`, versionCode `17`.

## Tests and builds — PASS

```powershell
.\gradlew.bat :data:metadata:test :core:network:test --console=plain
.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease :core:model:test :core:network:test :data:metadata:test :domain:classifier:test :domain:extension:test :data:extension:test :data:sync:test :domain:sync:test :domain:journey:test --console=plain
.\gradlew.bat test --console=plain
```

- Targeted tests: `BUILD SUCCESSFUL in 6s`.
- Full debug test/build and JVM verification: `BUILD SUCCESSFUL in 3m 43s`;
  901 actionable tasks, 7 executed, 894 up-to-date.
- All unit-test variants, including Android release and JVM modules:
  `BUILD SUCCESSFUL in 55s`; 513 actionable tasks, 36 executed, 12 from cache,
  465 up-to-date.
- XML results: **933 test executions**, **0 failures, 0 errors, 0 skipped**.
  Android tests are counted separately for debug/release variants.
- Metadata module: **25 tests**, including **13 ArtworkResolver tests**.
  Network module: **47 tests**, including **7 MangaDexCover tests**.
- The initial build command's tool timeout interrupted release R8 after 120 seconds;
  rerunning serially with a longer timeout completed successfully. No test failure
  or code workaround was required.
- CI now explicitly runs metadata/network JVM regression tests in addition to the
  existing Android and extension tests.

Coverage verifies English covers beat AniList original artwork across providers,
ComicVine/OpenLibrary priority, explicit non-English locale demotion despite English
titles, same-tier provider order, secure URL fallback, unchanged banner selection,
MangaDex relationship order independence, blank filenames and normalized locales.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 33,503,601 |
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 19,177,597 |

SDK 35.0.0 `aapt dump badging` confirms `com.yomira.app`, versionCode `17`,
versionName `8.0.3-Beta` for both APKs. `apksigner verify --print-certs` passes
for both with `CN=Yomira Release`, certificate SHA-256:
`0f2d59d0146509e5ff921d697c395665aa136343012bc78fb8f430e82a12889c`.

SHA-256:

```text
app-debug.apk   306CA63D2D84A4CF4A0C498EA541E753D03229F91B904248CC83B624C305BE97
app-release.apk 557940A4BA3B904CEB6D0DC7A8D64978AA37B6437782CA356163254D2B4FC256
```

APKs remain in build outputs, not committed to Git. The existing tag workflow
publishes the release APK; local validation does not certify remote workflow completion.

## Scope and limitations

No device UI, instrumentation, live-provider artwork/OCR inspection, or full lint
certification is claimed. Cover selection ranks metadata evidence rather than
detecting text in pixels. English artwork always outranks known non-English artwork
when supplied among the identity-matched candidates. If no usable English artwork
exists, unknown and then non-English covers remain fallbacks as specified.
