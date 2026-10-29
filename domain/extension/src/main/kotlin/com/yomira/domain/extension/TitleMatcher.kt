package com.yomira.domain.extension

/**
 * The one series-name scoring function shared by every lookup path:
 * [SmartSourceMatcher] source routing, the metadata waterfall's cross-provider
 * identity check, shelf (UI) search and the alias dictionary.
 *
 * Inputs are folded through [TitleNormalizer.canonical] first — [similarity]
 * expects already-folded keys, [score] folds a raw query itself. A score is
 * 0..1 where 1.0 means "certainly this series" and combines:
 *
 *  - **token Jaccard** over non-stopword tokens (`A Series` vs `Series A` → 1.0);
 *  - **edit similarity** on space-squashed strings for typos (`Naroto`);
 *  - **abbreviation similarity**: `AoT`/`MHA`/`SAO` resolve through token
 *    initials, initials-prefixes or constrained subsequences;
 *  - a **containment bonus** when one side's tokens are a subset of the
 *    other's, weighted below exact equality so `Solo Leveling` never resolves
 *    to `Solo Leveling: Ragnarok` ahead of its own exact match;
 *  - **year/genre evidence** ([Evidence]) as small bounded bonuses that break
 *    ties between title-equal candidates.
 *
 * Two safety gates run before any similarity is computed: distinct
 * season/sequel markers (`A Series 2` ≠ `A Series`, `Overlord III` ≠
 * `Overlord`) and volume/year noise (`Vol 1`, `2024`, 3+ digit counts) are
 * stripped or vetoed, mirroring the legacy matcher's guards.
 */
object TitleMatcher {

    /** Identity acceptance: a match at or above this is the same series. */
    const val MATCH_THRESHOLD = 0.75

    /**
     * Typeahead gate for shelf search: looser than identity on purpose — while
     * the user is still typing (`one` for `One Piece`) the shelf must answer
     * "known" so the live import does not fire on a partial word.
     */
    const val TYPING_GATE = 0.5

    /** Head keys (text before a colon) are weaker than full-title equality. */
    private const val HEAD_WEIGHT = 0.95
    private const val CONTAINMENT_BONUS = 0.15
    private const val ABBREVIATION_EXACT = 0.9
    private const val ABBREVIATION_PREFIX = 0.8
    private const val ABBREVIATION_SUBSEQUENCE = 0.75
    private const val YEAR_AGREE_BONUS = 0.05
    private const val YEAR_CONFLICT_PENALTY = 0.10
    private const val GENRE_BONUS = 0.05

    /** Query-side evidence that can shift close comparisons, never dominate. */
    data class Evidence(val year: Int? = null, val genres: Set<String> = emptySet())

    /** A series seen through all its folded names, plus optional facts. */
    class Subject(names: Iterable<String>, val year: Int? = null, val genres: Set<String> = emptySet()) {
        internal val fullKeys: Set<String>
        internal val headKeys: Set<String>

        init {
            val full = mutableSetOf<String>()
            val heads = mutableSetOf<String>()
            for (name in names) {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) continue
                val key = TitleNormalizer.canonical(trimmed)
                if (key.isEmpty()) continue
                full += key
                val head = TitleNormalizer.head(trimmed)
                if (head.isNotEmpty() && head != key) heads += head
            }
            fullKeys = full
            headKeys = heads
        }

        val names: Set<String> get() = fullKeys

        companion object {
            fun of(vararg names: String) = Subject(names.toList())
        }
    }

    /** Folds [query] and scores it against every key of [subject]. */
    fun score(query: String, subject: Subject, evidence: Evidence = Evidence()): Double {
        val key = TitleNormalizer.canonical(query)
        if (key.isEmpty() || (subject.fullKeys.isEmpty() && subject.headKeys.isEmpty())) return 0.0
        val full = subject.fullKeys.maxOf { similarity(key, it) }
        val head = if (subject.headKeys.isEmpty()) 0.0 else subject.headKeys.maxOf { similarity(key, it) } * HEAD_WEIGHT
        val base = maxOf(full, head)
        if (base <= 0.0) return 0.0
        return (base + evidenceBonus(evidence, subject)).coerceIn(0.0, 1.0)
    }

    /** Identity acceptance for a raw query against a subject. */
    fun matches(query: String, subject: Subject, evidence: Evidence = Evidence()): Boolean =
        score(query, subject, evidence) >= MATCH_THRESHOLD

    /** Typeahead acceptance for a raw query against a subject. */
    fun knownWhileTyping(query: String, subject: Subject): Boolean =
        score(query, subject) >= TYPING_GATE

    /**
     * Similarity of two already-folded keys; 1.0 for equality, 0.0 for
     * distinct sequels or unrelated names. Public for callers that fold once
     * and compare many times (alias dictionary lookups, benchmarks).
     */
    fun similarity(query: String, name: String): Double {
        if (query == name) return 1.0
        if (query.isEmpty() || name.isEmpty()) return 0.0
        // Distinct season/sequel markers are different series, full stop.
        if (sequelMarkers(query) != sequelMarkers(name)) return 0.0
        // Volume/year noise folds away: "mob psycho 100 2024" == "mob psycho 100".
        val queryCore = stripNoise(query)
        val nameCore = stripNoise(name)
        if (queryCore.isNotEmpty() && queryCore == nameCore) return 1.0

        val queryTokens = tokens(queryCore.ifEmpty { query })
        val nameTokens = tokens(nameCore.ifEmpty { name })
        val queryContent = queryTokens.filterNot(STOPWORDS::contains)
        val nameContent = nameTokens.filterNot(STOPWORDS::contains)
        val left = queryContent.ifEmpty { queryTokens }
        val right = nameContent.ifEmpty { nameTokens }
        val jaccard = if (left.isEmpty() || right.isEmpty()) 0.0 else {
            left.toSet().intersect(right.toSet()).size.toDouble() / left.toSet().union(right.toSet()).size
        }
        val contained = if (left.isEmpty() || right.isEmpty()) false else run {
            val a = left.toSet(); val b = right.toSet()
            (a.size <= b.size && b.containsAll(a)) || (b.size <= a.size && a.containsAll(b))
        }

        val squashedQuery = queryCore.replace(" ", "").ifEmpty { query.replace(" ", "") }
        val squashedName = nameCore.replace(" ", "").ifEmpty { name.replace(" ", "") }
        val edit = editSimilarity(squashedQuery, squashedName)
        val abbreviation = abbreviationSimilarity(squashedQuery, queryTokens, nameTokens)
        val best = maxOf(jaccard, edit, abbreviation)
        if (best <= 0.0) return 0.0
        return (best + if (contained) CONTAINMENT_BONUS else 0.0).coerceAtMost(1.0)
    }

    private fun evidenceBonus(evidence: Evidence, subject: Subject): Double {
        var bonus = 0.0
        if (evidence.year != null && subject.year != null) {
            bonus += if (evidence.year == subject.year) YEAR_AGREE_BONUS else -YEAR_CONFLICT_PENALTY
        }
        if (evidence.genres.isNotEmpty() && subject.genres.isNotEmpty()) {
            val overlap = evidence.genres.intersect(subject.genres).size.toDouble() /
                evidence.genres.union(subject.genres).size
            bonus += GENRE_BONUS * overlap
        }
        return bonus
    }

    /** `s <n>` markers plus bare 1-2 digit sequel numbers, as a comparable set. */
    private fun sequelMarkers(key: String): Set<String> {
        val tokens = key.split(' ').filter { it.isNotEmpty() }
        val markers = mutableSetOf<String>()
        var index = 0
        while (index < tokens.size) {
            if (tokens[index] == "s" && index + 1 < tokens.size && TRAILING_SEQUEL.matches(tokens[index + 1])) {
                markers += "s:${tokens[index + 1]}"
                index += 2
            } else {
                if (TRAILING_SEQUEL.matches(tokens[index])) markers += "n:${tokens[index]}"
                index += 1
            }
        }
        return markers
    }

    /** Volume/chapter metadata and 3+ digit counts never carry identity. */
    private fun stripNoise(key: String): String {
        var text = VOLUME_METADATA.replace(key, " ")
        text = LARGE_NUMBERS.replace(text, " ")
        return text.replace(WHITESPACE, " ").trim()
    }

    private fun tokens(key: String): List<String> = key.split(' ').filter { it.isNotEmpty() }

    private fun editSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val row = IntArray(right.length + 1)
            row[0] = i + 1
            for (j in right.indices) {
                row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1, previous[j] + if (left[i] == right[j]) 0 else 1)
            }
            previous = row
        }
        return 1.0 - previous[right.length].toDouble() / maxOf(left.length, right.length)
    }

    /**
     * `aot`→`attack on titan` (initials), `goh`→`the god of high school`
     * (initials after a leading article), `fma`→`fullmetal alchemist`
     * (constrained subsequence: same start, at least one char per token).
     * Words that already appear as tokens of the name are not abbreviations.
     */
    private fun abbreviationSimilarity(squashedQuery: String, queryTokens: List<String>, nameTokens: List<String>): Double {
        if (squashedQuery.length !in 2..8 || squashedQuery.any { !it.isLetter() } || nameTokens.isEmpty()) return 0.0
        // `one` inside `One Piece` is a word, not an abbreviation of it.
        if (queryTokens.isNotEmpty() && nameTokens.toSet().containsAll(queryTokens)) return 0.0
        val withoutLeadingArticle = nameTokens.dropWhile { it in ARTICLES }
        val initialSets = listOf(nameTokens, withoutLeadingArticle)
            .map { tokens -> tokens.filter { it.first().isLetter() }.map { it.first() } }
        for (initials in initialSets) {
            if (initials.isEmpty()) continue
            val initialsKey = initials.joinToString("")
            when {
                squashedQuery == initialsKey -> return ABBREVIATION_EXACT
                initialsKey.startsWith(squashedQuery) && squashedQuery.length >= 2 -> return ABBREVIATION_PREFIX
            }
        }
        if (squashedQuery.length < maxOf(2, nameTokens.size)) return 0.0
        if (!isSubsequence(squashedQuery, nameTokens.joinToString(""))) return 0.0
        return ABBREVIATION_SUBSEQUENCE
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.isEmpty()) return true
        var cursor = 0
        for (char in haystack) {
            if (char == needle[cursor]) {
                cursor++
                if (cursor == needle.length) return true
            }
        }
        return false
    }

    private val STOPWORDS = setOf(
        "the", "a", "an", "of", "and", "in", "on", "at", "to", "for",
        "no", "na", "ni", "de", "wa", "ga", "o", "e", "wo", "he",
        "le", "la", "el", "los", "las", "der", "die", "das", "und",
    )
    private val ARTICLES = setOf("the", "a", "an")
    private val VOLUME_METADATA = Regex("""\b(?:vol(?:ume)?|chapter|ch)\s+\d+\b""")
    private val LARGE_NUMBERS = Regex("""\b\d{3,}\b""")
    private val TRAILING_SEQUEL = Regex("""[1-9]\d?""")
    private val WHITESPACE = Regex("""\s+""")
}
