# TASK_009 — WBS 1.8 live-device test

## Outcome

Yomira **6.0.0-Beta**, versionCode **11**, built and installed successfully on Xiaomi Mi 10T **b4bc1593**. A **180.62-second** interactive smoke-test window completed on 2026-10-27, approximately 15:49:53–15:52:54 device/local time. **Partial verification, not an all-features pass.** No source code was changed.

## Build and launch

- `gradlew.bat assembleDebug`: BUILD SUCCESSFUL in 12 seconds; 361 actionable tasks, 13 executed.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `adb -s b4bc1593 install -r ...`: Success; existing app data was preserved.
- Supplied component `com.yomira/.MainActivity` failed with Error type 3.
- APK metadata and package resolution identify the correct component as **`com.yomira.app/.MainActivity`**.
- Corrected cold launch: TotalTime 1180 ms, WaitTime 1183 ms. Later cold relaunch: 1138/1142 ms.
- Physical resolution: 1080×2400. Initially the device was rotated into landscape, so the first capture is 2400×1080. Portrait was temporarily locked for coordinate-driven testing. Original `accelerometer_rotation=1`, `user_rotation=0` were restored at the end.

## Verified coverage

| Area | Observation | Evidence in `art/screenshots/` |
|---|---|---|
| Home, landscape | Header, search, tags and left navigation rendered. Lower navigation items extend beyond the short landscape viewport; Journey is partially clipped and Vault is hidden. | `device_home.png` |
| Home, portrait | Hero, Read now, genre chips, shelf and bottom navigation rendered. | `device_home_portrait.png` |
| Shelf scrolling | Vertical swipe revealed For You and thematic rails with titles and gradient cover placeholders. Initial guessed card coordinate hit whitespace; corrected tap opened Haikyu!!. | `device_home_scrolled.png` |
| Series detail | Haikyu!! title, 8.8 score, completed state, author, genres, summary and Add to library rendered. | `device_detail.png` |
| Source matching | Chapters (0), progress indicator and “Finding the best source and loading chapters...” persisted in the observed detail interval. No readable chapter became available before leaving detail. | `device_detail_chapters.png` |
| Journey | Level 1, 0 XP, 0-day streak, weekly empty state and zero chapter/page totals rendered. No reading progress could be tested. | `device_journey.png` |
| Library | Empty-library state rendered correctly. Add/remove/persistence were not exercised. | `device_library.png` |
| Settings | Cache 0 B, Google Drive sync off, not-yet-synced status, Privacy & +18 Vault section rendered. Cache deletion and account sync were deliberately not triggered. | `device_settings.png` |
| Vault privacy | Vault absent from bottom navigation while HIDDEN. Tapping its Settings reveal toggle opened Android credential authentication. | `device_vault_locked.png` |
| Authentication cancellation | Window inspection confirmed `BiometricPrompt` and `com.android.settings/.password.ConfirmDeviceCredentialActivity`. Screenshot capture returned zero bytes on the secure prompt; the invalid file was removed. Back cancelled authentication; no credential was entered or bypassed. | `device_vault_cancelled.png` |
| Discover | Discover grid, series titles, search and genre controls rendered. | `device_discover.png` |

## Blockers, limitations and findings

1. **Reader not verified.** The visited title had zero chapters while matching. There is deliberately no `device_reader.png`: an attempted capture unexpectedly showed the device launcher, not Reader, and was removed rather than mislabeled as success. Reader scrolling, page loading, chapter progression and progress persistence require a title with an available chapter.
2. **Unexpected app exit/process change.** Around the first detail visit the foreground became the launcher, and the app required relaunch. PID changed from 18544 to 19272. The crash buffer contains no current Yomira fatal exception; cause is unconfirmed, so this is not classified as a proven crash or Back-navigation defect. Subsequent Journey, Library, Settings and Discover visits succeeded. Coordinate-only automation and possible live-device interference limit diagnosis.
3. **Vault unlock and panic lock blocked by owner authentication.** Authentication was reached, not completed. Neither an unlocked Vault screenshot nor a verified Hızlı Kilitle result exists. Do not treat the rightmost Journey tab as Vault.
4. **Artwork did not visibly resolve.** Multiple home/detail/discover captures show gradient title placeholders, loading spinners and later fallback book icons rather than actual covers. This indicates unavailable or slow artwork in this run, not proof of a specific network defect.
5. **Landscape navigation needs follow-up.** The initial screenshot visibly clips the lower side-navigation content. Portrait navigation fits. No landscape-scrolling workaround was tested.
6. Search input was attempted, but final evidence still shows the unfiltered Discover list and empty search hint. Search functionality is **not verified**. Genre filtering, chapter sorting, full-summary expansion, downloads, extension/repository management and sync are likewise not certified by this short run.
7. A heads-up notification overlays the final screenshot; this is device environment interference, not an app-rendering issue. Private launcher/contacts screenshots accidentally reached during the interrupted flow were removed immediately and are not retained as test evidence.

## Latency and smoothness

- Cold launch was measured by Android at **1.18 s**, with a later relaunch **1.14 s**.
- Screens rendered at the next sampled capture after 1–2-second waits. These waits are not precise measured tap-to-paint latency, and screenshots alone cannot establish continuous animation smoothness.
- Final `dumpsys gfxinfo` for PID 19272 reports **2275 frames**, **57 janky frames (2.51%)**, legacy jank **33.76%**, frame-duration p50 **17 ms**, p90 **27 ms**, p95 **28 ms**, p99 **109 ms**. GPU p50 **3 ms**, p95 **7 ms**, p99 **9 ms**.
- This supports mostly responsive navigation with measurable tail stalls, **not** a claim of flawless/high-refresh smoothness. Final graphics statistics cover the relaunched process only, not the entire 180-second interval; source matching and screenshot/input instrumentation also influence this smoke-test sample.

## Artifacts

- Timing: `art/live_device_test_start.json`, `art/live_device_test_end.json`.
- Graphics statistics: `art/device_gfxinfo.txt`.
- Screenshots: named evidence above plus `device_returned.png`, `device_search.png` (attempt only), `device_final.png`.
- No unlocked/Reader success artifact is fabricated. Follow-up needs an available chapter and owner-assisted device authentication.
