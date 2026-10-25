package com.yomira.data.extension

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class TachiyomiBackupParserTest {
    private val parser = TachiyomiBackupParser()

    private fun chapter(
        url: String,
        number: Float,
        read: Boolean,
        lastPage: Long = 0L,
        dateFetch: Long = 0L,
    ) = ProtoTestWriter()
        .stringField(1, url)
        .stringField(2, "Chapter $number")
        .boolField(4, read)
        .varintField(6, lastPage)
        .varintField(7, dateFetch)
        .floatField(9, number)

    private fun manga(
        source: Long,
        url: String,
        title: String,
        favorite: Boolean = true,
        categoryIds: List<Long> = emptyList(),
        packedCategories: Boolean = true,
        chapters: List<ProtoTestWriter> = emptyList(),
    ) = ProtoTestWriter().apply {
        varintField(1, source)
        stringField(2, url)
        stringField(3, title)
        stringField(5, "Author One")
        stringField(9, "https://cover.example/cover.jpg")
        varintField(8, 2L) // status: completed
        varintField(13, 1_700_000_000_000L)
        stringField(7, "Action")
        stringField(7, "Komedi")
        chapters.forEach { messageField(16, it) }
        if (packedCategories) packedLongs(17, categoryIds) else categoryIds.forEach { varintField(17, it) }
        boolField(100, favorite)
        // Fork extras Yomira must skip instead of failing on: tracking message + unknown varint.
        messageField(18, ProtoTestWriter().varintField(1, 7L))
        varintField(105, 3L)
    }

    private fun protobufBackup(): ByteArray {
        val backup = ProtoTestWriter()
            .messageField(
                1,
                manga(
                    source = 5_601_175_488_615_850_849L,
                    url = "series/one",
                    title = "Birinci Seri",
                    categoryIds = listOf(3L, 4L),
                    chapters = listOf(
                        chapter("series/one/1", 1.5f, read = true, lastPage = 4L, dateFetch = 1_690_000_000_000L),
                        chapter("series/one/2", 2.0f, read = false),
                    ),
                ),
            )
            .messageField(
                1,
                manga(
                    source = 7_980_140_199_189_800_807L,
                    url = "series/two",
                    title = "İkinci Seri",
                    packedCategories = false,
                    categoryIds = listOf(3L),
                    chapters = listOf(chapter("series/two/9", 9f, read = true, lastPage = 2L)),
                ),
            )
            .messageField(2, ProtoTestWriter().stringField(1, "Okunuyor").varintField(2, 0L).varintField(3, 3L))
            .messageField(2, ProtoTestWriter().stringField(1, "Sonra").varintField(2, 1L).varintField(3, 4L))
            // Unknown top-level fields: sources message (101) and a fixed64 oddity.
            .messageField(101, ProtoTestWriter().varintField(1, 1L))
        return backup.toByteArray()
    }

    @Test fun `parses raw protobuf backup with nested chapters and packed categories`() {
        val document = parser.parse(protobufBackup())
        assertEquals(com.yomira.domain.extension.BackupFormat.PROTOBUF, document.format)
        assertEquals(listOf("Birinci Seri", "İkinci Seri"), document.mangas.map { it.title })

        val first = document.mangas.first()
        assertEquals(5_601_175_488_615_850_849L, first.sourceId)
        assertEquals("series/one", first.url)
        assertEquals("Author One", first.author)
        assertEquals("https://cover.example/cover.jpg", first.thumbnailUrl)
        assertEquals(2, first.status)
        assertEquals(1_700_000_000_000L, first.dateAdded)
        assertEquals(listOf("Action", "Komedi"), first.genres)
        assertEquals(listOf(3L, 4L), first.categoryIds)
        assertTrue(first.favorite)
        assertEquals(2, first.chapters.size)
        assertEquals(1.5, first.chapters.first().chapterNumber, 0.0001)
        assertTrue(first.chapters.first().read)
        assertEquals(4, first.chapters.first().lastPageRead)
        assertFalse(first.chapters[1].read)

        val second = document.mangas[1]
        assertEquals(listOf(3L), second.categoryIds) // unpacked repeated varints
        assertEquals(9.0, second.chapters.single().chapterNumber, 0.0)

        assertEquals(listOf("Okunuyor", "Sonra"), document.categories.map { it.name })
        assertEquals(listOf(3L, 4L), document.categories.mapNotNull { it.id })
    }

    @Test fun `gzip tachibk payload decodes identically`() {
        val raw = parser.parse(protobufBackup())
        val gzipped = parser.parse(BackupTestFixtures.gzip(protobufBackup()))
        assertEquals(raw, gzipped)
    }

    @Test fun `chapter number also survives fixed64 double encoding`() {
        val manga = ProtoTestWriter()
            .varintField(1, 1L)
            .stringField(2, "u")
            .stringField(3, "t")
            .messageField(16, ProtoTestWriter().stringField(1, "ch").doubleField(9, 41.25))
        val document = parser.parse(ProtoTestWriter().messageField(1, manga).toByteArray())
        assertEquals(41.25, document.mangas.single().chapters.single().chapterNumber, 0.0)
    }

    @Test fun `json era backup with snake_case keys and standalone chapters joins manga ids`() {
        val json = """
        {
          "backupVersion": 1,
          "backupMangaExtension": 1,
          "mangas": [
            {
              "id": 42,
              "source": 5601175488615850849,
              "url": "manga/dust",
              "title": "Kum Fırtınası",
              "thumbnail_url": "https://cdn.example/dust.jpg",
              "favorite": true,
              "date_added": 1680000000000,
              "chapter_flags": 0,
              "genres": ["Aksiyon", "Dram"],
              "status": 1,
              "categories": [7],
              "initialize": true
            },
            { "id": 43, "source": 1, "url": "manga/ghost", "title": "Hayalet", "favorite": false }
          ],
          "categories": [ { "id": 9, "name": "Devam Ediyor", "order": 0 } ],
          "chapters": [
            { "url": "manga/dust/1", "name": "1. Bölüm", "read": true, "last_page_read": 7,
              "date_fetch": 1681000000000, "chapter_number": 1.0, "source": 5601175488615850849, "manga": 42 },
            { "url": "manga/dust/2", "name": "2. Bölüm", "read": true, "last_page_read": 3,
              "date_fetch": 1682000000000, "chapter_number": 2.0, "source": 5601175488615850849, "manga": 42 },
            { "url": "manga/lost/1", "name": "1", "read": false, "chapter_number": 1.0, "source": 1, "manga": 999 }
          ],
          "categoriesMap": { "7": 9 },
          "excludedScanlators": ["Legacy Group"]
        }
        """.trimIndent()
        val document = parser.parse(json.toByteArray(Charsets.UTF_8))
        assertEquals(com.yomira.domain.extension.BackupFormat.JSON, document.format)
        assertEquals(2, document.mangas.size)
        assertEquals(42L, document.mangas.first().rowId)
        assertEquals(listOf(7L), document.mangas.first().categoryIds)
        assertEquals(mapOf(7L to 9L), document.categoryAlias)
        assertEquals("Kum Fırtınası", document.mangas.first().title)
        assertEquals(3, document.standaloneChapters.size)
        assertEquals(42L, document.standaloneChapters.first().mangaRowId)
        assertEquals(7, document.standaloneChapters.first().lastPageRead)
        assertEquals(2.0, document.standaloneChapters[1].chapterNumber, 0.0)
        assertEquals("Devam Ediyor", document.categories.single().name)
    }

    @Test fun `gzipped json backup is accepted`() {
        val plain = parser.parse("""{"mangas":[],"categories":[],"chapters":[]}""".toByteArray())
        val gzipped = parser.parse(BackupTestFixtures.gzip("""{"mangas":[{"id":1,"source":2,"url":"u","title":"t"}],"categories":[],"chapters":[]}""".toByteArray()))
        assertTrue(plain.mangas.isEmpty())
        assertEquals("t", gzipped.mangas.single().title)
        assertEquals(com.yomira.domain.extension.BackupFormat.JSON, gzipped.format)
    }

    @Test fun `camelCase json keys from the dev era parse too`() {
        val json = """
        {"mangas":[{"source":3,"url":"u","title":"Başlık","thumbnailUrl":"https://x/y.jpg","dateAdded":5,"chapters":[
          {"url":"c1","name":"C1","read":true,"lastPageRead":1,"chapterNumber":1.0,"dateFetch":9}]}],
         "categories":[{"id":1,"name":"Cat","order":0}],"chapters":[]}
        """.trimIndent()
        val manga = parser.parse(json.toByteArray()).mangas.single()
        assertEquals("https://x/y.jpg", manga.thumbnailUrl)
        assertEquals(5L, manga.dateAdded)
        assertEquals(1.0, manga.chapters.single().chapterNumber, 0.0)
        assertEquals(9L, manga.chapters.single().dateFetch)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty payload is rejected`() { parser.parse(ByteArray(0)) }

    @Test(expected = IllegalArgumentException::class)
    fun `random bytes that are neither protobuf nor json are rejected`() {
        // Field number 0 is illegal in protobuf and the payload is not JSON either.
        parser.parse(byteArrayOf(0x00, 0x01, 0x02, 0x03))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized compressed payload is rejected`() {
        TachiyomiBackupParser(maxCompressedBytes = 16).parse(ByteArray(64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated protobuf message is rejected`() {
        // Field 1 promises 127 bytes but only one byte follows.
        parser.parse(byteArrayOf(0x0A, 0x7F, 0x01))
    }

    @Test fun `gzip of empty json document is a clear failure, not a crash`() {
        val empty = ByteArrayOutputStream().also { GZIPOutputStream(it).use { gzip -> gzip.write(ByteArray(0)) } }.toByteArray()
        try {
            parser.parse(empty)
            fail("Expected rejection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("empty", ignoreCase = true))
        }
    }
}
