# Yomira v8.0.5-Beta

Version code: **19** · Android 8.0+ (API 26)

## English default and full-app onboarding language sync

- Fresh installs explicitly use English, regardless of the device language.
- Selecting Turkish in onboarding immediately switches the application UI to Turkish.
- Selecting English or Both switches the UI to English; Both still enables both reading languages.
- The selected UI locale applies across Home, Discover, Library, Journey and Settings through AppCompat application locales.
- Completing setup applies the matching UI locale, and startup restores the saved onboarding language with the existing recreation-loop safeguard.
- All four onboarding steps and the settings survey now use English/Turkish localized copy, including age choices, navigation and exclusion guidance.

## Verification

```powershell
.\gradlew.bat :feature:home:testDebugUnitTest :app:testDebugUnitTest assembleDebug assembleRelease
```

APKs: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.
Results and checksums: [VALIDATION_v8.0.5-Beta.md](VALIDATION_v8.0.5-Beta.md).

## Device verification checklist

1. On a Turkish-language device, use a fresh installation or the app’s reset flow. Setup should open in English with English selected.
2. Select Türkçe (TR). Setup should switch to Turkish immediately without losing the current step or selections.
3. Complete setup and check Home, Discover, Library, Journey and Settings; each should use Turkish.
4. Repeat with English and Both. Both must keep both reading languages while using English for the UI.
5. Relaunch the app and verify the saved onboarding language is restored without repeated activity recreation.

This checklist describes the required device checks, not a claim that device/UI automation was run.
