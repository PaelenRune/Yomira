import { ContentRating } from '../types/index.js';

export interface ContentMetadataInput {
  isAdult?: boolean;
  genres?: string[];
  tags?: string[];
  title?: string;
  description?: string;
}

/**
 * Yomira Smart Content Rating Classifier
 * Solves the critical distinction between:
 * 1. SAFE (All ages)
 * 2. MATURE_ECCHI (Heavy sexual themes, fanservice, nudity, gore, but story-driven: Hero Moms, MILF Party, Berserk)
 * 3. PORNOGRAPHIC_18 (Pure adult/erotic webtoons, Toonily, Hentai)
 */
export class ContentRatingClassifier {
  // Pure pornographic keywords that mandate +18 lockdown
  private static readonly HARDCORE_KEYWORDS = new Set([
    'hentai',
    'erotica',
    'pornographic',
    'smut hardcore',
    'uncensored sex',
    'porhwa',
    'toonily adult',
    'doujinshi 18+'
  ]);

  // Mature / Ecchi keywords that feature sexual themes/nudity/fanservice/gore but are NOT pure porn
  private static readonly MATURE_ECCHI_KEYWORDS = new Set([
    'ecchi',
    'fanservice',
    'nudity',
    'milf',
    'harem',
    'sexual themes',
    'gore',
    'violence',
    'psychological dark',
    'seinen mature',
    'hero moms',
    'borderline'
  ]);

  /**
   * Evaluates series metadata and returns strict ContentRating
   */
  static classify(input: ContentMetadataInput): ContentRating {
    const { isAdult = false, genres = [], tags = [], title = '', description = '' } = input;

    const lowerGenres = genres.map(g => g.toLowerCase());
    const lowerTags = tags.map(t => t.toLowerCase());
    const combinedText = `${title} ${description} ${lowerGenres.join(' ')} ${lowerTags.join(' ')}`.toLowerCase();

    // 1. Check for pure hardcore pornographic webtoon / hentai
    const isHardcore = lowerGenres.includes('hentai') ||
      Array.from(this.HARDCORE_KEYWORDS).some(kw => combinedText.includes(kw));

    if (isHardcore) {
      return 'PORNOGRAPHIC_18';
    }

    // 2. Check for Mature / Ecchi / Heavy Fanservice (Hero Moms, MILF Party, etc.)
    const isMatureOrEcchi = isAdult ||
      lowerGenres.includes('ecchi') ||
      lowerGenres.includes('smut') ||
      Array.from(this.MATURE_ECCHI_KEYWORDS).some(kw => combinedText.includes(kw));

    if (isMatureOrEcchi) {
      return 'MATURE_ECCHI';
    }

    // 3. Otherwise Safe
    return 'SAFE';
  }

  /**
   * Checks if a series should be visible under the user's current settings
   */
  static isVisible(
    seriesRating: ContentRating,
    userSettings: { enableAdultContent18: boolean }
  ): boolean {
    if (seriesRating === 'PORNOGRAPHIC_18') {
      return userSettings.enableAdultContent18;
    }
    // SAFE and MATURE_ECCHI are always in catalog, with MATURE_ECCHI marked with a discreet age badge
    return true;
  }
}
