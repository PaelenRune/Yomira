import { Series, SeriesType } from '../types/index.js';

export interface MetadataProvider {
  readonly id: string;
  readonly name: string;
  readonly supportedTypes: SeriesType[];
  readonly priority: number; // Lower number = Higher priority (e.g. 1 is highest)

  search(query: string, limit?: number): Promise<Series[]>;
  getById(externalId: string): Promise<Series | null>;
  getTrending(limit?: number, type?: SeriesType): Promise<Series[]>;
}

export interface AggregatorOptions {
  enableAdultContent?: boolean;
}
