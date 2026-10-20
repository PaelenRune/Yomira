import { MetadataProvider } from './provider.js';
import { Series, SeriesType, ContentRating } from '../types/index.js';

export class MangaDexProvider implements MetadataProvider {
  readonly id = 'mangadex';
  readonly name = 'MangaDex Universal Index';
  readonly supportedTypes: SeriesType[] = ['MANGA', 'MANHWA', 'MANHUA', 'WEBTOON'];
  readonly priority = 2; // Secondary Asian Provider & Chapter Metadata Specialist

  private readonly baseUrl = 'https://api.mangadex.org';
  private readonly coverBase = 'https://uploads.mangadex.org/covers';

  private mapContentRating(rating?: string, tags: string[] = []): ContentRating {
    if (rating === 'pornographic') return 'PORNOGRAPHIC_18';
    if (rating === 'erotica' || rating === 'suggestive') return 'MATURE_ECCHI';
    if (tags.some(t => ['ecchi', 'sexual violence', 'gore', 'smut'].includes(t.toLowerCase()))) {
      return 'MATURE_ECCHI';
    }
    return 'SAFE';
  }

  private mapLangToType(originalLang?: string): SeriesType {
    if (originalLang === 'ko') return 'MANHWA';
    if (originalLang === 'zh' || originalLang === 'zh-hk') return 'MANHUA';
    return 'MANGA';
  }

  async search(query: string, limit = 20): Promise<Series[]> {
    const url = new URL(`${this.baseUrl}/manga`);
    url.searchParams.set('title', query);
    url.searchParams.set('limit', String(limit));
    url.searchParams.set('includes[]', 'cover_art');
    url.searchParams.set('includes[]', 'author');
    url.searchParams.set('includes[]', 'artist');
    url.searchParams.append('contentRating[]', 'safe');
    url.searchParams.append('contentRating[]', 'suggestive');
    url.searchParams.append('contentRating[]', 'erotica');
    url.searchParams.append('contentRating[]', 'pornographic');

    try {
      const res = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json', 'User-Agent': 'YomiraReader/1.0' }
      });
      if (!res.ok) return [];
      const json = await res.json() as any;
      const data = json.data || [];

      return data.map((m: any) => this.mapMangaToSeries(m));
    } catch {
      return [];
    }
  }

  async getById(externalId: string): Promise<Series | null> {
    const url = new URL(`${this.baseUrl}/manga/${externalId}`);
    url.searchParams.set('includes[]', 'cover_art');
    url.searchParams.set('includes[]', 'author');
    url.searchParams.set('includes[]', 'artist');

    try {
      const res = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json', 'User-Agent': 'YomiraReader/1.0' }
      });
      if (!res.ok) return null;
      const json = await res.json() as any;
      if (!json.data) return null;

      return this.mapMangaToSeries(json.data);
    } catch {
      return null;
    }
  }

  async getTrending(limit = 20, type?: SeriesType): Promise<Series[]> {
    const url = new URL(`${this.baseUrl}/manga`);
    url.searchParams.set('limit', String(limit));
    url.searchParams.set('order[followedCount]', 'desc');
    url.searchParams.set('includes[]', 'cover_art');
    url.searchParams.set('includes[]', 'author');
    url.searchParams.set('includes[]', 'artist');
    url.searchParams.append('contentRating[]', 'safe');
    url.searchParams.append('contentRating[]', 'suggestive');

    try {
      const res = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json', 'User-Agent': 'YomiraReader/1.0' }
      });
      if (!res.ok) return [];
      const json = await res.json() as any;
      const data = json.data || [];

      let list = data.map((m: any) => this.mapMangaToSeries(m));
      if (type) {
        list = list.filter((s: Series) => s.type === type);
      }
      return list;
    } catch {
      return [];
    }
  }

  private mapMangaToSeries(m: any): Series {
    const attrs = m.attributes || {};
    const titleObj = attrs.title || {};
    const canonical = titleObj.en || Object.values(titleObj)[0] || 'Unknown Title';

    const altTitles: string[] = (attrs.altTitles || []).map((t: any) => Object.values(t)[0]).filter(Boolean) as string[];
    const aliases = Array.from(new Set(altTitles)).filter(a => a !== canonical);

    const descObj = attrs.description || {};
    const description = descObj.en || Object.values(descObj)[0] || '';

    // Cover art relationship
    const coverRel = m.relationships?.find((r: any) => r.type === 'cover_art');
    const coverFileName = coverRel?.attributes?.fileName;
    const coverUrl = coverFileName 
      ? `${this.coverBase}/${m.id}/${coverFileName}.512.jpg`
      : '';

    // Author & Artist
    const authorRel = m.relationships?.find((r: any) => r.type === 'author');
    const artistRel = m.relationships?.find((r: any) => r.type === 'artist');

    const tags = (attrs.tags || []).map((t: any) => t.attributes?.name?.en).filter(Boolean);
    const contentRating = this.mapContentRating(attrs.contentRating, tags);

    return {
      id: `yomira-md-${m.id}`,
      canonicalTitle: canonical,
      aliases,
      description,
      coverUrl,
      type: this.mapLangToType(attrs.originalLanguage),
      status: attrs.status === 'completed' ? 'COMPLETED' : attrs.status === 'cancelled' ? 'CANCELLED' : 'ONGOING',
      contentRating,
      isAdult: contentRating === 'PORNOGRAPHIC_18',
      genres: tags.slice(0, 5),
      tags,
      author: authorRel?.attributes?.name,
      artist: artistRel?.attributes?.name,
      year: attrs.year || undefined,
      totalChapters: attrs.lastChapter ? parseFloat(attrs.lastChapter) : undefined,
      metadataSources: [{
        providerId: this.id,
        externalId: m.id,
        sourceUrl: `https://mangadex.org/title/${m.id}`,
        lastSyncedAt: Date.now()
      }],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
  }
}
