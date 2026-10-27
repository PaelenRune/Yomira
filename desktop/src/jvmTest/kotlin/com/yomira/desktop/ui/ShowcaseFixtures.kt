package com.yomira.desktop.ui

import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.desktop.model.CatalogItem
import com.yomira.desktop.model.DesktopChapter
import com.yomira.desktop.model.DesktopPage
import java.io.File

/**
 * Artwork and catalogue data for the README render suite.
 *
 * The posters, banners and webtoon pages in `art/showcase/` ship with the
 * repository and are drawn by `scripts/make_showcase_fixtures.py`; no published
 * cover is redistributed. Titles are catalogue labels only. The render suite
 * points [RemoteImages.assetRoot] at the repository `art/` folder, so the very
 * same `RemoteImage` pipeline that streams HTTPS artwork in production decodes
 * these files — the screenshots exercise the real composition, not a mock.
 */
internal object ShowcaseAssets {

    /** Repository root discovered from the module working directory. */
    val repoRoot: File by lazy {
        var candidate: File? = File("").absoluteFile
        while (candidate != null) {
            if (File(candidate, "art/showcase/covers").isDirectory) return@lazy candidate
            candidate = candidate.parentFile
        }
        error("Could not locate art/showcase; run the test from the repository")
    }

    val artDirectory: File get() = File(repoRoot, "art")

    fun install() {
        RemoteImages.assetRoot = artDirectory
    }

    fun cover(slug: String): String = "asset://showcase/covers/$slug.jpg"

    fun banner(slug: String): String = "asset://showcase/banners/$slug.jpg"

    fun page(slug: String, index: Int): String = "asset://showcase/pages/$slug-$index.png"
}

internal object ShowcaseCatalog {

    private fun item(
        slug: String,
        title: String,
        author: String,
        score: Double,
        year: Int,
        status: MangaStatus,
        genres: List<String>,
        synopsis: String,
        banner: Boolean = false,
        rating: ContentRating = ContentRating.SAFE,
    ) = CatalogItem(
        id = "mangadex:$slug",
        title = title,
        coverUrl = ShowcaseAssets.cover(slug),
        bannerUrl = if (banner) ShowcaseAssets.banner(slug) else null,
        score = score,
        year = year,
        status = status,
        synopsis = synopsis,
        genres = genres,
        author = author,
        sourceId = "mangadex",
        remoteId = slug,
        sourceUrl = "https://mangadex.org/title/$slug",
        contentRating = rating,
    )

    /** Twenty series so a 2K wall fills six columns with three full rows. */
    val items: List<CatalogItem> = listOf(
        item(
            "one-piece", "One Piece", "Eiichiro Oda", 9.2, 1997, MangaStatus.ONGOING,
            listOf("Action", "Adventure", "Comedy"),
            "Korsanlar çağının en büyük hazinesi tek parça halinde bekliyor. Hasır Şapka tayfası, " +
                "her adada hem yeni bir dost hem de yeni bir fırtına buluyor.",
            banner = true,
        ),
        item(
            "berserk", "Berserk", "Kentaro Miura", 9.5, 1989, MangaStatus.HIATUS,
            listOf("Action", "Dark Fantasy", "Horror"),
            "Kara Kılıç, kendi kaderini çizen bir adamın omzunda. Eclipse'ten sonra hiçbir şey eskisi gibi değil.",
            rating = ContentRating.MATURE,
        ),
        item(
            "solo-leveling", "Solo Leveling", "Chugong · DUBU", 8.7, 2018, MangaStatus.COMPLETED,
            listOf("Action", "Webtoon", "Fantasy"),
            "Dünyanın en zayıf avcısı, sistemin tek oyuncusu oldu. Gölgeler artık onun emrinde.",
            banner = true,
        ),
        item(
            "chainsaw-man", "Chainsaw Man", "Tatsuki Fujimoto", 9.0, 2018, MangaStatus.ONGOING,
            listOf("Action", "Demons", "Comedy"),
            "Devir saati çaldığında bir testere doğuyor. Şeytan avcısı Denji'nin borcu bitmek bilmiyor.",
            rating = ContentRating.MATURE,
        ),
        item(
            "frieren", "Frieren", "Kanehito Yamada", 9.0, 2020, MangaStatus.ONGOING,
            listOf("Adventure", "Fantasy", "Drama"),
            "Kahramanların yolculuğu bitti; elf büyücü Frieren için asıl hikâye şimdi başlıyor.",
            banner = true,
        ),
        item(
            "vinland-saga", "Vinland Saga", "Makoto Yukimura", 9.0, 2005, MangaStatus.ONGOING,
            listOf("Action", "Historical", "Drama"),
            "Kuzey denizlerinde intikamla başlayan yolculuk, savaşsız bir ülke hayaline dönüşüyor.",
        ),
        item(
            "monster", "Monster", "Naoki Urasawa", 9.2, 1994, MangaStatus.COMPLETED,
            listOf("Mystery", "Thriller", "Psychological"),
            "Bir beyin cerrahının kurtardığı hayat, Avrupa'yı saran bir kabusa dönüşür.",
        ),
        item(
            "vagabond", "Vagabond", "Takehiko Inoue", 9.3, 1998, MangaStatus.HIATUS,
            listOf("Action", "Historical", "Samurai"),
            "Miyamoto Musashi'nin kılıcı, en güçlü olmaktan çok kendini yenmeye çalışıyor.",
        ),
        item(
            "jujutsu-kaisen", "Jujutsu Kaisen", "Gege Akutami", 8.9, 2018, MangaStatus.ONGOING,
            listOf("Action", "Supernatural", "School"),
            "Lanetli enerjiyle beslenen bir dünyada, genç büyücüler kendi sınırlarını zorluyor.",
        ),
        item(
            "kaiju-no-8", "Kaiju No. 8", "Naoya Matsumoto", 8.4, 2020, MangaStatus.ONGOING,
            listOf("Action", "Sci-Fi", "Military"),
            "Savunma kuvvetlerinin temizlik ekibinden bir adam, canavarın kalbini taşıyor.",
        ),
        item(
            "blue-lock", "Blue Lock", "Muneyuki Kaneshiro", 8.3, 2018, MangaStatus.ONGOING,
            listOf("Sports", "Drama", "Shounen"),
            "Japonya'nın en egoist forvetini yaratmak için üç yüz genç bir tesise kapatılıyor.",
        ),
        item(
            "dandadan", "Dandadan", "Yukinobu Tatsu", 8.6, 2021, MangaStatus.ONGOING,
            listOf("Comedy", "Occult", "Action"),
            "Uzaylılar ve ruhlar aynı mahalleye düştü; iki lise öğrencisi aralarında kalıyor.",
        ),
        item(
            "oshi-no-ko", "Oshi no Ko", "Aka Akasaka", 8.7, 2020, MangaStatus.COMPLETED,
            listOf("Drama", "Music", "Psychological"),
            "Sahnenin ışığı, karanlığın en görünür olduğu yerdir.",
        ),
        item(
            "sakamoto-days", "Sakamoto Days", "Yuto Suzuki", 8.2, 2020, MangaStatus.ONGOING,
            listOf("Action", "Comedy", "Slice of Life"),
            "Efsanevi tetikçi emekli oldu, market açtı; ama geçmiş kapıyı çalmaya devam ediyor.",
        ),
        item(
            "hells-paradise", "Hell's Paradise", "Yuji Kaku", 8.5, 2018, MangaStatus.COMPLETED,
            listOf("Action", "Historical", "Supernatural"),
            "Ölüm cezasına çarptırılan mahkûmlar, ölümsüzlüğü arayan bir adaya gönderiliyor.",
            rating = ContentRating.MATURE,
        ),
        item(
            "witch-hat-atelier", "Witch Hat Atelier", "Kamome Shirahama", 8.8, 2016, MangaStatus.ONGOING,
            listOf("Fantasy", "Magic", "School"),
            "Büyü çizmekle olur. Çizgiyi bozan her şey, bedelini ister.",
        ),
        item(
            "oyasumi-punpun", "Oyasumi Punpun", "Inio Asano", 9.1, 2007, MangaStatus.COMPLETED,
            listOf("Drama", "Slice of Life", "Psychological"),
            "Bir çocuğun büyüme hikâyesi, çizgi kuşun gözünden anlatılıyor.",
            rating = ContentRating.MATURE,
        ),
        item(
            "20th-century-boys", "20th Century Boys", "Naoki Urasawa", 9.1, 1999, MangaStatus.COMPLETED,
            listOf("Mystery", "Thriller", "Sci-Fi"),
            "Çocukken uydurulan bir kehanet, otuz yıl sonra gerçek olmaya başlıyor.",
        ),
    )

    /** One series with a full table of contents for the detail dossier. */
    val detail: CatalogItem = items.first()

    fun chapters(item: CatalogItem): List<DesktopChapter> = listOf(
        DesktopChapter("${item.id}:c1079", item.remoteId, 1079.0, "The Emperors' Game", "MangaPlus", pageCount = 17),
        DesktopChapter("${item.id}:c1078", item.remoteId, 1078.0, "The Night Before the Finale", "MangaPlus", pageCount = 15),
        DesktopChapter("${item.id}:c1077", item.remoteId, 1077.0, "A Storm Called Freedom", "MangaPlus", pageCount = 19),
        DesktopChapter("${item.id}:c1076", item.remoteId, 1076.0, "Crossing the Red Line", "MangaPlus", pageCount = 16),
        DesktopChapter("${item.id}:c1075", item.remoteId, 1075.0, "The Weight of a Hat", "MangaPlus", pageCount = 18),
        DesktopChapter("${item.id}:c1074", item.remoteId, 1074.0, "Sunrise over Egghead", "MangaPlus", pageCount = 14),
        DesktopChapter("${item.id}:c1073", item.remoteId, 1073.0, "Allies and Anchors", "MangaPlus", pageCount = 17),
        DesktopChapter("${item.id}:c1072", item.remoteId, 1072.0, "The Price of a Promise", "MangaPlus", pageCount = 15),
        DesktopChapter("${item.id}:c1071", item.remoteId, 1071.0, "A Map With No Edges", "MangaPlus", pageCount = 19),
        DesktopChapter("${item.id}:c1070", item.remoteId, 1070.0, "Laugh Tale, Revisited", "MangaPlus", pageCount = 21),
        DesktopChapter("${item.id}:c1069", item.remoteId, 1069.0, "The Last Poneglyph", "MangaPlus", pageCount = 18),
        DesktopChapter("${item.id}:c1068", item.remoteId, 1068.0, "Sea Kings at Dawn", "MangaPlus", pageCount = 16),
    )

    /** Four original webtoon pages, enough for a full 2K reader frame. */
    fun pages(chapter: DesktopChapter): List<DesktopPage> =
        (1..4).map { index ->
            DesktopPage(index = index - 1, url = ShowcaseAssets.page("solo-leveling", index))
        }

    /** Detail model merged over the card, exactly like the live waterfall. */
    fun manga(item: CatalogItem): Manga = Manga(
        id = item.id,
        title = item.title,
        coverUrl = item.coverUrl,
        bannerUrl = item.bannerUrl,
        author = item.author,
        synopsis = item.synopsis,
        score = item.score,
        status = item.status,
        genres = item.genres,
        contentRating = item.contentRating,
        sourceId = item.sourceId,
    )
}
