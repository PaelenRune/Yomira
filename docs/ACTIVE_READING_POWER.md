# Active reading power optimizations

Scope: WBS-1.5.1 / WBS-1.5.2 / WBS-1.5.3. Audited 2026-10-27.

## Implemented

- UI snapshot/copy models in core UI, Home, Details and Reader declare `@Immutable` contracts. Published lists/maps/sets must be treated as snapshots: never mutate them in place. This does not make arbitrary collections immutable. Domain/database/ViewModel/service objects are not blanket-annotated.
- Home/search filtering and deduplication are remembered by actual catalog/query/tag inputs, instead of rebuilding the catalog when unrelated loading, paging or scroll-threshold state changes. Remembered results preserve list identity for Compose strong skipping.
- Home grid sections, rails, tags, detail chapter lists and related-series rows now supply stable keys and visual `contentType`s. The webtoon strip already had both; its chapter-scoped integer page indexes are stable identities, not a changing sort position. Reader chapter picker now distinguishes its heading from chapter rows. Pager already uses explicit slot keys and `beyondViewportPageCount = 0`.
- Home's existing `derivedStateOf` near-bottom gate is retained. Reader progress uses `snapshotFlow` plus `distinctUntilChanged` on visible/settled page, not scroll pixels. Reader spread planning is now derived/memoized instead of reallocating the complete plan/key on every HUD/page-progress update.
- Application artwork uses a shared Coil loader with an explicitly bounded LRU. Reader keeps a separate bounded loader because chapter images should be released at screen disposal; its existing `shutdown()` and painter disposal are retained.
- Both loaders prefer `Bitmap.Config.HARDWARE` and `allowHardware(true)` (minimum SDK is 26). Normal pages/artwork can avoid an additional software-to-GPU upload. Coil retains its own device/size compatibility checks. Webtoon seam transforms and guided panel analysis explicitly request `ARGB_8888` with hardware disabled, since they need CPU-readable pixels. Hardware-backed buffers are graphics memory, not a promise of dedicated GPU VRAM or zero total bitmap memory.
- Reader page-list HTTP is asynchronous and linked to coroutine cancellation until response-body parsing finishes. Leaving the reader cancels the underlying OkHttp call, rather than only cancelling a coroutine around blocking `execute()`.
- The primary dark canvas is now `#000000`; the reader already defaults to `Color.Black`. Near-black `#0D0D12` is not pixel-off black. Only OLED black pixels reduce emissive display power; white manga pages and LCD backlights do not gain the same saving.

## Bounded memory policy (deterministic, not a measured power claim)

`ImageMemoryPolicy` uses the process max heap as its budget input:

| Cache | Policy | Absolute maximum |
| --- | --- | --- |
| Shared artwork | 10% of heap | 32 MiB |
| Reader, only while composed | 15% of heap | 64 MiB |
| Combined retained LRU entries | at most 25% of heap | 96 MiB |

The old reader policy was 20% without an absolute cap. The new reader fraction is 25% lower before the cap applies. For a 256 MiB heap the new budgets are approximately 25.6 + 38.4 = 64 MiB; for a 512 MiB heap they are capped to 32 + 64 = 96 MiB. These are cache budgets, not actual RSS/PSS or peak decoded-memory measurements: visible painters, in-flight decodes, weak references, scratch buffers and graphics-driver allocations are additional. Lower cache retention can increase decodes on backscroll, so device profiling must verify the tradeoff rather than assuming smaller is always cheaper.

## Lifecycle/background audit

Reader Compose jobs/scopes are composition-bound; disposal calls `model.close`, cancels chapter loading, shuts down the page loader and trims seam scratch bitmaps. Lifecycle stop pauses the reading clock. Progress/journey channel consumers suspend awaiting events; they do not poll. Their short non-cancellable final drains deliberately preserve the user's last reading position/session. No reader-owned sync loop or continuous network/image prefetch was found. Lazy layout may still prepare neighboring items during a gesture; this is distinct from continuously preloading chapters. App-wide WorkManager/sync policy is handled separately by the automatic-sync controller; this document does not claim a reader screen alone prevents all app-wide background activity.

## Validation

Passed together:

```powershell
.\gradlew.bat :core:ui:testDebugUnitTest :feature:home:testDebugUnitTest :feature:reader:testDebugUnitTest :feature:details:compileDebugKotlin :app:compileDebugKotlin --console=plain
```

Results: core UI **34 tests**, Home **12 tests**, Reader **43 tests**, zero failures/errors (89 total; Reader rerun after adding late-response cancellation coverage). New regression coverage checks absolute memory caps, combined low-heap budgets, invalid inputs, immediate HTTP cancellation on reader dismissal and closing late responses without parsing them. Existing theme-token test asserts pure black. App and all affected feature compilation passed. No physical-device mAh, watts, GC counts or frame-time improvement was measured in this implementation session.

## Device measurement protocol

1. Use the same physical OLED device, release/profileable APK, fixed brightness and refresh rate, network, chapter, thermal state and battery band. Disable adaptive brightness. Measure baseline and optimized builds in alternating order, at least five repetitions each.
2. Record three separate 10-minute scenarios: static open page/HUD hidden; timed page turns; webtoon scroll plus backscroll. Also record a 5-minute screen-off/dismissed control. Keep cold-cache and warm-cache runs separate.
3. Use Perfetto/Android Studio system traces and Power Profiler or supported Macrobenchmark power metrics. Compare CPU/GPU/display energy, CPU scheduling, frame-time percentiles, decode/upload work and GC events. Use `adb shell dumpsys meminfo com.yomira.app` for total Java/native/graphics PSS; do not interpret a cache byte cap as process memory usage.
4. Check that ordinary pages actually decode as hardware bitmaps, transformed/guided pages remain software-readable, rereading does not regress through repeated downloads/decodes, and no requests continue after leaving Reader. Verify offline/manual sync and final progress persistence still work.
5. Publish device/OS/build identifiers, traces, raw samples and medians/variance before stating a battery percentage improvement. Batterystats estimates are supplementary; short battery-percentage deltas are too noisy for this claim.

## Primary references

- [Android Compose performance best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices): remember expensive calculations, lazy keys/content types, derived scroll state and deferred state reads.
- [Android Compose stability guidance](https://developer.android.com/develop/ui/compose/performance/stability/fix): annotations are contracts, not runtime enforcement; strong skipping already compares unannotated collection references, and large structural equality checks can cost more than recomposition. Preserve references and profile rather than globally declaring `kotlin.collections.*` stable.
- [Coil 2.7 MemoryCache source](https://github.com/coil-kt/coil/blob/2.7.0/coil-base/src/main/java/coil/memory/MemoryCache.kt): LRU semantics, explicit byte limits and separate weak-reference accounting.
- [Coil ImageLoader guidance](https://coil-kt.github.io/coil/image_loaders/): sharing loaders avoids independent cache/network-client duplication (current docs show Coil 3 API; implementation here deliberately uses this repository's Coil 2 factory API).
- [Android Bitmap.Config.HARDWARE](https://developer.android.com/reference/android/graphics/Bitmap.Config#HARDWARE): immutable graphics-backed bitmap restriction.
- [Android power profiling guidance](https://developer.android.com/topic/performance/power/setup-battery-historian): prefer system tracing/Power Profiler/Macrobenchmark; Battery Historian is no longer actively maintained.
