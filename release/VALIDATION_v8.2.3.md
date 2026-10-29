# Yomira v8.2.3 validation

- Task: REL-823 / Yomira v8.2.3 release pipeline (extension repo logos with favicon
  fallback + OLED-friendly plate)
- Version: `8.2.3`, versionCode `27`.

## Tests and builds — PASS

Release-gate run on the release source (`e8b135b` + version bump):

```powershell
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest --console=plain
```

- Acceptance gate: `BUILD SUCCESSFUL in 2m 36s`; 493 actionable tasks: 31 executed,
  462 up-to-date. Release R8 shrinking and resource shrinking completed successfully.
- `:app:testReleaseUnitTest` XML totals: 31 suites, **186 tests, 0 failures, 0 errors,
  0 skipped** — the 186/186 gate.

Development worktree evidence (`Yomira-wt-v823` @ `09648af`, feature branch
`feat/v823-extension-logos`) — all green at merge time:

| Module gate | Tests | Result |
|---|---:|---|
| `:domain:extension:test` | 40 | PASS |
| `:data:extension:test` | 103 | PASS |
| `:app:testReleaseUnitTest` (extensions suites) | 14 | PASS |

Post-merge main-tree verification (`e8b135b`): `:app:testReleaseUnitTest`
**186 tests / 0 failures / 0 errors / 0 skipped** plus a successful
`assembleRelease` (**4m 50s**).

New coverage in this release: **17 new unit tests** (17 `@Test` annotations added
across `1a85ea1..e8b135b`):
  - New suites — `ExtensionLogoTest` (domain: icon resolution, favicon fallback,
    initials, HTTPS-only policy, cache-key canonicalization), `IcoImageTest`
    (app: PNG-in-ICO extraction, BGRA DIB + legacy zero-alpha, truncated/oversized
    container rejection, pre-allocation dimension guards).
  - Extended — `JsonSourceManifestTest`, `KeiyoushiRepoParserTest`,
    `ProtoRepositoryParserTest`, `RepositorySyncTest` (icon URL parsing carries
    through every repository index format).
- Known non-fatal R8 warnings about Kotlin metadata parsing (Kotlin newer than the bundled
  R8); the build and shrinking stay green — same as v8.2.1.

## APKs — PASS

| Variant | Path | Bytes |
|---|---|---:|
| Signed, minified release | `app/build/outputs/apk/release/app-release.apk` | 25,270,567 |

SHA-256:

```text
app-release.apk 83d48754a026ffc3cec211f794508d498827c2f232c7875f4c62ed56778caf07
```

APKs remain in build outputs and are not committed to Git. The tag workflow publishes
the release APK and its `SHA256SUMS.txt`; local validation does not certify remote
workflow completion.

## Commit inventory — 1a85ea1..e8b135b (since v8.2.1 release commit `1a85ea1`)

| Commit | Subject | Scope |
|---|---|---|
| `09648af` | feat(extensions): source-site logos with favicon fallback + OLED plate (v8.2.3) | ExtensionLogo display policy (domain) + icon/favicon URL parsing in every repo index format (data) + ExtensionLogoImage/ExtensionIcoDecoder/IcoImage UI (app) |
| `e8b135b` | merge: v8.2.3 extension logos (pre-release) | Merge of `feat/v823-extension-logos` into `main` |

Aggregate: 17 files changed, 353 insertions(+), 3 deletions(-); test files alone:
5 files touched with 4 new/extended suites in `data/extension` plus 2 new suites.

## Phone verification — PENDING (bekliyor; left to the user's device tour)

No v8.2.3 screen/log evidence was collected at release time (device install out of
scope for this release). Items below cover the new logo work; the v8.2.1 device
checklist remains open separately in `release/VALIDATION_v8.2.1.md`.

1. **Bekliyor** — Logo visibility TR/EN: with the app language set to Turkish and then
   English, the Extensions screen shows the circular logo plate on every row of the
   repository list and the installed/available extension lists.
2. **Bekliyor** — Favicon fallback: an extension row without a usable index icon (or a
   repository row) resolves the source site's `/favicon.ico` and renders it; a broken
   icon URL silently falls through to the favicon instead of leaving an empty plate.
3. **Bekliyor** — OLED plate: on the dark/OLED theme the plate background renders as a
   dark surface; transparent favicons and light-colored logos stay legible, with no
   white box or halo around the artwork.
4. **Bekliyor** — Initial-letter fallback: with no artwork reachable (e.g. offline or
   site without favicon), rows show the name's initial letters (first two words,
   uppercase; `?` for empty names) on the plate.
