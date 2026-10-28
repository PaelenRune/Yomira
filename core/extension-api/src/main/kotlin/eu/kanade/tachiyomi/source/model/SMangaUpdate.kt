// API compatibility shim (Yomira, module :core:extension-api).
// Mirrors keiyoushi/extensions-lib v1.6 (commit 42255ee5fa) SMangaUpdate ABI.
package eu.kanade.tachiyomi.source.model

@Suppress("Unused")
class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
