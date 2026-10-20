import React from 'react';
import { SeriesType } from '@yomira/core';

export type CategoryFilter = 'ALL' | SeriesType | 'ADULT_18';

interface CategoryTabsProps {
  activeTab: CategoryFilter;
  onTabChange: (tab: CategoryFilter) => void;
  enableAdult: boolean;
}

export const CategoryTabs: React.FC<CategoryTabsProps> = ({
  activeTab,
  onTabChange,
  enableAdult
}) => {
  const tabs: { id: CategoryFilter; label: string; icon: string }[] = [
    { id: 'ALL', label: 'Tüm Seriler', icon: '🌐' },
    { id: 'MANHWA', label: 'Webtoon & Manhwa', icon: '⚡' },
    { id: 'MANGA', label: 'Manga (Japon)', icon: '⚔️' },
    { id: 'COMIC', label: 'Western (Marvel / DC)', icon: '🦸' },
    { id: 'MANHUA', label: 'Manhua (Çin)', icon: '🐉' },
  ];

  if (enableAdult) {
    tabs.push({ id: 'ADULT_18', label: 'Yetişkin (+18)', icon: '🔞' });
  }

  return (
    <div className="flex items-center gap-2 overflow-x-auto pb-2 mb-6 scrollbar-none">
      {tabs.map((t) => {
        const isActive = activeTab === t.id;
        return (
          <button
            key={t.id}
            onClick={() => onTabChange(t.id)}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold transition-all whitespace-nowrap cursor-pointer ${
              isActive
                ? 'bg-purple-600 text-white shadow-[0_2px_12px_rgba(124,58,237,0.4)] border border-purple-400/40'
                : 'bg-[#12111d] text-slate-400 hover:text-slate-200 hover:bg-[#1a1928] border border-white/5'
            }`}
          >
            <span>{t.icon}</span>
            <span>{t.label}</span>
          </button>
        );
      })}
    </div>
  );
};
