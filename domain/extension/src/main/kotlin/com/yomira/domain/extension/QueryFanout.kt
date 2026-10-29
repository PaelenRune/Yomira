package com.yomira.domain.extension

/**
 * Fan-out query generator for live lookups: from a series' ordered names
 * (query/EN title first, then the original, then aliases) it derives at most
 * [MAX_VARIANTS] raw search strings that remote sources are likely to have
 * indexed.
 *
 * Two passes keep the cap meaningful:
 *  1. one **primary spelling per name** — the macron-expanded, punctuation-free
 *     romaji (`Tōkyō!` → `Toukyou`) — de-duplicated by [TitleNormalizer.canonical]
 *     so two names that fold together never waste a slot, and four *distinct*
 *     names win the four slots before any spelling micro-variant does;
 *  2. remaining slots are filled with the next-best spellings of the leading
 *     names (raw title, doubled terminal `u` such as `Haikyu` → `Haikyuu`,
 *     fully folded canonical form). These dedupe by exact string only —
 *     `Haikyuu` folds onto `Haikyu` but a source may spell it either way.
 *
 * Probing stops at the first *rich* result — a variant whose match yields
 * chapters — which is the caller's loop, not this generator's.
 */
object QueryFanout {

    /** Bounded fan-out: each source probe costs a network round trip. */
    const val MAX_VARIANTS = 4

    fun generate(titles: List<String>, maxVariants: Int = MAX_VARIANTS): List<String> {
        require(maxVariants > 0) { "Fan-out needs at least one variant" }
        val names = titles.map(String::trim).filter(String::isNotEmpty)
        if (names.isEmpty()) return emptyList()

        val foldedNames = mutableSetOf<String>()
        val exactSpellings = mutableSetOf<String>()
        val output = mutableListOf<String>()

        fun add(candidate: String): Boolean {
            if (candidate.isBlank()) return true
            exactSpellings += candidate
            output += candidate
            return output.size < maxVariants
        }

        // Pass 1: the primary spelling of every distinct (folded) name.
        for (name in names) {
            val primary = primarySpelling(name)
            if (primary.isBlank()) continue
            if (!foldedNames.add(TitleNormalizer.canonical(primary))) continue
            if (!add(primary)) return output
        }
        // Pass 2: spelling micro-variants of the leading names fill leftovers.
        for (name in names) {
            val primary = primarySpelling(name)
            for (variant in TitleNormalizer.searchVariants(name)) {
                if (variant == primary) continue
                if (variant in exactSpellings) continue
                if (!add(variant)) return output
            }
        }
        return output
    }

    /**
     * The spelling a source most likely indexed: macron digraphs expanded and
     * punctuation/qualifiers removed, falling back to the raw title when the
     * clean form collapses (`(manhwa)` alone carries nothing searchable).
     */
    private fun primarySpelling(name: String): String =
        TitleNormalizer.clean(TitleNormalizer.expandMacrons(name)).ifBlank { name }
}
