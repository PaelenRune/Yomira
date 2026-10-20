import React, { useState, useEffect } from 'react';
import { X, Puzzle, Plus, Trash2, Globe, RefreshCw, AlertCircle, Loader2 } from 'lucide-react';
import { ExtensionManifest, RemoteExtensionPackage } from '@yomira/core';

interface ExtensionManagerModalProps {
  onClose: () => void;
  installedRepoUrls: string[];
  onAddRepoUrl: (url: string) => void;
  onRemoveRepoUrl: (url: string) => void;
  installedExtensions: ExtensionManifest[];
  onToggleExtension: (pkg: RemoteExtensionPackage) => Promise<void>;
  onRefreshRepos: () => Promise<RemoteExtensionPackage[]>;
}

export const ExtensionManagerModal: React.FC<ExtensionManagerModalProps> = ({
  onClose,
  installedRepoUrls,
  onAddRepoUrl,
  onRemoveRepoUrl,
  installedExtensions,
  onToggleExtension,
  onRefreshRepos
}) => {
  const [newRepoUrl, setNewRepoUrl] = useState('');
  const [activeTab, setActiveTab] = useState<'INSTALLED' | 'REPOS'>('INSTALLED');
  const [availableExtensions, setAvailableExtensions] = useState<RemoteExtensionPackage[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [selectedLang, setSelectedLang] = useState<string>('ALL');
  const [busyExtensionId, setBusyExtensionId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    loadExtensions();
  }, [installedRepoUrls]);

  const loadExtensions = async () => {
    setLoading(true);
    setActionError(null);
    try {
      const exts = await onRefreshRepos();
      setAvailableExtensions(exts);
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Repo indeksi yüklenemedi.');
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newRepoUrl.trim();
    if (!trimmed) return;
    if (installedRepoUrls.includes(trimmed)) {
      setActionError('Bu repo zaten ekli.');
      return;
    }
    setActionError(null);
    onAddRepoUrl(trimmed);
    setNewRepoUrl('');
  };

  const handleToggle = async (ext: RemoteExtensionPackage) => {
    setBusyExtensionId(ext.id);
    setActionError(null);
    try {
      await onToggleExtension(ext);
    } catch (err) {
      setActionError(
        err instanceof Error
          ? `"${ext.name}" kurulamadı: ${err.message}`
          : `"${ext.name}" kurulamadı.`
      );
    } finally {
      setBusyExtensionId(null);
    }
  };

  // Preset community repos that user can add with 1 click
  const presetRepos = [
    {
      name: 'Keiyoushi Topluluk Reposu (Mihon / Tachiyomi)',
      url: 'https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json',
      desc: 'Dünya çapında en popüler manga, manhwa ve çizgi roman sitelerinin eklenti arşivi.'
    },
    {
      name: 'Yomira Resmi Eklenti Reposu',
      url: 'https://raw.githubusercontent.com/yomira/repo-official/index.json',
      desc: 'MangaDex, Asura ve Western çizgi romanları için optimize edilmiş eklentiler.'
    }
  ];

  const filteredExtensions = availableExtensions.filter(ext => {
    if (selectedLang === 'ALL') return true;
    return ext.lang.toLowerCase() === selectedLang.toLowerCase();
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div 
        className="relative w-full max-w-2xl bg-[#0c0b14] border border-purple-900/40 rounded-2xl overflow-hidden shadow-2xl flex flex-col max-h-[85vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-white/5 bg-[#100f1a]">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-purple-600/20 border border-purple-500/30 flex items-center justify-center text-purple-400">
              <Puzzle className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Eklenti & Repo Merkezi</h2>
              <p className="text-xs text-slate-400">Keiyoushi ve topluluk repoları ile dinamik eklenti yönetimi</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={loadExtensions}
              disabled={loading}
              className="p-2 rounded-lg bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white transition-all cursor-pointer"
              title="Repoları Yenile"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin text-purple-400' : ''}`} />
            </button>
            <button
              onClick={onClose}
              className="w-8 h-8 rounded-full bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-all cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Tab switchers */}
        <div className="flex items-center justify-between border-b border-white/5 bg-[#090810] px-5">
          <div className="flex">
            <button
              onClick={() => setActiveTab('INSTALLED')}
              className={`py-3 px-4 text-xs font-bold border-b-2 transition-all cursor-pointer ${
                activeTab === 'INSTALLED'
                  ? 'border-purple-500 text-purple-300'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Repodaki Eklentiler ({availableExtensions.length})
            </button>
            <button
              onClick={() => setActiveTab('REPOS')}
              className={`py-3 px-4 text-xs font-bold border-b-2 transition-all cursor-pointer ${
                activeTab === 'REPOS'
                  ? 'border-purple-500 text-purple-300'
                  : 'border-transparent text-slate-400 hover:text-slate-200'
              }`}
            >
              Aktif Repolar ({installedRepoUrls.length})
            </button>
          </div>

          {activeTab === 'INSTALLED' && (
            <div className="flex gap-1 text-[10px]">
              {['ALL', 'TR', 'EN'].map((l) => (
                <button
                  key={l}
                  onClick={() => setSelectedLang(l)}
                  className={`px-2 py-0.5 rounded font-bold cursor-pointer transition-all ${
                    selectedLang === l ? 'bg-purple-600 text-white' : 'bg-white/5 text-slate-400 hover:text-white'
                  }`}
                >
                  {l === 'ALL' ? 'Tümü' : l}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Tab Content */}
        <div className="p-6 overflow-y-auto flex-1">
          
          {actionError && (
            <div className="mb-4 flex items-start gap-2 p-3 rounded-xl bg-rose-950/30 border border-rose-800/40 text-[11px] text-rose-300">
              <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
              <span className="leading-relaxed">{actionError}</span>
              <button
                onClick={() => setActionError(null)}
                className="ml-auto text-rose-400 hover:text-white transition-colors cursor-pointer"
                title="Kapat"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            </div>
          )}

          {activeTab === 'INSTALLED' && (
            <div className="space-y-3">
              {loading && (
                <div className="py-12 text-center text-xs text-purple-400 font-semibold flex flex-col items-center gap-2">
                  <RefreshCw className="w-6 h-6 animate-spin" />
                  <span>Repodaki eklentiler yükleniyor...</span>
                </div>
              )}

              {!loading && filteredExtensions.length === 0 && (
                <div className="py-12 text-center text-xs text-slate-500 flex flex-col items-center gap-2">
                  <AlertCircle className="w-6 h-6 text-slate-600" />
                  <span>Bu repolarda seçili dilde eklenti bulunamadı. "Kurulu Repolar" sekmesinden yeni repo ekleyebilirsiniz.</span>
                </div>
              )}

              {!loading && filteredExtensions.map((ext) => {
                const isInstalled = installedExtensions.some(e => e.id === ext.id);

                return (
                  <div
                    key={ext.id}
                    className="flex items-center justify-between p-3.5 rounded-xl bg-[#12111d] border border-white/5 hover:border-purple-500/30 transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-purple-950/40 border border-purple-800/30 flex items-center justify-center font-bold text-sm text-purple-300 shrink-0">
                        {ext.iconUrl ? (
                          <img src={ext.iconUrl} alt={ext.name} className="w-6 h-6 object-contain rounded" onError={(e) => { (e.target as HTMLElement).style.display = 'none'; }} />
                        ) : ext.name.charAt(0)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-white">{ext.name}</span>
                          <span className="text-[10px] text-slate-500">v{ext.version}</span>
                          <span className="px-1.5 py-0.2 rounded bg-purple-900/30 border border-purple-700/30 text-[9px] font-bold text-purple-300 uppercase">
                            {ext.lang}
                          </span>
                          {ext.isNsfw && (
                            <span className="px-1.5 py-0.2 rounded bg-rose-950/60 border border-rose-800/40 text-[9px] font-bold text-rose-400">
                              +18
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-slate-400 line-clamp-1">{ext.description || 'Topluluk çizgi roman eklentisi.'}</p>
                      </div>
                    </div>

                    <button
                      onClick={() => handleToggle(ext)}
                      disabled={busyExtensionId === ext.id}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all shrink-0 cursor-pointer disabled:opacity-60 disabled:cursor-wait ${
                        isInstalled
                          ? 'bg-purple-600/20 border border-purple-500/40 text-purple-300 hover:bg-rose-900/40 hover:text-rose-300 hover:border-rose-700/40'
                          : 'bg-purple-600 hover:bg-purple-500 text-white shadow-[0_2px_10px_rgba(124,58,237,0.3)]'
                      }`}
                    >
                      {busyExtensionId === ext.id ? (
                        <span className="flex items-center gap-1.5">
                          <Loader2 className="w-3.5 h-3.5 animate-spin" />
                          <span>İşleniyor</span>
                        </span>
                      ) : isInstalled ? (
                        '✓ Kurulu (Kaldır)'
                      ) : (
                        '+ Kur'
                      )}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {activeTab === 'REPOS' && (
            <div className="space-y-6">
              
              {/* Add Custom Repo Form */}
              <form onSubmit={handleAdd} className="flex gap-2">
                <input
                  type="url"
                  value={newRepoUrl}
                  onChange={(e) => setNewRepoUrl(e.target.value)}
                  placeholder="https://.../index.min.json (Keiyoushi veya özel repo URL)"
                  className="flex-1 bg-[#12111d] border border-white/10 rounded-xl px-4 py-2 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-purple-500"
                />
                <button
                  type="submit"
                  className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-purple-600 hover:bg-purple-500 text-white text-xs font-bold transition-all cursor-pointer shadow-[0_2px_10px_rgba(124,58,237,0.3)]"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Repo Ekle</span>
                </button>
              </form>

              {/* Ready-to-add Popular Presets */}
              <div className="space-y-2">
                <span className="text-xs font-bold text-purple-400 uppercase tracking-wider block">Önerilen Topluluk Repoları</span>
                {presetRepos.map((preset, idx) => {
                  const isAdded = installedRepoUrls.includes(preset.url);

                  return (
                    <div key={idx} className="p-3 bg-[#12111d] border border-white/5 rounded-xl flex items-center justify-between gap-3">
                      <div>
                        <div className="text-xs font-bold text-white">{preset.name}</div>
                        <div className="text-[10px] text-slate-400">{preset.desc}</div>
                      </div>
                      <button
                        onClick={() => isAdded ? onRemoveRepoUrl(preset.url) : onAddRepoUrl(preset.url)}
                        className={`px-3 py-1 rounded-lg text-xs font-bold transition-all shrink-0 cursor-pointer ${
                          isAdded 
                            ? 'bg-emerald-950/40 border border-emerald-500/30 text-emerald-400' 
                            : 'bg-purple-600 hover:bg-purple-500 text-white'
                        }`}
                      >
                        {isAdded ? '✓ Ekli' : '+ Ekle'}
                      </button>
                    </div>
                  );
                })}
              </div>

              {/* Currently Added Repos List */}
              <div className="space-y-2 pt-2">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider block">Aktif Ekli Repolarınız</span>
                {installedRepoUrls.map((url, i) => (
                  <div
                    key={i}
                    className="flex items-center justify-between p-3 rounded-xl bg-[#12111d] border border-white/5 text-xs text-slate-300"
                  >
                    <div className="flex items-center gap-2 truncate pr-2">
                      <Globe className="w-3.5 h-3.5 text-purple-400 shrink-0" />
                      <span className="truncate">{url}</span>
                    </div>
                    <button
                      onClick={() => onRemoveRepoUrl(url)}
                      className="text-slate-500 hover:text-rose-400 p-1 transition-colors cursor-pointer"
                      title="Repoyu Kaldır"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>

            </div>
          )}

        </div>

      </div>
    </div>
  );
};
