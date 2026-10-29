package com.yomira.feature.details

import com.yomira.core.model.AdaptationKind
import com.yomira.core.model.WatchableAdaptation
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailAdaptationsTest {
    @Test fun `empty metadata adds no badges`() {
        assertEquals(emptyList<AdaptationKind>(), detailAdaptationKinds(emptyList()))
    }

    @Test fun `seasons deduplicate and provider order cannot reorder summary`() {
        val entries = listOf(AdaptationKind.DONGHUA, AdaptationKind.ANIME, AdaptationKind.ANIME,
            AdaptationKind.HENTAI_OVA, AdaptationKind.LIVE_ACTION).mapIndexed { index, kind ->
            WatchableAdaptation("$index", "Season $index", kind)
        }
        val expected = listOf(AdaptationKind.ANIME, AdaptationKind.HENTAI_OVA,
            AdaptationKind.LIVE_ACTION, AdaptationKind.DONGHUA)
        assertEquals(expected, detailAdaptationKinds(entries))
        assertEquals(expected, detailAdaptationKinds(entries.reversed()))
    }
}
