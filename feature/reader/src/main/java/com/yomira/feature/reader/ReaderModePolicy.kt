package com.yomira.feature.reader

import com.yomira.core.model.ReadingMode

/** Which render pipeline a chapter should use. */
enum class ReaderPipeline { WEBTOON, PAGER, GUIDED, DUAL }

/** User-facing switches of the reader, all optional with safe defaults. */
data class ReaderPreferences(
    /** Akıllı Panel (Guided View) is on. */
    val guidedView: Boolean = false,
    /** Çift sayfa birleştirme is on. */
    val dualPage: Boolean = false,
    /** Device shows enough width for two pages (tablet / landscape phone). */
    val wideViewport: Boolean = false,
)

/**
 * Chooses the render pipeline for a chapter.
 *
 * - Webtoon chapters always use the vertical strip reader.
 * - Guided View wins over everything else on paged modes: its panel detection
 *   is designed for manga and western pages.
 * - Dual-page stitching only engages on wide viewports in paged modes; on a
 *   narrow phone it is silently ignored so pages stay readable.
 */
object ReaderModePolicy {

    fun pipeline(mode: ReadingMode, preferences: ReaderPreferences): ReaderPipeline = when (mode) {
        ReadingMode.WEBTOON -> ReaderPipeline.WEBTOON
        else -> when {
            preferences.guidedView -> ReaderPipeline.GUIDED
            preferences.dualPage && preferences.wideViewport -> ReaderPipeline.DUAL
            else -> ReaderPipeline.PAGER
        }
    }

    /** Panel reading direction implied by the chapter's reading mode. */
    fun readsRightToLeft(mode: ReadingMode): Boolean = mode == ReadingMode.MANGA_RTL

    /** Two-up display only makes sense for paged chapters on wide viewports. */
    fun supportsDualPage(mode: ReadingMode, wideViewport: Boolean): Boolean =
        mode != ReadingMode.WEBTOON && wideViewport

    /** Guided View operates on panels, which webtoon strips do not have. */
    fun supportsGuidedView(mode: ReadingMode): Boolean = mode != ReadingMode.WEBTOON

    /**
     * Seam cleanup runs for webtoon strips only; paged modes must keep their
     * page borders intact so panel detection and page counting stay aligned.
     */
    fun shouldCleanSeams(mode: ReadingMode): Boolean = mode == ReadingMode.WEBTOON
}