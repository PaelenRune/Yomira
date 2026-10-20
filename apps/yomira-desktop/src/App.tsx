import React, { useState, useEffect, useMemo } from 'react';
import { 
  Series, 
  Chapter, 
  CascadingAggregator, 
  ContentRatingClassifier, 
  GoogleDriveSyncService,
  ExtensionManifest,
  LibraryItem
} from '@yomira/core';

import { Navbar } from './components/Navbar';
import { HeroBanner } from './components/HeroBanner';
import { CategoryTabs, CategoryFilter } from './components/CategoryTabs';
import { SeriesGrid } from './components/SeriesGrid';
import { SeriesDetailModal } from './components/SeriesDetailModal';
import { ReaderView } from './components/ReaderView';
import { ExtensionManagerModal } from './components/ExtensionManagerModal';

export const App: React.FC = () => {
  // Core Services
  const aggregator = useMemo(() => new CascadingAggregator(), []);
  const syncService = useMemo(() => new GoogleDriveSyncService(), []);

  // Application State
  const [seriesList, setSeriesList] = useState<Series[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [activeCategory, setActiveCategory] = useState<CategoryFilter>('ALL');
  
  // Modals & Reader
  const [selectedSeries, setSelectedSeries] = useState<Series | null>(null);
  const [readingState, setReadingState] = useState<{ series: Series; chapter: Chapter } | null>(null);
  const [isExtensionModalOpen, setIsExtensionModalOpen] = useState<boolean>(false);

  // Settings & Sync
  const [enableAdultContent, setEnableAdultContent] = useState<boolean>(false);
  const [isDriveConnected, setIsDriveConnected] = useState<boolean>(true);
  const [lastSyncedText, setLastSyncedText] = useState<string>('Şimdi');
  const [library, setLibrary] = useState<LibraryItem[]>([]);

  // Repos & Installed Extensions
  const [installedRepoUrls, setInstalledRepoUrls] = useState<string[]>([
    'https://raw.githubusercontent.com/yomira/repo-official/index.json',
    'https://raw.githubusercontent.com/yomira/repo-western/index.json'
  ]);
  const [installedExtensions, setInstalledExtensions] = useState<ExtensionManifest[]>([
    {
      id: 'mangadex',
      name: 'MangaDex Eklentisi',
      version: '1.4.2',
      iconUrl: '',
      author: 'Yomira Team',
      description: 'Manga ve Webtoon arşivi.',
      baseUrl: 'https://mangadex.org',
      supportedLanguages: ['tr', 'en'],
      isNsfw: false,
      repoUrl: ''
    }
  ]);

  // Initial Data Fetch
  useEffect(() => {
    loadSeries();
  }, [activeCategory, enableAdultContent]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      loadSeries(searchQuery);
    }, 350);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  const loadSeries = async (query = '') => {
    setLoading(true);
    try {
      const type = (activeCategory !== 'ALL' && activeCategory !== 'ADULT_18') 
        ? activeCategory 
        : undefined;

      const maxRating = enableAdultContent ? 'PORNOGRAPHIC_18' : 'MATURE_ECCHI';
      
      const results = await aggregator.search({
        query,
        type,
        maxContentRating: maxRating,
        limit: 30
      });

      // Filter adult tab specifically if chosen
      if (activeCategory === 'ADULT_18') {
        setSeriesList(results.filter(s => s.contentRating === 'PORNOGRAPHIC_18' || s.contentRating === 'MATURE_ECCHI'));
      } else {
        setSeriesList(results);
      }
    } catch {
      setSeriesList([]);
    } finally {
      setLoading(false);
    }
  };

  // Google Drive Sync simulation
  const handleDriveToggle = () => {
    if (isDriveConnected) {
      setIsDriveConnected(false);
      setLastSyncedText('Bağlantı Kesildi');
    } else {
      setIsDriveConnected(true);
      setLastSyncedText('Senkronize');
    }
  };

  const handleAdultToggle = () => {
    if (!enableAdultContent) {
      const confirmed = window.confirm(
        'Yetişkin (+18) Modu Açılıyor:\nBu işlem pornografik webtoonları ve erotik içerikleri kataloğa dahil eder. Devam etmek istiyor musunuz?'
      );
      if (confirmed) setEnableAdultContent(true);
    } else {
      setEnableAdultContent(false);
    }
  };

  const handleToggleLibrary = (series: Series) => {
    setLibrary(prev => {
      const exists = prev.some(item => item.seriesId === series.id);
      if (exists) {
        return prev.filter(item => item.seriesId !== series.id);
      } else {
        return [...prev, {
          seriesId: series.id,
          category: 'READING',
          addedAt: Date.now(),
          favorite: true,
          customTags: []
        }];
      }
    });
    setLastSyncedText('Kaydedildi');
  };

  const handleProgressUpdate = (chapterNumber: number, pageIndex: number, percentage: number) => {
    setLastSyncedText(`Bölüm ${chapterNumber} : ${percentage}%`);
  };

  const handleToggleExtension = (id: string) => {
    setInstalledExtensions(prev => {
      const exists = prev.some(e => e.id === id);
      if (exists) {
        return prev.filter(e => e.id !== id);
      } else {
        return [...prev, {
          id,
          name: id,
          version: '1.0.0',
          iconUrl: '',
          author: 'Community',
          description: '',
          baseUrl: '',
          supportedLanguages: ['en'],
          isNsfw: false,
          repoUrl: ''
        }];
      }
    });
  };

  const featuredSeries = seriesList[0];

  return (
    <div className="min-h-screen bg-[#07070a] text-slate-100 flex flex-col font-sans">
      
      {/* Top Navigation */}
      <Navbar
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        isDriveConnected={isDriveConnected}
        onDriveToggle={handleDriveToggle}
        enableAdultContent={enableAdultContent}
        onAdultToggle={handleAdultToggle}
        onOpenExtensions={() => setIsExtensionModalOpen(true)}
        lastSyncedText={lastSyncedText}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-6 py-6">
        
        {/* Spotlight Hero Banner (Shown only when not searching) */}
        {!searchQuery && featuredSeries && (
          <HeroBanner
            series={featuredSeries}
            onOpenDetail={setSelectedSeries}
            onStartReading={(series) => {
              setReadingState({
                series,
                chapter: {
                  id: `ch-1`,
                  seriesId: series.id,
                  chapterNumber: 1,
                  title: 'Bölüm 1',
                  language: 'tr',
                  pageCount: 16
                }
              });
            }}
          />
        )}

        {/* Category Tabs */}
        <div className="flex items-center justify-between gap-4 mb-4">
          <CategoryTabs
            activeTab={activeCategory}
            onTabChange={setActiveCategory}
            enableAdult={enableAdultContent}
          />
          <div className="text-xs text-slate-400 font-semibold hidden md:block">
            {loading ? 'Yükleniyor...' : `${seriesList.length} Seri Bulundu`}
          </div>
        </div>

        {/* 2:3 Vertical Poster Grid */}
        <SeriesGrid
          seriesList={seriesList}
          onSelectSeries={setSelectedSeries}
          installedExtensionIds={installedExtensions.map(e => e.id)}
        />

      </main>

      {/* Series Detail Modal */}
      {selectedSeries && (
        <SeriesDetailModal
          series={selectedSeries}
          onClose={() => setSelectedSeries(null)}
          onReadChapter={(series, chapter) => {
            setSelectedSeries(null);
            setReadingState({ series, chapter });
          }}
          onToggleLibrary={handleToggleLibrary}
          isInLibrary={library.some(item => item.seriesId === selectedSeries.id)}
          installedExtensions={installedExtensions}
        />
      )}

      {/* Extension & Repo Manager Modal */}
      {isExtensionModalOpen && (
        <ExtensionManagerModal
          onClose={() => setIsExtensionModalOpen(false)}
          installedRepoUrls={installedRepoUrls}
          onAddRepoUrl={(url) => setInstalledRepoUrls(prev => [...prev, url])}
          onRemoveRepoUrl={(url) => setInstalledRepoUrls(prev => prev.filter(u => u !== url))}
          installedExtensions={installedExtensions}
          onToggleExtension={handleToggleExtension}
        />
      )}

      {/* Fullscreen High-Performance Reader */}
      {readingState && (
        <ReaderView
          series={readingState.series}
          chapter={readingState.chapter}
          onClose={() => setReadingState(null)}
          onProgressUpdate={handleProgressUpdate}
        />
      )}

      {/* Minimal Footer */}
      <footer className="border-t border-purple-900/20 py-6 text-center text-xs text-slate-500">
        Yomira — Every Story. One Library. (Manga • Webtoon • Comics)
      </footer>

    </div>
  );
};

export default App;
