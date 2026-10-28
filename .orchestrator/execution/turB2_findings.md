# V811-TUR-B2 — F6 device QA rerun

**Verdict: NO-GO for Tur C.** Protobuf repository entries and all three install/remove controls work, but VPN-free reading, source selection, and the strict five-minute active-usage gate were not qualified. Do not interpret this as a complete device test.

## Rig and artifact hygiene

- Xiaomi Mi 10T `b4bc1593`, Android 12, Turkish UI. At the end, connectivity reported validated Wi-Fi and `NOT_VPN`. Network settings were not changed.
- Both APKs are 19,527,165 bytes. QA SHA-256 `62c139063f8b89429a1606794b2bfbc69162ccdc386414a2ec827f68238e4455`; release SHA-256 `1eedd3dde7ab813e409672a0c12a928a6cca64f4b8906a4effe709c15af4a7bd`. The release copy has the same release hash. Handoff hash fields were already populated. Artifact commit: `570b112`.
- QA installed with `adb install -r`, succeeded, and package reports versionName `8.1.1`, versionCode `21`. The device remains on QA, with the canonical protobuf repository retained and the three test extensions removed.
- All referenced PNGs are in `turB2_screens/`; each is nonzero and was independently verified as a PNG. Corresponding UI dumps are alongside them. Screens and XML can include existing user data.

## Steps

| Step | Result | Observation and evidence |
|---|---|---|
| 1 Install | PASS | In-place install succeeded; package version 8.1.1/21; screenshot `01-install.png` 3,176,211 bytes, `01-install.xml`. This confirms screenshot capture, not comprehensive data preservation. |
| 2 Repository deep link | PASS | Removed stale `index.min.json` via Depoları yönet. Issued real `ACTION_VIEW` for `yomira://add-repo?url=https%3A%2F%2Fraw.githubusercontent.com%2Fkeiyoushi%2Fextensions%2Frepo%2Findex.pb`; confirmation displayed the exact URL (`02-confirm.png`, `.xml`), tapped Ekle, repo count 1 and real entries appeared (`02-added.png`, `.xml`). |
| 3 Refresh | PARTIAL | Refresh shows real entries and no outdated-client placeholder (`03-refresh.png`, `.xml`). App UI did not expose a total; therefore **device-loaded count is not verified**. An independent host HTTPS GET of the canonical URL returned HTTP 200, SHA-256 `396aab410bd65cade429c38ab577192b27bf40d2bb7054f5039cc41fd42451e6`, and 1,435 field-1 extension records within protobuf field 101. This is the upstream payload count, not proof the app loaded all 1,435. |
| 4 Installs | PARTIAL | MangaFire v1.6.35, Weeb Central v1.6.25, and Toonily v1.6.72 each changed from Yükle to Kaldır. The requested name “WeeCentral” was not found; Weeb Central is the apparent intended catalog entry. Screens `04-mangafire.png`, `04-weebcentral.png`, `04-toonily.png`, `04-installed.png` and corresponding XML. Tap-to-observed-remove upper bounds, including dump and screenshot overhead: MangaFire 3.099 s (12:10:07.358–12:10:10.457), Weeb Central 3.127 s (12:10:56.975–12:11:00.102), Toonily 3.129 s (12:11:10.606–12:11:13.735), +03:00. These demonstrate UI installed state, **not** runtime/source readiness. |
| 5 VPN-free reading | FAIL / PARTIAL | Library contained Frieren (`05-library.png`, `05-frieren.png`); its Hemen oku produced “Kaynağa erişilemedi” (`05-frieren-read.png`, `.xml`). Existing home One Piece continue-reading entry produced the same (`05-onepiece.png`, `.xml`). One Piece was not verified as a library item. No pages rendered, no scrolling/load-more verified, and no chapter was established as belonging to any of the three freshly installed sources. Vault item not tested; tapping the visible Kasa setting did not produce a credential prompt or identifiable vault item (`05-vault.png`, `.xml`); credential requirement is unknown, not VAULT-CREDENTIAL. |
| 6 Source selection | BLOCKED | Frieren detail showed `Bölümler (1)` and chapter 1 (`06-frieren-chapters.png`, `.xml`), but no per-source counts/latest chapters or chosen source were established. Freshest/most-complete policy is unverified. |
| 7 Active usage | BLOCKED | Search for Frieren was exercised (`07-search.png`, `.xml`), as were library, extension, detail and settings surfaces. **Zero interactions have the required paired before/after timestamp record**; total interactions and first-to-last qualifying span are unknown. Five minutes of continuous active use and 25 qualifying distinct interactions are not proven. No sleeps were used to pad a span. Romanization, Updates, journey and reader gestures were not qualified in this rerun. |
| 8 Cleanup | PASS | Removed MangaFire, Toonily, Weeb Central via in-app Kaldır; installed filter shows no matching extensions and Depolar (1) (`08-clean.png`, `.xml`; intermediate `08-remove1.xml`, `08-remove2.xml`). Repository retained. |

## Bug / investigation list

### B2-1 — High, provisional: saved reading entry reports source unavailable

**Reproduction:** With the three extensions installed and Wi-Fi validated, open library → Frieren → Hemen oku; or home → One Piece → Devam et. Both show `Kaynağa erişilemedi` instead of pages. Evidence: `05-frieren-read.png` / `.xml`, `05-onepiece.png` / `.xml`.

**Qualification:** the saved chapter's source identity and compatibility with the newly installed sources were not established. These may be stale or missing source references, or an upstream availability condition; this is a user-visible high-impact reading failure requiring investigation, not a proven regression in any one extension or in protobuf parsing. The three-source test remains blocked rather than attributed to this symptom. No causal logcat exception was found.

**Severity counts:** 0 critical, 1 provisional high, 0 medium, 0 low. Incomplete coverage can hide additional bugs.

## Logcat and known gap

- Background `adb logcat -v threadtime` was started before the repository deep link; it includes existing ring-buffer history and is stored locally at `C:\Users\paele\AppData\Local\Temp\opencode\turb2-logcat.txt` (not committed, as it contains device-wide data). Filtered inspection of 12:08–12:14 found no `v811-notice shown`, `FATAL EXCEPTION`, `UnknownHostException`, `SSLHandshakeException`, `Cloudflare`, or `DnsOverHttps` matches. Absence of those strings does not prove DNS, DoH or Cloudflare success. No attributable source/reader error excerpt was available; do not infer a cause.
- Upgrade notice was already consumed in Tur B and could not be retested; this known gap is not counted as a bug.
- An attempted on-device `curl` through `adb exec-out` returned zero bytes without an error; it was not used to claim device network failure or a count. The count above is explicitly from the host.
