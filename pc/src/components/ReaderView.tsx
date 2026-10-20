import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  X,
  Maximize2,
  Minimize2,
  Columns,
  AlignJustify,
  Keyboard,
  ZoomIn,
  ZoomOut,
  RotateCcw,
  BookOpen
} from 'lucide-react';
import { Series, Chapter, Page, ReadingProgress } from '@yomira/core';

interface ReaderViewProps {
  series: Series;
  chapter: Chapter;
  onClose: () => void;
  onProgressUpdate: (chapterNumber: number, pageIndex: number, percentage: number) => void;
}

type ReaderMode = 'WEBTOON' | 'DOUBLE_PAGE';
type ReadingDirection = 'LTR' | 'RTL';

const ZOOM_MIN = 0.5;
const ZOOM_MAX = 3;
const ZOOM_STEP = 0.25;
const WHEEL_FLIP_LOCK_MS = 240;

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value));

/**
 * Returns the first page number of the spread that contains `page`.
 * With `coverSingle`, page 1 stands alone and spreads begin on even pages (2-3, 4-5...).
 * Without it, spreads begin on odd pages (1-2, 3-4...).
 */
const getSpreadStart = (page: number, coverSingle: boolean): number => {
  if (page <= 1) return 1;
  if (coverSingle) return page % 2 === 0 ? page : page - 1;
  return page % 2 === 1 ? page : page - 1;
};

const ShortcutRow: React.FC<{ keys: string[]; label: string }> = ({ keys, label }) => (
  <div className="flex items-center justify-between gap-3 rounded-lg bg-white/[0.03] px-3 py-2">
    <span className="text-xs font-medium text-slate-300">{label}</span>
    <span className="flex items-center gap-1">
      {keys.map((key) => (
        <kbd
          key={key}
          className="min-w-[24px] rounded-md border border-white/15 bg-white/[0.06] px-1.5 py-0.5 text-center text-[10px] font-bold text-purple-200 shadow-sm"
        >
          {key}
        </kbd>
      ))}
    </span>
  </div>
);

export const ReaderView: React.FC<ReaderViewProps> = ({
  series,
  chapter,
  onClose,
  onProgressUpdate
}) => {
  const totalPages = Math.max(1, chapter.pageCount || 16);
  const progressStorageKey = `yomira:progress:${series.id}`;

  // Webtoons default to vertical scroll; Manga/Comics default to dual-page on PC
  const [mode, setMode] = useState<ReaderMode>(
    series.type === 'WEBTOON' || series.type === 'MANHWA' ? 'WEBTOON' : 'DOUBLE_PAGE'
  );

  // Japanese manga is read right-to-left, western comics left-to-right.
  const [direction, setDirection] = useState<ReadingDirection>(
    series.type === 'MANGA' || series.type === 'MANHUA' ? 'RTL' : 'LTR'
  );

  const [currentPage, setCurrentPage] = useState<number>(() => {
    try {
      const raw = localStorage.getItem(progressStorageKey);
      if (raw) {
        const saved = JSON.parse(raw) as Partial<ReadingProgress>;
        if (saved.chapterId === chapter.id && typeof saved.pageIndex === 'number') {
          return clamp(Math.round(saved.pageIndex), 1, totalPages);
        }
      }
    } catch {
      /* localStorage may be unavailable; ignore and start at page 1 */
    }
    return 1;
  });

  const [coverSingle, setCoverSingle] = useState<boolean>(true);
  const [zoom, setZoom] = useState<number>(1);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [showHelp, setShowHelp] = useState<boolean>(false);

  const containerRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const pageRefs = useRef<Array<HTMLImageElement | null>>([]);
  const onProgressRef = useRef(onProgressUpdate);
  const wheelLockRef = useRef<number>(0);
  const scrollFrameRef = useRef<number | null>(null);

  // Keep the latest callback without re-running the progress effect on every App render.
  useEffect(() => {
    onProgressRef.current = onProgressUpdate;
  }, [onProgressUpdate]);

  const pages: Page[] = useMemo(
    () =>
      Array.from({ length: totalPages }, (_, i) => ({
        index: i + 1,
        imageUrl: `https://picsum.photos/seed/${series.id}-${chapter.chapterNumber}-${i + 1}/900/1300`
      })),
    [totalPages, series.id, chapter.chapterNumber]
  );

  // Persist reading progress instantly, both to the parent and to localStorage.
  useEffect(() => {
    const percentage = Math.round((currentPage / totalPages) * 100);

    onProgressRef.current(chapter.chapterNumber, currentPage, percentage);

    const progress: ReadingProgress = {
      seriesId: series.id,
      chapterId: chapter.id,
      chapterNumber: chapter.chapterNumber,
      pageIndex: currentPage,
      totalPageCount: totalPages,
      percentage,
      lastReadAt: Date.now(),
      isCompleted: currentPage >= totalPages
    };

    try {
      localStorage.setItem(progressStorageKey, JSON.stringify(progress));
    } catch {
      /* Storage quota / privacy mode: onProgressUpdate still carries the progress */
    }
  }, [
    currentPage,
    totalPages,
    chapter.chapterNumber,
    chapter.id,
    series.id,
    progressStorageKey
  ]);

  // Keep page image refs tidy when the chapter changes.
  useEffect(() => {
    pageRefs.current = [];
  }, [pages]);

  const scrollToPage = useCallback((page: number) => {
    window.requestAnimationFrame(() => {
      pageRefs.current[page - 1]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }, []);

  const goNext = useCallback(() => {
    if (mode === 'DOUBLE_PAGE') {
      const start = getSpreadStart(currentPage, coverSingle);
      const nextStart = start === 1 && coverSingle ? 2 : start + 2;
      if (nextStart <= totalPages) setCurrentPage(nextStart);
      return;
    }

    const next = Math.min(totalPages, currentPage + 1);
    if (next !== currentPage) {
      setCurrentPage(next);
      scrollToPage(next);
    }
  }, [mode, currentPage, coverSingle, totalPages, scrollToPage]);

  const goPrev = useCallback(() => {
    if (mode === 'DOUBLE_PAGE') {
      const start = getSpreadStart(currentPage, coverSingle);
      if (start <= 1) return;
      const prevStart = start === 2 && coverSingle ? 1 : Math.max(1, start - 2);
      setCurrentPage(prevStart);
      return;
    }

    const prev = Math.max(1, currentPage - 1);
    if (prev !== currentPage) {
      setCurrentPage(prev);
      scrollToPage(prev);
    }
  }, [mode, currentPage, coverSingle, scrollToPage]);

  const toggleMode = useCallback(() => {
    const nextMode: ReaderMode = mode === 'WEBTOON' ? 'DOUBLE_PAGE' : 'WEBTOON';
    if (nextMode === 'DOUBLE_PAGE') {
      setCurrentPage((page) => getSpreadStart(page, coverSingle));
    }
    setMode(nextMode);
  }, [mode, coverSingle]);

  const toggleDirection = useCallback(() => {
    setDirection((prev) => (prev === 'LTR' ? 'RTL' : 'LTR'));
  }, []);

  const toggleCoverSingle = useCallback(() => {
    const next = !coverSingle;
    setCoverSingle(next);
    setCurrentPage((page) => getSpreadStart(page, next));
  }, [coverSingle]);

  const zoomIn = useCallback(() => {
    setZoom((prev) => clamp(Math.round((prev + ZOOM_STEP) * 100) / 100, ZOOM_MIN, ZOOM_MAX));
  }, []);

  const zoomOut = useCallback(() => {
    setZoom((prev) => clamp(Math.round((prev - ZOOM_STEP) * 100) / 100, ZOOM_MIN, ZOOM_MAX));
  }, []);

  const resetZoom = useCallback(() => setZoom(1), []);

  const toggleFullscreen = useCallback(() => {
    try {
      if (!document.fullscreenElement) {
        void containerRef.current?.requestFullscreen().catch(() => {});
      } else {
        void document.exitFullscreen().catch(() => {});
      }
    } catch {
      /* Fullscreen can be rejected by the host window; ignore */
    }
  }, []);

  useEffect(() => {
    const handleFullscreenChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => document.removeEventListener('fullscreenchange', handleFullscreenChange);
  }, []);

  // Keyboard shortcuts for a desktop reading experience.
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }

      if (e.ctrlKey || e.metaKey || e.altKey) return;

      const key = e.key;
      const isHelpToggle =
        key === '?' || key === 'h' || key === 'H' || (key === '/' && e.shiftKey);

      if (key === 'Escape') {
        e.preventDefault();
        if (showHelp) {
          setShowHelp(false);
        } else if (document.fullscreenElement) {
          void document.exitFullscreen().catch(() => {});
        } else {
          onClose();
        }
        return;
      }

      if (showHelp) {
        if (isHelpToggle) {
          e.preventDefault();
          setShowHelp(false);
        }
        return;
      }

      switch (key) {
        case 'ArrowRight':
        case 'd':
        case 'D':
          e.preventDefault();
          goNext();
          break;
        case 'ArrowLeft':
        case 'a':
        case 'A':
          e.preventDefault();
          goPrev();
          break;
        case 'f':
        case 'F':
        case 'F11':
          e.preventDefault();
          toggleFullscreen();
          break;
        case 'w':
        case 'W':
          e.preventDefault();
          toggleMode();
          break;
        case 'r':
        case 'R':
          e.preventDefault();
          toggleDirection();
          break;
        case '+':
        case '=':
          e.preventDefault();
          zoomIn();
          break;
        case '-':
        case '_':
          e.preventDefault();
          zoomOut();
          break;
        case '0':
          e.preventDefault();
          resetZoom();
          break;
        default:
          if (isHelpToggle) {
            e.preventDefault();
            setShowHelp(true);
          }
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [
    showHelp,
    onClose,
    goNext,
    goPrev,
    toggleFullscreen,
    toggleMode,
    toggleDirection,
    zoomIn,
    zoomOut,
    resetZoom
  ]);

  // Mouse wheel: double-page mode flips spreads; webtoon keeps native vertical scroll.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;

    const handleWheel = (e: WheelEvent) => {
      if (showHelp || mode !== 'DOUBLE_PAGE') return;
      if (Math.abs(e.deltaY) < 10) return;

      e.preventDefault();

      const now = Date.now();
      if (now - wheelLockRef.current < WHEEL_FLIP_LOCK_MS) return;
      wheelLockRef.current = now;

      if (e.deltaY > 0) goNext();
      else goPrev();
    };

    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [mode, showHelp, goNext, goPrev]);

  // Webtoon scroll tracking keeps the page counter and saved progress in sync.
  const handleScroll = useCallback(() => {
    if (mode !== 'WEBTOON') return;
    if (scrollFrameRef.current !== null) return;

    scrollFrameRef.current = window.requestAnimationFrame(() => {
      scrollFrameRef.current = null;
      const container = scrollRef.current;
      if (!container) return;

      const threshold = container.getBoundingClientRect().top + container.clientHeight * 0.35;
      let visible = 1;

      pageRefs.current.forEach((el, idx) => {
        if (!el) return;
        const rect = el.getBoundingClientRect();
        if (rect.top <= threshold && rect.bottom >= threshold) visible = idx + 1;
      });

      setCurrentPage((prev) => (prev === visible ? prev : visible));
    });
  }, [mode]);

  useEffect(
    () => () => {
      if (scrollFrameRef.current !== null) window.cancelAnimationFrame(scrollFrameRef.current);
    },
    []
  );

  // Restore the scroll position when entering webtoon mode (initial mount or mode switch).
  useEffect(() => {
    if (mode !== 'WEBTOON') return;
    const timer = window.setTimeout(() => scrollToPage(currentPage), 250);
    return () => window.clearTimeout(timer);
    // currentPage is intentionally read once per mode switch; scroll tracking handles the rest.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, scrollToPage]);

  const spreadStart = getSpreadStart(currentPage, coverSingle);
  const spreadPages = useMemo(() => {
    if (mode !== 'DOUBLE_PAGE') return [] as Page[];
    if (coverSingle && spreadStart === 1) return pages.slice(0, 1);
    return pages.slice(spreadStart - 1, spreadStart + 1);
  }, [mode, coverSingle, spreadStart, pages]);

  const pageLabel =
    mode === 'DOUBLE_PAGE' && spreadPages.length > 1
      ? `${spreadPages[0].index}-${spreadPages[spreadPages.length - 1].index}`
      : `${mode === 'DOUBLE_PAGE' ? spreadStart : currentPage}`;

  const percentage = Math.round((currentPage / totalPages) * 100);
  const isRtl = direction === 'RTL';

  return (
    <div
      ref={containerRef}
      className="fixed inset-0 z-50 bg-[#050508] text-white flex flex-col select-none overflow-hidden animate-fadeIn"
    >
      {/* Top Floating Control Bar */}
      <div className="h-14 bg-[#0c0b14]/90 backdrop-blur-md border-b border-purple-900/30 px-4 flex items-center justify-between gap-3 z-20 shrink-0">
        {/* Back & Series Info */}
        <div className="flex items-center gap-3 min-w-0">
          <button
            onClick={onClose}
            className="w-8 h-8 shrink-0 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-300 hover:text-white transition-all cursor-pointer"
            title="Okuyucudan Çık"
            aria-label="Okuyucudan Çık"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className="min-w-0">
            <div className="text-xs font-bold text-white truncate max-w-[220px]">
              {series.canonicalTitle}
            </div>
            <div className="text-[10px] text-purple-400 font-semibold truncate max-w-[220px]">
              {chapter.title || `Bölüm ${chapter.chapterNumber}`}
            </div>
          </div>
        </div>

        {/* Reader Controls */}
        <div className="flex items-center gap-2 shrink-0">
          {/* Mode Switch */}
          <div className="hidden sm:flex items-center bg-[#181726] border border-white/10 rounded-lg p-0.5 text-xs">
            <button
              onClick={() => {
                if (mode !== 'WEBTOON') {
                  setMode('WEBTOON');
                }
              }}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                mode === 'WEBTOON'
                  ? 'bg-purple-600 text-white font-bold'
                  : 'text-slate-400 hover:text-white'
              }`}
              title="Dikey Webtoon (W)"
            >
              <AlignJustify className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Webtoon</span>
            </button>
            <button
              onClick={() => {
                if (mode !== 'DOUBLE_PAGE') {
                  setMode('DOUBLE_PAGE');
                  setCurrentPage((page) => getSpreadStart(page, coverSingle));
                }
              }}
              className={`flex items-center gap-1 px-2.5 py-1 rounded-md transition-all cursor-pointer ${
                mode === 'DOUBLE_PAGE'
                  ? 'bg-purple-600 text-white font-bold'
                  : 'text-slate-400 hover:text-white'
              }`}
              title="Çift Sayfa (W)"
            >
              <Columns className="w-3.5 h-3.5" />
              <span className="hidden xl:inline">Çift Sayfa</span>
            </button>
          </div>

          {/* Direction & Cover (spread only) */}
          {mode === 'DOUBLE_PAGE' && (
            <>
              <button
                onClick={toggleDirection}
                className="h-8 px-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/5 flex items-center gap-1.5 text-[10px] font-bold text-slate-300 hover:text-white transition-all cursor-pointer"
                title={`Okuma yönü: ${isRtl ? 'Sağdan Sola (Manga)' : 'Soldan Sağa (Batı)'} — R`}
              >
                {isRtl ? <ArrowLeft className="w-3.5 h-3.5" /> : <ArrowRight className="w-3.5 h-3.5" />}
                <span className="hidden lg:inline">{isRtl ? 'Sağdan Sola' : 'Soldan Sağa'}</span>
              </button>

              <button
                onClick={toggleCoverSingle}
                className={`h-8 px-2 rounded-lg border flex items-center gap-1.5 text-[10px] font-bold transition-all cursor-pointer ${
                  coverSingle
                    ? 'bg-purple-600/20 border-purple-500/40 text-purple-300'
                    : 'bg-white/5 border-white/5 text-slate-400 hover:text-white'
                }`}
                title={coverSingle ? 'Kapak tek sayfa gösteriliyor' : 'Kapak çift sayfa açılıyor'}
              >
                <BookOpen className="w-3.5 h-3.5" />
                <span className="hidden lg:inline">{coverSingle ? 'Tek Kapak' : 'Çift Kapak'}</span>
              </button>
            </>
          )}

          {/* Zoom */}
          <div className="flex items-center bg-[#181726] border border-white/10 rounded-lg p-0.5">
            <button
              onClick={zoomOut}
              disabled={zoom <= ZOOM_MIN}
              className="w-7 h-7 rounded-md flex items-center justify-center text-slate-400 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-all cursor-pointer"
              title="Uzaklaştır (-)"
              aria-label="Uzaklaştır"
            >
              <ZoomOut className="w-3.5 h-3.5" />
            </button>
            <button
              onClick={resetZoom}
              className="w-11 h-7 rounded-md text-[10px] font-bold text-purple-300 hover:bg-white/5 transition-all cursor-pointer"
              title="Zoom sıfırla (0)"
            >
              {Math.round(zoom * 100)}%
            </button>
            <button
              onClick={zoomIn}
              disabled={zoom >= ZOOM_MAX}
              className="w-7 h-7 rounded-md flex items-center justify-center text-slate-400 hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition-all cursor-pointer"
              title="Yakınlaştır (+)"
              aria-label="Yakınlaştır"
            >
              <ZoomIn className="w-3.5 h-3.5" />
            </button>
          </div>

          <span className="text-xs font-semibold text-slate-400 whitespace-nowrap hidden md:inline">
            Sayfa <span className="text-purple-400 font-bold">{pageLabel}</span> / {totalPages}
          </span>

          <button
            onClick={() => setShowHelp((prev) => !prev)}
            className={`w-8 h-8 rounded-lg flex items-center justify-center transition-all cursor-pointer ${
              showHelp
                ? 'bg-purple-600 text-white'
                : 'bg-white/5 hover:bg-white/10 text-slate-300 hover:text-white'
            }`}
            title="Klavye Kısayolları (?)"
            aria-label="Klavye Kısayolları"
          >
            <Keyboard className="w-4 h-4" />
          </button>

          <button
            onClick={toggleFullscreen}
            className="w-8 h-8 rounded-lg bg-white/5 hover:bg-white/10 flex items-center justify-center text-slate-300 hover:text-white transition-all cursor-pointer"
            title="Tam Ekran (F / F11)"
            aria-label="Tam Ekran"
          >
            {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
          </button>

          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-rose-950/40 hover:bg-rose-900/60 border border-rose-800/40 flex items-center justify-center text-rose-300 hover:text-white transition-all cursor-pointer"
            title="Okuyucudan Çık (Esc)"
            aria-label="Okuyucudan Çık"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="h-0.5 bg-white/5 shrink-0 z-20">
        <div
          className="h-full bg-gradient-to-r from-purple-500 to-fuchsia-400 transition-all duration-300"
          style={{ width: `${percentage}%` }}
        />
      </div>

      {/* Reader Canvas Area */}
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="flex-1 overflow-auto flex items-start justify-center relative"
      >
        {/* MODE 1: Dikey Webtoon Sonsuz Akış */}
        {mode === 'WEBTOON' && (
          <div className="w-full flex justify-center py-4">
            <div
              className="flex flex-col items-center transition-[width] duration-200"
              style={{ width: `${zoom * 100}%`, maxWidth: `${42 * zoom}rem` }}
            >
              {pages.map((p, idx) => (
                <img
                  key={p.index}
                  ref={(el) => {
                    pageRefs.current[idx] = el;
                  }}
                  src={p.imageUrl}
                  alt={`Sayfa ${p.index}`}
                  loading="lazy"
                  className="w-full h-auto shadow-2xl block bg-[#0b0a12]"
                />
              ))}
            </div>
          </div>
        )}

        {/* MODE 2: PC Geniş Ekran Çift Sayfa (Manga / Comic) */}
        {mode === 'DOUBLE_PAGE' && (
          <div className="min-h-full w-full flex items-center justify-center p-4">
            <div
              className={`flex items-center justify-center gap-1.5 transition-transform duration-200 ${
                isRtl ? 'flex-row-reverse' : ''
              }`}
              style={{ transform: `scale(${zoom})`, transformOrigin: 'center center' }}
            >
              {spreadPages.map((pageItem, idx) => {
                const isFirst = idx === 0;
                const isLast = idx === spreadPages.length - 1;
                const radiusClass = isRtl
                  ? `${isFirst ? 'rounded-r-lg' : ''} ${isLast ? 'rounded-l-lg' : ''}`
                  : `${isFirst ? 'rounded-l-lg' : ''} ${isLast ? 'rounded-r-lg' : ''}`;

                return (
                  <div key={pageItem.index} className="relative flex flex-col items-center">
                    <img
                      src={pageItem.imageUrl}
                      alt={`Sayfa ${pageItem.index}`}
                      className={`max-h-[82vh] max-w-full w-auto object-contain shadow-2xl border border-white/5 bg-[#0b0a12] ${radiusClass}`}
                    />
                    <span className="absolute bottom-2 right-2 rounded-md bg-black/70 px-2 py-0.5 text-[10px] font-bold text-white/80 backdrop-blur-sm">
                      {pageItem.index}
                    </span>
                    {coverSingle && spreadStart === 1 && spreadPages.length === 1 && (
                      <span className="absolute top-2 left-2 rounded-md bg-purple-600/80 px-2 py-0.5 text-[10px] font-bold text-white backdrop-blur-sm">
                        KAPAK
                      </span>
                    )}
                  </div>
                );
              })}

              {spreadPages.length === 0 && (
                <div className="text-slate-600 text-xs italic">Bölüm Sonu</div>
              )}
            </div>

            {/* Floating Navigation Arrows */}
            <button
              onClick={goPrev}
              disabled={spreadStart <= 1}
              className="absolute left-4 md:left-6 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/70 hover:bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-xl cursor-pointer"
              title="Önceki Sayfa (← / A)"
              aria-label="Önceki Sayfa"
            >
              <ArrowLeft className="w-5 h-5" />
            </button>

            <button
              onClick={goNext}
              disabled={spreadStart + spreadPages.length - 1 >= totalPages}
              className="absolute right-4 md:right-6 top-1/2 -translate-y-1/2 w-11 h-11 rounded-full bg-black/70 hover:bg-purple-950/80 border border-purple-500/30 flex items-center justify-center text-white disabled:opacity-30 disabled:cursor-not-allowed transition-all shadow-xl cursor-pointer"
              title="Sonraki Sayfa (→ / D)"
              aria-label="Sonraki Sayfa"
            >
              <ArrowRight className="w-5 h-5" />
            </button>
          </div>
        )}

        {/* Bottom hint */}
        <div className="pointer-events-none absolute bottom-3 left-1/2 -translate-x-1/2 rounded-full bg-black/55 border border-white/10 px-3 py-1 text-[10px] text-slate-400 backdrop-blur-sm hidden md:flex items-center gap-2">
          <Keyboard className="w-3 h-3" />
          <span>
            {mode === 'DOUBLE_PAGE'
              ? 'Tekerlek ile sayfa çevir'
              : 'Dikey kaydır'}
            {' · '}
            <span className="text-purple-300 font-bold">?</span> kısayollar
          </span>
        </div>
      </div>

      {/* Keyboard Shortcut Help Modal */}
      {showHelp && (
        <div
          className="absolute inset-0 z-40 flex items-center justify-center bg-black/65 backdrop-blur-sm p-6 animate-fadeIn"
          onClick={() => setShowHelp(false)}
          role="presentation"
        >
          <div
            role="dialog"
            aria-modal="true"
            aria-label="Klavye Kısayolları"
            className="w-full max-w-lg overflow-hidden rounded-2xl border border-purple-500/30 bg-[#0d0b16]/95 shadow-2xl shadow-purple-950/40"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-white/10 px-5 py-4">
              <div className="flex items-center gap-2.5">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-600/20 text-purple-300">
                  <Keyboard className="h-4 w-4" />
                </div>
                <div>
                  <h2 className="text-sm font-bold text-white">Klavye Kısayolları</h2>
                  <p className="text-[10px] text-slate-400">Hızlı ve akıcı bir okuma için</p>
                </div>
              </div>
              <button
                onClick={() => setShowHelp(false)}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 transition-all hover:bg-white/10 hover:text-white cursor-pointer"
                title="Kapat (Esc)"
                aria-label="Kapat"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="grid grid-cols-1 gap-2 p-5 sm:grid-cols-2">
              <ShortcutRow keys={['→', 'D']} label="Sonraki Sayfa" />
              <ShortcutRow keys={['←', 'A']} label="Önceki Sayfa" />
              <ShortcutRow keys={['F', 'F11']} label="Tam Ekran" />
              <ShortcutRow keys={['W']} label="Webtoon / Çift Sayfa" />
              <ShortcutRow keys={['R']} label="Okuma Yönü" />
              <ShortcutRow keys={['+', '-']} label="Yakınlaştır / Uzaklaştır" />
              <ShortcutRow keys={['0']} label="Zoom Sıfırla" />
              <ShortcutRow keys={['?', 'H']} label="Yardımı Aç / Kapat" />
              <ShortcutRow keys={['Esc']} label="Yardımı / Okuyucuyu Kapat" />
              <ShortcutRow keys={['Tekerlek']} label="Çift sayfada sayfa çevir" />
            </div>

            <div className="flex items-center gap-2 border-t border-white/10 px-5 py-3 text-[10px] text-slate-500">
              <RotateCcw className="h-3 w-3 shrink-0" />
              <span>
                {isRtl
                  ? 'Japon Manga yönü: sayfalar sağdan sola okunur.'
                  : 'Batı çizgi roman yönü: sayfalar soldan sağa okunur.'}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
