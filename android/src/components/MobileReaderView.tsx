import React, { useState } from 'react';
import { ArrowLeft, X, AlignJustify, BookOpen, Layers } from 'lucide-react';
import { Series, Chapter, Page } from '@yomira/core';

interface MobileReaderViewProps {
  series: Series;
  chapter: Chapter;
  onClose: () => void;
  onProgressUpdate: (chapterNumber: number, pageIndex: number, percentage: number) => void;
}

export const MobileReaderView: React.FC<MobileReaderViewProps> = ({
  series,
  chapter,
  onClose,
  onProgressUpdate
}) => {
  const [showControls, setShowControls] = useState<boolean>(true);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const totalPages = chapter.pageCount || 18;

  // Pages
  const pages: Page[] = Array.from({ length: totalPages }, (_, i) => ({
    index: i + 1,
    imageUrl: `https://picsum.photos/seed/${series.id}-${chapter.chapterNumber}-${i + 1}/720/1100`
  }));

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const scrollPercent = el.scrollTop / (el.scrollHeight - el.clientHeight);
    const estimatedPage = Math.min(Math.max(1, Math.ceil(scrollPercent * totalPages)), totalPages);
    if (estimatedPage !== currentPage) {
      setCurrentPage(estimatedPage);
      onProgressUpdate(chapter.chapterNumber, estimatedPage, Math.round(scrollPercent * 100));
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-[#050508] text-white flex flex-col select-none overflow-hidden">
      
      {/* Top Touch Bar (Collapsible on tap) */}
      {showControls && (
        <div className="h-12 bg-[#0c0b14]/95 backdrop-blur-md border-b border-purple-900/30 px-3 flex items-center justify-between z-20 transition-all">
          <div className="flex items-center gap-2 truncate">
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg bg-white/5 active:bg-white/10 text-slate-300"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div className="truncate">
              <span className="text-xs font-bold text-white block truncate">{series.canonicalTitle}</span>
              <span className="text-[10px] text-purple-400 font-semibold">{chapter.title}</span>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <span className="text-[11px] font-bold text-slate-400">
              {currentPage} / {totalPages}
            </span>
            <button
              onClick={onClose}
              className="p-1.5 rounded-lg bg-rose-950/40 text-rose-300"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Infinite Scroll Webtoon Container */}
      <div 
        className="flex-1 overflow-y-auto overflow-x-hidden touch-pan-y"
        onScroll={handleScroll}
        onClick={() => setShowControls(prev => !prev)}
      >
        <div className="w-full flex flex-col items-center">
          {pages.map((p) => (
            <img
              key={p.index}
              src={p.imageUrl}
              alt={`Sayfa ${p.index}`}
              loading="lazy"
              className="w-full h-auto block"
            />
          ))}
        </div>
      </div>

      {/* Bottom Floating Page Pill */}
      {!showControls && (
        <div className="fixed bottom-4 right-4 z-20 px-3 py-1 rounded-full bg-black/75 backdrop-blur-md border border-white/10 text-xs font-bold text-purple-300 shadow-xl pointer-events-none">
          {currentPage} / {totalPages}
        </div>
      )}

    </div>
  );
};
