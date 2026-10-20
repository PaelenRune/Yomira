import React from 'react';
import { Star, CheckCircle2, AlertCircle } from 'lucide-react';
import { Series } from '@yomira/core';

interface MobileSeriesCardProps {
  series: Series;
  onClick: () => void;
  isBound: boolean;
}

export const MobileSeriesCard: React.FC<MobileSeriesCardProps> = ({
  series,
  onClick,
  isBound
}) => {
  return (
    <div
      onClick={onClick}
      className="flex flex-col bg-[#111019] border border-white/5 active:scale-95 rounded-xl overflow-hidden cursor-pointer transition-all duration-200"
    >
      {/* Strict 2:3 Vertical Poster Aspect Ratio */}
      <div className="w-full aspect-poster bg-[#1a1829] relative overflow-hidden">
        <img
          src={series.coverUrl}
          alt={series.canonicalTitle}
          loading="lazy"
          className="w-full h-full object-cover"
          onError={(e) => {
            (e.target as HTMLElement).style.display = 'none';
          }}
        />

        {/* Source Badge */}
        <div className="absolute top-1.5 right-1.5">
          {isBound ? (
            <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-emerald-500/90 text-white text-[8px] font-extrabold backdrop-blur-md">
              <CheckCircle2 className="w-2.5 h-2.5" />
              <span>Bağlı</span>
            </span>
          ) : (
            <span className="flex items-center gap-0.5 px-1.5 py-0.5 rounded bg-amber-500/90 text-black text-[8px] font-extrabold backdrop-blur-md">
              <AlertCircle className="w-2.5 h-2.5" />
              <span>Kaynak</span>
            </span>
          )}
        </div>

        {/* Rating Pill */}
        <div className="absolute bottom-1.5 left-1.5 px-1.5 py-0.5 rounded bg-black/80 backdrop-blur-md text-[8px] font-extrabold text-amber-400 flex items-center gap-0.5">
          <Star className="w-2.5 h-2.5 fill-amber-400 text-amber-400" />
          <span>{series.rating ? `${series.rating}★` : '4.8★'}</span>
        </div>

        {/* Type pill */}
        <div className="absolute bottom-1.5 right-1.5 px-1 py-0.5 rounded bg-black/80 backdrop-blur-md text-[8px] font-bold text-slate-300 uppercase">
          {series.type}
        </div>
      </div>

      {/* Title & Info */}
      <div className="p-2 flex flex-col gap-0.5">
        <h3 className="text-xs font-bold text-white line-clamp-1 leading-tight">
          {series.canonicalTitle}
        </h3>
        <div className="flex items-center justify-between text-[10px] text-slate-400">
          <span className="line-clamp-1">{series.genres[0] || 'Genel'}</span>
          <span>{series.totalChapters ? `${series.totalChapters} Bölüm` : 'Güncel'}</span>
        </div>
      </div>
    </div>
  );
};
