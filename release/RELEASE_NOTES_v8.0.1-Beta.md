# Yomira v8.0.1-Beta

Android versionCode: **15**.

## Tam sıfırlama / Factory Reset

- Ayarlar → Depolama & Önbellek → **Tüm Verileri Sıfırla (Fabrika Ayarlarına Dön)**.
- Destructive button with Turkish/English copy and an explicit **Vazgeç / Sıfırla** confirmation.
- Removes the library, history, progress, Room tables, all Settings and Onboarding DataStore
  preferences, image memory/disk cache, network cache and temporary files.
- Android's full application-data wipe then removes remaining private files, installed sandbox
  extensions, repository configuration, sync identity, UI preferences and process memory.
- **The app closes intentionally. Open Yomira again to start fresh onboarding**, just like a
  first installation. Factory reset is irreversible. Normal updates do not reset user data.
- Google Drive backups, exported files outside app-owned storage and other apps are not deleted.
- Duplicate reset requests are blocked; failures are surfaced with a retry / Android clear-storage hint.

## Validation

```powershell
.\gradlew.bat :feature:settings:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease
```

Unit coverage exercises production preference-store wrappers with in-memory DataStore backing
(including unknown keys), restoring secure
defaults and absent onboarding, cache/database dispatch, owner failure fallback, Android refusal,
duplicate reset prevention, maintenance exclusion and retry. Device-level first-run verification
requires relaunching after Android terminates the app; it is not implied by the JVM tests.

Local result: **114 tests passed; debug and signed release APKs built successfully**.
See [validation details and APK hashes](VALIDATION_v8.0.1-Beta.md). No ADB device was connected,
so live reset/relaunch verification remains unperformed.
