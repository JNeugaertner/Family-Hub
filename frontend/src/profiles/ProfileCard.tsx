import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { useMe } from '../auth/AuthContext';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { useTaskData } from '../tasks/TaskDataContext';
import { listAchievementProgress, type AchievementItem } from '../achievements/api';
import { ROLE_NAMES } from '../roles';

// Kleines Profil einer Person (Entscheidung vom 29.09.2026): Rolle, Farbe, Alter, Punkte und Erfolge. Öffnet sich
// an der angeklickten Stelle; jede Person sieht nur, was sie laut Rechten sehen darf (Punkte und Erfolge liefert das
// Backend bereits gefiltert: Kinder nur die eigenen, Gäste keine).

type OpenProfile = (memberId: string, anchor: HTMLElement) => void;

const ProfileCardContext = createContext<OpenProfile>(() => {});

export const useOpenProfile = () => useContext(ProfileCardContext);

export function ProfileCardProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState<{ memberId: string; anchor: HTMLElement } | null>(null);
  const openProfile = useCallback<OpenProfile>((memberId, anchor) => setOpen({ memberId, anchor }), []);
  const close = useCallback(() => {
    setOpen(current => {
      current?.anchor.focus();
      return null;
    });
  }, []);
  return (
    <ProfileCardContext.Provider value={openProfile}>
      {children}
      {open && <ProfileCard key={open.memberId} memberId={open.memberId} anchor={open.anchor} onClose={close} />}
    </ProfileCardContext.Provider>
  );
}

function ageOf(birthDate: string, today = new Date()): number {
  const [y, m, d] = birthDate.split('-').map(Number);
  let age = today.getFullYear() - y;
  if (today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d)) age--;
  return age;
}

const birthdayLabel = (birthDate: string) =>
  new Date(`${birthDate}T00:00`).toLocaleDateString('de-DE', { day: 'numeric', month: 'long' });

const WIDTH = 288;

function ProfileCard({ memberId, anchor, onClose }: { memberId: string; anchor: HTMLElement; onClose: () => void }) {
  const me = useMe();
  const { memberById } = useCalendarData();
  const { balances } = useTaskData();
  const member = memberById(memberId);
  const card = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<{ top: number; left: number } | null>(null);
  const [achievements, setAchievements] = useState<AchievementItem[] | null | 'none'>(null);

  const collects = member?.effectiveRole === 'kind' || member?.effectiveRole === 'jugendlicher';
  const points = balances[memberId];

  // Erfolge nur für Kinder und Jugendliche, deren Punkte die angemeldete Person sehen darf (gleiche Regel im Backend)
  const mayViewPoints = collects && points !== undefined;
  useEffect(() => {
    if (!mayViewPoints) { setAchievements('none'); return; }
    let cancelled = false;
    listAchievementProgress()
      .then(all => { if (!cancelled) setAchievements(all.find(a => a.memberId === memberId)?.items ?? 'none'); })
      .catch(() => { if (!cancelled) setAchievements('none'); });
    return () => { cancelled = true; };
  }, [mayViewPoints, memberId]);

  // Unter dem angeklickten Kreis, bei zu wenig Platz darüber; immer ganz im Fenster
  useLayoutEffect(() => {
    const r = anchor.getBoundingClientRect();
    const h = card.current?.offsetHeight ?? 0;
    let top = r.bottom + 8;
    if (top + h > window.innerHeight - 8) top = Math.max(8, r.top - h - 8);
    const left = Math.min(Math.max(8, r.left + r.width / 2 - WIDTH / 2), window.innerWidth - WIDTH - 8);
    setPos({ top, left });
  }, [anchor, achievements]);

  useEffect(() => {
    card.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    const onDown = (e: MouseEvent) => {
      if (!card.current?.contains(e.target as Node) && !anchor.contains(e.target as Node)) onClose();
    };
    const onScroll = (e: Event) => { if (!card.current?.contains(e.target as Node)) onClose(); };
    window.addEventListener('keydown', onKey);
    window.addEventListener('mousedown', onDown);
    window.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', onClose);
    return () => {
      window.removeEventListener('keydown', onKey);
      window.removeEventListener('mousedown', onDown);
      window.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', onClose);
    };
  }, [anchor, onClose]);

  if (!member) return null;
  const earned = Array.isArray(achievements) ? achievements.filter(a => a.earnedAt) : [];
  const next = Array.isArray(achievements)
    ? achievements.filter(a => !a.earnedAt).sort((a, b) => b.current / b.target - a.current / a.target)[0]
    : undefined;

  return (
    <div ref={card} role="dialog" aria-label={`Profil von ${member.name}`} tabIndex={-1}
      className="fixed z-[60] bg-white rounded-2xl shadow-2xl border border-slate-100 overflow-hidden outline-none"
      style={{ width: WIDTH, top: pos?.top ?? -9999, left: pos?.left ?? -9999 }}>
      <div className="px-4 py-3 flex items-center gap-3" style={{ backgroundColor: member.color }}>
        <div className="w-11 h-11 rounded-full bg-white/25 flex items-center justify-center text-white font-bold text-lg">
          {member.initials[0]}
        </div>
        <div className="flex-1 min-w-0 text-white">
          <div className="font-bold text-base truncate">{member.name}{member.id === me.id ? ' (du)' : ''}</div>
          <div className="text-xs text-white/85">{ROLE_NAMES[member.effectiveRole]}</div>
        </div>
        <button type="button" onClick={onClose} aria-label="Profil schließen" className="text-white/80 hover:text-white text-lg leading-none">✕</button>
      </div>

      <div className="p-4 space-y-3 text-sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs">
          <dt className="text-slate-400">Farbe</dt>
          <dd className="flex items-center gap-1.5 text-slate-700">
            <span className="w-3 h-3 rounded-full" style={{ backgroundColor: member.color }} />{member.color}
          </dd>
          {member.birthDate && (
            <>
              <dt className="text-slate-400">Alter</dt>
              <dd className="text-slate-700">{ageOf(member.birthDate)} Jahre · Geburtstag am {birthdayLabel(member.birthDate)}</dd>
            </>
          )}
        </dl>

        {collects && points !== undefined && (
          <div className="flex items-baseline gap-2 border-t border-slate-100 pt-3">
            <span className="text-2xl font-bold text-[#D97706]">⭐ {points}</span>
            <span className="text-xs text-slate-500">Punkte</span>
          </div>
        )}

        {mayViewPoints && achievements === null && <p className="text-xs text-slate-400">Erfolge werden geladen…</p>}
        {Array.isArray(achievements) && (
          <div className="border-t border-slate-100 pt-3">
            <div className="text-xs font-semibold text-slate-600 mb-1.5">
              Erfolge · {earned.length} von {achievements.length} erreicht
            </div>
            {earned.length > 0 ? (
              <div className="flex flex-wrap gap-1.5">
                {earned.map(a => (
                  <span key={a.achievementId} title={a.name}
                    className="text-[11px] bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] px-2 py-0.5 rounded-full">
                    {a.icon} {a.name}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-400">Noch kein Erfolg erreicht.</p>
            )}
            {next && (
              <div className="mt-2.5">
                <div className="flex justify-between text-[11px] text-slate-500 mb-1">
                  <span>Nächster: {next.icon} {next.name}</span>
                  <span>{next.current}/{next.target}</span>
                </div>
                <div className="h-1.5 bg-slate-100 rounded-full overflow-hidden">
                  <div className="h-full rounded-full" style={{ width: `${Math.round((next.current / next.target) * 100)}%`, backgroundColor: member.color }} />
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
