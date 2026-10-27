# Yomira v6.0.0-Beta

2026-10-27 · Android 8.0+ (API 26) · package `com.yomira.app` · versionCode `11`

## New in this beta

### 10x Smart Matcher Engine

- Automatic Language Lock uses the saved onboarding reading language (Turkish, English, or both); an unrelated source language cannot silently replace it.
- Source ranking considers availability, chapter completeness, recency, and response time within the permitted language tier.
- Opening a series with no stored chapters starts matching and activates a ready source directly. No source-choice dialog is required.
- Built-in MangaDex API routing supplies chapter/page references without claiming that an upstream extension APK has been installed.
- “10x” is the feature name, not a measured throughput or latency guarantee. Network availability and source coverage still determine whether a series can be opened.

### Watchable Adaptations Engine

- Detail screens distinguish watchable adaptations from related readable series: Anime, Hentai OVA, Drama / Live-action (including provider-labelled K-Drama), Donghua, and Specials.
- Adaptation cards preserve available format, episode count, release year, score, and artwork. They are metadata cards, not video playback or manga-reader destinations.
- Badges are shown only when the metadata provider supplies a qualifying relation. Live-action coverage is provider-dependent; there is no separate K-Drama discovery service in this beta.

### Vault 2.0 Dual-Realm Home Screen

- A separate adult home mirrors the public experience with its own hero, curated rails, style filters, and complete collection grid.
- Four styles: **Adult Webtoons**, **Hentai Manga**, **Doujinshi**, and **Western / 3D**.
- Every Vault surface requires explicit content and an unlocked Vault; public surfaces exclude explicit records.
- OS device-credential authentication (PIN/pattern/password), panic navigation, configurable background re-lock, and release-build secure-window protection remain in place. A separate biometric-prompt flow is not implemented or verified here.
- Device-lock and background-lock behavior follow the user's privacy settings; they are not unconditional when those settings are disabled.

### AllPornComic & Content Classifier v2

- AllPornComic source identity and normalized provider tags retain Western/3D adult imports in the appropriate Vault style.
- Explicit platform/pornographic evidence is distinguished from softer mature content. `3D Comics`, `Western Erotica`, `Ecchi`, and `Smut` alone do not imply pornography.
- Known mature serials and alias corrections remain public rather than being incorrectly hidden in the Vault.

## Persistence and known limits

- Onboarding preferences and settings persist through DataStore; saved-library flags persist locally; chapter metadata and reading progress use Room.
- Offline catalogue/library browsing and stored reading positions are supported. **Full chapter-image offline downloads are not implemented**: the download action currently explains this limitation, and the reader has no persistent image disk cache.
- Missing/untrusted extensions, unavailable providers, incomplete source catalogues, and empty page lists can still prevent reading. Automatic source activation applies to ready matches, not every provider or series.
- Vault rail headings currently use module-default Turkish text in both UI languages; style chip labels are English.

## Build and verification

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Version configuration: `app/build.gradle.kts` → `6.0.0-Beta` / `11`.
- Fresh full-suite run: **491 tests passed**, 0 failures/errors/skips. `assembleDebug` succeeded; APK v2 signature verified.
- APK SHA-256: `d89db2a3300f559c0ca64fd4b400209ee822e8d963c1bb20f767c3c624496c64`.
- Detailed test, build, and simulated ten-minute journey results: [VALIDATION_v6.0.0-Beta.md](VALIDATION_v6.0.0-Beta.md).
- This document prepares a beta release; it does not publish an APK, Git tag, or GitHub release.
