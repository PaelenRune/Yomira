# Extension integration (Phase 3)

## Implemented modules

* `:domain:extension`: platform-independent source DTOs, repository/bridge/discovery contracts,
  `SmartSourceMatcher`, onboarding preference model, and `OnboardingWizardManager`.
* `:data:extension`: `KeiyoushiRepoParser`, `ExtensionManager`, bounded/cancellable
  `OkHttpRepositoryClient`, atomic private-file persistence, and `SandboxSourceBridge`.

The core contains no bundled repository indexes, chapter content, source implementations, APK bundles, or server. A fresh URL store and the in-memory `ExtensionManager` snapshot are empty. The app does not seed or migrate repositories automatically; existing explicitly stored URLs remain intact. Users may explicitly add the official Keiyoushi index or a custom HTTPS index. Adding a URL stores consent; `refresh()` fetches metadata and replaces the snapshot only after all indexes validate. No offline bundled snapshot is seeded. No APK is downloaded or installed during onboarding or repository registration; installation requires a separate user action.

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
   Up to eight trusted-source probes race concurrently with a 15-second per-source deadline. Titles
   match after punctuation/editorial-suffix normalization with >=85% edit or token similarity. Numeric
   sequel identifiers must agree. Readable candidates rank by bounded runtime weights: exact language
   +1000, chapter completeness relative to the best observed source +500, recency +200, verified healthy
   status +300 and response speed +100. Blocked discoveries are excluded; unknown health earns no bonus.
   No site is favored. Probe failures silently leave the next healthy candidate eligible. Reader hosts
   can call `failover(series, preferences, failedSources)` after a later failure to rerace remaining sites.
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
Overlapping configured repositories require an explicit ownership policy; the manager deliberately does not silently replace a publisher.

## Verification

Run with JDK 17:

```text
gradlew.bat :domain:extension:test :data:extension:test
```

Tests cover repo parsing/validation, missing/installed/multiple-source matching, language/NSFW policy,
cancellation, onboarding persistence decisions, version updates, refresh rollback, trust checks,
bridge timeouts, private-file persistence, empty initial catalogs, binary/text manifest inspection,
APK JSON metadata, DEX descriptor discovery, conversion round trips, and package mismatch rollback.
