# Yomira Reading Journey

Phase 2 adds a **local-only** reading journey: fair, format-aware activity tracking,
four-category achievements with four tiers, XP levels, a profile tab and a cinematic
monthly/yearly **Yomira Wrapped** recap with a shareable card.

Nothing in this document leaves the device. There is no account, no analytics upload
and no remote profile. The journey tables live next to the existing Room catalogue and
are intentionally excluded from Google Drive sync.

---

## 1. Fair, weighted activity tracking

### The problem

A 100-page western album is not the same reading effort as a 10-page manga chapter,
and a phone left open on a page must not inflate "reading time".

### The model

`domain/journey/ReadingWeight.kt` normalises every format to a **standard chapter of
20 weighted pages**:

| Format | Page factor | Chapter bonus |
| --- | ---: | ---: |
| Manga (RTL) | 1.00 | 12 XP |
| Webtoon | 0.82 | 8 XP |
| Western (LTR) | 1.18 | 18 XP |

- `weightedPages = pages × pageFactor`
- `readingUnits = weightedPages / 20` — the fair volume metric used by badges
- a chapter's XP = `readingUnits × 8 + chapterBonus`
- a re-read pays **20%** of the first-read XP and never the full bundle again

So a 100-page western album ≈ 5.9 units while a 10-page manga chapter ≈ 0.5 units.

### Active time only

`ActiveReadingTimer` credits time only between two interactions **closer than 60
seconds**. A longer gap is discarded, and `pause()` (called from
`ReaderScreen`'s `ON_STOP`) drops the anchor without crediting the background gap.
The timer is flushed in bounded slices (every 8 pages or 20 s), so the database never
sees one row per scroll tick.

### Idempotent chapter ledger

`read_chapters` has a composite primary key `(mangaId, chapterId)`. The first
completion is the only one that can award full XP; later completions increment
`completionCount` and pay the re-read amount. Reading the final chapter does **not**
mark a series finished unless the ledger proves every known chapter was completed.

### Local-first storage (Room v4)

`MIGRATION_3_4` adds four tables:

| Table | Purpose |
| --- | --- |
| `reading_activity` | local aggregates: pages, chapters, weighted pages, active millis, XP, day/hour, genre, western/night/weekend flags |
| `read_chapters` | idempotency ledger for first reads and completions |
| `journey_profile` | single row with badge bonus XP and last active day |
| `unlocked_badges` | `(badgeId, tier)` primary key so awards are idempotent |

Only aggregates are stored — never page URLs, image bytes or chapter text.

---

## 2. Badges & achievements

Four categories × four tiers (Bronze 50, Silver 125, Gold 300, Platinum 750 XP):

| Category | Badge | Metric | Bronze | Silver | Gold | Platinum |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| Reading volume | 🌱 İlk Adım | reading units | 1 | 10 | 50 | 150 |
| Reading volume | 💯 Yüzler Kulübü | reading units | 100 | 250 | 500 | 1000 |
| Reading volume | 🐉 Efsane Okur | reading units | 1000 | 2500 | 5000 | 10000 |
| Consistency | 🔥 Alev Serisi | current streak | 7 | 14 | 30 | 100 |
| Exploration | 🧭 Tür Gezgini | distinct genres | 5 | 10 | 20 | 30 |
| Exploration | 🦸 Batı Kaşifi | western series | 1 | 5 | 15 | 30 |
| Special | 🌙 Gece Kuşu | chapters 00:00–05:00 | 50 | 150 | 400 | 1000 |
| Special | 🏁 Haftasonu Maratoncusu | weekend chapters | 20 | 100 | 250 | 500 |

`refreshAchievements()` runs after every completed chapter inside a mutex, inserts any
newly reached tier with `INSERT OR IGNORE`, and pays the tier XP exactly once via
`journey_profile.bonusXp`.

### Flexible streak (no punishment)

`FlexibleStreak` allows **one rest day per rolling seven-day window**. Skipping a
single day keeps the flame; skipping two consecutive days (or two within the same
window) breaks it. The current day is never punished before it ends: reading
yesterday is enough to keep the streak alive this morning.

### Levels

`LevelProgression` uses a gentle quadratic curve (`n² × 40 + n × 120`) and titles:

- 1+ Meraklı Çırak
- 5+ Kütüphane Gezgini
- 10+ **Fantastik Gezgin**
- 15+ Efsane Avcısı
- 20+ Zaman Yolcusu
- 30+ Yomira Ustası
- 40+ Yomira Efsanesi

Example: **Seviye 12 · Fantastik Gezgin** (6,160 XP).

---

## 3. Profile & statistics tab

`feature/journey/ProfileJourneyScreen.kt` renders:

- level hero (`Seviye 12 · Fantastik Gezgin`) with XP progress and streak chip
- **Monday–Sunday** weekly chapter bar chart, today highlighted
- all-time cards: chapters, pages, active time, finished series, active days,
  longest streak, genres, favourite genre, reading units
- the Wrapped call-to-action with badge completion count
- badge showcase grouped by the four categories; earned tiers are coloured, locked
  tiers remain visible as goals with progress to the next threshold

The tab is `profile` in the app shell (`nav_profile`), between Library and Vault.

---

## 4. Yomira Wrapped

`feature/journey/WrappedScreen.kt` is a swipeable, dark cinematic recap:

1. Period intro
2. Chapters + pages + reading units + finished series
3. Active time and active days
4. Favourite genre with a genre breakdown and most-read series
5. Growth vs. the previous period and the current level
6. Finale with badges earned and the share action

Monthly and yearly ranges are calendar ranges in the user's own time zone
(`WrappedPeriod.range`). Growth is omitted for a first period instead of showing
`+∞%`.

### Shareable card

`WrappedShareCard` renders a platform-stable **1080×1920 PNG** with the platform
canvas (not a composable screenshot). It is written to the app cache and exposed
read-only through a `FileProvider` (`${applicationId}.fileprovider`,
`res/xml/file_paths.xml`), then handed to the system chooser. No storage permission
and no gallery side effects.

---

## 5. Reader integration

`ReadingSessionTracker` is injected into `ReaderViewModel`:

- `open()` starts a session (series, chapter, genre, mode, western flag)
- every page callback records an interaction; the last page triggers a completion
- `ON_STOP` flushes and pauses the timer
- ViewModel teardown drains the channel and flushes under `NonCancellable`

Journey failures are swallowed after logging intent: reading must never fail because
the gamification ledger is unavailable.

---

## 6. Verification

- `:domain:journey:testDebugUnitTest` — 35 tests covering weighting, XP, levels,
  badge thresholds, flexible streak, active-time idle cap and Wrapped ranges.
- `core/database` — `JourneyMigrationTest` validates v3 → v4 and all four tables.
- `:feature:journey:compileDebugKotlin` — compiles against the app's Compose BOM.
