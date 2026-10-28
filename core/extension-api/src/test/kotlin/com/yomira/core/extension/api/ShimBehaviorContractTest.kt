// Reflection + behavior contract tests for the shim layer (task V811-RT-A).
// FakeSource acts as a synthetic mini-extension compiled against the shim.
package com.yomira.core.extension.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.shim.ShimServiceLocator
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.runBlocking
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class ShimBehaviorContractTest {

    @Before
    fun setUp() {
        ShimServiceLocator.reset()
        ShimServiceLocator.configure(OkHttpClient())
    }

    @After
    fun tearDown() {
        ShimServiceLocator.reset()
    }

    @Test
    fun filterDefaultsMatchTheExtensionCompiledShape() {
        class CheckBoxFilter(name: String) : Filter.CheckBox(name)
        class TextFilter(name: String) : Filter.Text(name)
        class SelectFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values)
        class TriStateFilter(name: String) : Filter.TriState(name)
        class GroupFilter(name: String, states: List<String>) : Filter.Group<String>(name, states)

        val checkBox = CheckBoxFilter("Completed").apply { state = true }
        assertTrue(checkBox.state)
        assertEquals("Completed", checkBox.name)
        checkBox.state = false
        assertFalse(CheckBoxFilter("other").state)

        assertEquals("", TextFilter("q").state)
        TextFilter("q").state = "query"

        val select = SelectFilter("Status", arrayOf("Any", "Ongoing"))
        assertEquals(0, select.state)
        assertArrayEquals(arrayOf("Any", "Ongoing"), select.values)
        select.state = 1

        val tri = TriStateFilter("genre")
        assertEquals(Filter.TriState.STATE_IGNORE, tri.state)
        assertTrue(tri.isIgnored())
        assertFalse(tri.isIncluded())
        assertFalse(tri.isExcluded())
        tri.state = Filter.TriState.STATE_INCLUDE
        assertTrue(tri.isIncluded())
        tri.state = Filter.TriState.STATE_EXCLUDE
        assertTrue(tri.isExcluded())

        val group = GroupFilter("genres", listOf("a"))
        assertEquals(listOf("a"), group.state)

        assertEquals("Header", Filter.Header("Header").name)
        assertEquals("", Filter.Separator().name)
        assertEquals("-", Filter.Separator("-").name)
    }

    @Test
    fun filterListBehavesAsAList() {
        val checkBox = object : Filter.CheckBox("cb") {}
        val list = FilterList(listOf(checkBox))
        assertEquals(1, list.size)
        assertEquals(listOf<Filter<*>>(checkBox), list.list)
        assertFalse(list.isEmpty())
        assertTrue(FilterList(emptyList()).isEmpty())
        assertTrue(FilterList().isEmpty())

        val vararg = FilterList(checkBox, object : Filter.Text("t") {})
        assertEquals(2, vararg.size)
        assertEquals(checkBox, vararg.iterator().next())
    }

    @Test
    fun mangaModelsRoundTripThroughTheShimShape() {
        val manga = SManga.create()
        assertTrue(manga is eu.kanade.tachiyomi.source.model.SMangaImpl)
        manga.url = "/manga/1"
        manga.title = "Title"
        manga.thumbnail_url = "https://example.com/thumb.jpg"
        manga.artist = "Artist"
        manga.author = "Author"
        manga.status = SManga.COMPLETED
        manga.description = "Description"
        manga.genre = "Action, Drama"
        manga.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        manga.initialized = true
        assertEquals("/manga/1", manga.url)
        assertEquals("Title", manga.title)
        assertEquals("https://example.com/thumb.jpg", manga.thumbnail_url)
        assertEquals(SManga.COMPLETED, manga.status)
        assertEquals(UpdateStrategy.ONLY_FETCH_ONCE, manga.update_strategy)
        assertTrue(manga.initialized)
        assertEquals(6, SManga.ON_HIATUS)
        assertEquals(UpdateStrategy.ALWAYS_UPDATE, SManga.create().update_strategy)

        val chapter = SChapter.create()
        assertTrue(chapter is eu.kanade.tachiyomi.source.model.SChapterImpl)
        chapter.url = "/chapter/1"
        chapter.name = "Chapter 1"
        chapter.chapter_number = 1.5f
        chapter.scanlator = "Group"
        chapter.date_upload = 1234567890L
        assertEquals("/chapter/1", chapter.url)
        assertEquals(1.5f, chapter.chapter_number)
        assertEquals(-1f, SChapter.create().chapter_number)
        assertEquals(0L, SChapter.create().date_upload)

        val update = SMangaUpdate(manga, listOf(chapter))
        assertSame(manga, update.manga)
        assertEquals(listOf(chapter), update.chapters)

        val page = Page(0, "https://example.com/page0")
        assertEquals(0, page.index)
        assertEquals("https://example.com/page0", page.url)
        assertNull(page.imageUrl)
        page.imageUrl = "https://example.com/img.jpg"
        assertEquals("https://example.com/img.jpg", page.imageUrl)

        val mangasPage = MangasPage(listOf(manga), hasNextPage = true)
        assertTrue(mangasPage.hasNextPage)
        assertEquals(1, mangasPage.mangas.size)
        val (list, next) = mangasPage
        assertEquals(1, list.size)
        assertTrue(next)
    }

    @Test
    fun sourceIdUsesTheMihonAlgorithm() {
        val source = FakeSource()
        val key = "fake source/en/1"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        val expected = (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
        assertEquals(expected, source.id)
        assertTrue(source.id > 0)
        assertEquals("Fake Source (EN)", source.toString())
        assertEquals("https://example.com", source.getHomeUrl())
    }

    @Test
    fun setUrlWithoutDomainStripsSchemeHostAndKeepsQuery() {
        val source = FakeSource()
        val manga = SManga.create()
        with(source) {
            manga.setUrlWithoutDomain("https://example.com/manga/1?tab=info")
        }
        assertEquals("/manga/1?tab=info", manga.url)

        val chapter = SChapter.create()
        with(source) {
            chapter.setUrlWithoutDomain("https://cdn.example.net/chapter/9 page")
        }
        // URI.path decodes percent-encoded characters, matching the Mihon
        // implementation extensions were built against.
        assertEquals("/chapter/9 page", chapter.url)

        with(source) {
            try {
                manga.setUrlWithoutDomain("https://exa[mple.com/manga/1")
                fail("Expected IllegalArgumentException")
            } catch (_: IllegalArgumentException) {
                // expected: URI syntax error
            }
        }
    }

    @Test
    fun headersCarryTheDefaultUserAgent() {
        val source = FakeSource()
        assertEquals(ShimServiceLocator.DEFAULT_USER_AGENT, source.headers["User-Agent"])
    }

    @Test
    fun requestsBuildMihonStyleRequests() {
        val url = "https://example.com/list"
        val get = GET(url)
        assertEquals(url, get.url.toString())
        assertEquals("GET", get.method)
        assertEquals(ShimServiceLocator.DEFAULT_USER_AGENT, get.header("User-Agent"))
        assertTrue(get.cacheControl.noCache)

        val cacheGet = GET(url, cache = CacheControl.Builder().noStore().build())
        assertTrue(cacheGet.cacheControl.noStore)

        val post = POST(url, body = "a=b".toRequestBody(null))
        assertEquals("POST", post.method)
        assertEquals(3L, post.body!!.contentLength())
    }

    @Test
    fun awaitSuccessReturnsResponsesAndThrowsHttpExceptionOnErrorCodes() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val ok = runBlocking {
                ShimServiceLocator.httpClient.newCall(GET(server.url("/list").toString())).awaitSuccess()
            }
            assertEquals(200, ok.code)
            assertEquals("ok", ok.body!!.string())

            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            try {
                runBlocking {
                    ShimServiceLocator.httpClient.newCall(GET(server.url("/list").toString())).awaitSuccess()
                }
                fail("Expected HttpException")
            } catch (e: HttpException) {
                assertEquals(500, e.code)
                assertEquals("HTTP error 500", e.message)
            }
        } finally {
            server.shutdown()
        }
    }
}
