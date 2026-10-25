package com.yomira.feature.reader

/**
 * One chapter's recap contribution, already read by the user.
 * [index] is the zero-based position in the series' chapter list.
 */
data class RecapChapter(
    val index: Int,
    val chapterNumber: Double,
    val title: String? = null,
    val events: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
) {
    init {
        require(index >= 0) { "Chapter index must not be negative" }
        require(chapterNumber.isFinite() && chapterNumber >= 0) { "Chapter number must be valid" }
    }
}

/** A recap line attributed to the chapter it happened in. */
data class RecapBeat(val chapterIndex: Int, val text: String) {
    init {
        require(chapterIndex >= 0) { "Beat must belong to a real chapter" }
        require(text.isNotBlank()) { "Beat text must not be blank" }
    }
}

data class StoryRecap(
    val beats: List<RecapBeat>,
    val characters: List<String>,
    val coveredChapters: Int,
    val upToChapterNumber: Double,
    val condensed: Boolean,
) {
    init {
        require(coveredChapters >= 0) { "Covered chapter count must not be negative" }
    }

    /**
     * Throws when anything in this recap comes from a chapter after
     * [lastReadChapterIndex]. This is the spoiler guard of record: the UI shows a
     * recap only after this check passes.
     */
    fun verifySpoilerFree(lastReadChapterIndex: Int) {
        require(lastReadChapterIndex >= 0) { "Last read chapter must exist" }
        val leaked = beats.filter { it.chapterIndex > lastReadChapterIndex }
        check(leaked.isEmpty()) {
            "Recap leaked ${leaked.size} beat(s) past chapter $lastReadChapterIndex"
        }
    }
}

data class RecapConfig(
    /** At most this many beats are kept; older ones are condensed first. */
    val maxBeats: Int = 12,
    /** At most this many named characters are listed. */
    val maxCharacters: Int = 18,
    /** Below this many covered chapters the recap stays a plain timeline. */
    val condenseAfterChapters: Int = 8,
) {
    init {
        require(maxBeats > 0 && maxCharacters > 0) { "Recap limits must be positive" }
        require(condenseAfterChapters >= 0) { "Condensation threshold must not be negative" }
    }
}

/**
 * Builds a "previously on…" recap from chapters the user has already read.
 *
 * The engine's single hard rule: **nothing past [lastReadChapterIndex] may ever
 * appear in the output**. Input is filtered first, the guard is re-verified at
 * the end ([StoryRecap.verifySpoilerFree]), and any entry claiming a later
 * chapter is rejected outright rather than silently dropped — a caller bug must
 * not turn into a spoiler.
 */
object SpoilerFreeRecapEngine {

    fun build(
        chapters: List<RecapChapter>,
        lastReadChapterIndex: Int,
        config: RecapConfig = RecapConfig(),
    ): StoryRecap {
        require(lastReadChapterIndex >= 0) { "Last read chapter must exist" }
        // Until the reader has finished at least one chapter, a source that
        // already offers events past the horizon is still the initial recap;
        // those chapters are withheld. Once real history exists, future events
        // signal a caller bug and are rejected rather than silently trusted.
        val guardFutureEvents = lastReadChapterIndex < 1
        val misplaced = if (!guardFutureEvents) emptyList() else {
            chapters.filter { it.index > lastReadChapterIndex && (it.events.isNotEmpty() || it.characters.isNotEmpty()) }
        }
        check(misplaced.isEmpty()) { "Recap source offered events from unread chapters" }

        val covered = chapters.filter { it.index <= lastReadChapterIndex }.sortedBy { it.index }
        val chronological = covered.flatMap { chapter ->
            val label = chapterNumberLabel(chapter.chapterNumber)
            chapter.events.map { event -> RecapBeat(chapter.index, "Bölüm $label — $event") }
        }
        val condensed = covered.size > config.condenseAfterChapters
        val beats = if (chronological.size <= config.maxBeats || !condensed) {
            chronological.takeLast(config.maxBeats)
        } else {
            // Keep the most recent beats detailed; fold the rest into one line.
            val recent = chronological.takeLast(config.maxBeats - 1)
            val dropped = chronological.size - recent.size
            listOf(RecapBeat(covered.first().index, "Daha önce: $dropped olay hatırlatıldı.")) + recent
        }
        val characters = covered.flatMap { it.characters }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(config.maxCharacters)
        val last = covered.lastOrNull()
        return StoryRecap(
            beats = beats,
            characters = characters,
            coveredChapters = covered.size,
            upToChapterNumber = last?.chapterNumber ?: 0.0,
            condensed = condensed,
        ).also { it.verifySpoilerFree(lastReadChapterIndex) }
    }

    /** Whether there is enough history for a meaningful recap button. */
    fun hasHistory(chapters: List<RecapChapter>, lastReadChapterIndex: Int): Boolean =
        lastReadChapterIndex >= 0 && chapters.any { it.index <= lastReadChapterIndex }

    private fun chapterNumberLabel(number: Double): String =
        if (number % 1.0 == 0.0) number.toInt().toString() else number.toString()
}