package com.yomira.domain.extension

import org.junit.Assert.*
import org.junit.Test

class TitleNormalizerTest {

    // --- Rule 1: NFKC compatibility ------------------------------------------

    @Test fun `canonical folds fullwidth latin and ligatures`() {
        assertEquals("one piece", TitleNormalizer.canonical("Ｏｎｅ　Ｐｉｅｃｅ"))
        assertEquals("naruto", TitleNormalizer.canonical("ＮＡＲＵＴＯ"))
    }

    // --- Rules 2+3: parentheses, qualifiers, diacritics ----------------------

    @Test fun `canonical drops parenthetical qualifiers`() {
        assertEquals("ragnarok", TitleNormalizer.canonical("Ragnarok (manhwa)"))
        assertEquals("invincible", TitleNormalizer.canonical("Invincible (comic)"))
        assertEquals("phoenix", TitleNormalizer.canonical("Phoenix (Osamu Tezuka)"))
    }

    @Test fun `canonical strips latin diacritics`() {
        assertEquals("tokyo", TitleNormalizer.canonical("Tōkyō"))
        assertEquals("asterix le gaulois", TitleNormalizer.canonical("Astérix le Gaulois"))
        assertEquals("kimi no suizo o tabetai", TitleNormalizer.canonical("Kimi no Suizō o Tabetai"))
    }

    @Test fun `canonical keeps kana voicing marks`() {
        // The legacy matcher corrupted voiced kana to their unvoiced base.
        assertEquals(TitleNormalizer.canonical("ですのーと"), TitleNormalizer.canonical("デスノート"))
        assertNotEquals(TitleNormalizer.canonical("てすのーと"), TitleNormalizer.canonical("デスノート"))
    }

    // --- Rules 4+5: script and punctuation -----------------------------------

    @Test fun `canonical folds katakana and hiragana into one script`() {
        assertEquals(TitleNormalizer.canonical("わんぴーす"), TitleNormalizer.canonical("ワンピース"))
    }

    @Test fun `canonical turns punctuation and symbols into spaces`() {
        assertEquals("hunter hunter", TitleNormalizer.canonical("Hunter × Hunter"))
        assertEquals("re zero", TitleNormalizer.canonical("Re:Zero"))
        assertEquals("spy family", TitleNormalizer.canonical("SPY×FAMILY"))
        assertEquals("naruto なると", TitleNormalizer.canonical("NARUTO -ナルト-"))
    }

    // --- Rule 6: editorial suffixes -------------------------------------------

    @Test fun `canonical drops editorial trailing tokens`() {
        assertEquals("attack on titan", TitleNormalizer.canonical("Attack on Titan Full Color Edition"))
        assertEquals("haikyu", TitleNormalizer.canonical("Haikyuu!! (Official) (Digital) Scanlation"))
    }

    // --- Rule 7: romaji variants ----------------------------------------------

    @Test fun `canonical folds long vowel digraphs`() {
        assertEquals(TitleNormalizer.canonical("Naruto Shippuden"), TitleNormalizer.canonical("Naruto Shippuuden"))
        assertEquals(
            TitleNormalizer.canonical("Naruto Shippuden"),
            TitleNormalizer.canonical("Naruto Shippūden"),
        )
        assertEquals(TitleNormalizer.canonical("Yokoso"), TitleNormalizer.canonical("Youkoso"))
    }

    @Test fun `canonical folds hepburn and kunrei consonants`() {
        assertEquals(TitleNormalizer.canonical("Tubasa"), TitleNormalizer.canonical("Tsubasa"))
        assertEquals(TitleNormalizer.canonical("Syojo"), TitleNormalizer.canonical("Shojo"))
        assertEquals(TitleNormalizer.canonical("Syojo"), TitleNormalizer.canonical("Shoujo"))
    }

    @Test fun `canonical folds japanese particles wo and he`() {
        assertEquals(
            TitleNormalizer.canonical("Kimi no Suizō o Tabetai"),
            TitleNormalizer.canonical("Kimi no Suizo wo Tabetai"),
        )
    }

    // --- Rule 8: roman numerals and ordinals ----------------------------------

    @Test fun `canonical folds roman numerals to arabic`() {
        assertEquals("overlord s 3", TitleNormalizer.canonical("Overlord III"))
        assertEquals(TitleNormalizer.canonical("Mob Psycho 100 II"), TitleNormalizer.canonical("Mob Psycho 100 2"))
    }

    @Test fun `canonical never folds single letter numerals`() {
        assertEquals("v for vendetta", TitleNormalizer.canonical("V for Vendetta"))
        assertEquals("y the last man", TitleNormalizer.canonical("Y: The Last Man"))
    }

    @Test fun `canonical folds ordinal words and suffixes`() {
        assertEquals(TitleNormalizer.canonical("Major 2nd"), TitleNormalizer.canonical("Major Second"))
        assertEquals("major s 2", TitleNormalizer.canonical("Major 2nd"))
    }

    // --- Rule 9: season and part markers ---------------------------------------

    @Test fun `canonical marks explicit season and part phrases`() {
        assertEquals("re zero s 2", TitleNormalizer.canonical("Re:Zero 2nd Season"))
        assertEquals(
            "jojo s bizarre adventure s 6 stone ocean",
            TitleNormalizer.canonical("JoJo's Bizarre Adventure Part 6: Stone Ocean"),
        )
        assertEquals(TitleNormalizer.canonical("Ace of Diamond Act II"), TitleNormalizer.canonical("Ace of Diamond Act 2"))
        assertEquals(TitleNormalizer.canonical("Classroom of the Elite 2nd Year"), "classroom of the elite s 2")
    }

    @Test fun `canonical marks japanese part suffixes`() {
        assertTrue(TitleNormalizer.canonical("ジョジョの奇妙な冒険 第6部 ストーンオーシャン").endsWith("s 6 すとーんおーしゃん"))
        assertTrue(TitleNormalizer.canonical("ようこそ実力至上主義の教室へ 2年生編").endsWith("s 2"))
        assertEquals(TitleNormalizer.canonical("Yōkoso Jitsuryoku Shijō Shugi no Kyōshitsu e 2-nensei-hen"),
            TitleNormalizer.canonical("Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e 2-nensei-hen"))
    }

    @Test fun `canonical marks trailing sequel numbers but not intrinsic ones`() {
        assertEquals("a series s 2", TitleNormalizer.canonical("A Series 2"))
        assertEquals("mob psycho 100", TitleNormalizer.canonical("Mob Psycho 100"))
        assertEquals("20 century boys", TitleNormalizer.canonical("20th Century Boys"))
    }

    @Test fun `canonical strips volume and chapter metadata entirely`() {
        assertEquals("a series", TitleNormalizer.canonical("A Series Vol. 1"))
        assertEquals("a series", TitleNormalizer.canonical("A Series Chapter 12"))
        assertEquals("mob psycho 100 2024", TitleNormalizer.canonical("Mob Psycho 100 2024 Vol 1"))
    }

    // --- head(): colon-split long titles ---------------------------------------

    @Test fun `head returns the canonical brand before the colon`() {
        assertEquals("demon slayer", TitleNormalizer.head("Demon Slayer: Kimetsu no Yaiba"))
        assertEquals("kaguya sama", TitleNormalizer.head("Kaguya-sama: Love Is War"))
        assertEquals("watchmen", TitleNormalizer.head("Watchmen"))
        // A short single-token head is still the head; callers weight it as a weak key.
        assertEquals("re", TitleNormalizer.head("Re:Zero - Starting Life in Another World"))
    }

    // --- searchVariants(): source-friendly spellings ---------------------------

    @Test fun `searchVariants produce macron punctuation and terminal-u spellings`() {
        val tokyo = TitleNormalizer.searchVariants("Tōkyō!")
        assertTrue(tokyo.contains("Toukyou"))
        assertTrue(tokyo.contains("Tokyo"))
        val haikyu = TitleNormalizer.searchVariants("Haikyu!!")
        assertTrue(haikyu.contains("Haikyuu"))
        assertTrue(haikyu.contains("Haikyu"))
    }

    @Test fun `searchVariants never return blank or duplicate spellings`() {
        val variants = TitleNormalizer.searchVariants("  Shingeki no Kyojin!  ")
        assertTrue(variants.isNotEmpty())
        assertEquals(variants.size, variants.distinct().size)
        assertTrue(variants.none { it.isBlank() })
        assertTrue(variants.contains("Shingeki no Kyojin"))
    }

    // --- overall contract -------------------------------------------------------

    @Test fun `canonical is idempotent and blank safe`() {
        val title = "Komi-san wa, Komyōshō desu. (Official) 第2部"
        assertEquals(TitleNormalizer.canonical(title), TitleNormalizer.canonical(TitleNormalizer.canonical(title)))
        assertEquals("", TitleNormalizer.canonical("   "))
        assertEquals("", TitleNormalizer.canonical("(!?)"))
    }

    @Test fun `clean preserves case while stripping noise`() {
        assertEquals("Tokyo", TitleNormalizer.clean("Tōkyō!"))
        assertEquals("Haikyu", TitleNormalizer.clean("Haikyu!! (Digital)"))
        assertEquals("", TitleNormalizer.clean("(manhwa)"))
    }
}
