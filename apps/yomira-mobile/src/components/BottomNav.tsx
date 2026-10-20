import React from 'react';
import { Compass, Bookmark, Puzzle, Settings } from 'lucide-react';

export type MobileTab = 'DISCOVER' | 'LIBRARY' | 'EXTENSIONS' | 'SETTINGS';

interface BottomNavProps {
  activeTab: MobileTab;
  onTabChange: (tab: MobileTab) => void;
  libraryCount: number;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  activeTab,
  onTabChange,
  libraryCount
}) => {
  const tabs = [
    { id: 'DISCOVER' as MobileTab, label: 'Keşfet', icon: Compass },
    { id: 'LIBRARY' as MobileTab, label: 'Kitaplığım', icon: Bookmark, badge: libraryCount > 0 ? libraryCount : null },
    { id: 'EXTENSIONS' as MobileTab, label: 'Eklentiler', icon: Puzzle },
    { id: 'SETTINGS' as MobileTab, label: 'Ayarlar', icon: Settings },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-[#0a0912]/95 backdrop-blur-lg border-t border-purple-900/30 px-3 py-1.5 flex items-center justify-around pb-safe">
      {tabs.map((t) => {
        const Icon = t.icon;
        const isActive = activeTab === t.id;

        return (
          <button
            key={t.id}
            onClick={() => onTabChange(t.id)}
            className={`flex flex-col items-center justify-center py-1 px-3 rounded-xl transition-all relative cursor-pointer ${
              isActive 
                ? 'text-purple-400 font-bold' 
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <div className="relative">
              <Icon className={`w-5 h-5 ${isActive ? 'stroke-[2.5px] filter drop-shadow-[0_0_8px_rgba(168,85,247,0.6)]' : ''}`} />
              {t.badge && (
                <span className="absolute -top-1 -right-2 bg-purple-600 text-white text-[9px] font-extrabold px-1.5 py-0.2 rounded-full">
                  {t.badge}
                </span>
              )}
            </div>
            <span className="text-[10px] mt-0.5 tracking-tight">{t.label}</span>
          </button>
        );
      })}
    </nav>
  );
};
