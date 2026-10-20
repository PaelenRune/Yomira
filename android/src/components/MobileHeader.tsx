import React from 'react';
import { Search, Cloud, CloudOff, ShieldAlert } from 'lucide-react';

interface MobileHeaderProps {
  searchQuery: string;
  onSearchChange: (q: string) => void;
  isDriveConnected: boolean;
  onDriveToggle: () => void;
  enableAdult: boolean;
  onAdultToggle: () => void;
}

export const MobileHeader: React.FC<MobileHeaderProps> = ({
  searchQuery,
  onSearchChange,
  isDriveConnected,
  onDriveToggle,
  enableAdult,
  onAdultToggle
}) => {
  return (
    <header className="sticky top-0 z-40 bg-[#07070a]/95 backdrop-blur-md border-b border-purple-900/30 px-4 pt-3 pb-2.5">
      <div className="flex items-center justify-between gap-3 mb-2">
        {/* Brand */}
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-full bg-gradient-to-br from-purple-600 to-fuchsia-500 flex items-center justify-center font-black text-white text-sm shadow-[0_0_12px_rgba(168,85,247,0.5)]">
            Y
          </div>
          <span className="text-base font-extrabold tracking-wider bg-gradient-to-r from-white to-purple-400 bg-clip-text text-transparent">
            YOMIRA
          </span>
        </div>

        {/* Right Badges */}
        <div className="flex items-center gap-2">
          {/* Google Drive Status Pill */}
          <button
            onClick={onDriveToggle}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold border transition-all ${
              isDriveConnected 
                ? 'bg-emerald-950/40 border-emerald-500/30 text-emerald-400' 
                : 'bg-white/5 border-white/10 text-slate-400'
            }`}
          >
            {isDriveConnected ? (
              <>
                <Cloud className="w-3 h-3 text-emerald-400" />
                <span>Drive</span>
              </>
            ) : (
              <>
                <CloudOff className="w-3 h-3 text-slate-500" />
                <span>Çevrimdışı</span>
              </>
            )}
          </button>

          {/* +18 Toggle */}
          <button
            onClick={onAdultToggle}
            className={`flex items-center gap-1 px-2 py-1 rounded-md text-[10px] font-bold border transition-all ${
              enableAdult 
                ? 'bg-rose-950/50 border-rose-500/40 text-rose-400' 
                : 'bg-white/5 border-white/10 text-slate-500'
            }`}
          >
            <ShieldAlert className="w-3 h-3" />
            <span>{enableAdult ? '+18' : '+18'}</span>
          </button>
        </div>
      </div>

      {/* Mobile Search Bar */}
      <div className="relative w-full">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Manga, webtoon veya çizgi roman ara..."
          className="w-full pl-9 pr-3 py-1.5 bg-[#12111d] border border-white/10 rounded-xl text-xs text-slate-200 placeholder-slate-500 focus:outline-none focus:border-purple-500 transition-all"
        />
      </div>
    </header>
  );
};
