import { Series, Chapter, Page, ExtensionSource } from '../types/index.js';

export interface ResolvedSourceResult {
  extensionId: string;
  extensionName: string;
  matchedSeriesTitle: string;
  chapters: Chapter[];
  isAvailable: boolean;
}

/**
 * Yomira Smart Source Resolver
 * Bridges the gap between the Universal Metadata Catalog and installed site extensions.
 * When a user views a series in the catalog, this resolver searches installed extensions,
 * automatically matches titles/aliases, and provides readable chapters.
 */
export class SourceResolver {
  private installedSources: Map<string, ExtensionSource> = new Map();

  registerSource(source: ExtensionSource) {
    this.installedSources.set(source.manifest.id, source);
  }

  unregisterSource(extensionId: string) {
    this.installedSources.delete(extensionId);
  }

  getAvailableSources(): ExtensionSource[] {
    return Array.from(this.installedSources.values());
  }

  /**
   * Discovers which installed extensions have readable chapters for this series
   */
  async resolveSeriesAcrossSources(series: Series): Promise<ResolvedSourceResult[]> {
    const results: ResolvedSourceResult[] = [];
    const searchQueries = [series.canonicalTitle, ...series.aliases.slice(0, 2)];

    for (const [id, source] of this.installedSources.entries()) {
      try {
        let matchedSeriesId: string | undefined;
        let matchedTitle: string | undefined;

        // Try searching by canonical title and aliases
        for (const query of searchQueries) {
          const searchHits = await source.search(query, 5);
          const hit = searchHits.find(h => 
            this.fuzzyMatch(h.canonicalTitle, series.canonicalTitle) ||
            h.aliases.some(a => this.fuzzyMatch(a, series.canonicalTitle))
          );

          if (hit) {
            matchedSeriesId = hit.id;
            matchedTitle = hit.canonicalTitle;
            break;
          }
        }

        if (matchedSeriesId) {
          const chapters = await source.getChapters(matchedSeriesId);
          results.push({
            extensionId: id,
            extensionName: source.manifest.name,
            matchedSeriesTitle: matchedTitle || series.canonicalTitle,
            chapters,
            isAvailable: chapters.length > 0
          });
        }
      } catch {
        // Individual extension failure does not crash the resolver
      }
    }

    return results;
  }

  /**
   * Fetches pages for a specific chapter through its bound extension
   */
  async getPagesForChapter(extensionId: string, chapterId: string): Promise<Page[]> {
    const source = this.installedSources.get(extensionId);
    if (!source) {
      throw new Error(`Extension source with ID '${extensionId}' is not installed.`);
    }

    return source.getChapterPages(chapterId);
  }

  private fuzzyMatch(strA: string, strB: string): boolean {
    const cleanA = strA.toLowerCase().replace(/[^a-z0-9]/g, '');
    const cleanB = strB.toLowerCase().replace(/[^a-z0-9]/g, '');
    return cleanA === cleanB || cleanA.includes(cleanB) || cleanB.includes(cleanA);
  }
}
