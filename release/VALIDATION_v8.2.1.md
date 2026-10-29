# Yomira v8.2.1 validation

- Task: REL-821 / Yomira v8.2.1 release pipeline (adaptation badges + EN-first titles +
  tachibk backups + onboarding/reader fixes)
- Version: `8.2.1`, versionCode `26`.
- Versioning note (internal): the `8.2.2` number is intentionally skipped; the scope
  originally earmarked for it shipped in this release. Not surfaced in user-facing notes.

## Tests and builds — PASS

Release-gate run on the release source (`e219fc4` + version bump):

```powershell
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest --console=plain
```

- Acceptance gate: `BUILD SUCCESSFUL in 1m 46s`; 493 actionable tasks: 32 executed,
  461 up-to-date. Release R8 shrinking and resource shrinking completed successfully.
- `:app:testReleaseUnitTest` XML totals: 30 suites, **182 tests, 0 failures, 0 errors,
  0 skipped** — the 182/182 gate.
- C2 report raw numbers on the release source: full **release Android+JVM sweep —
  748 tests, 0 failures / 0 errors / 0 skipped**; **root test sweep successful**;
  **`assembleRelease` successful**.
- New coverage in this release: **36 new unit tests + 1 new Compose test** (37 `@Test`
  annotations added across `3108aa7..e219fc4`):
  - New suites — `DetailAdaptationsTest`, `TitleLocalizerTest`, `ArtworkResolverTest`
    (extended), `MangaDexCoverTest` (extended), `WaterfallMetadataAggregatorTest`
    (extended), `LibraryMetadataBackfillTest`, `LocalBackupCodecTest`,
    `SyncProjectionWriterTest`, `OnboardingPrimaryActionTest`.
  - Extended — `CatalogRecordMapperTest`, `ReadingSourceResolverTest`,
    `ReaderRetryWiringTest`, `CatalogSeederTest`, `PagedCatalogLoaderTest`.
  - Compose — `OnboardingScreenTest` (feature:home `androidTest`): repository step
    primary-action layout.
- Known non-fatal R8 warnings about Kotlin metadata parsing (Kotlin newer than the bundled
  R8); the build and shrinking stay green.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 25,254,183 |

SHA-256:

```text
app-release.apk 033053eaa6a54cf7cb21f14df82025d3ea2b5518057c2b37362d50ec4179ba61
```

APKs remain in build outputs and are not committed to Git. The tag workflow publishes
the release APK and its `SHA256SUMS.txt`; local validation does not certify remote
workflow completion.

## Commit inventory — 0f4d8fa..e219fc4 (since v8.2.0 release commit `3108aa7`)

| Commit | Subject | Scope |
|---|---|---|
| `0f4d8fa` | feat(details): keep adaptation badges in tier-led metadata header | Adaptation badges permanent on series detail |
| `2cc2100` | feat(metadata): EN-first title/cover rule + library backfill | TitleLocalizer/ArtworkResolver policy + backfill worker + Settings entry |
| `04a055d` | feat(metadata): share title policy in feeds and prefer original MangaDex covers | Feeds use same title policy; MangaDex original covers |
| `ef231b1` | feat(settings): add SAF tachibk backup and restore entries | LocalBackup codec/service/VM + Settings entries (TR/EN) |
| `2dcbbe1` | fix(onboarding): make repository primary action add, not skip | Onboarding repo step: add is primary, skip secondary |
| `5b7bfd0` | fix(reader): retry unreachable source resolution in one tap | Reader error screen one-tap retry |
| `e219fc4` | fix(backup): isolate library restore from extension materialization | Restore works without extensions materialized |

Aggregate: 46 files changed, 944 insertions(+), 23 deletions(-); test files alone:
15 files, +317/−3.

## Phone verification — PENDING (bekliyor; left to the user's device tour)

No v8.2.1 screen/log evidence was collected at release time (device install out of
scope for this release). Debt list below merges (1) every check from
`docs/validation/C2-V821-v2-soak.md`, carried as **bekliyor**, and (2) the unclosed
items carried from `release/VALIDATION_v8.2.0.md`.

### New in v8.2.1 — from `C2-V821-v2-soak.md`

Onboarding repository step:

1. **Bekliyor** — First-run onboarding TR and EN: progress language → age → tags →
   favorites → repository step.
2. **Bekliyor** — Primary purple button must read **Depo ekle / Add repository**;
   **Atla / Skip** must be a secondary text button.
3. **Bekliyor** — Primary button opens only the confirmation dialog; cancel must stay
   on the same step without adding a repository.
4. **Bekliyor** — After confirmation the repository is added exactly once and onboarding
   completes; double-tap during save must be prevented.
5. **Bekliyor** — Secondary Skip finishes onboarding without adding a repository;
   back/forward must preserve favorite selections.
6. **Bekliyor** — Re-running the survey from Settings must not show the repository step;
   the final button keeps the Start action.

Other package checks:

7. **Bekliyor** — Detail header: Anime/K-Drama/Donghua/OVA badges with the tier badge
   leading; no explicit adaptation leak while the Vault is locked.
8. **Bekliyor** — Storage: scan progress, cancel, manual restart; pause on screen lock /
   Data Saver / network change. Reading and library state must not change.
9. **Bekliyor** — Storage: SAF backup create; file named
   `yomira-backup-YYYYMMDD-HHmm.tachibk`. Cancel silent; corrupt file must show the
   error toast (TR/EN).
10. **Bekliyor** — Restore the backup on a fresh/empty install: name/cover,
    SAFE/MATURE/EXPLICIT tier, favorites/tags and reading history preserved. Current
    fresh state must not be rolled back by an older backup.
11. **Bekliyor** — Cut source access and see the current reader error text; fix the
    network and restart resolution with a single **Yeniden dene / Retry**. Button must
    disappear while waiting; repeat error and Back must work.

Backup scope reminder: library and reading history only; downloads and security
preferences excluded; file unencrypted; the UI shows that Vault titles may be included.

### Carried from v8.2.0 (`VALIDATION_v8.2.0.md`, all still bekliyor)

12. **Bekliyor** — Vault NSFW consent dialog timing and one-shot behavior: asked exactly
    once, right after the biometric/PIN unlock, never again afterwards.
13. **Bekliyor** — Consent rejection visual: declining keeps the Vault shelves empty while
    the entry stays locked (the "Yetişkin içerik gizli" state).
14. **Bekliyor** — Placement matrix live scan: safe mode sees SAFE only; an 18+ profile also
    sees MATURE on Home, Discover, search and rails; EXPLICIT stays Vault-only.
15. **Bekliyor** — 16+/18+ tier badge placement on series detail in both TR and EN.
    (REL-820 tour dumps in `.orchestrator/execution/rel820_screens/` show `16+` badges
    rendering on home and detail, but no findings document closes this item.)
16. **Bekliyor** — Kitsu `nsfw` companion field confirmation against live API data.
17. **Bekliyor** — AniList `isAdult` flag and mature-theme tags live mapping match.
18. **Bekliyor** — Onboarding annotated copy overflow check in TR and EN.

### Long-standing debt

19. **Bekliyor (borç)** — **Zoom → black screen in the reader** still open: candidate
    root cause is the GPU texture limit at 5× scale (hardware bitmap + large layer);
    candidate fixes (clamp zoom upper bound to the device texture limit, re-decode /
    tile at zoom time) are tracked in the WBS for the v9.0.0 release gate. Not addressed
    in v8.2.1.
