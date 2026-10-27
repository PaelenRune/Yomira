# Cross-device Google Drive synchronization

## Status and activation

The service, local adapters and automatic triggers are implemented. **Production Google OAuth is not connected yet:** `app/.../di/SyncAuthModule.kt` still supplies a null `DriveAccessTokenProvider`. Consequently real Drive requests remain disabled and Settings reports that sign-in is required. Register the Android package/signing certificate with Google Identity Services and supply short-lived tokens authorized for `https://www.googleapis.com/auth/drive.appdata` before considering end-to-end sync released. Never embed a client secret or static bearer token.

Settings offers a persisted, device-local **Automatic cross-device sync** opt-in and **Sync Now** action. Consent defaults to off and is never restored from a cloud document. The Settings suggestion explains cross-device continuation; no additional first-run modal is required.

## Portable state

`yomira_sync.json` schema 2 remains compatible with schema 1 input and includes:

- Library series IDs, explicit membership/removal, favorites, legacy category, multiple categories and custom tags.
- Series resume state plus per-chapter history: series/chapter IDs, chapter index, page index/count, read timestamp and completion status.
- Installed extension package IDs, versions, repository sources, portable custom configuration, explicit uninstall records and JSON manifests. APK/DEX binaries, covers and chapter media are not uploaded.
- Existing sync preferences. Automatic-sync consent and OAuth tokens are never part of this payload.

Room schema 5 preserves library rows through `MIGRATION_4_5`. Reader state is projected from the actual `reader_progress` and `read_chapters` tables, not just the older series-level `reading_progress` projection. Restoring completion history does not award XP.

`DriveExtensionStore` keeps portable configuration and pending restore records atomically under app-private `sync/extensions.json`. Its `updateConfiguration` API is for portable preferences only, not credentials. Restores use `SandboxExtensionInstaller.rehydrate`, verifying DEX fingerprints. Repositories not enabled locally remain pending instead of becoming trusted through cloud input. The extension repository now uses the real manifest-backed installed provider.

## Automatic operation and conflict handling

- Application launch and activity resume enqueue a network-constrained WorkManager sync when locally enabled.
- Actual reader final-page transitions and chapter-completion ledger changes enqueue sync. Initial database replay and unchanged observations are suppressed.
- Periodic six-hour background work provides eventual reconciliation; disabling consent cancels pending automatic work, and workers check consent again before starting.
- Settings manual sync runs even when automatic sync is disabled.
- Conflicts use last-write-wins by the record's read/update timestamp, including intentional rereads and removal tombstones. Equal timestamps use a stable content ordering, independent of the publishing device ID.
- A mutex serializes manual/background syncs within this process. Database writes recheck local timestamps after the network call so ongoing reading is not overwritten by an older snapshot.
- Drive v3 ETags come from HTTP headers, not unsupported JSON fields. Existing-document updates use `If-Match`; concurrent discovery/update/deletion causes bounded re-fetch/merge retries.

LWW assumes reasonably synchronized device clocks. Simultaneous first-ever creation can produce duplicate same-name Drive files because Drive names are not unique; the transport downloads all such documents (up to 100, refusing truncated listings), and the orchestrator merges their state before publishing to the newest document. Duplicate files are not deleted automatically.

## Verification

Executed successfully:

```text
gradlew.bat :data:sync:test :domain:sync:testDebugUnitTest :app:compileDebugKotlin :core:database:compileDebugAndroidTestKotlin --console=plain
```

37 unit tests passed across transport, schema/serialization, conflict resolution and completion triggering. The Room migration instrumentation test compiles. Device execution was attempted on the connected Android 12 phone, but installation was rejected with `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`; zero device tests ran. Allow the test APK installation before rerunning `:core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.yomira.core.database.CrossDeviceSyncMigrationTest`. Live two-device OAuth testing also remains outstanding.

## Changed files (WBS-1.4.3)

Modified:

```text
app/src/main/java/com/yomira/app/YomiraApp.kt
app/src/main/java/com/yomira/app/di/OnboardingModule.kt
core/database/src/main/java/com/yomira/core/database/YomiraDatabase.kt
core/database/src/main/java/com/yomira/core/database/dao/ReadChapterDao.kt
core/database/src/main/java/com/yomira/core/database/dao/SyncDataDao.kt
core/database/src/main/java/com/yomira/core/database/di/DatabaseModule.kt
core/database/src/main/java/com/yomira/core/database/entity/LibraryEntryEntity.kt
data/sync/src/main/kotlin/com/yomira/data/sync/GoogleDriveClient.kt
data/sync/src/main/kotlin/com/yomira/data/sync/SyncPayload.kt
data/sync/src/main/kotlin/com/yomira/data/sync/SyncRemoteStore.kt
data/sync/src/test/kotlin/com/yomira/data/sync/SyncPayloadSerializationTest.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/ConflictResolutionEngine.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/SyncLocalStore.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/SyncManager.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/SyncScheduler.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/SyncWorker.kt
domain/sync/src/test/kotlin/com/yomira/domain/sync/ConflictResolutionEngineTest.kt
```

Created:

```text
app/src/main/java/com/yomira/app/di/CrossDeviceSyncModule.kt
app/src/main/java/com/yomira/app/sync/AutomaticSyncController.kt
app/src/main/java/com/yomira/app/sync/DriveExtensionStore.kt
core/database/schemas/com.yomira.core.database.YomiraDatabase/5.json
core/database/src/main/java/com/yomira/core/database/Migration4To5.kt
core/database/src/androidTest/java/com/yomira/core/database/CrossDeviceSyncMigrationTest.kt
data/sync/src/test/kotlin/com/yomira/data/sync/GoogleDriveClientTest.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/CompletionSyncTracker.kt
domain/sync/src/main/kotlin/com/yomira/domain/sync/SyncExtensionStore.kt
domain/sync/src/test/kotlin/com/yomira/domain/sync/CompletionSyncTrackerTest.kt
docs/CROSS_DEVICE_SYNC.md
```
