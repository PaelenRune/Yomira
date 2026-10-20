import React from 'react';
import { Play, Info, Sparkles, Star } from 'lucide-react';
import { Series } from '@yomira/core';

interface HeroBannerProps {
  series?: Series;
  onOpenDetail: (series: Series) => void;
  onStartReading: (series: Series) => void;
}

export const HeroBanner: React.FC<HeroBannerProps> = ({
  series,
  onOpenDetail,
  onStartReading
}) => {
  if (!series) return null;

  return (
    <div className="relative w-full rounded-2xl overflow-hidden border border-purple-900/30 bg-[#0f0e18] mb-8 shadow-2xl">
      {/* Background Cover Backdrop with Gradient Overlays */}
      <div 
        className="absolute inset-0 bg-cover bg-center opacity-25 filter blur-md scale-105"
        style={{ backgroundImage: `url(${series.coverUrl})` }}
      />
      <div className="absolute inset-0 bg-gradient-to-r from-[#07070a] via-[#07070a]/85 to-transparent" />
      <div className="absolute inset-0 bg-gradient-to-t from-[#07070a] via-transparent to-transparent" />

      {/* Content Container */}
      <div className="relative z-10 p-8 flex flex-col md:flex-row items-center gap-8">
        
        {/* Strict 2:3 Vertical Cover Poster */}
        <div className="w-44 shrink-0 aspect-poster rounded-xl overflow-hidden border border-purple-500/30 shadow-[0_0_25px_rgba(124,58,237,0.3)] group relative">
          <img 
            src={series.coverUrl} 
            alt={series.canonicalTitle}
            className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
          <div className="absolute top-2 left-2 px-2 py-0.5 rounded bg-black/75 backdrop-blur-md text-[10px] font-bold text-amber-400 flex items-center gap-1 border border-amber-500/20">
            <Star className="w-3 h-3 fill-amber-400 text-amber-400" />
            <span>{series.rating ? `${series.rating}★` : '4.9★'}</span>
          </div>
        </div>

        {/* Series Info & Actions */}
        <div className="flex-1 flex flex-col items-start justify-center">
          <div className="flex items-center gap-2 mb-2">
            <span className="px-2.5 py-0.5 rounded-full bg-purple-600/20 border border-purple-500/40 text-purple-300 text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
              <Sparkles className="w-3 h-3" />
              <span>Öne Çıkan Seri • {series.type}</span>
            </span>
            {series.contentRating === 'MATURE_ECCHI' && (
              <span className="px-2 py-0.5 rounded bg-amber-500/20 border border-amber-500/30 text-amber-400 text-[10px] font-bold">
                17+ Mature
              </span>
            )}
          </div>

          <h1 className="text-3xl md:text-4xl font-extrabold text-white mb-3 tracking-tight">
            {series.canonicalTitle}
          </h1>

          <p className="text-sm text-slate-300 line-clamp-3 max-w-2xl mb-4 leading-relaxed">
            {series.description.replace(/<[^>]*>?/gm, '') || 'Evrensel metadata kataloğunda listelenen popüler çizgi roman.'}
          </p>

          {/* Genre Badges */}
          <div className="flex flex-wrap gap-1.5 mb-6">
            {series.genres.slice(0, 4).map((g) => (
              <span key={g} className="px-2.5 py-1 rounded-md bg-white/5 border border-white/10 text-xs font-medium text-slate-300">
                {g}
              </span>
            ))}
            {series.author && (
              <span className="px-2.5 py-1 rounded-md bg-purple-950/30 border border-purple-800/20 text-xs font-medium text-purple-300">
                Yazar: {series.author}
              </span>
            )}
          </div>

          {/* Call to Actions */}
          <div className="flex items-center gap-3">
            <button
              onClick={() => onStartReading(series)}
              className="flex items-center gap-2 px-6 py-2.5 rounded-xl bg-gradient-to-r from-purple-600 to-fuchsia-600 hover:from-purple-500 hover:to-fuchsia-500 text-white font-bold text-sm shadow-[0_4px_20px_rgba(124,58,237,0.4)] transition-all cursor-pointer"
            >
              <Play className="w-4 h-4 fill-white" />
              <span>Okumaya Başla</span>
            </button>

            <button
              onClick={() => onOpenDetail(series)}
              className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-slate-200 font-semibold text-sm transition-all cursor-pointer"
            >
              <Info className="w-4 h-4 text-purple-400" />
              <span>Detaylar & Kaynaklar</span>
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};
