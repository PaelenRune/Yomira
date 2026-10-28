// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors the Mihon app's SChapterImpl (the runtime implementation behind
// SChapter.create()); not referenced directly by the audited DEXes.
@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.json.JsonObject

class SChapterImpl : SChapter {

    override lateinit var url: String

    override lateinit var name: String

    override var chapter_number: Float = -1f

    override var scanlator: String? = null

    override var date_upload: Long = 0

    override var memo: JsonObject = JsonObject(emptyMap())
}
