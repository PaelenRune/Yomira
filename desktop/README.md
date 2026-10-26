# Yomira Desktop — Windows (FAZ 4-A)

Independent Compose Multiplatform (pure JVM) target of Yomira. It shares the
platform-neutral core with the Android app — `:core:model`, `:core:network`,
`:data:metadata` (waterfall), `:data:sync` — and never pulls in an Android
artifact, so it builds and packages without the Android SDK.

## Run from source

```
gradlew :desktop:run
```

## Native Windows installers (.EXE / .MSI)

```
gradlew :desktop:packageDistributionForCurrentOS
```

Output: `desktop/build/compose/binaries/main/exe/Yomira-0.4.0.exe` and
`desktop/build/compose/binaries/main/msi/Yomira-0.4.0.msi`
(JPackager; requires the JDK 17 toolchain already used by the Gradle build).

## Shell contract

| Input            | Behaviour                                             |
| ---------------- | ----------------------------------------------------- |
| `F11`            | Toggle fullscreen (also while typing)                  |
| `Esc`            | Leave fullscreen → otherwise navigate back             |
| `Ctrl+F`         | Jump to search from any screen                         |
| `←` / `→`        | Turn pages; meaning follows manga RTL / western LTR    |
| `Space`, `PgDn`  | Next page / scroll one viewport in webtoon             |
| `PgUp`           | Previous page / scroll back in webtoon                 |
| `Home` / `End`   | First / last page of the chapter                       |
| Mouse wheel      | Glides the webtoon strip                               |
| Poster wall      | 6–8 columns on wide/2K/4K monitors (`PosterGrid.columnsForWidth` in `state/DesktopPaging.kt`) |

Keyboard routing, the responsive column math and the cursor pager are covered by
`src/jvmTest/kotlin/com/yomira/desktop/DesktopNavigationTest.kt`
(`gradlew :desktop:test`).

## Reading transport

Metadata cascades Baka-Updates → AniList → MangaDex (`WaterfallMetadataAggregator`
from `:data:metadata`). Pages stream from MangaDex only, via the documented
`/manga/{id}/feed` + `/at-home/server/{chapterId}` endpoints; at-home answers are
never cached and the reader credits “Powered by MangaDex” as the API AUP requires.
