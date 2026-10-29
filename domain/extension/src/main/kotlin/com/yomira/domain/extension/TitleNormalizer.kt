package com.yomira.domain.extension

import java.text.Normalizer
import java.util.Locale

/**
 * Single normalization engine for every series-name comparison in Yomira:
 * source matching ([SmartSourceMatcher]), metadata identity (waterfall), shelf
 * search and the alias dictionary all fold names through [canonical].
 *
 * The fold is deterministic and symmetric — the same rules apply to both sides
 * of a comparison — so no rule needs to know whether it holds a query or an
 * indexed title:
 *
 *  1. NFKC compatibility folding (fullwidth Latin, ligatures, halfwidth kana).
 *  2. Parenthetical/bracketed groups are dropped: they carry qualifiers such as
 *     `(manhwa)`, `(official)` or `(Osamu Tezuka)`, never core title words.
 *  3. Diacritics are stripped for Latin (`Tōkyō` → `Tokyo`) while kana voicing
 *     marks survive, so `デ` never folds to `テ` (a bug the legacy matcher had).
 *  4. Katakana folds to hiragana so script style never splits a title.
 *  5. Punctuation and symbols (`×`, `:`, `!`, `☆`, …) become spaces.
 *  6. Editorial trailing tokens (`official`, `digital`, `color`, `edition`…)
 *     are dropped: `Attack on Titan Full Color Edition` → `attack on titan`.
 *  7. Romaji spelling variants fold together: macrons via (3), plus `ou`→`o`,
 *     `uu`→`u` (Hepburn long vowels) and `sh`→`sy`, `tsu`→`tu` (Hepburn vs
 *     kunrei consonants), and the particles `wo`/`he` fold to `o`/`e`.
 *  8. Roman numerals (`II`, `III`, …) and ordinals (`2nd`, `Second`) become
 *     arabic digits; single-letter numerals (`I`, `V`, `X`) never convert.
 *  9. Season/part markers canonicalize to `s <n>`: `Part 6`, `2nd Season`,
 *     `Act II`, `第6部`, `2年生編`, `2-nensei-hen` and a trailing sequel number
 *     (`Overlord III`) all end with the same marker, and a marked title never
 *     equals its unmarked base.
 *
 * [head] exposes the canonical form of the segment before the first colon
 * (`Demon Slayer: Kimetsu no Yaiba` → `demon slayer`) for brand-prefix
 * matching, and [searchVariants] produces raw spellings a remote source is
 * likely to have indexed (macron digraphs, punctuation-stripped, …).
 */
object TitleNormalizer {

    /** Fully folded, case-insensitive comparison key. Blank input folds to "". */
    fun canonical(title: String): String {
        if (title.isBlank()) return ""
        // CJK part markers fold first: they live inside unspaced tokens (第6部).
        var text = foldCjkPartMarkers(clean(title).lowercase(Locale.ROOT))
        if (text.isEmpty()) return ""

        // Romaji spelling variants (class doc, rule 7). CJK text is untouched.
        text = text.replace("ou", "o").replace("uu", "u")
            .replace("sh", "sy").replace("tsu", "tu")

        var tokens = tokenize(text)
        // Japanese particles: を/へ read "wo"/"he" in romaji but mean o/e.
        tokens = tokens.map { when (it) { "wo" -> "o"; "he" -> "e"; else -> it } }
        // Roman numerals and ordinal words become digits so `II` == `2` == `Second`.
        tokens = tokens.map(::foldRomanOrOrdinal)
        // Volume/chapter metadata never carries identity, not even as a marker:
        // `A Series Vol. 1` must equal `A Series`, so strip before marking.
        tokens = stripVolumeMetadata(tokens)

        text = tokens.joinToString(" ")
        // Season/part phrases (class doc, rule 9). Phrases fold before the bare
        // trailing number so an `s 6` marker is never re-marked.
        text = SEASON_PHRASE.replace(text, "s $1")
        text = SEASON_NUMBER_PREFIX.replace(text, "s $1")
        text = JAPANESE_PART_SUFFIX.replace(text, "s $1")

        // A trailing sequel number becomes a marker: `overlord 3` → `overlord s 3`.
        val parts = tokenize(text).toMutableList()
        if (parts.isNotEmpty()) {
            val last = parts.last()
            val before = if (parts.size >= 2) parts[parts.size - 2] else null
            if (TRAILING_SEQUEL.matches(last) && before != "s") {
                parts[parts.size - 1] = "s"
                parts.add(last)
            }
        }
        return parts.joinToString(" ").trim()
    }

    /**
     * NFKC + qualifier removal + diacritic folding + punctuation stripping.
     * Case is preserved: this is the "friendly" spelling used for queries that
     * remote sources must recognise literally.
     */
    fun clean(title: String): String {
        if (title.isBlank()) return ""
        var text = Normalizer.normalize(title.trim(), Normalizer.Form.NFKC)
        text = PARENTHESES.replace(text, " ")
        text = stripDiacritics(text)
        text = KATAKANA_TO_HIRAGANA.replace(text) { match ->
            String(Character.toChars(match.value.codePointAt(0) - KANA_OFFSET))
        }
        text = PUNCTUATION.replace(text, " ")
        text = text.replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) return ""
        // Editorial trailing tokens never carry identity.
        val tokens = tokenize(text).toMutableList()
        while (tokens.isNotEmpty() && tokens.last().lowercase(Locale.ROOT) in EDITORIAL_SUFFIXES) {
            tokens.removeAt(tokens.size - 1)
        }
        return tokens.joinToString(" ")
    }

    /**
     * Canonical form of the brand before the first colon. A colon-less title
     * is its own head. Callers treat the head as an additional, weaker key:
     * `Demon Slayer` must find `Demon Slayer: Kimetsu no Yaiba`.
     */
    fun head(title: String): String {
        val raw = title.trim()
        val separator = raw.indexOfFirst { it == ':' || it == '：' }
        if (separator <= 0 || separator == raw.length - 1) return canonical(title)
        return canonical(raw.substring(0, separator))
    }

    /**
     * Raw spellings a source may have indexed for [title], most likely first:
     * the untouched title, its macron-digraph expansion (`ō`→`ou`), the cleaned
     * punctuation-free form of both, a doubled terminal `u` (`Haikyu`→`Haikyuu`)
     * and the fully folded canonical form. Never empty for non-blank input.
     */
    fun searchVariants(title: String): List<String> {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return emptyList()
        val cleaned = clean(trimmed)
        val cleanedExpanded = clean(expandMacrons(trimmed))
        val doubledTerminalU =
            if (cleanedExpanded.endsWith("u") && !cleanedExpanded.endsWith("uu")) cleanedExpanded + "u" else ""
        return listOf(trimmed, expandMacrons(trimmed), cleaned, cleanedExpanded, doubledTerminalU, canonical(trimmed))
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Macrons to romaji digraphs, preserving case (`Ō`→`Ou`, `ū`→`uu`). */
    internal fun expandMacrons(title: String): String = title
        .replace("ō", "ou").replace("Ō", "Ou")
        .replace("ū", "uu").replace("Ū", "Uu")
        .replace("ā", "aa").replace("Ā", "Aa")
        .replace("ī", "ii").replace("Ī", "Ii")
        .replace("ē", "ee").replace("Ē", "Ee")

    /** CJK part markers (`第6部`, `2年生編`) fold to `s <n>` before tokenization. */
    internal fun foldCjkPartMarkers(text: String): String =
        CJK_PART_MARKERS.fold(text) { acc, regex -> regex.replace(acc, "s $1") }

    /**
     * Strips combining marks for Latin scripts while keeping kana voicing
     * (dakuten/handakuten), so `デ` stays `デ` and `Tōkyō` becomes `Tokyo`.
     */
    private fun stripDiacritics(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val protected = decomposed.replace(DAKUTEN, "\uE000").replace(HANDAKUTEN, "\uE001")
        val stripped = protected.replace(DIACRITICS, "")
        val restored = stripped.replace("\uE000", DAKUTEN).replace("\uE001", HANDAKUTEN)
        return Normalizer.normalize(restored, Normalizer.Form.NFC)
    }

    private fun tokenize(text: String): List<String> = text.split(' ').filter { it.isNotEmpty() }

    /** Drops `vol/volume/chapter/ch <number>` pairs from the token stream. */
    private fun stripVolumeMetadata(tokens: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val isVolumeWord = token in VOLUME_WORDS
            val nextIsNumber = index + 1 < tokens.size && tokens[index + 1].isNotEmpty() &&
                tokens[index + 1].all { it.isDigit() }
            if (isVolumeWord && nextIsNumber) {
                index += 2
            } else {
                result += token
                index += 1
            }
        }
        return result
    }

    private fun foldRomanOrOrdinal(token: String): String {
        ROMAN_NUMERALS[token]?.let { return it.toString() }
        return ORDINAL_SUFFIX.replace(token) { it.groupValues[1] }
            .let { ORDINAL_WORDS[it] ?: it }
    }

    private val PARENTHESES = Regex("""\([^)]*\)|\[[^\]]*\]|【[^】]*】|（[^）]*）""")
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE = Regex("""\s+""")
    private val DIACRITICS = Regex("""\p{M}+""")
    private val DAKUTEN = "\u3099"
    private val HANDAKUTEN = "\u309A"
    private val KATAKANA_TO_HIRAGANA = Regex("""[\u30A1-\u30F6]""")
    private const val KANA_OFFSET = 0x60

    private val EDITORIAL_SUFFIXES = setOf(
        "official", "digital", "color", "colour", "scanlation", "edition", "full",
    )

    /** Two-letter-plus numerals only: `I`, `V`, `X` are words (`V for Vendetta`). */
    private val ROMAN_NUMERALS = mapOf(
        "ii" to 2, "iii" to 3, "iv" to 4, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9,
        "xi" to 11, "xii" to 12, "xiii" to 13, "xiv" to 14, "xv" to 15, "xvi" to 16,
        "xvii" to 17, "xviii" to 18, "xix" to 19, "xx" to 20,
    )

    private val ORDINAL_SUFFIX = Regex("""^(\d+)(?:st|nd|rd|th)$""")
    private val ORDINAL_WORDS = mapOf(
        "first" to "1", "second" to "2", "third" to "3", "fourth" to "4", "fifth" to "5",
        "sixth" to "6", "seventh" to "7", "eighth" to "8", "ninth" to "9", "tenth" to "10",
    )
    private val VOLUME_WORDS = setOf("vol", "volume", "chapter", "ch")

    private val SEASON_WORDS = """(?:season|part|act|stage|cour|arc|year)"""
    private val SEASON_PHRASE = Regex("""\b$SEASON_WORDS\s+(\d+)\b""")
    private val SEASON_NUMBER_PREFIX = Regex("""\b(\d+)\s+(?:season|part|year)s?\b""")
    private val JAPANESE_PART_SUFFIX = Regex("""\b(?:dai\s+)?(\d+)\s*(?:bu|hen|kai|nensei\s+hen)\b""")
    private val TRAILING_SEQUEL = Regex("""[1-9]\d?""")

    // CJK part markers live inside unspaced tokens (`第6部`, `2年生編`, `6部`).
    private val CJK_PART_MARKERS = listOf(
        Regex("""第(\d+)部"""), Regex("""(\d+)年生編"""), Regex("""(\d+)年編"""),
        Regex("""第(\d+)期"""), Regex("""第(\d+)章"""), Regex("""(?<![第\d])(\d+)部"""),
    )
}
