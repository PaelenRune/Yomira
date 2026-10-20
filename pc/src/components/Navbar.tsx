import React from 'react';
import { Search, Cloud, CloudOff, Puzzle, ShieldAlert, Sparkles } from 'lucide-react';

interface NavbarProps {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  isDriveConnected: boolean;
  onDriveToggle: () => void;
  enableAdultContent: boolean;
  onAdultToggle: () => void;
  onOpenExtensions: () => void;
  lastSyncedText: string;
}

export const Navbar: React.FC<NavbarProps> = ({
  searchQuery,
  onSearchChange,
  isDriveConnected,
  onDriveToggle,
  enableAdultContent,
  onAdultToggle,
  onOpenExtensions,
  lastSyncedText
}) => {
  return (
    <header className="sticky top-0 z-40 w-full border-b border-purple-900/30 bg-[#07070a]/90 backdrop-blur-md px-6 py-3">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-4">
        
        {/* Brand Logo & Name */}
        <div className="flex items-center gap-3 select-none cursor-pointer">
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-purple-600 to-fuchsia-500 flex items-center justify-center font-black text-white text-lg shadow-[0_0_15px_rgba(168,85,247,0.5)]">
            Y
          </div>
          <div className="flex flex-col">
            <span className="text-xl font-extrabold tracking-[2px] bg-gradient-to-r from-white via-slate-100 to-purple-400 bg-clip-text text-transparent">
              YOMIRA
            </span>
            <span className="text-[9px] font-bold text-slate-400 tracking-wider -mt-1 uppercase">
              Every Story. One Library.
            </span>
          </div>
        </div>

        {/* Global Search Bar */}
        <div className="flex-1 max-w-md relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="Seri, yazar, manga, webtoon veya Marvel/DC ara..."
            className="w-full pl-9 pr-4 py-1.5 bg-[#12111d] border border-white/10 rounded-lg text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 transition-all"
          />
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-3">
          
          {/* Extensions & Repo Manager Button */}
          <button
            onClick={onOpenExtensions}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-white/5 hover:bg-purple-950/40 border border-white/10 hover:border-purple-500/40 text-xs font-semibold text-slate-300 transition-all cursor-pointer"
            title="Eklenti ve Repo Yöneticisi"
          >
            <Puzzle className="w-3.5 h-3.5 text-purple-400" />
            <span>Eklentiler</span>
          </button>

          {/* Google Drive Multi-Sync Indicator & Button */}
          <button
            onClick={onDriveToggle}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-full border text-xs font-semibold transition-all cursor-pointer ${
              isDriveConnected
                ? 'bg-emerald-950/30 border-emerald-500/30 text-emerald-400 hover:bg-emerald-900/40'
                : 'bg-slate-900/40 border-slate-700/40 text-slate-400 hover:border-slate-500'
            }`}
            title="Kişisel Google Drive ile PC ve Android senkronizasyonu"
          >
            {isDriveConnected ? (
              <>
                <Cloud className="w-3.5 h-3.5 text-emerald-400 animate-pulse" />
                <span className="hidden sm:inline">Drive: {lastSyncedText}</span>
              </>
            ) : (
              <>
                <CloudOff className="w-3.5 h-3.5 text-slate-500" />
                <span className="hidden sm:inline">Drive Bağla</span>
              </>
            )}
          </button>

          {/* +18 Mature/Adult Toggle */}
          <button
            onClick={onAdultToggle}
            className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg border text-xs font-bold transition-all cursor-pointer ${
              enableAdultContent
                ? 'bg-rose-950/40 border-rose-500/40 text-rose-400 shadow-[0_0_10px_rgba(244,63,94,0.2)]'
                : 'bg-white/5 border-white/10 text-slate-500 hover:text-slate-300'
            }`}
            title="Pornografik ve Yetişkin Webtoon İçerik Filtresi"
          >
            <ShieldAlert className="w-3.5 h-3.5" />
            <span>{enableAdultContent ? '+18 Açık' : '+18'}</span>
          </button>
        </div>

      </div>
    </header>
  );
};
