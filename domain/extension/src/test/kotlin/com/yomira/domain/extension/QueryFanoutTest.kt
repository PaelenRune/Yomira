package com.yomira.domain.extension

import org.junit.Assert.*
import org.junit.Test

class QueryFanoutTest {

    @Test fun `primary spellings of distinct names fill the slots first`() {
        val queries = QueryFanout.generate(listOf("Demon Slayer: Kimetsu no Yaiba", "鬼滅の刃", "Kimetsu no Yaiba"))
        assertEquals("Demon Slayer Kimetsu no Yaiba", queries.first())
        assertTrue(queries.contains("鬼滅の刃"))
        assertTrue(queries.contains("Kimetsu no Yaiba"))
        assertTrue(queries.size <= QueryFanout.MAX_VARIANTS)
    }

    @Test fun `fan-out never exceeds the cap however many aliases exist`() {
        val queries = QueryFanout.generate(listOf(
            "Attack on Titan", "進撃の巨人", "Shingeki no Kyojin", "AoT", "Eotan", "Advancing Giant",
        ))
        assertEquals(QueryFanout.MAX_VARIANTS, queries.size)
        assertEquals("Attack on Titan", queries.first())
    }

    @Test fun `names that fold together do not waste primary slots`() {
        // `Naruto Shippuden` and `Naruto Shippūden` fold onto one key: only the
        // first earns a primary slot, the second name's original still follows.
        val queries = QueryFanout.generate(listOf("Naruto Shippuden", "Naruto Shippūden", "疾風伝"))
        assertEquals("Naruto Shippuden", queries[0])
        assertEquals("疾風伝", queries[1])
        assertTrue(queries.size <= QueryFanout.MAX_VARIANTS)
    }

    @Test fun `spelling micro variants keep slots despite folding together`() {
        // `Haikyuu` folds onto `Haikyu` but sources may spell it either way;
        // the variant must still be offered to the bridge.
        val queries = QueryFanout.generate(listOf("Haikyu!!"))
        assertEquals("Haikyu", queries.first())
        assertTrue(queries.contains("Haikyuu"))
        assertTrue(queries.contains("Haikyu!!"))
    }

    @Test fun `macrons expand into the primary romaji spelling`() {
        val queries = QueryFanout.generate(listOf("Tōkyō Ghoul"))
        assertEquals("Toukyou Ghoul", queries.first())
        assertTrue(queries.size <= QueryFanout.MAX_VARIANTS)
    }

    @Test fun `blank and empty names produce no queries`() {
        assertTrue(QueryFanout.generate(emptyList()).isEmpty())
        assertTrue(QueryFanout.generate(listOf("  ", "")).isEmpty())
    }

    @Test fun `qualifier only names fall back to the raw title`() {
        val queries = QueryFanout.generate(listOf("(manhwa)"))
        assertEquals(listOf("(manhwa)"), queries)
    }

    @Test fun `custom caps are honoured`() {
        val queries = QueryFanout.generate(listOf("One Piece", "ワンピース", "Wan Pīsu"), maxVariants = 2)
        assertEquals(listOf("One Piece", "ワンピース"), queries)
    }
}
