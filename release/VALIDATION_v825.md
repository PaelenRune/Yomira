# Yomira v8.2.5 validation

- Task: REL-825 / Yomira v8.2.5 release pipeline (two-tier blacklist + vault density
  diagnostics: hard-core tags always hidden, style tags demote-only; Vault rail
  fullness with deeper adult scan, two-variant title queries and stage-count
  diagnostics)
- Version: `8.2.5`, versionCode `29`.
- Merge: `feat/v825-vault-density` (single commit `59a8aac`, 11 files, +143/−21)
  merged into `main` (`588506a`) as **`3b90cf7`** - `git merge --no-ff`, **temiz**
  (no conflicts). The branch forked from `0f4d8fa` (v8.2.0-era main), so the
  three-way merge also carried main's v8.2.1-v8.2.4 work; overlap was limited to
  `MainActivity.kt` (main's v8.2.4 reader wiring + feature's `diagnosticInput`
  argument, distinct regions) and `PagedCatalogLoaderTest.kt` (main's v8.2.4 test
  additions + feature's variant-query assertions, distinct regions), both auto-merged
  by the ort strategy; both sides' contracts verified present post-merge.

## Tests and builds - PASS

Release gate on the merged tree (`3b90cf7`, pre-bump):

```powershell
.\gradlew.bat :app:testReleaseUnitTest --console=plain
.\gradlew.bat :core:model:test :feature:home:testReleaseUnitTest :feature:vault:testReleaseUnitTest :app:assembleRelease --console=plain
```

- Acceptance gate: `BUILD SUCCESSFUL` (`:app:testReleaseUnitTest` first full run
  **38s**; module gates + `assembleRelease` **1m 38s**). Release R8 shrinking and
  resource shrinking completed successfully (known non-fatal R8 Kotlin-metadata
  warnings, same as v8.2.1+).
- `:app:testReleaseUnitTest` XML totals: 32 suites, **192 tests, 0 failures, 0
  errors, 0 skipped** - the 192/192 gate (includes the merged vault/filters suites:
  `PagedCatalogLoaderTest`, `ReadingSourcePolicyTest`, `ContentTierMappingTest`).

Development worktree evidence (branch `feat/v825-vault-density` @ `59a8aac`, forked
from `0f4d8fa`) - all green at merge time:

| Module gate | Tests | Result |
|---|---:|---|
| `:core:model:test` | 30 | PASS |
| `:feature:home:testReleaseUnitTest` | 32 | PASS |
| `:feature:vault:testReleaseUnitTest` | 27 | PASS |
| `:app:testReleaseUnitTest` | 167 | PASS |
| **Total (worktree)** | **256** | **PASS** |

Post-merge main-tree module verification (`3b90cf7`, raw XML totals - all green):

| Module gate | Suites | Tests | Result |
|---|---:|---:|---|
| `:app:testReleaseUnitTest` | 32 | 192 | PASS |
| `:core:model:test` | 5 | 30 | PASS |
| `:feature:home:testReleaseUnitTest` | 8 | 39 | PASS |
| `:feature:vault:testReleaseUnitTest` | 4 | 27 | PASS |
| **Total (main tree)** | | **288** | **PASS** |

The worktree vs main-tree deltas come from the fork base, not from the merge: the
branch forked at v8.2.0-era `0f4d8fa`, so the worktree's app (167) and feature-home
(32) counts did not include main's v8.2.1-v8.2.4 additions (+25 app, +7 home, all
still present and green post-merge). core/model (30) and feature/vault (27) are
identical on both trees - the branch's own 7 new tests are included in both.

Release-source gate re-run after the version bump (28/8.2.4 -> 29/8.2.5):
`:app:testReleaseUnitTest` **192/192** and `assembleRelease` **BUILD SUCCESSFUL in
1m 40s** - the gate is green on the exact release source.

New coverage in `59a8aac`: **7 new unit tests** + 2 strengthened existing tests:

- `ContentFiltersTest` (core/model) +2: `hard aliases hide while style aliases only
  penalize`, `hard core is independent of preferences and rating` (every hard tag x
  every rating x preference combos).
- `ReaderSafetyCatalogTest` (feature/home) +1: `public catalog always hides hard
  core`.
- `ReaderSafetyCatalogTest` (feature/vault) +1: `all vault surfaces always hide hard
  core` (catalog + `buildVaultHome` collection/hero/rails).
- `VaultRailsTest` (feature/vault) +3: `diagnostics count each filtering stage`,
  `diagnostics break down each source and rail`, `style preference demotes without
  hiding` (penalized series demoted on the rail, still present in the collection).
- Strengthened (no new method): `PagedCatalogLoaderTest` (app) now asserts the exact
  two-variant query set (`vaultSearchVariants` union) reaches MangaDex;
  `ReadingSourcePolicyTest` (app) blacklist-wins test re-locked to a hard tag
  (Netorare/ntr -> Futanari/futanari), matching the two-tier semantics.

## APKs - PASS

| Variant | Path | Bytes |
|---|---|---:|
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 25,272,143 |

SHA-256:

```text
app-release.apk c15ab642ee12241bb1613fcdd8ae9849add61d738862636d7bb09e6d63f8b257
```

Built on the release source (versionCode 29 / versionName 8.2.5 confirmed via
`output-metadata.json`). APKs remain in build outputs and are not committed to Git.
The tag workflow publishes the release APK and its `SHA256SUMS.txt`; local validation
does not certify remote workflow completion.

## Commit inventory - 588506a..HEAD (since the v8.2.4 release commit)

| Commit | Subject | Scope |
|---|---|---|
| `59a8aac` | feat(vault): two-tier blacklist + vault density diagnostics (v8.2.5) | `ContentFilters` hard/style tag tiers + `stylePenalty` (core/model); `PagedCatalogLoader` adult page size 50 + first-page two-variant rail searches (`vaultSearchVariants`); `VaultRails` excluded-tags wiring + rank penalty + `vaultDiagnostics`/`vaultRailDiagnostics`; `VaultScreen` sparse-rail hint, true-empty guard, diagnostics `LaunchedEffect`; `MainActivity` `diagnosticInput` wiring |
| `3b90cf7` | merge: v8.2.5 two-tier blacklist + vault density (59a8aac) | Merge of `feat/v825-vault-density` into `main` (clean, no conflicts) |
| (release commit) | release: Yomira v8.2.5 (versionCode 29) - blacklist tiers + vault density | versionCode 28->29, versionName 8.2.4->8.2.5, `RELEASE_NOTES_v8.2.5.md`, `RELEASE_NOTES.md` sync, this validation file |

Merge aggregate (`588506a..3b90cf7`): 11 files changed, 143 insertions(+),
21 deletions(-).

## Phone verification - PENDING (bekliyor; left to the user's device tour)

No v8.2.5 device evidence was collected at release time. The two-tier semantics and
rail density are locked by the unit gates above; the items below need a device.

1. **Bekliyor** - Vault doluluk gözlemi: on the device with the Vault unlocked, open
   the Vault and compare shelf fullness against v8.2.4 - rails should be visibly
   fuller (adult pages now fetch 50 per provider instead of 25, plus the first-page
   two-variant title searches). Thin rails (3 items or fewer) must show the
   "Bu raf yavaş doldu; daha fazla sonuç için kaynakları eşitle." hint above the row,
   and the full empty-state must only appear when the grid is truly empty (not during
   load-more or a paging error). Optionally capture `VaultDiagnostics` logcat lines
   (per source x rail stage counts, no titles) to attach as evidence.
2. **Bekliyor (not)** - MangaDex başlık-tabanlı varyant limiti notu: the two-variant
   rail queries are **MangaDex-only, title-based, first-page-only**, two variants per
   rail with 20 results each - a bounded design limit (providers have no OR operator;
   only page 1 repeats no searches). On the device, confirm the variant-fed rails
   (Adult Webtoons / Hentai Manga / Doujinshi / Western-3D) actually fill; if a rail
   stays thin despite the variants, the bottleneck is MangaDex title-search coverage,
   not the app - that observation closes this item.
3. **Bekliyor** - EXPLICIT sızıntı taraması: with adult content hidden (Vault locked /
   adult content toggle off), sweep the public discovery feed, search results and a
   few series-detail screens for any EXPLICIT-rated or hard-core-tagged series
   (futanari/incest/bestiality/rape/loli/shota). None may appear - the two-tier gate
   hides hard core on every surface regardless of preferences, age or consent
   (asserted in unit tests; needs a live data sweep on the device). Conversely, with
   the Vault unlocked, +18-themed series carrying only style tags (harem/NTR/gore/
   ecchi) must be visible in discovery and merely demoted in ordering.
