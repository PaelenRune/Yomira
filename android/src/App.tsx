import React, { useState, useEffect, useMemo } from 'react';
import { 
  Series, 
  Chapter, 
  CascadingAggregator, 
  GoogleDriveSyncService,
  ExtensionManifest,
  LibraryItem,
  ReadingProgress,
  SeriesType,
  RepositoryManager,
  ExtensionRuntime,
  RemoteExtensionPackage
} from '@yomira/core';

import { MobileHeader } from './components/MobileHeader';
import { BottomNav, MobileTab } from './components/BottomNav';
import { MobileSeriesCard } from './components/MobileSeriesCard';
import { MobileReaderView } from './components/MobileReaderView';
import { MobileLibraryView } from './components/MobileLibraryView';
import { 
  X, 
  Play, 
  Bookmark, 
  Star, 
  Layers, 
  Puzzle, 
  Plus, 
  Trash2, 
  Cloud, 
  ShieldAlert, 
  Moon, 
  Info,
  CheckCircle2,
  RefreshCw,
  Loader2,
  AlertCircle,
  Globe
} from 'lucide-react';

export const App: React.FC = () => {
  // Core Services
  const aggregator = useMemo(() => new CascadingAggregator(), []);
  const syncService = useMemo(() => new GoogleDriveSyncService(), []);
  const runtime = useMemo(() => new ExtensionRuntime(), []);

  // State
  const [activeTab, setActiveTab] = useState<MobileTab>('DISCOVER');
  const [seriesList, setSeriesList] = useState<Series[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');

  // Modals & Reader
  const [selectedSeries, setSelectedSeries] = useState<Series | null>(null);
  const [readingChapter, setReadingChapter] = useState<{ series: Series; chapter: Chapter } | null>(null);

  // Settings & Sync
  const [enableAdult, setEnableAdult] = useState<boolean>(false);
  const [isDriveConnected, setIsDriveConnected] = useState<boolean>(true);
  const [library, setLibrary] = useState<LibraryItem[]>([]);
  const [readingProgress, setReadingProgress] = useState<ReadingProgress[]>([]);

  // Community Repos & Dynamic Extensions
  const defaultRepoUrls = useMemo(
    () => ['https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json'],
    []
  );
  const [installedRepoUrls, setInstalledRepoUrls] = useState<string[]>(defaultRepoUrls);
  const repoManager = useMemo(
    () => new RepositoryManager(runtime, defaultRepoUrls),
    [runtime, defaultRepoUrls]
  );
  const [installedExtensions, setInstalledExtensions] = useState<ExtensionManifest[]>([]);
  const [availableExtensions, setAvailableExtensions] = useState<RemoteExtensionPackage[]>([]);
  const [extensionsLoading, setExtensionsLoading] = useState<boolean>(false);
  const [busyExtensionId, setBusyExtensionId] = useState<string | null>(null);
  const [extensionError, setExtensionError] = useState<string | null>(null);
  const [extensionLang, setExtensionLang] = useState<string>('ALL');
  const [newRepoInput, setNewRepoInput] = useState<string>('');

  // Initial Load
  useEffect(() => {
    loadSeries();
  }, [selectedCategory, enableAdult]);

  // Debounced Search
  useEffect(() => {
    const timer = setTimeout(() => {
      loadSeries(searchQuery);
    }, 350);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // Load dynamic extensions whenever the Extensions tab opens or repos change
  useEffect(() => {
    if (activeTab !== 'EXTENSIONS') return;

    let cancelled = false;
    const loadExtensions = async () => {
      setExtensionsLoading(true);
      setExtensionError(null);
      try {
        const extensions = await repoManager.getAllAvailableExtensions();
        if (!cancelled) setAvailableExtensions(extensions);
      } catch (err) {
        if (!cancelled) {
          setExtensionError(err instanceof Error ? err.message : 'Repo indeksi yüklenemedi.');
        }
      } finally {
        if (!cancelled) setExtensionsLoading(false);
      }
    };

    loadExtensions();
    return () => {
      cancelled = true;
    };
  }, [activeTab, installedRepoUrls, repoManager]);

  const loadSeries = async (query = '') => {
    setLoading(true);
    try {
      const type = (selectedCategory !== 'ALL' && selectedCategory !== 'ADULT_18')
        ? (selectedCategory as SeriesType)
        : undefined;

      const maxRating = enableAdult ? 'PORNOGRAPHIC_18' : 'MATURE_ECCHI';
      const results = await aggregator.search({
        query,
        type,
        maxContentRating: maxRating,
        limit: 28
      });

      if (selectedCategory === 'ADULT_18') {
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

  const handleDriveToggle = () => {
    setIsDriveConnected(prev => !prev);
  };

  const handleAdultToggle = () => {
    if (!enableAdult) {
      const ok = window.confirm('Yetişkin (+18) Modu Açılıyor:\nPornografik seriler kataloğa eklenecektir. Onaylıyor musunuz?');
      if (ok) setEnableAdult(true);
    } else {
      setEnableAdult(false);
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
  };

  const handleProgressUpdate = (chapterNumber: number, pageIndex: number, percentage: number) => {
    if (!readingChapter) return;
    const seriesId = readingChapter.series.id;

    setReadingProgress(prev => {
      const filtered = prev.filter(p => p.seriesId !== seriesId);
      return [...filtered, {
        seriesId,
        chapterId: readingChapter.chapter.id,
        chapterNumber,
        pageIndex,
        percentage,
        lastReadAt: Date.now(),
        isCompleted: percentage >= 95
      }];
    });
  };

  const handleAddRepo = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newRepoInput.trim();
    if (!trimmed) return;
    if (installedRepoUrls.includes(trimmed)) {
      setExtensionError('Bu repo zaten ekli.');
      return;
    }
    repoManager.addRepoUrl(trimmed);
    setInstalledRepoUrls(prev => (prev.includes(trimmed) ? prev : [...prev, trimmed]));
    setNewRepoInput('');
    setExtensionError(null);
  };

  const handleRemoveRepo = (url: string) => {
    repoManager.removeRepoUrl(url);
    setInstalledRepoUrls(prev => prev.filter(u => u !== url));
  };

  const handleRefreshExtensions = async () => {
    setExtensionsLoading(true);
    setExtensionError(null);
    try {
      const extensions = await repoManager.getAllAvailableExtensions();
      setAvailableExtensions(extensions);
    } catch (err) {
      setExtensionError(err instanceof Error ? err.message : 'Repo indeksi yüklenemedi.');
    } finally {
      setExtensionsLoading(false);
    }
  };

  const handleToggleExtension = async (pkg: RemoteExtensionPackage) => {
    setBusyExtensionId(pkg.id);
    setExtensionError(null);
    try {
      if (repoManager.isInstalled(pkg.id)) {
        repoManager.uninstallExtension(pkg.id);
        setInstalledExtensions(prev => prev.filter(e => e.id !== pkg.id));
      } else {
        const source = await repoManager.installExtension(pkg);
        setInstalledExtensions(prev => (
          prev.some(e => e.id === source.manifest.id) ? prev : [...prev, source.manifest]
        ));
      }
    } catch (err) {
      setExtensionError(
        err instanceof Error
          ? `"${pkg.name}" işlemi başarısız: ${err.message}`
          : `"${pkg.name}" işlemi başarısız.`
      );
    } finally {
      setBusyExtensionId(null);
    }
  };

  const filteredExtensions = availableExtensions.filter(ext => {
    if (extensionLang === 'ALL') return true;
    return ext.lang.toLowerCase() === extensionLang.toLowerCase();
  });

  const installedExtensionIds = installedExtensions.map(e => e.id);

  const categories = [
    { id: 'ALL', label: 'Tümü' },
    { id: 'MANHWA', label: 'Webtoon' },
    { id: 'MANGA', label: 'Manga' },
    { id: 'COMIC', label: 'Western (Marvel/DC)' },
    { id: 'MANHUA', label: 'Manhua' },
  ];

  if (enableAdult) {
    categories.push({ id: 'ADULT_18', label: 'Yetişkin (+18)' });
  }

  const isInLibrary = selectedSeries ? library.some(item => item.seriesId === selectedSeries.id) : false;

  return (
    <div className="min-h-screen bg-[#07070a] text-slate-100 flex flex-col font-sans select-none pb-16">
      
      {/* Mobile Top Header */}
      <MobileHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        isDriveConnected={isDriveConnected}
        onDriveToggle={handleDriveToggle}
        enableAdult={enableAdult}
        onAdultToggle={handleAdultToggle}
      />

      {/* Main Tab Views */}
      <main className="flex-1 flex flex-col">
        
        {/* TAB 1: KEŞFET / DISCOVER */}
        {activeTab === 'DISCOVER' && (
          <div className="p-3.5 space-y-3">
            {/* Category horizontal scroll bar */}
            <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none">
              {categories.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setSelectedCategory(c.id)}
                  className={`px-3 py-1.5 rounded-full text-xs font-bold whitespace-nowrap transition-all ${
                    selectedCategory === c.id
                      ? 'bg-purple-600 text-white shadow-[0_2px_10px_rgba(124,58,237,0.4)]'
                      : 'bg-[#12111d] text-slate-400 border border-white/5'
                  }`}
                >
                  {c.label}
                </button>
              ))}
            </div>

            {/* 2-Column strict 2:3 vertical poster cards */}
            {loading ? (
              <div className="py-16 text-center text-xs text-slate-500">Katalog yükleniyor...</div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                {seriesList.map((series) => (
                  <MobileSeriesCard
                    key={series.id}
                    series={series}
                    onClick={() => setSelectedSeries(series)}
                    isBound={series.metadataSources.some(s => installedExtensionIds.includes(s.providerId))}
                  />
                ))}
              </div>
            )}
          </div>
        )}

        {/* TAB 2: KİTAPLIĞIM / LIBRARY */}
        {activeTab === 'LIBRARY' && (
          <MobileLibraryView
            library={library}
            allSeries={seriesList}
            readingProgress={readingProgress}
            onSelectSeries={setSelectedSeries}
          />
        )}

        {/* TAB 3: EKLENTİLER / EXTENSIONS */}
        {activeTab === 'EXTENSIONS' && (
          <div className="p-4 space-y-4">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-sm font-bold text-white mb-1">Eklenti ve Kaynaklar</h2>
                <p className="text-xs text-slate-400">
                  Keiyoushi ve eklediğiniz repolardan dinamik eklenti yönetimi.
                </p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                <span className="px-2 py-1 rounded-full bg-emerald-950/40 border border-emerald-500/30 text-[10px] font-bold text-emerald-400">
                  {installedExtensions.length} kurulu
                </span>
                <button
                  onClick={handleRefreshExtensions}
                  disabled={extensionsLoading}
                  className="p-2 rounded-xl bg-[#12111d] border border-white/10 text-slate-300 disabled:opacity-60"
                  title="Repoları Yenile"
                >
                  <RefreshCw className={`w-4 h-4 ${extensionsLoading ? 'animate-spin text-purple-400' : ''}`} />
                </button>
              </div>
            </div>

            {extensionError && (
              <div className="flex items-start gap-2 p-3 rounded-xl bg-rose-950/30 border border-rose-800/40 text-[11px] text-rose-300">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span className="leading-relaxed flex-1">{extensionError}</span>
                <button onClick={() => setExtensionError(null)} className="text-rose-400">
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            )}

            {/* Language filters */}
            <div className="flex items-center gap-1 text-[10px]">
              {['ALL', 'TR', 'EN'].map((lang) => (
                <button
                  key={lang}
                  onClick={() => setExtensionLang(lang)}
                  className={`px-2.5 py-1 rounded-lg font-bold transition-all ${
                    extensionLang === lang
                      ? 'bg-purple-600 text-white'
                      : 'bg-[#12111d] border border-white/5 text-slate-400'
                  }`}
                >
                  {lang === 'ALL' ? 'Tümü' : lang}
                </button>
              ))}
            </div>

            {/* Dynamic extension list from community repositories */}
            <div className="space-y-2">
              {extensionsLoading && (
                <div className="py-10 flex flex-col items-center gap-2 text-xs text-purple-400 font-semibold">
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>Repodaki eklentiler yükleniyor...</span>
                </div>
              )}

              {!extensionsLoading && filteredExtensions.length === 0 && (
                <div className="py-10 flex flex-col items-center gap-2 text-center text-xs text-slate-500">
                  <Puzzle className="w-6 h-6 text-slate-600" />
                  <span>Bu repolarda seçili dilde eklenti bulunamadı. Aşağıdan yeni repo ekleyebilirsiniz.</span>
                </div>
              )}

              {!extensionsLoading && filteredExtensions.map((ext) => {
                const isInstalled = installedExtensionIds.includes(ext.id);
                const isBusy = busyExtensionId === ext.id;

                return (
                  <div
                    key={ext.id}
                    className="flex items-center justify-between gap-2 p-3 rounded-xl bg-[#12111d] border border-white/5"
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      <div className="w-9 h-9 rounded-lg bg-purple-950/40 border border-purple-800/30 flex items-center justify-center text-xs font-bold text-purple-300 shrink-0 overflow-hidden">
                        {ext.iconUrl ? (
                          <img
                            src={ext.iconUrl}
                            alt={ext.name}
                            className="w-5 h-5 object-contain rounded"
                            onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }}
                          />
                        ) : (
                          ext.name.charAt(0)
                        )}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="text-xs font-bold text-white truncate max-w-[150px]">{ext.name}</span>
                          <span className="text-[9px] text-slate-500">v{ext.version}</span>
                          <span className="px-1 py-0.2 rounded bg-purple-900/30 border border-purple-700/30 text-[8px] font-bold text-purple-300 uppercase">
                            {ext.lang}
                          </span>
                          {ext.isNsfw && (
                            <span className="px-1 py-0.2 rounded bg-rose-950/60 border border-rose-800/40 text-[8px] font-bold text-rose-400">
                              +18
                            </span>
                          )}
                        </div>
                        <p className="text-[10px] text-slate-400 truncate">{ext.description || 'Topluluk eklentisi.'}</p>
                      </div>
                    </div>

                    <button
                      onClick={() => handleToggleExtension(ext)}
                      disabled={isBusy}
                      className={`px-3 py-1.5 rounded-lg text-[11px] font-bold transition-all shrink-0 disabled:opacity-60 ${
                        isInstalled
                          ? 'bg-purple-600/20 border border-purple-500/40 text-purple-300'
                          : 'bg-purple-600 text-white'
                      }`}
                    >
                      {isBusy ? (
                        <Loader2 className="w-3.5 h-3.5 animate-spin" />
                      ) : isInstalled ? (
                        '✓ Kaldır'
                      ) : (
                        '+ Kur'
                      )}
                    </button>
                  </div>
                );
              })}
            </div>

            {/* Repos section */}
            <div className="pt-2 space-y-2">
              <span className="text-xs font-bold text-slate-300 block">Harici Repo Ekle</span>
              <form onSubmit={handleAddRepo} className="flex gap-2">
                <input
                  type="url"
                  value={newRepoInput}
                  onChange={(e) => setNewRepoInput(e.target.value)}
                  placeholder="https://.../index.min.json"
                  className="flex-1 bg-[#12111d] border border-white/10 rounded-xl px-3 py-1.5 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-purple-500"
                />
                <button
                  type="submit"
                  className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-purple-600 text-white text-xs font-bold"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Ekle
                </button>
              </form>

              {/* Keiyoushi preset */}
              <div className="flex items-center justify-between gap-2 p-2.5 rounded-xl bg-[#12111d] border border-white/5">
                <div className="flex items-center gap-2 min-w-0">
                  <Globe className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                  <div className="min-w-0">
                    <div className="text-[11px] font-bold text-white">Keiyoushi Topluluk Reposu</div>
                    <div className="text-[9px] text-slate-500 truncate">Mihon / Tachiyomi eklenti arşivi</div>
                  </div>
                </div>
                <span className="px-2 py-1 rounded-lg bg-emerald-950/40 border border-emerald-500/30 text-[10px] font-bold text-emerald-400 shrink-0">
                  {installedRepoUrls.includes(defaultRepoUrls[0]) ? '✓ Ekli' : 'Hazır'}
                </span>
              </div>

              <div className="space-y-1.5">
                {installedRepoUrls.map((url) => (
                  <div key={url} className="flex items-center justify-between p-2 rounded-lg bg-[#12111d] border border-white/5 text-[11px] text-slate-400">
                    <span className="truncate pr-2">{url}</span>
                    <button
                      onClick={() => handleRemoveRepo(url)}
                      className="p-1 text-slate-500 hover:text-rose-400"
                      title="Repoyu Kaldır"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: AYARLAR / SETTINGS */}
        {activeTab === 'SETTINGS' && (
          <div className="p-4 space-y-4">
            <h2 className="text-sm font-bold text-white mb-2">Uygulama Ayarları</h2>
            
            <div className="space-y-2">
              {/* Google Drive Status */}
              <div className="flex items-center justify-between p-3.5 rounded-xl bg-[#12111d] border border-white/5">
                <div className="flex items-center gap-3">
                  <Cloud className="w-5 h-5 text-emerald-400" />
                  <div>
                    <span className="text-xs font-bold text-white block">Google Drive Senkronizasyonu</span>
                    <span className="text-[10px] text-slate-400">Kişisel Google Drive AppData klasörü</span>
                  </div>
                </div>
                <button
                  onClick={handleDriveToggle}
                  className={`px-3 py-1 rounded-lg text-xs font-bold ${
                    isDriveConnected ? 'bg-emerald-950/40 border border-emerald-500/30 text-emerald-400' : 'bg-white/5 text-slate-400'
                  }`}
                >
                  {isDriveConnected ? 'Bağlı' : 'Bağla'}
                </button>
              </div>

              {/* Adult Toggle */}
              <div className="flex items-center justify-between p-3.5 rounded-xl bg-[#12111d] border border-white/5">
                <div className="flex items-center gap-3">
                  <ShieldAlert className="w-5 h-5 text-rose-400" />
                  <div>
                    <span className="text-xs font-bold text-white block">Yetişkin (+18) Kataloğu</span>
                    <span className="text-[10px] text-slate-400">Pornografik manhwa ve hentai filtresi</span>
                  </div>
                </div>
                <button
                  onClick={handleAdultToggle}
                  className={`px-3 py-1 rounded-lg text-xs font-bold ${
                    enableAdult ? 'bg-rose-950/40 border border-rose-500/30 text-rose-400' : 'bg-white/5 text-slate-400'
                  }`}
                >
                  {enableAdult ? 'Açık' : 'Kapalı'}
                </button>
              </div>

              {/* Version info */}
              <div className="p-3.5 rounded-xl bg-[#12111d] border border-white/5 flex items-center justify-between">
                <span className="text-xs font-bold text-white">Yomira Sürümü</span>
                <span className="text-xs text-purple-400 font-mono font-bold">v1.0.0 (Core Engine)</span>
              </div>
            </div>
          </div>
        )}

      </main>

      {/* Series Detail Bottom Sheet */}
      {selectedSeries && (
        <div className="fixed inset-0 z-50 flex items-end bg-black/80 backdrop-blur-sm animate-fadeIn">
          <div className="w-full bg-[#0e0d18] border-t border-purple-900/40 rounded-t-3xl max-h-[85vh] overflow-y-auto p-5 space-y-4">
            
            <div className="flex justify-between items-start">
              <div className="flex gap-3">
                <div className="w-20 aspect-poster rounded-xl overflow-hidden shrink-0 border border-purple-500/30">
                  <img src={selectedSeries.coverUrl} alt={selectedSeries.canonicalTitle} className="w-full h-full object-cover" />
                </div>
                <div>
                  <span className="px-2 py-0.5 rounded-full bg-purple-600/20 text-purple-300 text-[10px] font-bold uppercase">{selectedSeries.type}</span>
                  <h3 className="text-base font-extrabold text-white leading-tight mt-1 line-clamp-2">{selectedSeries.canonicalTitle}</h3>
                  <div className="flex items-center gap-1 text-[11px] text-amber-400 font-bold mt-1">
                    <Star className="w-3.5 h-3.5 fill-amber-400" />
                    <span>{selectedSeries.rating ? `${selectedSeries.rating} / 5.0` : '4.9★'}</span>
                  </div>
                </div>
              </div>
              <button onClick={() => setSelectedSeries(null)} className="p-1 rounded-full bg-white/10 text-slate-400">
                <X className="w-4 h-4" />
              </button>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed bg-[#141322] p-3 rounded-xl border border-white/5">
              {selectedSeries.description.replace(/<[^>]*>?/gm, '') || 'Evrensel metadata kataloğunda listelenen çizgi roman.'}
            </p>

            {/* Actions */}
            <div className="flex gap-2 pt-1">
              <button
                onClick={() => {
                  const ch: Chapter = {
                    id: `ch-1`,
                    seriesId: selectedSeries.id,
                    chapterNumber: 1,
                    title: 'Bölüm 1',
                    language: 'tr',
                    pageCount: 16
                  };
                  setSelectedSeries(null);
                  setReadingChapter({ series: selectedSeries, chapter: ch });
                }}
                className="flex-1 flex items-center justify-center gap-2 py-3 rounded-xl bg-gradient-to-r from-purple-600 to-fuchsia-600 text-white font-bold text-xs shadow-[0_4px_15px_rgba(124,58,237,0.4)]"
              >
                <Play className="w-4 h-4 fill-white" />
                <span>1. Bölümü Oku</span>
              </button>

              <button
                onClick={() => handleToggleLibrary(selectedSeries)}
                className={`px-4 py-3 rounded-xl border text-xs font-bold ${
                  isInLibrary ? 'bg-purple-900/30 border-purple-500/50 text-purple-300' : 'bg-white/5 border-white/10 text-slate-300'
                }`}
              >
                <Bookmark className={`w-4 h-4 ${isInLibrary ? 'fill-purple-400 text-purple-400' : ''}`} />
              </button>
            </div>

          </div>
        </div>
      )}

      {/* Touch Reader View */}
      {readingChapter && (
        <MobileReaderView
          series={readingChapter.series}
          chapter={readingChapter.chapter}
          onClose={() => setReadingChapter(null)}
          onProgressUpdate={handleProgressUpdate}
        />
      )}

      {/* Android Bottom Navigation Bar */}
      <BottomNav
        activeTab={activeTab}
        onTabChange={setActiveTab}
        libraryCount={library.length}
      />

    </div>
  );
};

export default App;
