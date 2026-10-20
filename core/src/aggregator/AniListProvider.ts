import { MetadataProvider } from './provider.js';
import { Series, SeriesType, ContentRating } from '../types/index.js';

export class AniListProvider implements MetadataProvider {
  readonly id = 'anilist';
  readonly name = 'AniList Global Catalog';
  readonly supportedTypes: SeriesType[] = ['MANGA', 'MANHWA', 'MANHUA', 'WEBTOON'];
  readonly priority = 1; // Primary for Asian Comics

  private readonly endpoint = 'https://graphql.anilist.co';

  private mapCountryToType(country?: string): SeriesType {
    if (country === 'KR') return 'MANHWA';
    if (country === 'CN') return 'MANHUA';
    return 'MANGA';
  }

  private mapContentRating(isAdult: boolean, genres: string[], tags: string[]): ContentRating {
    if (isAdult) {
      // Check if it's pure hentai/adult or mature ecchi
      const isPureHentai = genres.includes('Hentai') || tags.some(t => t.toLowerCase().includes('erotica') || t.toLowerCase().includes('hentai'));
      return isPureHentai ? 'PORNOGRAPHIC_18' : 'MATURE_ECCHI';
    }
    const hasMatureThemes = genres.includes('Ecchi') || tags.some(t => 
      ['milf', 'fanservice', 'nudity', 'sexual violence', 'gore'].some(kw => t.toLowerCase().includes(kw))
    );
    return hasMatureThemes ? 'MATURE_ECCHI' : 'SAFE';
  }

  async search(query: string, limit = 20): Promise<Series[]> {
    const gql = `
      query ($search: String, $perPage: Int) {
        Page(page: 1, perPage: $perPage) {
          media(search: $search, type: MANGA, sort: SEARCH_MATCH) {
            id
            title {
              romaji
              english
              native
            }
            synonyms
            description(asHtml: false)
            coverImage {
              extraLarge
              large
            }
            bannerImage
            countryOfOrigin
            status
            isAdult
            genres
            tags {
              name
              rank
              isMediaSpoiler
            }
            startDate {
              year
            }
            averageScore
            chapters
            staff(perPage: 3) {
              edges {
                role
                node {
                  name {
                    full
                  }
                }
              }
            }
          }
        }
      }
    `;

    try {
      const res = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ query: gql, variables: { search: query, perPage: limit } })
      });

      if (!res.ok) return [];
      const json = await res.json() as any;
      const mediaList = json.data?.Page?.media || [];

      return mediaList.map((m: any) => this.mapMediaToSeries(m));
    } catch {
      return [];
    }
  }

  async getById(externalId: string): Promise<Series | null> {
    const gql = `
      query ($id: Int) {
        Media(id: $id, type: MANGA) {
          id
          title {
            romaji
            english
            native
          }
          synonyms
          description(asHtml: false)
          coverImage {
            extraLarge
            large
          }
          bannerImage
          countryOfOrigin
          status
          isAdult
          genres
          tags {
            name
          }
          startDate {
            year
          }
          averageScore
          chapters
          staff(perPage: 3) {
            edges {
              role
              node {
                name {
                  full
                }
              }
            }
          }
        }
      }
    `;

    try {
      const res = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ query: gql, variables: { id: parseInt(externalId, 10) } })
      });

      if (!res.ok) return null;
      const json = await res.json() as any;
      const m = json.data?.Media;
      if (!m) return null;

      return this.mapMediaToSeries(m);
    } catch {
      return null;
    }
  }

  async getTrending(limit = 20, type?: SeriesType): Promise<Series[]> {
    const gql = `
      query ($perPage: Int) {
        Page(page: 1, perPage: $perPage) {
          media(type: MANGA, sort: TRENDING_DESC) {
            id
            title {
              romaji
              english
              native
            }
            synonyms
            description(asHtml: false)
            coverImage {
              extraLarge
              large
            }
            bannerImage
            countryOfOrigin
            status
            isAdult
            genres
            tags {
              name
            }
            startDate {
              year
            }
            averageScore
            chapters
            staff(perPage: 3) {
              edges {
                role
                node {
                  name {
                    full
                  }
                }
              }
            }
          }
        }
      }
    `;

    try {
      const res = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body: JSON.stringify({ query: gql, variables: { perPage: limit } })
      });

      if (!res.ok) return [];
      const json = await res.json() as any;
      const mediaList = json.data?.Page?.media || [];

      let series = mediaList.map((m: any) => this.mapMediaToSeries(m));
      if (type) {
        series = series.filter((s: Series) => s.type === type);
      }
      return series;
    } catch {
      return [];
    }
  }

  private mapMediaToSeries(m: any): Series {
    const canonical = m.title?.english || m.title?.romaji || m.title?.native || 'Unknown Title';
    const aliases = [
      m.title?.romaji,
      m.title?.english,
      m.title?.native,
      ...(m.synonyms || [])
    ].filter((t): t is string => Boolean(t) && t !== canonical);

    const genres: string[] = m.genres || [];
    const tags: string[] = (m.tags || []).map((t: any) => t.name).filter(Boolean);
    const contentRating = this.mapContentRating(Boolean(m.isAdult), genres, tags);

    const authorEdge = m.staff?.edges?.find((e: any) => 
      e.role?.toLowerCase().includes('story') || e.role?.toLowerCase().includes('original')
    );
    const artistEdge = m.staff?.edges?.find((e: any) => 
      e.role?.toLowerCase().includes('art') || e.role?.toLowerCase().includes('illustration')
    );

    return {
      id: `yomira-al-${m.id}`,
      canonicalTitle: canonical,
      originalTitle: m.title?.native || undefined,
      aliases: Array.from(new Set(aliases)),
      description: m.description || '',
      coverUrl: m.coverImage?.extraLarge || m.coverImage?.large || '',
      bannerUrl: m.bannerImage || undefined,
      type: this.mapCountryToType(m.countryOfOrigin),
      status: m.status === 'FINISHED' ? 'COMPLETED' : m.status === 'CANCELLED' ? 'CANCELLED' : 'ONGOING',
      contentRating,
      isAdult: contentRating === 'PORNOGRAPHIC_18',
      genres,
      tags,
      author: authorEdge?.node?.name?.full,
      artist: artistEdge?.node?.name?.full,
      year: m.startDate?.year,
      rating: m.averageScore ? Math.round((m.averageScore / 20) * 10) / 10 : undefined, // Convert 100-score to 5-star
      totalChapters: m.chapters || undefined,
      metadataSources: [{
        providerId: this.id,
        externalId: String(m.id),
        sourceUrl: `https://anilist.co/manga/${m.id}`,
        lastSyncedAt: Date.now()
      }],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
  }
}
