import React from 'react';
import { Bookmark, Play, CheckCircle, Clock } from 'lucide-react';
import { Series, LibraryItem, ReadingProgress } from '@yomira/core';

interface MobileLibraryViewProps {
  library: LibraryItem[];
  allSeries: Series[];
  readingProgress: ReadingProgress[];
  onSelectSeries: (series: Series) => void;
}

export const MobileLibraryView: React.FC<MobileLibraryViewProps> = ({
  library,
  allSeries,
  readingProgress,
  onSelectSeries
}) => {
  if (library.length === 0) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-8 text-center min-h-[50vh]">
        <div className="w-16 h-16 rounded-2xl bg-purple-950/40 border border-purple-800/30 flex items-center justify-center mb-4">
          <Bookmark className="w-8 h-8 text-purple-400" />
        </div>
        <h3 className="text-base font-bold text-white mb-1">Kitaplığınız Henüz Boş</h3>
        <p className="text-xs text-slate-400 max-w-xs">
          Keşfet sekmesinden dilediğiniz manga, webtoon veya çizgi romanı kitaplığınıza ekleyin; tüm cihazlarınızda (PC ve Android) anında eşitlensin.
        </p>
      </div>
    );
  }

  return (
    <div className="p-4 space-y-3 pb-20">
      <div className="flex items-center justify-between mb-2">
        <h2 className="text-sm font-bold text-white">Okuma Listem ({library.length})</h2>
        <span className="text-[10px] text-purple-400 font-semibold">Google Drive ile Eşitlendi</span>
      </div>

      <div className="space-y-2.5">
        {library.map((item) => {
          const series = allSeries.find(s => s.id === item.seriesId);
          if (!series) return null;

          const progress = readingProgress.find(p => p.seriesId === series.id);
          const percent = progress?.percentage || 0;

          return (
            <div
              key={item.seriesId}
              onClick={() => onSelectSeries(series)}
              className="flex gap-3 p-2.5 bg-[#12111d] border border-white/5 active:border-purple-500/40 rounded-xl cursor-pointer transition-all"
            >
              {/* 2:3 Vertical Thumbnail */}
              <div className="w-14 aspect-poster rounded-lg overflow-hidden shrink-0 border border-white/10 bg-[#1a1829]">
                <img
                  src={series.coverUrl}
                  alt={series.canonicalTitle}
                  className="w-full h-full object-cover"
                />
              </div>

              {/* Series Info & Reading Bar */}
              <div className="flex-1 flex flex-col justify-between py-0.5">
                <div>
                  <h4 className="text-xs font-bold text-white line-clamp-1">{series.canonicalTitle}</h4>
                  <span className="text-[10px] text-slate-400">{series.type} • {series.genres[0] || 'Genel'}</span>
                </div>

                <div>
                  <div className="flex items-center justify-between text-[10px] text-slate-400 mb-1">
                    <span>{progress ? `Bölüm ${progress.chapterNumber}` : 'Henüz başlanmadı'}</span>
                    <span className="font-bold text-purple-300">%{percent}</span>
                  </div>
                  {/* Progress track */}
                  <div className="w-full h-1 bg-white/10 rounded-full overflow-hidden">
                    <div 
                      className="h-full bg-gradient-to-r from-purple-600 to-fuchsia-500 rounded-full"
                      style={{ width: `${percent}%` }}
                    />
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-center pl-1">
                <Play className="w-4 h-4 text-purple-400 fill-purple-400/20" />
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
