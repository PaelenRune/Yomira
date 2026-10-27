package com.yomira.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import com.yomira.core.model.Chapter
import com.yomira.core.model.ContentRating
import com.yomira.core.model.Manga
import com.yomira.core.model.MangaStatus
import com.yomira.core.model.ReadingMode
import com.yomira.core.ui.CatalogCopy
import com.yomira.core.ui.MainDestination
import com.yomira.core.ui.YomiraBottomNavBar
import com.yomira.core.ui.YomiraNavigationRail
import com.yomira.core.ui.theme.DeepOledBlack
import com.yomira.core.ui.theme.YomiraTheme
import com.yomira.domain.extension.SourcePage
import com.yomira.feature.details.ChapterUiState
import com.yomira.feature.details.DetailCopy
import com.yomira.feature.details.MangaDetailScreen
import com.yomira.feature.home.ContinueReadingUi
import com.yomira.feature.home.HomeCopy
import com.yomira.feature.home.HomeScreen
import com.yomira.feature.home.HomeUiState
import com.yomira.feature.reader.ReaderChapter
import com.yomira.feature.reader.ReaderCopy
import com.yomira.feature.reader.ReaderOverlay
import com.yomira.feature.reader.ReaderUiState
import com.yomira.feature.reader.WebtoonReader
import java.time.Instant

/**
 * Debug-only screenshot harness for the README gallery.
 *
 * It renders the *shipping* composables (`HomeScreen`, `MangaDetailScreen`,
 * `WebtoonReader` + `ReaderOverlay`, the bottom bar and the navigation rail)
 * with fixture artwork bundled under `assets/showcase`. Nothing here is used by
 * the release build: the file lives in `src/debug` and only the debug manifest
 * declares the activity.
 *
 * Launch: `adb shell am start -n com.yomira.app/.ShowcaseActivity --es screen home`
 * (`home`, `detail`, `reader`, `tablet`).
 */
class ShowcaseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = intent.getStringExtra("screen") ?: "home"
        setContent {
            YomiraTheme {
                Surface(color = DeepOledBlack, modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        "detail" -> PhoneDetailShowcase()
                        "reader" -> ReaderShowcase()
                        "tablet" -> TabletShowcase()
                        else -> PhoneHomeShowcase()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixture catalogue (titles are labels; artwork is the repository's own)
// ---------------------------------------------------------------------------

private object ShowcaseData {

    private fun art(path: String) = "file:///android_asset/$path"

    data class Entry(
        val slug: String,
        val title: String,
        val author: String,
        val score: Double,
        val status: MangaStatus,
        val genres: List<String>,
        val synopsis: String,
        val banner: Boolean = false,
        val rating: ContentRating = ContentRating.SAFE,
    ) {
        val cover: String get() = art("covers/$slug.jpg")
        val bannerUrl: String? get() = if (banner) art("banners/$slug.jpg") else null
        val id: String get() = "mangadex:$slug"
    }

    val entries = listOf(
        Entry(
            "solo-leveling", "Solo Leveling", "Chugong · DUBU", 8.7, MangaStatus.COMPLETED,
            listOf("Action", "Webtoon", "Fantasy"),
            "Dünyanın en zayıf avcısı, sistemin tek oyuncusu oldu. Gölgeler artık onun emrinde.",
            banner = true,
        ),
        Entry(
            "one-piece", "One Piece", "Eiichiro Oda", 9.2, MangaStatus.ONGOING,
            listOf("Action", "Adventure", "Comedy"),
            "Korsanlar çağının en büyük hazinesi tek parça halinde bekliyor. Hasır Şapka tayfası, " +
                "her adada hem yeni bir dost hem de yeni bir fırtına buluyor.",
            banner = true,
        ),
        Entry(
            "frieren", "Frieren", "Kanehito Yamada", 9.0, MangaStatus.ONGOING,
            listOf("Adventure", "Fantasy", "Drama"),
            "Kahramanların yolculuğu bitti; elf büyücü Frieren için asıl hikâye şimdi başlıyor.",
            banner = true,
        ),
        Entry(
            "berserk", "Berserk", "Kentaro Miura", 9.5, MangaStatus.HIATUS,
            listOf("Action", "Dark Fantasy", "Horror"),
            "Kara Kılıç, kendi kaderini çizen bir adamın omzunda. Eclipse'ten sonra hiçbir şey eskisi gibi değil.",
            rating = ContentRating.MATURE,
        ),
        Entry(
            "chainsaw-man", "Chainsaw Man", "Tatsuki Fujimoto", 9.0, MangaStatus.ONGOING,
            listOf("Action", "Demons", "Comedy"),
            "Devir saati çaldığında bir testere doğuyor. Şeytan avcısı Denji'nin borcu bitmek bilmiyor.",
            rating = ContentRating.MATURE,
        ),
        Entry(
            "jujutsu-kaisen", "Jujutsu Kaisen", "Gege Akutami", 8.9, MangaStatus.ONGOING,
            listOf("Action", "Supernatural", "School"),
            "Lanetli enerjiyle beslenen bir dünyada, genç büyücüler kendi sınırlarını zorluyor.",
        ),
        Entry(
            "vinland-saga", "Vinland Saga", "Makoto Yukimura", 9.0, MangaStatus.ONGOING,
            listOf("Action", "Historical", "Drama"),
            "Kuzey denizlerinde intikamla başlayan yolculuk, savaşsız bir ülke hayaline dönüşüyor.",
        ),
        Entry(
            "kaiju-no-8", "Kaiju No. 8", "Naoya Matsumoto", 8.4, MangaStatus.ONGOING,
            listOf("Action", "Sci-Fi", "Military"),
            "Savunma kuvvetlerinin temizlik ekibinden bir adam, canavarın kalbini taşıyor.",
        ),
        Entry(
            "dandadan", "Dandadan", "Yukinobu Tatsu", 8.6, MangaStatus.ONGOING,
            listOf("Comedy", "Occult", "Action"),
            "Uzaylılar ve ruhlar aynı mahalleye düştü; iki lise öğrencisi aralarında kalıyor.",
        ),
        Entry(
            "blue-lock", "Blue Lock", "Muneyuki Kaneshiro", 8.3, MangaStatus.ONGOING,
            listOf("Sports", "Drama", "Shounen"),
            "Japonya'nın en egoist forvetini yaratmak için üç yüz genç bir tesise kapatılıyor.",
        ),
        Entry(
            "monster", "Monster", "Naoki Urasawa", 9.2, MangaStatus.COMPLETED,
            listOf("Mystery", "Thriller", "Psychological"),
            "Bir beyin cerrahının kurtardığı hayat, Avrupa'yı saran bir kabusa dönüşür.",
        ),
        Entry(
            "oshi-no-ko", "Oshi no Ko", "Aka Akasaka", 8.7, MangaStatus.COMPLETED,
            listOf("Drama", "Music", "Psychological"),
            "Sahnenin ışığı, karanlığın en görünür olduğu yerdir.",
        ),
        Entry(
            "witch-hat-atelier", "Witch Hat Atelier", "Kamome Shirahama", 8.8, MangaStatus.ONGOING,
            listOf("Fantasy", "Magic", "School"),
            "Büyü çizmekle olur. Çizgiyi bozan her şey, bedelini ister.",
        ),
        Entry(
            "vagabond", "Vagabond", "Takehiko Inoue", 9.3, MangaStatus.HIATUS,
            listOf("Action", "Historical", "Samurai"),
            "Miyamoto Musashi'nin kılıcı, en güçlü olmaktan çok kendini yenmeye çalışıyor.",
        ),
    )

    val hero = entries.first()

    fun manga(entry: Entry = hero) = Manga(
        id = entry.id,
        title = entry.title,
        coverUrl = entry.cover,
        bannerUrl = entry.bannerUrl,
        author = entry.author,
        synopsis = entry.synopsis,
        score = entry.score,
        status = entry.status,
        genres = entry.genres,
        contentRating = entry.rating,
        sourceId = "mangadex",
    )

    val catalog: List<Manga> = entries.map(::manga)

    /** The series the detail sheet and the tablet two-pane open. */
    val detail = entries.first { it.slug == "one-piece" }

    private val chapterTitles = listOf(
        "Romance Dawn", "The Man in the Straw Hat", "Buggy the Clown", "The Great Swordsman",
        "A Map With No Edges", "Allies and Anchors", "The Weight of a Hat", "A Storm Called Freedom",
        "Crossing the Red Line", "Sunrise over Egghead", "The Night Before the Finale", "The Emperors' Game",
    )

    val chapters: List<ChapterUiState> = chapterTitles.mapIndexed { index, title ->
        ChapterUiState(
            chapter = Chapter(
                id = "${detail.id}:c${index + 1}",
                mangaId = detail.id,
                chapterNumber = (index + 1).toDouble(),
                title = title,
                releaseDate = Instant.now().minusSeconds((chapterTitles.size - index).toLong() * 86_400L * 7),
                scanlationGroup = "MangaPlus",
                url = "file:///android_asset/pages/solo-leveling-1.png",
            ),
            read = index < 8,
        )
    }

    val pages: List<SourcePage> = (1..4).map { index ->
        SourcePage(index = index - 1, imageUrl = art("pages/solo-leveling-$index.png"))
    }

    val readerState = ReaderUiState(
        mangaId = hero.id,
        chapterId = "${hero.id}:c1",
        title = hero.title,
        chapterTitle = "Bölüm 1 · The Gate That Opened",
        chapters = chapterTitles.reversed().mapIndexed { index, title ->
            ReaderChapter("${hero.id}:c${index + 1}", "Bölüm ${index + 1} · $title", (index + 1).toDouble())
        },
        pages = pages,
        pageIndex = 0,
        mode = ReadingMode.WEBTOON,
        oled = true,
        hudVisible = true,
        loading = false,
    )
}

private val CatalogCopyTr = CatalogCopy()
private val DetailCopyTr = DetailCopy()
private val HomeCopyTr = HomeCopy()
private val ReaderCopyTr = ReaderCopy()

// ---------------------------------------------------------------------------
// Phone home
// ---------------------------------------------------------------------------

@Composable
private fun PhoneHomeShowcase() {
    Scaffold(
        topBar = { ShowcaseTopChrome() },
        bottomBar = {
            YomiraBottomNavBar(
                selected = MainDestination.HOME.route,
                onSelect = {},
                copy = CatalogCopyTr,
                vaultVisible = true,
            )
        },
        containerColor = DeepOledBlack,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            HomeScreen(
                state = HomeUiState(
                    manga = ShowcaseData.catalog,
                    trending = ShowcaseData.catalog.sortedByDescending { it.score },
                    updated = ShowcaseData.catalog,
                    columns = 1,
                    query = "",
                ),
                onQuery = {},
                onColumns = {},
                onManga = {},
                onRetry = {},
                title = "Yomira",
                showHero = true,
                tagOptions = emptyList(),
                copy = HomeCopyTr,
                sizeClass = com.yomira.core.ui.WindowSizeClass.Compact,
                continueReading = ContinueReadingUi(
                    mangaId = ShowcaseData.hero.id,
                    chapterId = ShowcaseData.chapters[6].chapter.id,
                    title = ShowcaseData.hero.title,
                    coverUrl = ShowcaseData.hero.cover,
                    chapterTitle = "Bölüm 7 · Ruler's Hand",
                    pageIndex = 11,
                    pageCount = 18,
                ),
                onContinueReading = {},
            )
        }
    }
}

@Composable
private fun ShowcaseTopChrome() {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = {}) {
            Text("TR", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = Color.White)
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Phone detail
// ---------------------------------------------------------------------------

@Composable
private fun PhoneDetailShowcase() {
    MangaDetailScreen(
        manga = ShowcaseData.manga(ShowcaseData.detail),
        chapters = ShowcaseData.chapters,
        matching = false,
        saved = true,
        onBack = {},
        onSave = {},
        onRead = {},
        onToggleRead = {},
        onContinue = {},
        copy = DetailCopyTr,
        twoPane = false,
    )
}

// ---------------------------------------------------------------------------
// Reader (real WebtoonReader + real floating HUD)
// ---------------------------------------------------------------------------

@Composable
private fun ReaderShowcase() {
    val context = LocalContext.current.applicationContext
    val loader = remember(context) {
        ImageLoader.Builder(context).crossfade(false).build()
    }
    Box(Modifier.fillMaxSize().background(DeepOledBlack)) {
        WebtoonReader(
            state = ShowcaseData.readerState,
            imageLoader = loader,
            jumpTo = null,
            onJumpHandled = {},
            onPage = {},
            onTap = {},
            copy = ReaderCopyTr,
        )
        ReaderOverlay(
            state = ShowcaseData.readerState,
            onBack = {},
            onMode = {},
            onSeek = {},
            onOled = {},
            onInvert = {},
            onGuided = {},
            onRecap = {},
            copy = ReaderCopyTr,
            onChapter = {},
        )
    }
}

// ---------------------------------------------------------------------------
// Tablet: navigation rail + 2-pane detail
// ---------------------------------------------------------------------------

@Composable
private fun TabletShowcase() {
    Row(Modifier.fillMaxSize()) {
        YomiraNavigationRail(
            selected = MainDestination.HOME.route,
            onSelect = {},
            copy = CatalogCopyTr,
            vaultVisible = true,
        )
        // The rail runs edge to edge; the dossier dodges the status bar so the
        // chapter heading is never clipped on a landscape tablet.
        Box(Modifier.weight(1f).fillMaxSize().statusBarsPadding()) {
            MangaDetailScreen(
                manga = ShowcaseData.manga(ShowcaseData.detail),
                chapters = ShowcaseData.chapters,
                matching = false,
                saved = true,
                onBack = {},
                onSave = {},
                onRead = {},
                onToggleRead = {},
                onContinue = {},
                copy = DetailCopyTr,
                twoPane = true,
            )
        }
    }
}
