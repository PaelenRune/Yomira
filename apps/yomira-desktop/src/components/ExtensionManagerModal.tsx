import React, { useState } from 'react';
import { X, Puzzle, Plus, Check, Trash2, Globe, Shield, RefreshCw } from 'lucide-react';
import { ExtensionManifest } from '@yomira/core';

interface ExtensionManagerModalProps {
  onClose: () => void;
  installedRepoUrls: string[];
  onAddRepoUrl: (url: string) => void;
  onRemoveRepoUrl: (url: string) => void;
  installedExtensions: ExtensionManifest[];
  onToggleExtension: (id: string) => void;
}

export const ExtensionManagerModal: React.FC<ExtensionManagerModalProps> = ({
  onClose,
  installedRepoUrls,
  onAddRepoUrl,
  onRemoveRepoUrl,
  installedExtensions,
  onToggleExtension
}) => {
  const [newRepoUrl, setNewRepoUrl] = useState('');
  const [activeTab, setActiveTab] = useState<'INSTALLED' | 'REPOS'>('INSTALLED');

  const handleAdd = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRepoUrl.trim()) return;
    onAddRepoUrl(newRepoUrl.trim());
    setNewRepoUrl('');
  };

  const availableCatalogExtensions: ExtensionManifest[] = [
    {
      id: 'com.yomira.mangadex',
      name: 'MangaDex Eklentisi',
      version: '1.4.2',
      iconUrl: 'https://mangadex.org/favicon.ico',
      author: 'Yomira Community',
      description: 'Dünya çapında milyonlarca manga ve webtoon bölümü, çoklu dil desteği.',
      baseUrl: 'https://mangadex.org',
      supportedLanguages: ['tr', 'en', 'es'],
      isNsfw: false,
      repoUrl: 'https://raw.githubusercontent.com/yomira/repo-official/index.json'
    },
    {
      id: 'com.yomira.asurascans',
      name: 'Asura Scans Eklentisi',
      version: '2.0.1',
      iconUrl: 'https://asuracomic.net/favicon.ico',
      author: 'Scraper Team',
      description: 'Aksiyon, Reenkarnasyon ve Seviye Atlama Manhwa/Webtoon serileri.',
      baseUrl: 'https://asuracomic.net',
      supportedLanguages: ['en'],
      isNsfw: false,
      repoUrl: 'https://raw.githubusercontent.com/yomira/repo-official/index.json'
    },
    {
      id: 'com.yomira.marveldc',
      name: 'ReadComicsOnline (Marvel & DC)',
      version: '1.1.0',
      iconUrl: 'https://readcomiconline.li/favicon.ico',
      author: 'Western Hub',
      description: 'Spider-Man, Batman, X-Men ve Image Comics Batı çizgi romanları.',
      baseUrl: 'https://readcomiconline.li',
      supportedLanguages: ['en'],
      isNsfw: false,
      repoUrl: 'https://raw.githubusercontent.com/yomira/repo-western/index.json'
    },
    {
      id: 'com.yomira.toonily',
      name: 'Toonily (+18 Yetişkin Eklentisi)',
      version: '1.8.0',
      iconUrl: 'https://toonily.com/favicon.ico',
      author: 'Adult Repos',
      description: 'Yetişkin içerikli Porhwa, Hentai ve erotik webtoon arşivi.',
      baseUrl: 'https://toonily.com',
      supportedLanguages: ['en', 'tr'],
      isNsfw: true,
      repoUrl: 'https://raw.githubusercontent.com/yomira/repo-adult/index.json'
    }
  ];

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
              <h2 className="text-base font-bold text-white">Eklenti & Kaynak Merkezi</h2>
              <p className="text-xs text-slate-400">Mihon / Tachiyomi mantığıyla harici repo ve site eklentileri</p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-white transition-all cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Tab switchers */}
        <div className="flex border-b border-white/5 bg-[#090810] px-5">
          <button
            onClick={() => setActiveTab('INSTALLED')}
            className={`py-3 px-4 text-xs font-bold border-b-2 transition-all cursor-pointer ${
              activeTab === 'INSTALLED'
                ? 'border-purple-500 text-purple-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Mevcut Eklentiler ({availableCatalogExtensions.length})
          </button>
          <button
            onClick={() => setActiveTab('REPOS')}
            className={`py-3 px-4 text-xs font-bold border-b-2 transition-all cursor-pointer ${
              activeTab === 'REPOS'
                ? 'border-purple-500 text-purple-300'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            Kurulu Repolar ({installedRepoUrls.length})
          </button>
        </div>

        {/* Tab Content */}
        <div className="p-6 overflow-y-auto flex-1">
          
          {activeTab === 'INSTALLED' && (
            <div className="space-y-3">
              {availableCatalogExtensions.map((ext) => {
                const isInstalled = installedExtensions.some(e => e.id === ext.id);

                return (
                  <div
                    key={ext.id}
                    className="flex items-center justify-between p-4 rounded-xl bg-[#12111d] border border-white/5 hover:border-purple-500/30 transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-lg bg-purple-950/40 border border-purple-800/30 flex items-center justify-center font-bold text-sm text-purple-300">
                        {ext.name.charAt(0)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-xs font-bold text-white">{ext.name}</span>
                          <span className="text-[10px] text-slate-500">v{ext.version}</span>
                          {ext.isNsfw && (
                            <span className="px-1.5 py-0.2 rounded bg-rose-950/60 border border-rose-800/40 text-[9px] font-bold text-rose-400">
                              +18
                            </span>
                          )}
                        </div>
                        <p className="text-[11px] text-slate-400 line-clamp-1">{ext.description}</p>
                      </div>
                    </div>

                    <button
                      onClick={() => onToggleExtension(ext.id)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                        isInstalled
                          ? 'bg-purple-600/20 border border-purple-500/40 text-purple-300 hover:bg-purple-600/30'
                          : 'bg-purple-600 hover:bg-purple-500 text-white shadow-[0_2px_10px_rgba(124,58,237,0.3)]'
                      }`}
                    >
                      {isInstalled ? '✓ Kurulu' : '+ Yükle'}
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {activeTab === 'REPOS' && (
            <div className="space-y-6">
              
              {/* Add Repo Form */}
              <form onSubmit={handleAdd} className="flex gap-2">
                <input
                  type="url"
                  value={newRepoUrl}
                  onChange={(e) => setNewRepoUrl(e.target.value)}
                  placeholder="https://.../index.min.json (Topluluk Repo URL)"
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

              {/* Repo List */}
              <div className="space-y-2">
                <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">Aktif Repolar</span>
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
