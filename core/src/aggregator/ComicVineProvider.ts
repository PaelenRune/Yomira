import { MetadataProvider } from './provider.js';
import { Series, SeriesType } from '../types/index.js';

export class ComicVineProvider implements MetadataProvider {
  readonly id = 'comicvine';
  readonly name = 'ComicVine Western Catalog (Marvel/DC/Dark Horse)';
  readonly supportedTypes: SeriesType[] = ['COMIC'];
  readonly priority = 1; // Primary for Western Comics

  private apiKey?: string;

  constructor(apiKey?: string) {
    this.apiKey = apiKey;
  }

  setApiKey(key: string) {
    this.apiKey = key;
  }

  async search(query: string, limit = 20): Promise<Series[]> {
    if (!this.apiKey) {
      // Offline/Default curated Western Comics index when API key is not configured
      return this.searchCuratedWesternIndex(query, limit);
    }

    const url = new URL('https://comicvine.gamespot.com/api/volumes/');
    url.searchParams.set('api_key', this.apiKey);
    url.searchParams.set('format', 'json');
    url.searchParams.set('filter', `name:${query}`);
    url.searchParams.set('limit', String(limit));

    try {
      const res = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json', 'User-Agent': 'YomiraComicApp/1.0' }
      });
      if (!res.ok) return this.searchCuratedWesternIndex(query, limit);
      const json = await res.json() as any;
      const results = json.results || [];

      return results.map((v: any) => this.mapVolumeToSeries(v));
    } catch {
      return this.searchCuratedWesternIndex(query, limit);
    }
  }

  async getById(externalId: string): Promise<Series | null> {
    if (!this.apiKey) return null;
    const url = new URL(`https://comicvine.gamespot.com/api/volume/4050-${externalId}/`);
    url.searchParams.set('api_key', this.apiKey);
    url.searchParams.set('format', 'json');

    try {
      const res = await fetch(url.toString(), {
        headers: { 'Accept': 'application/json', 'User-Agent': 'YomiraComicApp/1.0' }
      });
      if (!res.ok) return null;
      const json = await res.json() as any;
      if (!json.results) return null;

      return this.mapVolumeToSeries(json.results);
    } catch {
      return null;
    }
  }

  async getTrending(limit = 20): Promise<Series[]> {
    return this.searchCuratedWesternIndex('', limit);
  }

  private mapVolumeToSeries(v: any): Series {
    return {
      id: `yomira-cv-${v.id}`,
      canonicalTitle: v.name || 'Unknown Comic',
      aliases: v.aliases ? v.aliases.split('\n') : [],
      description: v.deck || v.description || '',
      coverUrl: v.image?.super_url || v.image?.medium_url || '',
      type: 'COMIC',
      status: 'COMPLETED',
      contentRating: 'SAFE',
      isAdult: false,
      genres: ['Superhero', 'Action', 'Adventure'],
      tags: [v.publisher?.name || 'Comics', 'Western'],
      author: v.publisher?.name,
      year: v.start_year ? parseInt(v.start_year, 10) : undefined,
      totalChapters: v.count_of_issues || undefined,
      metadataSources: [{
        providerId: this.id,
        externalId: String(v.id),
        sourceUrl: v.site_detail_url,
        lastSyncedAt: Date.now()
      }],
      createdAt: Date.now(),
      updatedAt: Date.now()
    };
  }

  /**
   * Curated Western / Marvel / DC Comics Catalog
   * Available immediately out-of-the-box without requiring user API keys
   */
  private searchCuratedWesternIndex(query: string, limit: number): Series[] {
    const curated: Series[] = [
      {
        id: 'yomira-cv-curated-1',
        canonicalTitle: 'The Amazing Spider-Man',
        aliases: ['Spider-Man', 'Amazing Spider-Man (2022)'],
        description: 'Peter Parker returns with groundbreaking adventures across the Marvel Universe. High-stakes superhero action and personal drama.',
        coverUrl: 'https://images.unsplash.com/photo-1635863138275-d9b33299680b?w=600&auto=format&fit=crop&q=80',
        type: 'COMIC',
        status: 'ONGOING',
        contentRating: 'SAFE',
        isAdult: false,
        genres: ['Superhero', 'Action', 'Sci-Fi'],
        tags: ['Marvel Comics', 'Spider-Verse', 'Stan Lee Legacy'],
        author: 'Zeb Wells',
        artist: 'John Romita Jr.',
        year: 2022,
        rating: 4.7,
        totalChapters: 60,
        metadataSources: [{ providerId: this.id, externalId: 'asm-2022', lastSyncedAt: Date.now() }],
        createdAt: Date.now(),
        updatedAt: Date.now()
      },
      {
        id: 'yomira-cv-curated-2',
        canonicalTitle: 'Batman: The Dark Knight Returns',
        aliases: ['The Dark Knight Returns', 'Batman TDKR'],
        description: 'Frank Miller revolutionized Batman with this gritty, futuristic masterpiece of an aging Bruce Wayne donning the cape one last time.',
        coverUrl: 'https://images.unsplash.com/photo-1531259683007-016a7b628fc3?w=600&auto=format&fit=crop&q=80',
        type: 'COMIC',
        status: 'COMPLETED',
        contentRating: 'MATURE_ECCHI', // Dark, violent, mature themes
        isAdult: false,
        genres: ['Superhero', 'Noir', 'Psychological'],
        tags: ['DC Comics', 'Batman', 'Classic', 'Frank Miller'],
        author: 'Frank Miller',
        artist: 'Klaus Janson',
        year: 1986,
        rating: 4.9,
        totalChapters: 4,
        metadataSources: [{ providerId: this.id, externalId: 'batman-tdkr', lastSyncedAt: Date.now() }],
        createdAt: Date.now(),
        updatedAt: Date.now()
      },
      {
        id: 'yomira-cv-curated-3',
        canonicalTitle: 'Invincible',
        aliases: ['Invincible (Image Comics)', 'Mark Grayson'],
        description: 'Mark Grayson is just a normal teenager whose father is the most powerful superhero on the planet Omni-Man. Then his own powers manifest.',
        coverUrl: 'https://images.unsplash.com/photo-1563089145-599997674d42?w=600&auto=format&fit=crop&q=80',
        type: 'COMIC',
        status: 'COMPLETED',
        contentRating: 'MATURE_ECCHI', // Heavy gore & violence
        isAdult: false,
        genres: ['Superhero', 'Action', 'Gore', 'Drama'],
        tags: ['Image Comics', 'Robert Kirkman', 'Omni-Man'],
        author: 'Robert Kirkman',
        artist: 'Cory Walker, Ryan Ottley',
        year: 2003,
        rating: 4.9,
        totalChapters: 144,
        metadataSources: [{ providerId: this.id, externalId: 'invincible-image', lastSyncedAt: Date.now() }],
        createdAt: Date.now(),
        updatedAt: Date.now()
      }
    ];

    if (!query) return curated.slice(0, limit);
    const q = query.toLowerCase();
    return curated.filter(c => 
      c.canonicalTitle.toLowerCase().includes(q) ||
      c.aliases.some(a => a.toLowerCase().includes(q)) ||
      c.tags.some(t => t.toLowerCase().includes(q))
    ).slice(0, limit);
  }
}
