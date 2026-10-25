package com.yomira.domain.journey.recap

/**
 * One chapter's share of the "what happened so far" story.
 *
 * The reader builds these from what it legitimately knows - chapter titles and
 * any locally stored chapter notes - never from chapters the user has not
 * reached. [chapterNumber] is what makes the spoiler guard possible.
 */
data class RecapChapter(
    val chapterNumber: Double,
    val title: String? = null,
    val beats: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
) {
    init {
        require(chapterNumber.isFinite() && chapterNumber >= 0) { "Chapter number must be finite and non-negative" }
    }
}

/**
 * Everything the engine needs about the return to a series.
 *
 * [readThroughChapter] is the spoiler horizon: the last chapter the user has
 * actually finished. Everything strictly after it is withheld - including its
 * title, its events and any character it introduces. A negative horizon
 * expresses "nothing has been finished yet", which yields an empty recap.
 */
data class RecapRequest(
    val seriesTitle: String,
    val readThroughChapter: Double,
    val chapters: List<RecapChapter>,
    val premise: String? = null,
    val resumeChapter: Double? = null,
    val lastReadAt: Long = 0L,
    val now: Long = 0L,
) {
    init {
        require(seriesTitle.isNotBlank()) { "Series title must not be blank" }
        require(readThroughChapter.isFinite()) { "Spoiler horizon must be finite" }
    }
}

/**
 * The recap itself. Every string in here comes from a chapter at or before the
 * spoiler horizon; [withheldChapters] counts what the guard kept back so the
 * UI can say "there is more, but it would spoil you".
 */
data class SpoilerFreeRecap(
    val seriesTitle: String,
    val coveredThrough: Double,
    val storyBeats: List<String>,
    val characters: List<String>,
    val premise: String?,
    val resumeChapter: Double?,
    val withheldChapters: Int,
)

/**
 * Builds the "Geçmiş Olayları Hatırla" recap for a series the user returns to
 * after a break.
 *
 * The contract is absolute: content is emitted only for chapters with
 * `chapterNumber <= readThroughChapter`. A character that first appears after
 * the horizon never shows up in the recap, and neither does the title of an
 * unread chapter. The engine is pure Kotlin with no I/O - the caller supplies
 * the chapter notes, so nothing leaves the device and no network round-trip is
 * needed to remember your own reading.
 */
object SpoilerFreeRecapEngine {

    /** A break longer than this makes the recap worth offering. */
    const val LONG_BREAK_MILLIS: Long = 7L * 24 * 60 * 60 * 1000

    /** True when the user has been away long enough to benefit from a recap. */
    fun shouldOffer(lastReadAt: Long, now: Long, thresholdMillis: Long = LONG_BREAK_MILLIS): Boolean {
        if (lastReadAt <= 0L || now <= 0L) return false
        return now - lastReadAt >= thresholdMillis
    }

    fun build(request: RecapRequest): SpoilerFreeRecap {
        val horizon = request.readThroughChapter
        val sorted = request.chapters.sortedBy { it.chapterNumber }
        val covered = sorted.filter { it.chapterNumber <= horizon }
        val withheld = sorted.count { it.chapterNumber > horizon }

        val beats = ArrayList<String>()
        val characters = ArrayList<String>()
        val seenCharacters = HashSet<String>()
        for (chapter in covered) {
            val heading = chapter.title?.takeIf { it.isNotBlank() }
            for (beat in chapter.beats) {
                val text = beat.trim()
                if (text.isEmpty()) continue
                beats += if (heading != null) "$heading - $text" else text
            }
            for (character in chapter.characters) {
                val name = character.trim()
                if (name.isEmpty() || !seenCharacters.add(name.lowercase())) continue
                characters += name
            }
            // A chapter without explicit beats still counts: its title alone is
            // a safe, spoiler-free landmark of where the story has been.
            if (heading != null && chapter.beats.none { it.isNotBlank() }) {
                beats += heading
            }
        }

        return SpoilerFreeRecap(
            seriesTitle = request.seriesTitle,
            coveredThrough = horizon,
            storyBeats = beats,
            characters = characters,
            premise = request.premise?.trim()?.takeIf { it.isNotEmpty() },
            resumeChapter = request.resumeChapter?.takeIf { it.isFinite() && it >= 0 },
            withheldChapters = withheld,
        )
    }
}