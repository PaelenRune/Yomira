# Yomira v8.0.0-Beta

Version code: **14**

## Highlights

- **Mihon & Nuvio-inspired clean architecture:** zero bundled pirate scraper JSONs. The app is a legal metadata browser; reading requires user-installed, explicitly trusted source extensions. Users remain responsible for source permissions and content rights.
- **2-factor installed-source matcher:** ranks matching installed sources by chapter completeness first, then update freshness. No uninstalled source is executed or silently installed.
- **Cloudflare & WebView fallback interceptor foundation:** detects challenge/Turnstile responses, exposes a WebView challenge-handler integration hook, retries once with clearance cookies, and retains cookies through a supplied cookie jar. This release does not wire a built-in WebView solver into the app client; automatic Turnstile solving and durable cookie persistence are not guaranteed.
- **Multi-source page & chapter failover:** when chapter page-list loading fails, returns no pages, or receives HTTP 404, the reader silently searches other trusted installed sources for the same manga and exact chapter number. Alternatives are ordered by completeness and freshness, and retain the chosen language when known. If none succeeds, the original error is preserved. This covers page-list retrieval, not image-download failures after the list has loaded.
- **MangaUpdates (Baka-Updates) discovery:** active discovery-feed integration for metadata browsing.
- **Lightweight sync:** Google Drive and WebDAV support for portable user data.

## Artifacts

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Signed release: `app/build/outputs/apk/release/app-release.apk`
- Device download name: `yomira-v8.0.0-beta.apk`

Both variants use the existing project signing identity for in-place upgrades.

## Verification

- `:feature:reader:testDebugUnitTest`: 55 tests, zero failures.
- `:app:testDebugUnitTest`: 98 tests, zero failures.
- `:domain:extension:test`: 25 tests, zero failures.
- `:data:extension:test`: 73 tests, zero failures.
- `:core:network:test`: 44 tests, zero failures.
- `assembleDebug assembleRelease`: successful, including release R8 and vital lint.
- Device copy skipped: no ADB device connected during release verification.

Live third-party source recovery has not been device-tested in this environment; automated tests exercise bridge failures, empty pages, chapter matching, trusted-source filtering, backup exhaustion, and cancellation.
