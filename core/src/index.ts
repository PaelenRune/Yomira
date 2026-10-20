/**
 * @yomira/core
 * Universal Comic & Manga Engine with Multi-Device Sync
 */

// Domain Types
export * from './types/index.js';

// Cascading Metadata Aggregator & Providers
export * from './aggregator/provider.js';
export * from './aggregator/AniListProvider.js';
export * from './aggregator/MangaDexProvider.js';
export * from './aggregator/ComicVineProvider.js';
export * from './aggregator/CascadingAggregator.js';

// Smart Content Rating & +18 Classifier
export * from './classifier/ContentRatingClassifier.js';

// Google Drive Personal Cloud Sync
export * from './sync/GoogleDriveSyncService.js';

// Extension & Source Resolver
export * from './resolver/SourceResolver.js';

// Sandboxed Community Extension Runtime
export * from './runtime/BaseExtension.js';
export * from './runtime/ExtensionRuntime.js';
