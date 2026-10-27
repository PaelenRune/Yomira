# Yomira v7.0.0-Beta

Version code: **12**

## Library updates
- Switch between saved series and a chronological unread chapter feed.
- Open the listed chapter directly with **▶ Read / ▶ Oku**.
- **Check for updates** refreshes source chapter indexes for saved public series without switching an existing source or downloading pages.
- Saved posters show an unread chapter count; reading progress and manual read markers update the count live.
- Fractional chapter numbers are supported. Unknown release dates sort after dated chapters.
- The feed follows the locally available source chapter index; it does not invent chapter arrivals or bypass the Vault gate.

## Adaptation indicators
- Poster cards support optional Anime, Hentai OVA, K-Drama, live-action and Donghua indicators independently of age ratings.
- Visible catalogue posters look up adaptation metadata in small, concurrency-limited batches and cache known labels across restarts.
- Adaptation information comes from the metadata relations graph, not guesses based on title or genre.
- Live-action metadata is not automatically presented as Korean drama when the provider does not establish that distinction.

## Verification
Passed: **51 unit tests** across `core:ui` and `feature:home`, plus app debug Kotlin compilation.

```powershell
.\gradlew.bat :core:ui:testDebugUnitTest :feature:home:testDebugUnitTest :app:compileDebugKotlin
```
