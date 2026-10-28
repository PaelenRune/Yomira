# Yomira v8.2.0 validation

- Task: REL-820 / Yomira v8.2.0 release pipeline (three-tier content model + vault consent)
- Version: `8.2.0`, versionCode `25`.

## Tests and builds — PASS

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleDebug :app:testReleaseUnitTest --console=plain
```

- Acceptance gate: `BUILD SUCCESSFUL in 1m 49s`; 782 actionable tasks: 47 executed,
  735 up-to-date. Release R8 shrinking and resource shrinking completed successfully.
- `:app:testReleaseUnitTest` XML totals: 27 suites, **167 tests, 0 failures, 0 errors,
  0 skipped** — the 167/167 gate.
- Full module unit-test sweep on the release source: **172 suites, 1046 tests, 0 failures**.
- New tier-mapping coverage: `ContentTierMappingTest` — **13 tests** covering every provider
  signal (MangaDex contentRating, AniList isAdult + mature theme tags, Kitsu ageRating +
  nsfw flag, Baka Mature genre labels), end-to-end explicit mapping, cautious-upper conflict
  resolution and the classifier honouring it.
- Vault NSFW consent roundtrip: **2 tests** (`vaultNsfwConsentRoundTripsThroughTheDataStore`,
  `vaultNsfwConsentDecisionChangesThroughTheUpdatePipeline`).
- Public-surface isolation tests extended to the new placement matrix
  (`CatalogIsolationTest`, `HomeRailsTest`, `ReaderSafetyCatalogTest`) and the tier badge
  pattern (`PosterAgeLabelTest`).
- Known non-fatal R8 warnings about Kotlin metadata parsing (Kotlin newer than the bundled
  R8); the build and shrinking stay green.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | 38,341,735 |
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 25,232,515 |

SHA-256:

```text
app-debug.apk   783ad6070ffd1bc50512d56e11e1be8b221498831063061bad716a7a8cc4034a
app-release.apk 5203379300af46b134b96a11fc8a478b7e9653c5a457c3e6211afa9e825723de
```

APKs remain in build outputs and are not committed to Git. The tag workflow publishes
the release APK and its `SHA256SUMS.txt`; local validation does not certify remote
workflow completion.

## Phone verification — PENDING (bekliyor; left to the user's device tour)

Device check performed at release time (does not block the release):

- `adb devices -l` found one authorized device: `b4bc1593` (Xiaomi M2007J3SY, `apollo`).
- Installed Yomira on that device: `8.1.4` (versionCode `24`, last update 2026-10-28).
  The v8.2.0 build is not installed and installing via adb was out of scope for this
  release, so no v8.2.0 screen/log evidence could be collected for the items below.

Checklist — every item remains **bekliyor (pending; left to the user's device tour if no
suitable device)**:

1. **Bekliyor** — Vault NSFW consent dialog timing and one-shot behavior: asked exactly
   once, right after the biometric/PIN unlock, never again afterwards.
2. **Bekliyor** — Consent rejection visual: declining keeps the Vault shelves empty while
   the entry stays locked (the "Yetişkin içerik gizli" state).
3. **Bekliyor** — Placement matrix live scan: safe mode sees SAFE only; an 18+ profile also
   sees MATURE on Home, Discover, search and rails; EXPLICIT stays Vault-only.
4. **Bekliyor** — 16+/18+ tier badge placement on series detail in both TR and EN.
5. **Bekliyor** — Kitsu `nsfw` companion field confirmation against live API data.
6. **Bekliyor** — AniList `isAdult` flag and mature-theme tags live mapping match.
7. **Bekliyor** — Onboarding annotated copy overflow check in TR and EN.
