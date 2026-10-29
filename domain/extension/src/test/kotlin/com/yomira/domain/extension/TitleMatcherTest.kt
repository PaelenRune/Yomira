package com.yomira.domain.extension

import org.junit.Assert.*
import org.junit.Test

class TitleMatcherTest {

    private fun subject(vararg names: String) = TitleMatcher.Subject(names.toList())

    // --- token jaccard ---------------------------------------------------------

    @Test fun `token order and stopwords never change identity`() {
        assertEquals(1.0, TitleMatcher.similarity(TitleNormalizer.canonical("A Series"), "series a"), 1e-9)
        assertEquals(1.0, TitleMatcher.similarity(TitleNormalizer.canonical("Series A"), "a series"), 1e-9)
        assertTrue(TitleMatcher.matches("Series A", subject("A Series")))
    }

    @Test fun `stopword removal lets brand names match through articles`() {
        assertTrue(TitleMatcher.matches("Sandman", subject("The Sandman")))
        assertTrue(TitleMatcher.matches("Breaker", subject("The Breaker")))
    }

    // --- exact vs containment discrimination -----------------------------------

    @Test fun `exact equality outranks containment so brands resolve to themselves`() {
        val solo = subject("Solo Leveling")
        val ragnarok = subject("Solo Leveling: Ragnarok")
        assertTrue(TitleMatcher.matches("Solo Leveling", solo))
        // A base series never scores higher against its sequel than against
        // its own exact card, whichever key the sequel exposes.
        assertTrue(TitleMatcher.score("Solo Leveling", ragnarok) < TitleMatcher.score("Solo Leveling", solo))
    }

    @Test fun `colon heads act as weaker keys for subtitle brands`() {
        val kimetsu = subject("Demon Slayer: Kimetsu no Yaiba", "Kimetsu no Yaiba")
        assertTrue(TitleMatcher.matches("Demon Slayer", kimetsu))
        // A full exact key always beats a head key.
        val exact = subject("Demon Slayer")
        assertTrue(TitleMatcher.score("Demon Slayer", exact) > TitleMatcher.score("Demon Slayer", kimetsu))
    }

    // --- sequel and volume guards ----------------------------------------------

    @Test fun `distinct sequel markers never match`() {
        assertFalse(TitleMatcher.matches("A Series 2", subject("A Series")))
        assertFalse(TitleMatcher.matches("A Series 12", subject("A Series 11")))
        assertFalse(TitleMatcher.matches("Re:Zero - Starting Life in Another World",
            subject("Re:Zero - Starting Life in Another World 2nd Season")))
    }

    @Test fun `season marker spellings are interchangeable`() {
        val overlord = subject("Overlord III")
        assertTrue(TitleMatcher.matches("Overlord 3", overlord))
        val act = subject("Ace of Diamond Act II")
        assertTrue(TitleMatcher.matches("Ace of Diamond Act 2", act))
        val major = subject("Major 2nd")
        assertTrue(TitleMatcher.matches("Major Second", major))
    }

    @Test fun `volume and year noise fold away`() {
        val mob = subject("Mob Psycho 100")
        assertTrue(TitleMatcher.matches("Mob Psycho 100 2024 Vol 1", mob))
        assertTrue(TitleMatcher.matches("A Series Vol. 1", subject("A Series")))
    }

    // --- fuzzy spelling ---------------------------------------------------------

    @Test fun `typos survive through edit similarity`() {
        assertTrue(TitleMatcher.matches("Naroto", subject("Naruto")))
        assertFalse(TitleMatcher.matches("Unrelated Adventures", subject("Attack on Titan")))
    }

    @Test fun `romaji spellings fold to one identity`() {
        val shippuden = subject("Naruto Shippuden")
        assertTrue(TitleMatcher.matches("Naruto Shippuuden", shippuden))
        assertTrue(TitleMatcher.matches("Naruto Shippūden", shippuden))
        val youkoso = subject("Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e 2-nensei-hen")
        assertTrue(TitleMatcher.matches("Yōkoso Jitsuryoku Shijō Shugi no Kyōshitsu e 2-nensei-hen", youkoso))
    }

    // --- abbreviation similarity --------------------------------------------------

    @Test fun `initialisms resolve through token initials`() {
        val aot = subject("Attack on Titan")
        assertTrue(TitleMatcher.matches("AoT", aot))
        val mha = subject("My Hero Academia")
        assertTrue(TitleMatcher.matches("MHA", mha))
        val sao = subject("Sword Art Online")
        assertTrue(TitleMatcher.matches("SAO", sao))
    }

    @Test fun `initial prefixes and constrained subsequences resolve abbreviations`() {
        val goh = subject("The God of High School")
        assertTrue(TitleMatcher.matches("GOH", goh))
        val fma = subject("Fullmetal Alchemist")
        assertTrue(TitleMatcher.matches("FMA", fma))
    }

    @Test fun `abbreviations never invent matches for unrelated series`() {
        assertFalse(TitleMatcher.matches("AoT", subject("Asterix the Gaul")))
        assertFalse(TitleMatcher.matches("SAO", subject("One Piece")))
    }

    // --- year and genre evidence ----------------------------------------------------

    @Test fun `year agreement breaks ties and conflict penalizes`() {
        // A partial-overlap subject keeps the base score below 1.0 so the
        // bounded evidence deltas are observable.
        val slime = TitleMatcher.Subject(listOf("Tensei shitara Slime Datta Ken"), year = 2013)
        val base = TitleMatcher.score("Slime Datta Ken", slime)
        assertTrue(base in 0.5..0.95)
        assertEquals(base + 0.05, TitleMatcher.score("Slime Datta Ken", slime, TitleMatcher.Evidence(year = 2013)), 1e-9)
        assertEquals(base - 0.10, TitleMatcher.score("Slime Datta Ken", slime, TitleMatcher.Evidence(year = 2005)), 1e-9)
        // Unknown evidence never fabricates a bonus.
        assertEquals(base, TitleMatcher.score("Slime Datta Ken", slime, TitleMatcher.Evidence()), 1e-9)
    }

    @Test fun `genre overlap is a bounded bonus`() {
        val subject = TitleMatcher.Subject(listOf("Tensei shitara Slime Datta Ken"), genres = setOf("Action", "Adventure"))
        val base = TitleMatcher.score("Slime Datta Ken", subject)
        val agreed = TitleMatcher.score("Slime Datta Ken", subject, TitleMatcher.Evidence(genres = setOf("Action")))
        val disjoint = TitleMatcher.score("Slime Datta Ken", subject, TitleMatcher.Evidence(genres = setOf("Horror")))
        assertTrue(agreed > base)
        assertEquals(base, disjoint, 1e-9)
    }

    // --- typing gate ------------------------------------------------------------

    @Test fun `typing gate accepts prefixes identity threshold rejects them`() {
        val onePiece = subject("One Piece")
        assertTrue(TitleMatcher.knownWhileTyping("one", onePiece))
        assertTrue(TitleMatcher.knownWhileTyping("one piec", onePiece))
        assertFalse(TitleMatcher.matches("one", onePiece))
    }

    // --- contract ------------------------------------------------------------------

    @Test fun `blank queries and empty subjects score zero`() {
        assertEquals(0.0, TitleMatcher.score("   ", subject("One Piece")), 1e-9)
        assertEquals(0.0, TitleMatcher.score("One Piece", subject("  ", "(?)")), 1e-9)
    }
}
