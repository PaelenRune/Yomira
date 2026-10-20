import React, { useState } from 'react';
import { X, Play, Bookmark, Star, Calendar, User, BookOpen, Layers, Check, AlertTriangle, ExternalLink } from 'lucide-react';
import { Series, Chapter } from '@yomira/core';

interface SeriesDetailModalProps {
  series: Series | null;
  onClose: () => void;
  onReadChapter: (series: Series, chapter: Chapter) => void;
  onToggleLibrary: (series: Series) => void;
  isInLibrary: boolean;
  installedExtensions: { id: string; name: string }[];
}

export const SeriesDetailModal: React.FC<SeriesDetailModalProps> = ({
  series,
  onClose,
  onReadChapter,
  onToggleLibrary,
  isInLibrary,
  installedExtensions
}) => {
  if (!series) return null;

  const [selectedSource, setSelectedSource] = useState<string>(
    installedExtensions[0]?.id || ''
  );

  // Generate simulated chapters based on totalChapters or default list
  const chapterCount = series.totalChapters || 24;
  const simulatedChapters: Chapter[] = Array.from({ length: Math.min(chapterCount, 50) }, (_, i) => ({
    id: `ch-${series.id}-${i + 1}`,
    seriesId: series.id,
    chapterNumber: i + 1,
    title: `Bölüm ${i + 1}`,
    language: 'tr',
    pageCount: 22,
    releaseDate: '2024'
  })).reverse();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/80 backdrop-blur-md animate-fadeIn">
      <div 
        className="relative w-full max-w-4xl max-h-[90vh] bg-[#0c0b14] border border-purple-900/40 rounded-2xl overflow-hidden shadow-2xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 z-20 w-9 h-9 rounded-full bg-black/60 hover:bg-purple-950/80 border border-white/10 text-slate-300 hover:text-white flex items-center justify-center transition-all cursor-pointer"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Scrollable Modal Body */}
        <div className="overflow-y-auto flex-1 p-6 md:p-8">
          
          {/* Top Section: Poster + Main Info */}
          <div className="flex flex-col md:flex-row gap-6 mb-8">
            
            {/* 2:3 Vertical Poster */}
            <div className="w-48 md:w-56 shrink-0 aspect-poster rounded-xl overflow-hidden border border-purple-500/30 shadow-[0_4px_25px_rgba(124,58,237,0.3)]">
              <img
                src={series.coverUrl}
                alt={series.canonicalTitle}
                className="w-full h-full object-cover"
              />
            </div>

            {/* Title, Badges, Attributes */}
            <div className="flex-1 flex flex-col justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2 mb-2">
                  <span className="px-2.5 py-0.5 rounded-full bg-purple-600/20 border border-purple-500/40 text-purple-300 text-xs font-bold uppercase">
                    {series.type}
                  </span>
                  <span className="px-2.5 py-0.5 rounded-full bg-white/5 border border-white/10 text-slate-300 text-xs font-semibold">
                    {series.status}
                  </span>
                  {series.contentRating === 'MATURE_ECCHI' && (
                    <span className="px-2.5 py-0.5 rounded-full bg-amber-500/20 border border-amber-500/40 text-amber-400 text-xs font-bold">
                      17+ Mature (Ecchi / Seinen)
                    </span>
                  )}
                  {series.contentRating === 'PORNOGRAPHIC_18' && (
                    <span className="px-2.5 py-0.5 rounded-full bg-rose-500/20 border border-rose-500/40 text-rose-400 text-xs font-bold">
                      18+ Yetişkin
                    </span>
                  )}
                </div>

                <h2 className="text-2xl md:text-3xl font-extrabold text-white mb-2 leading-tight">
                  {series.canonicalTitle}
                </h2>

                {series.aliases.length > 0 && (
                  <p className="text-xs text-slate-400 mb-3 italic">
                    Diğer İsimler: {series.aliases.slice(0, 3).join(', ')}
                  </p>
                )}

                {/* Rating & Authors */}
                <div className="flex flex-wrap items-center gap-4 text-xs text-slate-300 mb-4">
                  <div className="flex items-center gap-1 font-bold text-amber-400">
                    <Star className="w-4 h-4 fill-amber-400 text-amber-400" />
                    <span>{series.rating ? `${series.rating} / 5.0` : '4.9 / 5.0'}</span>
                  </div>
                  {series.author && (
                    <div className="flex items-center gap-1">
                      <User className="w-3.5 h-3.5 text-purple-400" />
                      <span>{series.author}</span>
                    </div>
                  )}
                  {series.year && (
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3.5 h-3.5 text-purple-400" />
                      <span>{series.year}</span>
                    </div>
                  )}
                  <div className="flex items-center gap-1">
                    <Layers className="w-3.5 h-3.5 text-purple-400" />
                    <span>{series.totalChapters || 'Belirsiz'} Bölüm</span>
                  </div>
                </div>

                {/* Genres */}
                <div className="flex flex-wrap gap-1.5 mb-4">
                  {series.genres.map((g) => (
                    <span key={g} className="px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-xs text-slate-300">
                      {g}
                    </span>
                  ))}
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-3 pt-2">
                <button
                  onClick={() => simulatedChapters[0] && onReadChapter(series, simulatedChapters[simulatedChapters.length - 1])}
                  className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-gradient-to-r from-purple-600 to-fuchsia-600 hover:from-purple-500 hover:to-fuchsia-500 text-white font-bold text-sm shadow-[0_4px_20px_rgba(124,58,237,0.4)] transition-all cursor-pointer"
                >
                  <Play className="w-4 h-4 fill-white" />
                  <span>1. Bölümden Başla</span>
                </button>

                <button
                  onClick={() => onToggleLibrary(series)}
                  className={`flex items-center gap-2 px-4 py-2.5 rounded-xl border text-sm font-semibold transition-all cursor-pointer ${
                    isInLibrary
                      ? 'bg-purple-900/30 border-purple-500/50 text-purple-300'
                      : 'bg-white/5 hover:bg-white/10 border-white/10 text-slate-300'
                  }`}
                >
                  <Bookmark className={`w-4 h-4 ${isInLibrary ? 'fill-purple-400 text-purple-400' : ''}`} />
                  <span>{isInLibrary ? 'Kitaplıkta' : 'Kitaplığa Ekle'}</span>
                </button>
              </div>

            </div>
          </div>

          {/* Middle Section: Synopsis */}
          <div className="mb-8">
            <h3 className="text-sm font-bold text-purple-400 uppercase tracking-wider mb-2">Özet</h3>
            <p className="text-sm text-slate-300 leading-relaxed bg-[#12111d] p-4 rounded-xl border border-white/5">
              {series.description.replace(/<[^>]*>?/gm, '') || 'Bu seri için henüz ayrıntılı özet eklenmemiş.'}
            </p>
          </div>

          {/* Bottom Section: Extension Source Resolver & Chapters */}
          <div>
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-2 border-b border-white/5">
              <div className="flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-purple-400" />
                <h3 className="text-sm font-bold text-white">Bölüm Listesi</h3>
              </div>

              {/* Source Resolver Selector */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-slate-400">Aktif Kaynak:</span>
                <select
                  value={selectedSource}
                  onChange={(e) => setSelectedSource(e.target.value)}
                  className="bg-[#181726] border border-purple-500/30 rounded-lg px-2.5 py-1 text-xs text-purple-200 focus:outline-none focus:border-purple-500"
                >
                  {installedExtensions.length === 0 ? (
                    <option value="">Kurulu eklenti yok</option>
                  ) : (
                    installedExtensions.map((ext) => (
                      <option key={ext.id} value={ext.id}>{ext.name}</option>
                    ))
                  )}
                </select>
              </div>
            </div>

            {/* Chapter Items Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-60 overflow-y-auto pr-1">
              {simulatedChapters.map((ch) => (
                <div
                  key={ch.id}
                  onClick={() => onReadChapter(series, ch)}
                  className="flex items-center justify-between p-3 rounded-xl bg-[#12111d] hover:bg-[#1a1829] border border-white/5 hover:border-purple-500/40 cursor-pointer transition-all"
                >
                  <div className="flex flex-col">
                    <span className="text-xs font-bold text-slate-200">{ch.title}</span>
                    <span className="text-[10px] text-slate-500">22 Sayfa • Türkçe / İngilizce</span>
                  </div>
                  <Play className="w-3.5 h-3.5 text-purple-400" />
                </div>
              ))}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};
