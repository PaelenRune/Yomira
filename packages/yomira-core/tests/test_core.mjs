import { 
  CascadingAggregator, 
  ContentRatingClassifier, 
  GoogleDriveSyncService 
} from '../dist/index.js';

async function runCoreTests() {
  console.log('=== [YOMIRA CORE ENGINE INTEGRATION TEST] ===\n');

  // TEST 1: Content Rating Classifier
  console.log('-> 1. Testing Content Rating & +18 Separation:');
  const testItems = [
    {
      title: 'One Piece',
      genres: ['Action', 'Adventure'],
      tags: ['Pirates', 'Shounen'],
      expected: 'SAFE'
    },
    {
      title: 'Hero Moms',
      genres: ['Ecchi', 'Comedy'],
      tags: ['MILF', 'Superheroes', 'Fanservice'],
      expected: 'MATURE_ECCHI'
    },
    {
      title: 'I Formed a MILF Party',
      genres: ['Fantasy', 'Ecchi', 'Adventure'],
      tags: ['Harem', 'Adult Humor'],
      expected: 'MATURE_ECCHI'
    },
    {
      title: 'Secret Class (Adult Toonily)',
      genres: ['Hentai', 'Romance'],
      tags: ['Pornographic', 'Uncensored'],
      expected: 'PORNOGRAPHIC_18'
    }
  ];

  for (const item of testItems) {
    const rating = ContentRatingClassifier.classify({
      title: item.title,
      genres: item.genres,
      tags: item.tags
    });
    const passed = rating === item.expected;
    console.log(`   [${passed ? 'PASS' : 'FAIL'}] "${item.title}" -> Classified as: ${rating} (Expected: ${item.expected})`);
    if (!passed) throw new Error(`Classification failed for ${item.title}`);
  }

  // TEST 2: Cascading Aggregator
  console.log('\n-> 2. Testing Cascading Aggregator (AniList + MangaDex + ComicVine):');
  const aggregator = new CascadingAggregator();
  console.log('   Fetching trending series across all providers...');
  const trending = await aggregator.search({ limit: 6, maxContentRating: 'SAFE' });
  console.log(`   Found ${trending.length} merged series.`);
  for (const s of trending.slice(0, 3)) {
    console.log(`   - [${s.type}] "${s.canonicalTitle}" | Rating: ${s.rating || 'N/A'}★ | Sources: ${s.metadataSources.map(m => m.providerId).join(', ')}`);
  }

  // TEST 3: Google Drive Sync Conflict Resolver
  console.log('\n-> 3. Testing Google Drive Multi-Device Sync Conflict Resolver:');
  const sync = new GoogleDriveSyncService();

  const phonePayload = {
    version: 1,
    deviceId: 'android-phone-001',
    deviceName: 'Pixel 8',
    lastSyncedAt: 1000,
    library: [
      { seriesId: 'solo-leveling', category: 'READING', addedAt: 100, favorite: true, customTags: [] }
    ],
    progress: [
      { seriesId: 'solo-leveling', chapterId: 'ch-42', chapterNumber: 42, pageIndex: 15, percentage: 50, lastReadAt: 2000, isCompleted: false }
    ],
    installedRepoUrls: ['https://raw.githubusercontent.com/yomira/repo-main/index.json'],
    pinnedExtensions: {},
    userSettings: {
      enableAdultContent18: false,
      requireAdultPin: true,
      readerMode: 'WEBTOON_VERTICAL',
      readerDirection: 'VERTICAL',
      theme: 'OBSIDIAN_VIOLET'
    }
  };

  const pcPayload = {
    version: 1,
    deviceId: 'pc-desktop-001',
    deviceName: 'Windows Desktop',
    lastSyncedAt: 900,
    library: [
      { seriesId: 'chainsaw-man', category: 'READING', addedAt: 150, favorite: false, customTags: [] }
    ],
    progress: [
      { seriesId: 'solo-leveling', chapterId: 'ch-40', chapterNumber: 40, pageIndex: 5, percentage: 10, lastReadAt: 1500, isCompleted: false }
    ],
    installedRepoUrls: ['https://raw.githubusercontent.com/yomira/repo-western/index.json'],
    pinnedExtensions: {},
    userSettings: {
      enableAdultContent18: false,
      requireAdultPin: true,
      readerMode: 'PAGED_DOUBLE',
      readerDirection: 'RIGHT_TO_LEFT',
      theme: 'OBSIDIAN_VIOLET'
    }
  };

  const merged = sync.mergeStates(pcPayload, phonePayload);
  const soloProgress = merged.progress.find(p => p.seriesId === 'solo-leveling');
  console.log(`   Merged library size: ${merged.library.length} items (expected: 2)`);
  console.log(`   Merged reading progress for Solo Leveling: Chapter ${soloProgress?.chapterNumber} Page ${soloProgress?.pageIndex} (expected: 42 / 15)`);
  console.log(`   Merged repo URLs count: ${merged.installedRepoUrls.length} (expected: 2)`);

  if (merged.library.length !== 2 || soloProgress?.chapterNumber !== 42 || merged.installedRepoUrls.length !== 2) {
    throw new Error('Sync conflict resolution failed.');
  }

  console.log('\n=== ALL CORE TESTS PASSED SUCCESSFULLY! ===');
}

runCoreTests().catch(err => {
  console.error('Test error:', err);
  process.exit(1);
});
