# Yomira v8.2.4 validation

- Task: REL-824 / Yomira v8.2.4 release pipeline (unified matcher engine:
  normalization + alias dictionary + fan-out, plus the FIX-M2 device-tour fixes)
- Version: `8.2.4`, versionCode `28`.
- Merge: `feat/v824-matcher-engine-r2` (5 commits: `5a3f64d`, `3118ba5`, `35a260f`,
  `6e7a68c`, `a077574`) merged into `main` (`6696b37`) as **`b8df750`** -
  `git merge --no-ff`, **temiz** (no conflicts): `YomiraViewModel.kt`
  (main's `readingIds` library-search scope + feature's `TitleMatcher` integration,
  distinct regions) and `data/metadata/build.gradle.kts` (main's MockWebServer test
  dependency kept + feature's serialization-json test dependency added) were
  auto-merged by the ort strategy; both sides' contracts verified present post-merge.

## Tests and builds - PASS

Release-gate run on the release source (`b8df750` + version bump 27/8.2.3 -> 28/8.2.4):

```powershell
.\gradlew.bat :app:testReleaseUnitTest :domain:extension:test :app:assembleRelease --console=plain
```

- Acceptance gate: `BUILD SUCCESSFUL` (first full run **3m 14s**, re-run on the bumped
  release source **1m 51s**); release R8 shrinking and resource shrinking completed
  successfully (known non-fatal R8 Kotlin-metadata warnings, same as v8.2.1+).
- `:app:testReleaseUnitTest` XML totals: 32 suites, **192 tests, 0 failures, 0 errors,
  0 skipped** - the 192/192 gate.
- `:domain:extension:test` (matcher engine incl. benchmark) XML totals: 10 suites,
  **88 tests, 0 failures, 0 errors, 0 skipped**.

FIX-M2 module gates (the three device-tour fix commits on `main`, evidence from the
commits' own gate runs):

| Module gate | Tests | Result | Commit |
|---|---:|---|---|
| `:app:testReleaseUnitTest` | 192 / 0 | PASS | `4b6805b` (initials fallback) |
| `:feature:home:testReleaseUnitTest` | 38 / 0 | PASS | `6696b37` (library search scope) |
| `:data:metadata:test` | 43 / 0 | PASS | `0774c19` (connections pipeline tests) |
| `:core:network:test` | 51 / 0 | PASS | `0774c19` (same gate run) |

Post-merge main-tree module re-verification (after `b8df750`, all green):

| Module gate | Tests | Result |
|---|---:|---|
| `:app:testReleaseUnitTest` | 192 / 0 | PASS |
| `:domain:extension:test` | 88 / 0 | PASS |
| `:feature:home:testReleaseUnitTest` | 38 / 0 | PASS |
| `:data:metadata:test` | 50 / 0 | PASS (43 FIX-M2 + 7 alias-sink/aggregator tests from the merge) |
| `:core:network:test` | 51 / 0 | PASS |

New coverage since v8.2.3 (`09ff273..b8df750`): **73 new unit tests** (+ 1 new
androidTest locking Room migration 6->7) - TitleNormalizer 22, TitleMatcher 16,
QueryFanout 8, benchmark harness 1, SmartSourceMatcher +1, ExtensionLogoFallback 6,
LibrarySearchScope +4, SeriesConnectionRepository +4, WaterfallMetadataAggregator
+2, WaterfallMetadataAggregatorAliasSink 5, RoomSeriesAliasSink 4, and the
`SeriesAliasMigrationTest` androidTest.

## Benchmark - %90 kapısı GEÇTİ (PASSED)

100-series benchmark harness (`SeriesTitleBenchmarkTest`, testset-100.json): every
series' EN, original and alias names issued as lookup queries against all 100 series;
the best-scoring series must be the query's own canonical id at/above
`TitleMatcher.MATCH_THRESHOLD`. Raw stdout lines:

```text
BENCHMARK total queries: 412
BENCHMARK BEFORE (v8.2.3 legacy engine): 93,7% (386/412)
BENCHMARK AFTER  (unified engine):      100,0% (412/412)
BENCHMARK hard subset (zor/low):        100,0% (151/151)
```

- Acceptance gate "unified engine resolves at least 90 percent of en original and
  alias queries": **GEÇTİ** - 100,0% (412/412) >= 90% (asserted by the test itself;
  suite green in the 88/88 gate above).
- Hard subset (difficulty `zor` / confidence `low`): 151/151, 100,0%.
- BEFORE is the verbatim port of the private matcher inside `SmartSourceMatcher`
  at v8.2.3 (`09ff273`), measured on the same queries with the same tie-breaking.

## APKs - PASS

| Variant | Path | Bytes |
|---|---|---:|
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 25,272,143 |

SHA-256:

```text
app-release.apk f4120df4785ecfeb9818d3ffb66f4524760ad0b37c5e21c376f36d00d371d9ca
```

Built on the release source (versionCode 28 / versionName 8.2.4 confirmed via
`output-metadata.json`). APKs remain in build outputs and are not committed to Git.
The tag workflow publishes the release APK and its `SHA256SUMS.txt`; local validation
does not certify remote workflow completion.

## Commit inventory - 09ff273..b8df750 (since v8.2.3 release commit)

| Commit | Subject | Scope |
|---|---|---|
| `5a3f64d` | feat(matcher): unified normalization core | `TitleNormalizer` (NFKC fold, kana, romaji variants, season markers) shared by every comparison path |
| `3118ba5` | feat(matcher): series alias dictionary | `SeriesAliasEntity`/`Dao`/`SeriesAliasSink`, Room schema 7 + `MIGRATION_6_7`, waterfall aggregator feeds every observed name |
| `35a260f` | feat(matcher): shared scoring across search paths | `TitleMatcher` (subject cache, typing gate, fuzzy identity) wired into `SmartSourceMatcher`, shelf search, source router |
| `6e7a68c` | feat(matcher): multi-variant query fan-out | `QueryFanout` (bounded 4-variant live-lookup spellings, stop at first rich result) |
| `a077574` | test(matcher): 100-series benchmark harness | `SeriesTitleBenchmarkTest` + `testset-100.json` (100 series, EN/original/romaji/aliases, difficulty+confidence labels) |
| `4b6805b` | fix(extensions): render initials fallback when logo load fails | FIX-M2: drop `clearAndSetSemantics` pruning, `ExtensionLogoFallback` state machine, 6 tests |
| `0774c19` | test(metadata): lock connections pipeline that feeds adaptation badges | FIX-M2: MockWebServer end-to-end chain tests (title search, alt-title fallback, transport failure, unknown series) |
| `6696b37` | fix(search): find read-but-unsaved series in library search | FIX-M2: `SyncDao.observeReadingMangaIds()`, `readingIds` StateFlow, `searchBeyond` scope, 4 tests |
| `b8df750` | merge: v8.2.4 unified matcher engine (normalizasyon + alias sözlüğü + fan-out) | Merge of `feat/v824-matcher-engine-r2` into `main` (clean, no conflicts) |

Aggregate: 38 files changed, 2,988 insertions(+), 107 deletions(-).

## Phone verification - PENDING (bekliyor; left to the user's device tour)

No v8.2.4 device evidence was collected at release time. The FIX-M2 fixes above were
*derived from* device evidence (rel823_screens QA-823 tour, pre-v9-qa FAZ-0/1/2) but
the fixed build has not yet been re-verified on a device.

1. **Bekliyor** - Orijinal-ad araması (5 sorgu tekrarı): on the device, search by
   original/Japanese names and spelling variants - e.g. `ナルト` (NARUTO),
   `呪術廻戦` (Jujutsu Kaisen), romaji and season-suffix variants - and repeat each
   query 5 times to confirm the matcher resolves the same series stably on every
   repetition (fan-out + alias dictionary must not flake between attempts).
2. **Bekliyor** - Logo fallback görünürlüğü (dump'ta initials-Node): on the Extensions
   screen with artwork unreachable, the uiautomator dump must now contain the
   initials text node inside the logo slot. QA-823 evidence (`rel823_screens`) showed
   every 121x121 slot as a childless self-closing View with no fallback text
   (screencap unavailable on that MIUI device, so the dump is the verification
   channel); `4b6805b` removed the semantics pruning, so the initials-Node is the
   expected pass signal.
3. **Bekliyor** - 'One Piece' kitaplık araması: with the Home resume card actively
   reading One Piece (not saved to the library), typing `one piece` on the Kitaplık
   tab must return the series (QA-823 reproduced the bug: "Sonuç bulunamadı" while
   the resume card showed the same series; `6696b37` added read-but-unsaved series to
   the library search scope).
4. **VERİ-KISITI** (not pending device work) - Uyarlama rozeti (Anime/Donghua/K-Drama):
   marked as a **data constraint** per decision İŞ-2 - the provider signal is
   insufficient (the QA device cannot reach `graphql.anilist.co`; the connections
   payload never arrived, and no offline adaptation signal exists to wire). The
   chain that will render the badges once the provider is reachable is locked by the
   MockWebServer end-to-end tests in commit `0774c19` (title-search resolution,
   alt-title fallback, transport-failure degradation, unknown-series empty
   showcases). No device verification is possible until a provider-reachable session.
