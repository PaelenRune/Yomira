# Extension integration (Phase 3)

## Implemented modules

* `:domain:extension`: platform-independent source DTOs, repository/bridge/discovery contracts,
  `SmartSourceMatcher`, onboarding preference model, and `OnboardingWizardManager`.
* `:data:extension`: `KeiyoushiRepoParser`, `ExtensionManager`, bounded/cancellable
  `OkHttpRepositoryClient`, atomic private-file persistence, and `SandboxSourceBridge`.

The core contains no chapter content, source implementations, APK bundles, or server. Repository
metadata is fetched only after explicit onboarding opt-in. Completing onboarding prepares a list of
suggested packages, **not silent APK installation or automatic certificate trust**.

## Application wiring

1. Create singleton `FileRepositoryStore` and `FileOnboardingStore` using separate paths inside the
   private application directory. Construct `ExtensionManager` with an HTTP client, parser, and an
   `InstalledExtensionProvider` backed by Android's package manager and persisted certificate consent.
2. Present TR/EN and +18 controls and invoke `OnboardingWizardManager.complete`. Handle exceptions
   as retryable errors; completion is persisted only when setup succeeds. On a cold start, invoke
   `ExtensionManager.refresh()` before matching (the repository snapshot is intentionally in memory).
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
   Android PackageInstaller consent, and refresh after installation. `Ready` supplies chapters and
   alternatives; `canRead` is true only for non-empty lists. `NoMatch` and `Unavailable` are distinct.

## Honest integration limits

This change does **not** implement an APK interpreter, the Android IPC service, package installer,
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

## Verification

Run with JDK 17:

```text
gradlew.bat :domain:extension:test :data:extension:test
```

Tests cover repo parsing/validation, missing/installed/multiple-source matching, language/NSFW policy,
cancellation, onboarding persistence decisions, version updates, refresh rollback, trust checks,
bridge timeouts, and private-file persistence. On this Windows machine, Gradle test workers cannot
load test classes under the non-ASCII checkout path; verification uses an ASCII temporary copy.
