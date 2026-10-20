import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  RotateCcw,
  X,
  ZoomIn,
  ZoomOut
} from 'lucide-react';
import { Series, Chapter, Page } from '@yomira/core';

interface MobileReaderViewProps {
  series: Series;
  chapter: Chapter;
  onClose: () => void;
  onProgressUpdate: (chapterNumber: number, pageIndex: number, percentage: number) => void;
  /** Opsiyonel: bölüm geçişleri bağlanırsa kaydırma ile bölüm de değiştirilebilir. */
  onPrevChapter?: () => void;
  onNextChapter?: () => void;
}

interface ZoomState {
  scale: number;
  originX: number;
  originY: number;
}

interface PanState {
  x: number;
  y: number;
}

interface GestureState {
  startX: number;
  startY: number;
  startPanX: number;
  startPanY: number;
  startTime: number;
  pinching: boolean;
  pinchStartDist: number;
  pinchStartScale: number;
  pinchOriginX: number;
  pinchOriginY: number;
  target: HTMLElement | null;
}

interface CapacitorStatusBarPlugin {
  setStyle?: (options: { style: 'DARK' | 'LIGHT' | 'DEFAULT' }) => Promise<void> | void;
  setBackgroundColor?: (options: { color: string }) => Promise<void> | void;
}

interface CapacitorWindow extends Window {
  Capacitor?: {
    Plugins?: {
      StatusBar?: CapacitorStatusBarPlugin;
    };
  };
}

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

/** Android titreşim (haptic) yardımcısı — desteklenmeyen cihazlarda sessizce yutulur. */
const vibrate = (pattern: number | number[]): void => {
  try {
    if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
      navigator.vibrate(pattern);
    }
  } catch {
    // Haptic desteklenmiyor: kritik değil.
  }
};

const distanceBetween = (x1: number, y1: number, x2: number, y2: number): number =>
  Math.hypot(x1 - x2, y1 - y2);

export const MobileReaderView: React.FC<MobileReaderViewProps> = ({
  series,
  chapter,
  onClose,
  onProgressUpdate,
  onPrevChapter,
  onNextChapter
}) => {
  const totalPages = Math.max(1, chapter.pageCount || 18);
  const chapterLabel = chapter.title || `Bölüm ${chapter.chapterNumber}`;

  // Demo sayfa kaynakları — gerçek sağlayıcıda `Page[]` doğrudan beslenebilir.
  const pages = useMemo<Page[]>(
    () =>
      Array.from({ length: totalPages }, (_, i) => ({
        index: i + 1,
        imageUrl: `https://picsum.photos/seed/${series.id}-${chapter.chapterNumber}-${i + 1}/720/1100`
      })),
    [series.id, chapter.chapterNumber, totalPages]
  );

  // --- UI state ---
  const [showControls, setShowControls] = useState<boolean>(true);
  const [currentPage, setCurrentPage] = useState<number>(1);
  const [zoom, setZoom] = useState<ZoomState>({ scale: 1, originX: 50, originY: 50 });
  const [pan, setPan] = useState<PanState>({ x: 0, y: 0 });
  const [gestureActive, setGestureActive] = useState<boolean>(false);
  const [endVisible, setEndVisible] = useState<boolean>(false);

  // --- Bellek dostu sanallaştırma state'i ---
  const [loadedPages, setLoadedPages] = useState<Set<number>>(() => {
    const initial = new Set<number>();
    for (let i = 1; i <= Math.min(3, totalPages); i += 1) initial.add(i);
    return initial;
  });
  const [decodedPages, setDecodedPages] = useState<Set<number>>(new Set<number>());
  const [imageErrors, setImageErrors] = useState<number[]>([]);
  const [imageVersions, setImageVersions] = useState<Record<number, number>>({});

  // --- Refs ---
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const pageRefs = useRef<Array<HTMLDivElement | null>>([]);
  const currentPageRef = useRef<number>(1);
  const zoomRef = useRef<ZoomState>(zoom);
  zoomRef.current = zoom;

  const tapTimerRef = useRef<number | null>(null);
  const lastTapRef = useRef<{ time: number; x: number; y: number } | null>(null);
  const rafRef = useRef<number | null>(null);
  const suppressScrollUntilRef = useRef<number>(0);
  const endTimerRef = useRef<number | null>(null);
  const endNotifiedRef = useRef<boolean>(false);

  const gestureRef = useRef<GestureState>({
    startX: 0,
    startY: 0,
    startPanX: 0,
    startPanY: 0,
    startTime: 0,
    pinching: false,
    pinchStartDist: 0,
    pinchStartScale: 1,
    pinchOriginX: 50,
    pinchOriginY: 50,
    target: null
  });

  const resetZoom = (): void => {
    setZoom({ scale: 1, originX: 50, originY: 50 });
    setPan({ x: 0, y: 0 });
  };

  const reportPage = (page: number): void => {
    const normalized = clamp(Math.round(page), 1, totalPages);
    if (normalized === currentPageRef.current) return;
    currentPageRef.current = normalized;
    setCurrentPage(normalized);
    onProgressUpdate(
      chapter.chapterNumber,
      normalized,
      Math.round((normalized / totalPages) * 100)
    );
  };

  const scrollToPage = (page: number, smooth: boolean): void => {
    const container = scrollRef.current;
    const node = pageRefs.current[clamp(page, 1, totalPages) - 1];
    if (!container || !node) return;

    const top =
      node.getBoundingClientRect().top -
      container.getBoundingClientRect().top +
      container.scrollTop;

    // Programatik kaydırma sırasında ara sayfalara sıçramayı engelle.
    suppressScrollUntilRef.current = performance.now() + (smooth ? 900 : 160);
    container.scrollTo({ top: Math.max(0, top), behavior: smooth ? 'smooth' : 'auto' });
    reportPage(page);
  };

  const triggerEnd = (withHaptic = true): void => {
    setEndVisible(true);
    if (endTimerRef.current !== null) window.clearTimeout(endTimerRef.current);
    endTimerRef.current = window.setTimeout(() => setEndVisible(false), 6000);

    if (withHaptic && !endNotifiedRef.current) {
      endNotifiedRef.current = true;
      // Pürüzsüz "bölüm bitti" geri bildirimi: kısa-kısa-uzun titreşim.
      vibrate([0, 30, 55, 70]);
    }
  };

  const handleDoubleTap = (clientX: number, clientY: number): void => {
    if (zoomRef.current.scale > 1.05) {
      resetZoom();
      vibrate(12);
      return;
    }

    const rect = scrollRef.current?.getBoundingClientRect();
    const originX = rect
      ? clamp(((clientX - rect.left) / rect.width) * 100, 8, 92)
      : 50;
    const originY = rect
      ? clamp(((clientY - rect.top) / rect.height) * 100, 8, 92)
      : 50;

    setPan({ x: 0, y: 0 });
    setZoom({ scale: 2.25, originX, originY });
    vibrate(18);
  };

  const turnPage = (direction: 1 | -1): void => {
    const target = currentPageRef.current + direction;
    if (target < 1) {
      vibrate(6);
      return;
    }
    if (target > totalPages) {
      vibrate(12);
      triggerEnd(true);
      return;
    }
    resetZoom();
    scrollToPage(target, true);
    vibrate(10);
  };

  const handleSwipe = (direction: 1 | -1): void => {
    if (zoomRef.current.scale > 1.05) return;

    const page = currentPageRef.current;

    // Bölüm sonu/başında yatay kaydırma bölüm değiştirir (handler bağlıysa).
    if (direction === 1 && page >= totalPages) {
      if (onNextChapter) {
        vibrate([0, 20, 35, 20]);
        onNextChapter();
      } else {
        vibrate(12);
        triggerEnd(true);
      }
      return;
    }

    if (direction === -1 && page <= 1) {
      if (onPrevChapter) {
        vibrate([0, 20, 35, 20]);
        onPrevChapter();
      } else {
        vibrate(6);
      }
      return;
    }

    vibrate(14);
    resetZoom();
    scrollToPage(page + direction, true);
  };

  const handleTap = (clientX: number, clientY: number): void => {
    const now = Date.now();
    const last = lastTapRef.current;

    // Çift dokunma: akıllı büyüteç (zoom in / zoom out).
    if (last && now - last.time < 300 && Math.hypot(clientX - last.x, clientY - last.y) < 44) {
      if (tapTimerRef.current !== null) {
        window.clearTimeout(tapTimerRef.current);
        tapTimerRef.current = null;
      }
      lastTapRef.current = null;
      handleDoubleTap(clientX, clientY);
      return;
    }

    lastTapRef.current = { time: now, x: clientX, y: clientY };
    if (tapTimerRef.current !== null) window.clearTimeout(tapTimerRef.current);

    // Tek dokunmayı çift dokunmadan ayırmak için kısa gecikme.
    tapTimerRef.current = window.setTimeout(() => {
      tapTimerRef.current = null;
      lastTapRef.current = null;

      const rect = scrollRef.current?.getBoundingClientRect();
      if (!rect) return;

      const ratio = (clientX - rect.left) / rect.width;
      if (ratio < 0.32) {
        turnPage(-1);
      } else if (ratio > 0.68) {
        turnPage(1);
      } else {
        setShowControls(prev => !prev);
        vibrate(8);
      }
    }, 280);
  };

  const beginPinch = (touches: React.TouchList): void => {
    const gesture = gestureRef.current;
    gesture.pinching = true;
    gesture.pinchStartDist = distanceBetween(
      touches[0].clientX,
      touches[0].clientY,
      touches[1].clientX,
      touches[1].clientY
    );
    gesture.pinchStartScale = zoomRef.current.scale;

    const midX = (touches[0].clientX + touches[1].clientX) / 2;
    const midY = (touches[0].clientY + touches[1].clientY) / 2;
    const rect = scrollRef.current?.getBoundingClientRect();
    gesture.pinchOriginX = rect
      ? clamp(((midX - rect.left) / rect.width) * 100, 0, 100)
      : 50;
    gesture.pinchOriginY = rect
      ? clamp(((midY - rect.top) / rect.height) * 100, 0, 100)
      : 50;
    gesture.startX = midX;
    gesture.startY = midY;
  };

  const handleTouchStart = (event: React.TouchEvent<HTMLDivElement>): void => {
    const gesture = gestureRef.current;
    const touch = event.touches[0];
    if (!touch) return;

    gesture.target = event.target as HTMLElement;
    gesture.startX = touch.clientX;
    gesture.startY = touch.clientY;
    gesture.startPanX = pan.x;
    gesture.startPanY = pan.y;
    gesture.startTime = Date.now();
    setGestureActive(true);

    if (event.touches.length >= 2) {
      if (tapTimerRef.current !== null) {
        window.clearTimeout(tapTimerRef.current);
        tapTimerRef.current = null;
      }
      lastTapRef.current = null;
      beginPinch(event.touches);
    } else {
      gesture.pinching = false;
    }
  };

  const handleTouchMove = (event: React.TouchEvent<HTMLDivElement>): void => {
    const gesture = gestureRef.current;

    if (event.touches.length >= 2) {
      if (!gesture.pinching || gesture.pinchStartDist <= 0) {
        beginPinch(event.touches);
      }

      const dist = distanceBetween(
        event.touches[0].clientX,
        event.touches[0].clientY,
        event.touches[1].clientX,
        event.touches[1].clientY
      );
      if (dist <= 0 || gesture.pinchStartDist <= 0) return;

      const nextScale = clamp(
        gesture.pinchStartScale * (dist / gesture.pinchStartDist),
        1,
        4
      );
      setPan({ x: 0, y: 0 });
      setZoom(prev =>
        prev.scale === nextScale
          ? prev
          : {
              scale: nextScale,
              originX: gesture.pinchOriginX,
              originY: gesture.pinchOriginY
            }
      );
      return;
    }

    // Yakınlaştırılmış görselde tek parmakla gezinme (pan).
    if (zoomRef.current.scale > 1.02) {
      const touch = event.touches[0];
      if (!touch) return;

      const dx = touch.clientX - gesture.startX;
      const dy = touch.clientY - gesture.startY;
      const rect = scrollRef.current?.getBoundingClientRect();
      if (!rect) return;

      const maxX = ((zoomRef.current.scale - 1) * rect.width) / 2;
      const maxY = ((zoomRef.current.scale - 1) * rect.height) / 2;
      setPan({
        x: clamp(gesture.startPanX + dx, -maxX, maxX),
        y: clamp(gesture.startPanY + dy, -maxY, maxY)
      });
    }
  };

  const handleTouchEnd = (event: React.TouchEvent<HTMLDivElement>): void => {
    const gesture = gestureRef.current;

    if (gesture.pinching) {
      if (event.touches.length < 2) {
        gesture.pinching = false;
        setGestureActive(false);
        if (zoomRef.current.scale < 1.08) resetZoom();

        // Bir parmak ekranda kaldıysa pan referansını güncelle.
        const remaining = event.touches[0];
        if (remaining) {
          gesture.startX = remaining.clientX;
          gesture.startY = remaining.clientY;
          gesture.startPanX = pan.x;
          gesture.startPanY = pan.y;
        }
      }
      return;
    }

    setGestureActive(false);

    const touch = event.changedTouches[0];
    if (!touch) return;

    const dx = touch.clientX - gesture.startX;
    const dy = touch.clientY - gesture.startY;
    const duration = Date.now() - gesture.startTime;

    // Yatay kaydırma: sayfa geçişi / bölüm geçişi.
    if (
      Math.abs(dx) > 52 &&
      Math.abs(dx) > Math.abs(dy) * 1.35 &&
      duration < 750 &&
      zoomRef.current.scale <= 1.05
    ) {
      handleSwipe(dx < 0 ? 1 : -1);
      return;
    }

    // Buton / link / slider üzerindeki dokunuşlar jest olarak yorumlanmaz.
    const interactive = gesture.target?.closest(
      'button, a, input, textarea, select, [data-reader-control]'
    );
    if (!interactive && Math.hypot(dx, dy) < 14 && duration < 400) {
      handleTap(touch.clientX, touch.clientY);
    }
  };

  const handleTouchCancel = (): void => {
    gestureRef.current.pinching = false;
    setGestureActive(false);
  };

  // --- Bölüm değişiminde okuyucuyu sıfırla ---
  useEffect(() => {
    currentPageRef.current = 1;
    setCurrentPage(1);
    setShowControls(true);
    setEndVisible(false);
    setDecodedPages(new Set<number>());
    setImageErrors([]);
    setImageVersions({});
    setZoom({ scale: 1, originX: 50, originY: 50 });
    setPan({ x: 0, y: 0 });
    endNotifiedRef.current = false;

    const initialLoaded = new Set<number>();
    for (let i = 1; i <= Math.min(3, totalPages); i += 1) initialLoaded.add(i);
    setLoadedPages(initialLoaded);

    // Önceki bölümden kalan ref'leri temizle (yeni ref'ler commit sırasında bağlandı).
    pageRefs.current.length = totalPages;

    if (endTimerRef.current !== null) {
      window.clearTimeout(endTimerRef.current);
      endTimerRef.current = null;
    }

    const container = scrollRef.current;
    if (container) container.scrollTo({ top: 0, behavior: 'auto' });

    onProgressUpdate(chapter.chapterNumber, 1, Math.round(100 / totalPages));

    return () => {
      if (tapTimerRef.current !== null) {
        window.clearTimeout(tapTimerRef.current);
        tapTimerRef.current = null;
      }
      if (endTimerRef.current !== null) {
        window.clearTimeout(endTimerRef.current);
        endTimerRef.current = null;
      }
      if (rafRef.current !== null) {
        window.cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chapter.id, totalPages]);

  // --- Android durum çubuğu (Capacitor varsa) + tarayıcı theme-color ---
  useEffect(() => {
    const existingMeta = document.querySelector('meta[name="theme-color"]');
    const meta = existingMeta ?? document.createElement('meta');
    if (!existingMeta) {
      meta.setAttribute('name', 'theme-color');
      document.head.appendChild(meta);
    }
    const previousColor = meta.getAttribute('content');
    meta.setAttribute('content', '#050508');

    const statusBar = (window as CapacitorWindow).Capacitor?.Plugins?.StatusBar;
    void statusBar?.setBackgroundColor?.({ color: '#050508' });
    void statusBar?.setStyle?.({ style: 'LIGHT' });

    return () => {
      if (previousColor === null) meta.removeAttribute('content');
      else meta.setAttribute('content', previousColor);
      void statusBar?.setBackgroundColor?.({ color: '#07070a' });
      void statusBar?.setStyle?.({ style: 'DEFAULT' });
    };
  }, []);

  // --- IntersectionObserver tabanlı sanallaştırma ---
  useEffect(() => {
    const root = scrollRef.current;
    if (!root || typeof IntersectionObserver === 'undefined') {
      // Fallback: eski WebView'larda tüm sayfaları render et.
      const all = new Set<number>();
      for (let i = 1; i <= totalPages; i += 1) all.add(i);
      setLoadedPages(all);
      return;
    }

    const observer = new IntersectionObserver(
      entries => {
        setLoadedPages(prev => {
          const next = new Set(prev);
          let changed = false;

          entries.forEach(entry => {
            const index = Number((entry.target as HTMLElement).dataset.pageIndex);
            if (!Number.isFinite(index)) return;

            if (entry.isIntersecting) {
              if (!next.has(index)) {
                next.add(index);
                changed = true;
              }
            } else if (next.delete(index)) {
              changed = true;
            }
          });

          return changed ? next : prev;
        });
      },
      {
        root,
        // Görünür alanın ~1600px üstü/altı önceden yüklenir; gerisi DOM'dan düşer.
        rootMargin: '1600px 0px 1600px 0px',
        threshold: 0.01
      }
    );

    const nodes = root.querySelectorAll<HTMLElement>('[data-page-index]');
    nodes.forEach(node => observer.observe(node));

    return () => observer.disconnect();
  }, [chapter.id, totalPages]);

  const handleScroll = (): void => {
    const container = scrollRef.current;
    if (!container) return;
    if (rafRef.current !== null) return;

    rafRef.current = window.requestAnimationFrame(() => {
      rafRef.current = null;
      const el = scrollRef.current;
      if (!el) return;

      // Sayfa sonu geri bildirimi (tek seferlik titreşim).
      if (el.scrollHeight - el.scrollTop - el.clientHeight <= 28) {
        triggerEnd(true);
      }

      if (performance.now() < suppressScrollUntilRef.current) return;

      const probe = el.scrollTop + el.clientHeight * 0.45;
      let page = 1;
      for (let i = 0; i < pageRefs.current.length; i += 1) {
        const node = pageRefs.current[i];
        if (!node) continue;
        if (node.offsetTop <= probe) page = i + 1;
        else break;
      }

      reportPage(page);
    });
  };

  const handleSliderChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    scrollToPage(Number(event.target.value), false);
  };

  const markDecoded = (index: number): void => {
    setDecodedPages(prev => {
      if (prev.has(index)) return prev;
      const next = new Set(prev);
      next.add(index);
      return next;
    });
  };

  const markImageError = (index: number): void => {
    setImageErrors(prev => (prev.includes(index) ? prev : [...prev, index]));
  };

  const retryImage = (index: number): void => {
    setImageErrors(prev => prev.filter(item => item !== index));
    setImageVersions(prev => ({ ...prev, [index]: (prev[index] ?? 0) + 1 }));
  };

  const pagePercent = Math.round((currentPage / totalPages) * 100);
  const sliderPercent = totalPages > 1 ? ((currentPage - 1) / (totalPages - 1)) * 100 : 100;
  const zoomed = zoom.scale > 1.02;

  return (
    <div className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-[#050508] text-white select-none">
      {/* ===== Dikey Webtoon Akışı (sanallaştırılmış) ===== */}
      <div
        ref={scrollRef}
        className="reader-scroll absolute inset-0 overflow-x-hidden overflow-y-auto"
        style={{
          touchAction: zoomed ? 'none' : 'pan-y',
          overscrollBehavior: 'contain',
          WebkitOverflowScrolling: 'touch'
        }}
        onScroll={handleScroll}
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onTouchCancel={handleTouchCancel}
      >
        <div
          className="w-full flex flex-col items-center"
          style={{
            transform: `translate3d(${pan.x}px, ${pan.y}px, 0) scale(${zoom.scale})`,
            transformOrigin: `${zoom.originX}% ${zoom.originY}%`,
            transition: gestureActive
              ? 'none'
              : 'transform 240ms cubic-bezier(0.16, 1, 0.3, 1)',
            willChange: 'transform'
          }}
        >
          {pages.map(page => {
            const nearCurrent = Math.abs(page.index - currentPage) <= 2;
            const shouldRenderImage = loadedPages.has(page.index) || nearCurrent;
            const isDecoded = decodedPages.has(page.index);
            const hasError = imageErrors.includes(page.index);
            const version = imageVersions[page.index] ?? 0;

            return (
              <div
                key={`${chapter.id}-${page.index}`}
                ref={node => {
                  pageRefs.current[page.index - 1] = node;
                }}
                data-page-index={page.index}
                className="relative w-full overflow-hidden bg-[#08070d]"
                style={{ aspectRatio: '720 / 1100' }}
              >
                {shouldRenderImage && !hasError && (
                  <img
                    key={`${chapter.id}-${page.index}-${version}`}
                    src={page.imageUrl}
                    alt={`Sayfa ${page.index}`}
                    loading="lazy"
                    decoding="async"
                    draggable={false}
                    onLoad={() => markDecoded(page.index)}
                    onError={() => markImageError(page.index)}
                    className={`reader-page-image absolute inset-0 h-full w-full object-cover transition-opacity duration-300 ${
                      isDecoded ? 'opacity-100' : 'opacity-0'
                    }`}
                  />
                )}

                {!isDecoded && !hasError && (
                  <div className="absolute inset-0 flex items-center justify-center bg-[#08070d]">
                    {nearCurrent && (
                      <div className="reader-shimmer absolute inset-0" aria-hidden="true" />
                    )}
                    <span className="relative text-[10px] font-semibold tracking-[0.25em] text-white/15">
                      SAYFA {page.index}
                    </span>
                  </div>
                )}

                {hasError && (
                  <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-[#0b0a13] px-4 text-center">
                    <span className="text-[11px] text-slate-500">
                      Sayfa {page.index} yüklenemedi
                    </span>
                    <button
                      type="button"
                      data-reader-control
                      onTouchStart={event => event.stopPropagation()}
                      onClick={() => retryImage(page.index)}
                      className="rounded-lg border border-purple-500/40 bg-purple-600/20 px-3 py-1.5 text-[11px] font-bold text-purple-200 active:bg-purple-600/30"
                    >
                      Yeniden Dene
                    </button>
                  </div>
                )}
              </div>
            );
          })}

          {/* Bölüm sonu (akışın doğal parçası) */}
          <div className="flex w-full flex-col items-center gap-2 px-6 pb-36 pt-8 text-center">
            <CheckCircle2 className="h-8 w-8 text-purple-500/70" />
            <p className="text-sm font-bold text-white">Bölüm Sonu</p>
            <p className="text-[11px] text-slate-500">
              {onNextChapter
                ? 'Bir sonraki bölüm sizi bekliyor.'
                : 'Yeni bölümler yakında eklenecek.'}
            </p>
            {onNextChapter && (
              <button
                type="button"
                data-reader-control
                onTouchStart={event => event.stopPropagation()}
                onClick={onNextChapter}
                className="mt-1 flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-purple-600 to-fuchsia-600 px-4 py-2 text-xs font-bold text-white shadow-[0_4px_18px_rgba(124,58,237,0.4)] active:scale-95 transition-transform"
              >
                Sonraki Bölüm
                <ArrowRight className="h-3.5 w-3.5" />
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ===== Dokunmatik bölge ipuçları ===== */}
      <div
        className={`pointer-events-none absolute inset-y-0 left-0 z-20 flex w-[32%] items-center justify-start pl-1 transition-opacity duration-300 ${
          showControls ? 'opacity-40' : 'opacity-0'
        }`}
        aria-hidden="true"
      >
        <ChevronLeft className="h-7 w-7 text-white drop-shadow-[0_0_8px_rgba(168,85,247,0.8)]" />
      </div>
      <div
        className={`pointer-events-none absolute inset-y-0 right-0 z-20 flex w-[32%] items-center justify-end pr-1 transition-opacity duration-300 ${
          showControls ? 'opacity-40' : 'opacity-0'
        }`}
        aria-hidden="true"
      >
        <ChevronRight className="h-7 w-7 text-white drop-shadow-[0_0_8px_rgba(168,85,247,0.8)]" />
      </div>

      {/* ===== Üst kontrol çubuğu ===== */}
      <div
        className={`absolute inset-x-0 top-0 z-40 border-b border-purple-900/30 bg-[#0c0b14]/95 backdrop-blur-xl transition-all duration-300 ${
          showControls
            ? 'translate-y-0 opacity-100'
            : 'pointer-events-none -translate-y-full opacity-0'
        }`}
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        <div className="flex h-12 items-center justify-between px-3">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg bg-white/5 p-1.5 text-slate-300 active:bg-white/10"
              aria-label="Okuyucudan çık"
            >
              <ArrowLeft className="h-4 w-4" />
            </button>
            <div className="min-w-0">
              <span className="block truncate text-xs font-bold text-white">
                {series.canonicalTitle}
              </span>
              <span className="block truncate text-[10px] font-semibold text-purple-400">
                {chapterLabel}
              </span>
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <span className="rounded-full border border-white/10 bg-white/5 px-2 py-0.5 text-[11px] font-bold text-slate-300">
              {currentPage} / {totalPages}
            </span>
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg bg-rose-950/40 p-1.5 text-rose-300 active:bg-rose-900/50"
              aria-label="Kapat"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      </div>

      {/* ===== Zoom göstergesi ===== */}
      {zoomed && (
        <div
          className="reader-fade-in absolute right-3 z-40 flex items-center gap-1.5 rounded-full border border-purple-500/40 bg-black/80 py-1 pl-2 pr-1 backdrop-blur-md"
          style={{ top: 'calc(env(safe-area-inset-top) + 3.75rem)' }}
        >
          <ZoomIn className="h-3.5 w-3.5 text-purple-300" />
          <span className="text-[10px] font-bold text-purple-200">
            {zoom.scale.toFixed(1)}x
          </span>
          <button
            type="button"
            onClick={resetZoom}
            className="rounded-full bg-white/10 p-1 text-white active:bg-white/20"
            aria-label="Yakınlaştırmayı sıfırla"
          >
            <ZoomOut className="h-3.5 w-3.5" />
          </button>
        </div>
      )}

      {/* ===== Alt sayfa kaydırıcısı ===== */}
      <div
        className={`absolute inset-x-0 bottom-0 z-40 transition-all duration-300 ${
          showControls
            ? 'translate-y-0 opacity-100'
            : 'pointer-events-none translate-y-full opacity-0'
        }`}
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <div className="mx-2 mb-2 rounded-2xl border border-purple-500/20 bg-[#0c0b14]/95 p-3 shadow-[0_-8px_30px_rgba(0,0,0,0.6)] backdrop-blur-xl">
          <div className="flex items-center justify-between text-[11px] font-semibold">
            <span className="text-slate-300">
              Sayfa <span className="font-extrabold text-purple-300">{currentPage}</span> /{' '}
              {totalPages}
            </span>
            <span className="text-slate-500">%{pagePercent}</span>
          </div>

          <div className="py-2">
            <input
              type="range"
              min={1}
              max={totalPages}
              step={1}
              value={currentPage}
              onChange={handleSliderChange}
              onPointerDown={() => vibrate(8)}
              aria-label="Hızlı sayfa atlama kaydırıcısı"
              className="yomira-range w-full"
              style={
                { '--reader-progress': `${sliderPercent}%` } as React.CSSProperties
              }
            />
          </div>

          <div className="flex items-center justify-between gap-2">
            <button
              type="button"
              onClick={() => turnPage(-1)}
              className="flex items-center gap-1 rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-[11px] font-bold text-slate-300 active:bg-white/10"
            >
              <ChevronLeft className="h-3.5 w-3.5" />
              Önceki
            </button>

            <span className="text-center text-[9px] leading-tight text-slate-500">
              Ortaya dokun: menü
              <br />
              Çift dokun: yakınlaştır
            </span>

            <button
              type="button"
              onClick={() => turnPage(1)}
              className="flex items-center gap-1 rounded-xl border border-purple-500/30 bg-purple-600/20 px-3 py-2 text-[11px] font-bold text-purple-200 active:bg-purple-600/30"
            >
              Sonraki
              <ChevronRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* ===== Bölüm sonu geri bildirimi ===== */}
      {endVisible && (
        <div
          className="reader-end-card absolute inset-x-3 z-50 rounded-2xl border border-purple-500/40 bg-[#12101f]/95 p-4 shadow-[0_0_45px_rgba(124,58,237,0.35)] backdrop-blur-xl"
          style={{ bottom: 'calc(env(safe-area-inset-bottom) + 8.5rem)' }}
          role="status"
          aria-live="polite"
        >
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-purple-500/40 bg-purple-600/20">
              <CheckCircle2 className="h-5 w-5 text-purple-300" />
            </div>

            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold text-white">Bölüm sonuna ulaştınız</p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                Tüm sayfalar okundu. Keyifli okumalar!
              </p>

              <div className="mt-3 flex items-center gap-2">
                {onNextChapter && (
                  <button
                    type="button"
                    onClick={() => {
                      setEndVisible(false);
                      onNextChapter();
                    }}
                    className="flex items-center gap-1.5 rounded-xl bg-gradient-to-r from-purple-600 to-fuchsia-600 px-3 py-2 text-xs font-bold text-white active:scale-95 transition-transform"
                  >
                    Sonraki Bölüm
                    <ArrowRight className="h-3.5 w-3.5" />
                  </button>
                )}

                <button
                  type="button"
                  onClick={() => {
                    setEndVisible(false);
                    resetZoom();
                    scrollToPage(1, true);
                  }}
                  className="flex items-center gap-1.5 rounded-xl border border-white/10 bg-white/5 px-3 py-2 text-xs font-semibold text-slate-300 active:bg-white/10"
                >
                  <RotateCcw className="h-3.5 w-3.5" />
                  Başa Dön
                </button>

                <button
                  type="button"
                  onClick={() => setEndVisible(false)}
                  className="ml-auto rounded-full bg-white/5 p-2 text-slate-400 active:bg-white/10"
                  aria-label="Bildirimi kapat"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
