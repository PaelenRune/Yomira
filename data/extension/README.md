# Extension integration (Phase 3)

## Implemented modules

* `:domain:extension`: platform-independent source DTOs, repository/bridge/discovery contracts,
  `SmartSourceMatcher`, onboarding preference model, and `OnboardingWizardManager`.
* `:data:extension`: `KeiyoushiRepoParser`, `ExtensionManager`, bounded/cancellable
  `OkHttpRepositoryClient`, atomic private-file persistence, and `SandboxSourceBridge`.

The core contains no chapter content, source implementations, APK bundles, or server. Curated repository
indexes are packaged as JVM resources (also included in the Android APK) under `yomira/repositories/`.
`repositories/catalog.json` lists their canonical HTTPS identities and resource paths. Every index in
that catalog is immediately available through `ExtensionManager`, even with an empty URL store and
without calling `refresh`. Bundled snapshots require no network to load. `available()` automatically
attempts an upstream metadata sync on first use and then at most once per six hours while in use;
`refresh()` forces a check. Offline/invalid upstream responses retain the last valid in-memory snapshot.
No background service runs while the app is closed, and refreshed indexes are not persisted across
process restarts. Explicit additional URLs are fetched on refresh. Completing onboarding does
**not silently install or automatically trust APK code**.

## Bundled catalog generation and exports

Run `powershell -ExecutionPolicy Bypass -File scripts/update-curated-repositories.ps1` on Windows,
or `pwsh -File scripts/update-curated-repositories.ps1` using PowerShell 7 on Linux/macOS.
The generator fetches Keiyoushi's v2 metadata and produces non-overlapping Turkish, Western, and
remaining-global catalogs, including all upstream entries. This is repository metadata conversion,
not conversion of executable APK scraping code into JSON.

* `repositories/{turkish,western,global}/index.enc`: bundled UTF-8 JSON encoded with the versioned
  `YOMIRA-XOR-1:` wrapper. The loader and parser accept ordinary JSON and wrapped payloads.
* `repositories/export/{turkish,western,global}/index.min.json`: clean, publishable JSON copies.
  **Never package this folder**: `processResources` explicitly excludes `export/**`.
* `repositories/catalog.json`: resource paths, canonical HTTPS identities, upstream URL and grouping.
  The application checks upstream directly; publishing the export URLs is not needed for auto-sync.

All groups are encoded, including any adult metadata in otherwise general-purpose groups. Fixed-key
XOR only hides readable strings from casual APK inspection. It is **not cryptographic secrecy**:
the app carries the key, a determined analyst can decode the files, and users can see source metadata
in memory. Repository exports and this source checkout intentionally contain plaintext metadata.
No APK execution trust or automatic source installation is implied by a metadata update.

For daily export regeneration, schedule the same command using Task Scheduler, or use cron:

```cron
0 4 * * * cd /path/to/Yomira && pwsh -NoProfile -File scripts/update-curated-repositories.ps1
```

Commit/publish the generated outputs as needed. Generation requires network access and PowerShell;
normal builds use committed snapshots and do not fetch upstream. Existing auto-seeded legacy URLs
are mapped to the bundled catalogs to avoid overlapping package ownership after upgrade.

## APK-to-JSON conversion

`RuntimeApkToJsonConverter` is used directly by `SandboxExtensionInstaller`. It reads APK ZIP entries
in-process, parses text or Android binary XML manifests, checks package/version against the advertised
descriptor, discovers declared `tachiyomi.extension.class` entry points and DEX class descriptors,
and fingerprints assets. An optional `assets/yomira/extension.json` asset can supply `packageName`
and `entryClasses`. ZIP paths, duplicate entries, entry counts and decompressed byte sizes are bounded.
Invalid metadata is rejected before replacing an existing DEX cache.

The installed definition is a schema-v2 `JsonSourceManifest`, including APK inspection provenance;
schema-v1 records remain readable. Assets are inspected/fingerprinted, not executed or installed.
Only root `classes*.dex` files are extracted. No Android PackageInstaller or unknown-app permission
is involved. Arbitrary compiled scraping logic cannot be losslessly translated into declarative JSON:
JSON records source routing, entry points and hashes, while executable behavior remains pinned DEX
and must run behind the existing `IsolatedExtensionRuntime` boundary. Manifest inspection is **not**
APK publisher-signature verification and does not grant execution trust.

## Application wiring

1. Create singleton `FileRepositoryStore` and `FileOnboardingStore` using separate paths inside the
   private application directory. Construct `ExtensionManager` with an HTTP client, parser, and an
    `InstalledExtensionProvider` backed by the JSON manifest store and persisted certificate consent.
2. Present TR/EN and +18 controls and invoke `OnboardingWizardManager.complete`. Handle exceptions
   as retryable errors; completion is persisted only when setup succeeds. On a cold start, invoke
    `ExtensionManager.refresh()` to force a sync, or use `available()` for throttled automatic sync
    before matching (the repository snapshot is intentionally in memory).
3. Implement `IsolatedExtensionRuntime` in the Android host using authenticated IPC, Mihon-compatible
   source APIs, cancellable calls, source ownership checks, and package certificate trust. Inject it
   into `SandboxSourceBridge`; never replace this boundary with an in-process class loader.
4. Inject a `SourceDiscovery` for independently obtained, identity-matched catalog observations of
   uninstalled sources. If no catalog exists, return an empty list; **do not invent chapter counts**.
5. `SmartSourceMatcher.match` filters by consent/language, searches trusted installed sources,
   fetches their chapter lists, and merges independently observed uninstalled-source candidates.
   It ranks by chapter count, then latest upload timestamp, then installed status for ties.
6. `MissingExtension` supplies the suggested source, factual chapter count, install URL, and alternatives
   for a confirmation modal. The host handles user-approved APK download, package/certificate checks,
    sandbox conversion through `SandboxExtensionInstaller`, and refresh after installation. `Ready` supplies chapters and
   alternatives; `canRead` is true only for non-empty lists. `NoMatch` and `Unavailable` are distinct.

## Honest integration limits

This module does **not** implement an APK interpreter, the Android IPC service, package installer,
package/certificate inventory, Compose wizard/modal, or an external discovery catalog. These are host
integration requirements, not working capabilities of these JVM modules. A timeout wrapper is not an
OS security sandbox. In particular Android `isolatedProcess` services have no ordinary network or
app-storage permissions; a real runtime requires a carefully constrained host network broker and
resource policy, as well as the extension API dependencies. Treating `DexClassLoader` as a sandbox
would violate the stated security contract.

The Keiyoushi index contains `apk`, `pkgName`, `lang`, `nsfw`, `version`, numeric `code`, and optional
`sources`; it does **not** contain manga titles, chapter counts, or latest chapter dates. Relative APK
filenames resolve under the index's `apk/` directory. Numeric source IDs are preserved as strings to
avoid precision loss. New repository snapshots replace the old only after every index validates;
duplicate package ownership across repositories is rejected instead of silently selecting a publisher.
Adding a full upstream repository that overlaps a curated snapshot therefore still requires an
explicit ownership policy; the manager deliberately does not silently replace a curated publisher.

## Verification

Run with JDK 17:

```text
gradlew.bat :domain:extension:test :data:extension:test
```

Tests cover repo parsing/validation, missing/installed/multiple-source matching, language/NSFW policy,
cancellation, onboarding persistence decisions, version updates, refresh rollback, trust checks,
bridge timeouts, private-file persistence, offline packaged catalogs, binary/text manifest inspection,
APK JSON metadata, DEX descriptor discovery, conversion round trips, and package mismatch rollback.
