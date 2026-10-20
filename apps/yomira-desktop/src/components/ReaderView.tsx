import React, { useState, useEffect } from 'react';
import { ArrowLeft, ArrowRight, X, Maximize2, Minimize2, Settings, Columns, AlignJustify } from 'lucide-react';
import { Series, Chapter, Page } from '@yomira/core';

interface ReaderViewProps {
  series: Series;
  chapter: Chapter;
  onClose: () => void;
  onProgressUpdate: (chapterNumber: number, pageIndex: number, percentage: number) => void;
}

export const ReaderView: React.FC<ReaderViewProps> = ({
  series,
  chapter,
  onClose,
  onProgressUpdate
}) => {
  // Webtoons default to vertical scroll; Manga/Comics default to dual-page on PC
  const [mode, setMode] = useState<'WEBTOON' | 'DOUBLE_PAGE'>(
    series.type === 'WEBTOON' || series.type === 'MANHWA' ? 'WEBTOON' : 'DOUBLE_PAGE'
  );
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);

  // Simulated 12 page spreads with high-contrast comic panels
  const totalPages = chapter.pageCount || 16;
  const pages: Page[] = Array.from({ length: totalPages }, (_, i) => ({
    index: i + 1,
    imageUrl: `https://picsum.photos/seed/${series.id}-${chapter.chapterNumber}-${i + 1}/900/1300`
  }));

  // Keyboard navigation for PC
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose();
      } else if (e.key === 'ArrowRight' || e.key === 'd') {
        nextPage();
      } else if (e.key === 'ArrowLeft' || e.key === 'a') {
        prevPage();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [currentPage, mode]);

  const nextPage = () => {
    const step = mode === 'DOUBLE_PAGE' ? 2 : 1;
    if (currentPage + step <= totalPages) {
      const next = currentPage + step;
      setCurrentPage(next);
      onProgressUpdate(chapter.chapterNumber, next, Math.round((next / totalPages) * 100));
    }
  };

  const prevPage = () => {
    const step = mode === 'DOUBLE_PAGE' ? 2 : 1;
    if (currentPage - step >= 1) {
      const prev = currentPage - step;
      setCurrentPage(prev);
      onProgressUpdate(chapter.chapterNumber, prev, Math.round((prev / totalPages) * 100));
    }
  };

  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen();
      setIsFullscreen(true);
    } else {
      document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-[#050508] text-white flex flex-col select-none overflow-hidden animate-fadeIn">
      
      {/* Top Floating Control Bar */}
      <div className="h-12 bg-[#0c0b14]/90 backdrop-blur-md border-b border-purple-900/30 px-4 flex items-center justify-between z-20">
        
        {/* Back & Series Info */}
        <div className="flex items-center gap-3">
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-300 hover:text-white transition-all cursor-pointer"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div>
            <span className="text-xs font-bold text-white line-clamp-1">{series.canonicalTitle}</span>
            <span className="text-[10px] text-purple-400 font-semibold">{chapter.title}</span>
          </div>
        </div>

        {/* Reader Mode & Page Counter */}
        <div className="flex items-center gap-4">
          
          <div className="flex items-center bg-[#181726] border border-white/10 rounded-lg p-0.5 text-xs">
            <button
              onClick={() => setMode('WEBTOON')}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                mode === 'WEBTOON' ? 'bg-purple-600 text-white font-bold' : 'text-slate-400 hover:text-white'
              }`}
            >
              <AlignJustify className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Dikey Webtoon</span>
            </button>
            <button
              onClick={() => setMode('DOUBLE_PAGE')}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                mode === 'DOUBLE_PAGE' ? 'bg-purple-600 text-white font-bold' : 'text-slate-400 hover:text-white'
              }`}
            >
              <Columns className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">PC Çift Sayfa</span>
            </button>
          </div>

          <span className="text-xs font-semibold text-slate-400">
            Sayfa <span className="text-purple-400 font-bold">{currentPage}</span> / {totalPages}
          </span>

          <button
            onClick={toggleFullscreen}
            className="w-8 h-8 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-300 hover:text-white transition-all cursor-pointer"
            title="Tam Ekran"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>

          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-rose-950/40 hover:bg-rose-900/60 border border-rose-800/40 flex items-center justify-center text-rose-300 hover:text-white transition-all cursor-pointer"
            title="Okuyucudan Çık"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

      </div>

      {/* Reader Canvas Area */}
      <div className="flex-1 overflow-auto flex items-center justify-center relative">
        
        {/* MODE 1: Dikey Webtoon Sonsuz Akış */}
        {mode === 'WEBTOON' && (
          <div className="w-full max-w-2xl mx-auto flex flex-col items-center py-4 space-y-0">
            {pages.map((p) => (
              <img
                key={p.index}
                src={p.imageUrl}
                alt={`Sayfa ${p.index}`}
                loading="lazy"
                className="w-full h-auto shadow-2xl block"
              />
            ))}
          </div>
        )}

        {/* MODE 2: PC Geniş Ekran Çift Sayfa (Manga / Comic) */}
        {mode === 'DOUBLE_PAGE' && (
          <div className="h-full w-full max-w-6xl mx-auto flex items-center justify-center gap-2 p-4">
            
            {/* Left Page */}
            {pages[currentPage - 1] && (
              <div className="h-full flex-1 flex items-center justify-end">
                <img
                  src={pages[currentPage - 1].imageUrl}
                  alt={`Sayfa ${currentPage}`}
                  className="max-h-[85vh] max-w-full object-contain rounded-l-lg shadow-2xl border border-white/5"
                />
              </div>
            )}

            {/* Right Page */}
            {pages[currentPage] ? (
              <div className="h-full flex-1 flex items-center justify-start">
                <img
                  src={pages[currentPage].imageUrl}
                  alt={`Sayfa ${currentPage + 1}`}
                  className="max-h-[85vh] max-w-full object-contain rounded-r-lg shadow-2xl border border-white/5"
                />
              </div>
            ) : (
              <div className="h-full flex-1 flex items-center justify-center text-slate-600 text-xs italic">
                Bölüm Sonu
              </div>
            )}

            {/* Floating Navigation Arrows */}
            <button
              onClick={prevPage}
              disabled={currentPage <= 1}
              className="absolute left-6 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/70 hover:bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-xl cursor-pointer"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>

            <button
              onClick={nextPage}
              disabled={currentPage >= totalPages}
              className="absolute right-6 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/70 hover:bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-xl cursor-pointer"
            >
              <ArrowRight className="w-5 h-5" />
            </button>

          </div>
        )}

      </div>

    </div>
  );
};
