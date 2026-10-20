import { MetadataProvider } from './provider.js';
import { Series, SeriesType, ContentRating } from '../types/index.js';
import { AniListProvider } from './AniListProvider.js';
import { MangaDexProvider } from './MangaDexProvider.js';
import { ComicVineProvider } from './ComicVineProvider.js';

export interface AggregatorFilter {
  query?: string;
  type?: SeriesType;
  maxContentRating?: ContentRating; // 'SAFE' | 'MATURE_ECCHI' | 'PORNOGRAPHIC_18'
  limit?: number;
}

export class CascadingAggregator {
  private providers: MetadataProvider[] = [];

  constructor(customProviders?: MetadataProvider[]) {
    if (customProviders && customProviders.length > 0) {
      this.providers = customProviders;
    } else {
      // Default priority chain
      this.providers = [
        new AniListProvider(),   // Priority 1 for Manga/Manhwa/Manhua
        new MangaDexProvider(),  // Priority 2 & Chapter counts / covers
        new ComicVineProvider()  // Priority 1 for Western Comics
      ];
    }
  }

  registerProvider(provider: MetadataProvider) {
    this.providers.push(provider);
    this.providers.sort((a, b) => a.priority - b.priority);
  }

  /**
   * Search across all prioritized providers with Attribute-Level Merging & Deduplication
   */
  async search(filter: AggregatorFilter): Promise<Series[]> {
    const { query = '', type, maxContentRating = 'SAFE', limit = 24 } = filter;

    const applicableProviders = this.providers.filter(p => 
      !type || p.supportedTypes.includes(type)
    );

    const searchPromises = applicableProviders.map(p => 
      query ? p.search(query, limit) : p.getTrending(limit, type)
    );

    const resultsByProvider = await Promise.allSettled(searchPromises);
    const flatSeries: Series[] = [];

    for (const result of resultsByProvider) {
      if (result.status === 'fulfilled') {
        flatSeries.push(...result.value);
      }
    }

    // Deduplicate and cascade attributes
    const merged = this.deduplicateAndMerge(flatSeries);

    // Apply strict content rating filter
    return merged.filter(s => this.isAllowedRating(s.contentRating, maxContentRating)).slice(0, limit);
  }

  /**
   * Deduplicates matching series across providers and merges attributes
   * Primary provider fields take precedence; missing fields are filled by secondary providers.
   */
  private deduplicateAndMerge(seriesList: Series[]): Series[] {
    const map = new Map<string, Series>();

    for (const item of seriesList) {
      const normalizedKey = this.normalizeTitle(item.canonicalTitle);

      // Check if an alias matches an already indexed series
      let existingKey: string | undefined;
      for (const [key, existing] of map.entries()) {
        if (key === normalizedKey) {
          existingKey = key;
          break;
        }
        const matchesAlias = existing.aliases.some(a => this.normalizeTitle(a) === normalizedKey) ||
          item.aliases.some(a => this.normalizeTitle(a) === key);
        if (matchesAlias) {
          existingKey = key;
          break;
        }
      }

      if (!existingKey) {
        map.set(normalizedKey, { ...item });
      } else {
        // Cascade & merge attributes into existing
        const existing = map.get(existingKey)!;
        map.set(existingKey, this.mergeSeriesAttributes(existing, item));
      }
    }

    return Array.from(map.values());
  }

  /**
   * Merges two series objects with priority given to base, filling holes from secondary
   */
  private mergeSeriesAttributes(base: Series, secondary: Series): Series {
    return {
      ...base,
      // If base cover is missing or low quality, use secondary
      coverUrl: base.coverUrl || secondary.coverUrl,
      bannerUrl: base.bannerUrl || secondary.bannerUrl,
      description: base.description && base.description.length > secondary.description.length 
        ? base.description 
        : (secondary.description || base.description),
      author: base.author || secondary.author,
      artist: base.artist || secondary.artist,
      year: base.year || secondary.year,
      totalChapters: base.totalChapters || secondary.totalChapters,
      rating: base.rating || secondary.rating,
      aliases: Array.from(new Set([...base.aliases, ...secondary.aliases, secondary.canonicalTitle])),
      genres: Array.from(new Set([...base.genres, ...secondary.genres])),
      tags: Array.from(new Set([...base.tags, ...secondary.tags])),
      metadataSources: [...base.metadataSources, ...secondary.metadataSources]
    };
  }

  private normalizeTitle(title: string): string {
    return title.toLowerCase().replace(/[^a-z0-9]/g, '');
  }

  private isAllowedRating(seriesRating: ContentRating, maxRating: ContentRating): boolean {
    if (maxRating === 'PORNOGRAPHIC_18') return true;
    if (maxRating === 'MATURE_ECCHI') return seriesRating !== 'PORNOGRAPHIC_18';
    // 'SAFE'
    return seriesRating === 'SAFE';
  }
}
