import React from 'react';
import { Star, Link as LinkIcon, AlertCircle, CheckCircle2 } from 'lucide-react';
import { Series } from '@yomira/core';

interface SeriesGridProps {
  seriesList: Series[];
  onSelectSeries: (series: Series) => void;
  installedExtensionIds: string[];
}

export const SeriesGrid: React.FC<SeriesGridProps> = ({
  seriesList,
  onSelectSeries,
  installedExtensionIds
}) => {
  if (seriesList.length === 0) {
    return (
      <div className="w-full py-16 flex flex-col items-center justify-center text-center">
        <div className="w-16 h-16 rounded-full bg-purple-950/40 border border-purple-800/30 flex items-center justify-center mb-4">
          <AlertCircle className="w-8 h-8 text-purple-400" />
        </div>
        <h3 className="text-lg font-bold text-white mb-1">Aramaya Uygun Seri Bulunamadı</h3>
        <p className="text-sm text-slate-400 max-w-sm">
          Filtreleri değiştirebilir veya arama çubuğuna farklı bir başlık yazabilirsiniz.
        </p>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
      {seriesList.map((series) => {
        // Check if an extension is bound or if any source is available
        const hasExtension = series.metadataSources.some(s => installedExtensionIds.includes(s.providerId));

        return (
          <div
            key={series.id}
            onClick={() => onSelectSeries(series)}
            className="group flex flex-col bg-[#111019] border border-white/5 hover:border-purple-500/50 rounded-xl overflow-hidden cursor-pointer transition-all duration-300 hover:-translate-y-1 hover:shadow-[0_8px_25px_rgba(124,58,237,0.25)]"
          >
            {/* Strict 2:3 Vertical Poster Aspect Ratio */}
            <div className="w-full aspect-poster bg-[#1a1829] relative overflow-hidden">
              <img
                src={series.coverUrl}
                alt={series.canonicalTitle}
                loading="lazy"
                className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                onError={(e) => {
                  // Fallback for broken image URLs
                  (e.target as HTMLElement).style.display = 'none';
                }}
              />

              {/* Status Badge: Source Needed vs Linked */}
              <div className="absolute top-2 right-2">
                {hasExtension ? (
                  <span className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-500/90 text-white text-[9px] font-bold backdrop-blur-md shadow-md">
                    <CheckCircle2 className="w-2.5 h-2.5" />
                    <span>Bağlı</span>
                  </span>
                ) : (
                  <span className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-500/90 text-black text-[9px] font-bold backdrop-blur-md shadow-md">
                    <AlertCircle className="w-2.5 h-2.5" />
                    <span>Kaynak Seç</span>
                  </span>
                )}
              </div>

              {/* Rating Pill */}
              <div className="absolute bottom-2 left-2 px-1.5 py-0.5 rounded bg-black/80 backdrop-blur-md text-[9px] font-extrabold text-amber-400 flex items-center gap-0.5">
                <Star className="w-2.5 h-2.5 fill-amber-400 text-amber-400" />
                <span>{series.rating ? `${series.rating}★` : '4.8★'}</span>
              </div>

              {/* Type tag */}
              <div className="absolute bottom-2 right-2 px-1.5 py-0.5 rounded bg-black/80 backdrop-blur-md text-[9px] font-bold text-slate-300 uppercase">
                {series.type}
              </div>
            </div>

            {/* Series Metadata Info */}
            <div className="p-3 flex flex-col flex-1 justify-between gap-1">
              <h3 
                className="text-xs font-bold text-white line-clamp-1 group-hover:text-purple-300 transition-colors"
                title={series.canonicalTitle}
              >
                {series.canonicalTitle}
              </h3>

              <div className="flex items-center justify-between text-[10px] text-slate-400">
                <span className="line-clamp-1">{series.genres[0] || 'Genel'}</span>
                <span>{series.totalChapters ? `${series.totalChapters} Bölüm` : 'Güncel'}</span>
              </div>
            </div>

          </div>
        );
      })}
    </div>
  );
};
