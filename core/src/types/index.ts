/**
 * Yomira Core Domain Models & Types
 * Universal Comic & Manga Platform Engine
 */

export type SeriesType = 'MANGA' | 'MANHWA' | 'MANHUA' | 'COMIC' | 'WEBTOON';

export type SeriesStatus = 'ONGOING' | 'COMPLETED' | 'HIATUS' | 'CANCELLED';

/**
 * 3-Tier Granular Content Rating System:
 * - SAFE: All-ages, Shounen, Superhero, Action, Comedy.
 * - MATURE_ECCHI: Story-driven series with heavy sexuality, nudity, or adult themes (e.g. Hero Moms, I Formed a MILF Party, Berserk).
 * - PORNOGRAPHIC_18: Pure adult/erotic content (e.g. Toonily pornographic webtoons, Hentai).
 */
export type ContentRating = 'SAFE' | 'MATURE_ECCHI' | 'PORNOGRAPHIC_18';

export interface SeriesMetadataMapping {
  providerId: string;
  externalId: string;
  sourceUrl?: string;
  lastSyncedAt: number;
}

export interface Series {
  id: string; // Universal Yomira Canonical UUID
  canonicalTitle: string;
  originalTitle?: string;
  aliases: string[]; // Alternate titles in JP, KR, EN, TR for search
  description: string;
  coverUrl: string; // High-res 2:3 vertical cover art
  bannerUrl?: string;
  type: SeriesType;
  status: SeriesStatus;
  contentRating: ContentRating;
  isAdult: boolean; // True only if PORNOGRAPHIC_18
  genres: string[];
  tags: string[];
  author?: string;
  artist?: string;
  year?: number;
  rating?: number; // 0.0 to 5.0 scale
  totalChapters?: number;
  metadataSources: SeriesMetadataMapping[];
  createdAt: number;
  updatedAt: number;
}

export interface Chapter {
  id: string;
  seriesId: string;
  chapterNumber: number;
  volumeNumber?: number;
  title?: string;
  releaseDate?: string;
  scanlationGroup?: string;
  language: string; // e.g. 'en', 'tr'
  pageCount?: number;
  sourceUrl?: string;
  extensionId?: string; // If sourced from an installed extension
}

export interface Page {
  index: number;
  imageUrl: string;
  fallbackUrls?: string[];
  headers?: Record<string, string>; // Referer, User-Agent for anti-bot
}

export interface ReadingProgress {
  seriesId: string;
  chapterId: string;
  chapterNumber: number;
  pageIndex: number;
  totalPageCount?: number;
  percentage: number;
  lastReadAt: number; // Unix timestamp
  isCompleted: boolean;
}

export interface LibraryItem {
  seriesId: string;
  category: 'READING' | 'PLAN_TO_READ' | 'COMPLETED' | 'ON_HOLD' | 'DROPPED';
  addedAt: number;
  favorite: boolean;
  customTags: string[];
  boundExtensionId?: string; // User preferred source extension
}

/**
 * Extension / Plugin System
 */
export interface ExtensionManifest {
  id: string; // e.g. 'com.yomira.mangadex'
  name: string;
  version: string;
  iconUrl: string;
  author: string;
  description: string;
  baseUrl: string;
  supportedLanguages: string[];
  isNsfw: boolean;
  repoUrl: string;
}

export interface ExtensionSource {
  manifest: ExtensionManifest;
  search(query: string, page?: number): Promise<Series[]>;
  getSeriesDetails(externalId: string): Promise<Partial<Series>>;
  getChapters(externalId: string): Promise<Chapter[]>;
  getChapterPages(chapterId: string): Promise<Page[]>;
}

/**
 * Google Drive Sync Payload
 * Stored in user's private Google Drive `appDataFolder` as `yomira_sync_v1.json`
 */
export interface GoogleDriveSyncPayload {
  version: number;
  deviceId: string;
  deviceName: string;
  lastSyncedAt: number;
  library: LibraryItem[];
  progress: ReadingProgress[];
  installedRepoUrls: string[];
  pinnedExtensions: Record<string, string>; // seriesId -> extensionId
  userSettings: {
    enableAdultContent18: boolean;
    requireAdultPin: boolean;
    adultPinHash?: string;
    readerMode: 'WEBTOON_VERTICAL' | 'PAGED_SINGLE' | 'PAGED_DOUBLE';
    readerDirection: 'LEFT_TO_RIGHT' | 'RIGHT_TO_LEFT' | 'VERTICAL';
    theme: 'OBSIDIAN_VIOLET' | 'CINEMATIC_ECLIPSE';
  };
}
