# Artwork and backdrop resolution

Series identity and text follow the metadata waterfall. Artwork is selected only
from those identity-matched records, with a separate quality preference:
AniList → Kitsu → MangaDex → ComicVine → MangaUpdates → Open Library.
Only HTTPS image references without embedded credentials are accepted by the resolver.

| Catalogue | Available artwork | Behaviour when landscape art is absent |
| --- | --- | --- |
| Manga, manhwa, webtoon | AniList extraLarge poster + bannerImage; Kitsu original posterImage + coverImage; MangaDex full cover | Sharp mirrored poster |
| Western comics | ComicVine original volume cover (requires caller-supplied API key); Open Library cover fallback | Sharp mirrored poster; portrait covers are never labelled landscape artwork |
| Adult Vault | The same AniList/Kitsu artwork fields, including adult records; MangaDex search includes pornographic ratings | Sharp mirrored poster, rendered only within the existing unlocked Vault/detail flow |

GCD is not an integrated public artwork API. No guessed image URLs, scraping,
unrelated search hits or unlicensed gallery endpoints are used. Providers cannot
guarantee a landscape image or complete metadata for every series.

`MangaBackdrop` is shared by the catalogue hero (including Vault), phone details
and two-pane details. A missing, loading or failed external banner displays a
vertically inverted, sharp poster with a linear alpha scrim into the OLED canvas
(`#0D0D12`). No Gaussian blur, render effect, image transformation, animated
shader or offscreen alpha mask is used. A failed poster leaves the OLED canvas;
the foreground poster component retains its title/error placeholder.
