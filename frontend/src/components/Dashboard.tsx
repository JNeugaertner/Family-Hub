import { useState } from 'react';
import type { CalendarEvent } from './data';
import { useAuth } from '../auth/AuthContext';
import { occursOn, useCalendarData } from '../calendar/CalendarDataContext';
import type { MealType } from '../meals/api';
import { useMealData } from '../meals/MealDataContext';
import { useShoppingData } from '../shopping/ShoppingDataContext';
import GoogleBadge from '../google/GoogleBadge';
import { MONTHS, WEEKDAYS_SHORT, formatDayMonth, formatLongDate, startOfToday, toDateKey, weekdayIndex } from '../calendar/dates';
import { cardBackground, dotBackground, memberColors } from '../calendar/eventStyle';
import ParticipantAvatars from '../calendar/ParticipantAvatars';
import AvatarButton from '../profiles/AvatarButton';
import { readable } from '../family/colors';
import { useTaskData } from '../tasks/TaskDataContext';
import { usePointHolders } from '../points/usePointHolders';
import WeatherWidget from '../weather/WeatherWidget';
import type { Focus } from '../navigation/focus';
import { SoonBadge } from './Placeholder';
import { PLACEHOLDER } from '../placeholders';
import {
  CalendarIcon, CheckSquareIcon, ShoppingCartIcon, UtensilsIcon,
  StarIcon, ChevronLeftIcon, ChevronRightIcon, ClockIcon,
  MapPinIcon, AlertTriangleIcon, SparklesIcon, CheckIcon,
} from './Icons';

type Page = 'dashboard' | 'calendar' | 'tasks' | 'rewards' | 'shopping' | 'meals' | 'assistant' | 'messenger' | 'profiles';
// focus: Sprungziel auf der nächsten Seite (Termin am Tag im Kalender, Aufgabe auf der Aufgabenseite)
type Navigate = (p: Page, focus?: Focus) => void;
interface Props { onNavigate: Navigate; }

const TRANSPORT_ICONS: Record<string, string> = {
  car: '🚗', transit: '🚌', bike: '🚲', walk: '🚶',
};

// ─── mini calendar ───────────────────────────────────────────────────────────

function MiniCalendar({ onNavigate }: { onNavigate: Navigate }) {
  const { events: calendarEvents, memberById } = useCalendarData();
  const today    = startOfToday();
  const [viewDate, setViewDate] = useState(() => new Date(today.getFullYear(), today.getMonth(), 1));
  const year     = viewDate.getFullYear();
  const month    = viewDate.getMonth();
  const firstDay = weekdayIndex(new Date(year, month, 1));
  const daysInM  = new Date(year, month + 1, 0).getDate();
  const daysInP  = new Date(year, month, 0).getDate();

  const cells: { day: number; type: 'prev' | 'curr' | 'next' }[] = [];
  for (let i = firstDay - 1; i >= 0; i--) cells.push({ day: daysInP - i, type: 'prev' });
  for (let d = 1; d <= daysInM; d++) cells.push({ day: d, type: 'curr' });
  while (cells.length % 7 !== 0) cells.push({ day: cells.length - daysInM - firstDay + 1, type: 'next' });

  const getEvents = (d: number) => {
    const s = `${year}-${String(month + 1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    return calendarEvents.filter(e => occursOn(e, s) && e.status === 'approved');
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base">{MONTHS[month]} {year}</h2>
        <div className="flex gap-1">
          <button onClick={() => setViewDate(new Date(year, month - 1, 1))} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 transition-colors" aria-label="Vorheriger Monat"><ChevronLeftIcon size={16} /></button>
          <button onClick={() => setViewDate(new Date(year, month + 1, 1))} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-500 transition-colors" aria-label="Nächster Monat"><ChevronRightIcon size={16} /></button>
        </div>
      </div>

      <div className="grid grid-cols-7 mb-2">
        {WEEKDAYS_SHORT.map(d =><div key={d} className="text-center text-[10px] font-semibold text-slate-500 py-1 uppercase tracking-wide">{d}</div>)}
      </div>

      <div className="grid grid-cols-7 gap-y-0.5">
        {cells.map((cell, i) => {
          // Tage des Vor- und Folgemonats liegen davor bzw. danach
          const cellDate = new Date(year, month, i - firstDay + 1);
          const events  = cell.type === 'curr' ? getEvents(cell.day) : [];
          const isToday = cell.type === 'curr' && cell.day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
          const hasConflict = events.some(e => e.conflict || e.travelConflict);
          return (
            <button
              key={i}
              onClick={() => onNavigate('calendar', { kind: 'day', date: toDateKey(cellDate) })}
              aria-label={`${formatLongDate(cellDate)} im Kalender öffnen`}
              className={`flex flex-col items-center py-1 rounded-lg transition-colors relative
                ${cell.type !== 'curr' ? '' : 'hover:bg-slate-50'}
                ${isToday ? 'bg-[#2563EB] hover:bg-[#2563EB]' : ''}`}
            >
              <span className={`text-xs leading-5 ${isToday ? 'text-white font-medium' : cell.type !== 'curr' ? 'text-slate-500' : 'text-slate-700 font-medium'}`}>
                {cell.day}
              </span>
              {events.length > 0 && !isToday && (
                <div className={`flex gap-0.5 mt-0.5 ${cell.type !== 'curr' ? 'opacity-40' : ''}`}>
                  {events.slice(0, 3).map((ev, ei) => (
                    <div key={ei} className="w-1 h-1 rounded-full" style={{ background: dotBackground(memberColors(ev.memberIds, memberById)) }} />
                  ))}
                </div>
              )}
              {hasConflict && !isToday && (
                <div className="absolute top-0.5 right-0.5 w-1.5 h-1.5 rounded-full bg-[#EF4444]" />
              )}
            </button>
          );
        })}
      </div>

      <button onClick={() => onNavigate('calendar')} className="mt-4 w-full py-2 text-[#2563EB] text-xs font-semibold hover:bg-[#EFF6FF] rounded-xl transition-colors">
        Vollständigen Kalender öffnen →
      </button>
    </div>
  );
}

// ─── today's agenda ───────────────────────────────────────────────────────────

function departureStr(ev: CalendarEvent): string | null {
  if (!ev.travelTime) return null;
  const [h, m] = ev.time.split(':').map(Number);
  const total  = h * 60 + m - ev.travelTime;
  return `${String(Math.floor(total / 60)).padStart(2,'0')}:${String(total % 60).padStart(2,'0')}`;
}

function TodayAgenda({ onNavigate }: { onNavigate: Navigate }) {
  const { events, memberById } = useCalendarData();
  const todayEvents = events
    .filter(e => occursOn(e, toDateKey(startOfToday())) && e.status === 'approved')
    .sort((a, b) => a.time.localeCompare(b.time));

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <CalendarIcon size={16} className="text-[#2563EB]" />
          Heute
        </h2>
        <button onClick={() => onNavigate('calendar')} className="text-xs text-[#2563EB] font-medium hover:underline">Alle →</button>
      </div>

      {todayEvents.length === 0 ? (
        <p className="text-sm text-slate-500 py-4 text-center">Heute keine Termine 🎉</p>
      ) : (
        <div className="space-y-2.5">
          {todayEvents.map(ev => {
            const dep    = departureStr(ev);
            const open = () => onNavigate('calendar', { kind: 'event', id: ev.id, date: ev.date });
            return (
              <div
                key={ev.id}
                onClick={open}
                className={`flex items-start gap-3 p-3 rounded-xl transition-colors cursor-pointer hover:shadow-sm has-[[data-open]:focus-visible]:outline-2 has-[[data-open]:focus-visible]:outline-[#2563EB] ${ev.travelConflict ? 'bg-[#FEF2F2]' : 'hover:bg-slate-50'}`}
                style={ev.travelConflict ? { borderLeft: '3px solid #EF4444' } : { background: cardBackground(memberColors(ev.memberIds, memberById)) }}
              >
                <button type="button" data-open className="flex-1 min-w-0 text-left focus-visible:outline-none">
                  <span className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-semibold text-slate-800">
                      {ev.source === 'google' && <GoogleBadge className="mr-1" />}{ev.title}
                    </span>
                    {(ev.conflict || ev.travelConflict) && (
                      <span className="text-[10px] bg-[#FEF2F2] text-[#B91C1C] px-1.5 py-0.5 rounded-full font-medium flex items-center gap-1">
                        <AlertTriangleIcon size={10} /> Konflikt
                      </span>
                    )}
                  </span>
                  <span className="flex items-center gap-2 mt-0.5 text-xs text-slate-500 flex-wrap">
                    <span className="flex items-center gap-1"><ClockIcon size={10} />{ev.allDay ? 'Ganztägig' : `${ev.time}${ev.endTime ? ` – ${ev.endTime}` : ''}`}</span>
                    {ev.location && <span className="flex items-center gap-1"><MapPinIcon size={10} />{ev.location}</span>}
                  </span>
                  {dep && (
                    <span className={`mt-1 text-[11px] font-semibold flex items-center gap-1 ${ev.travelConflict ? 'text-[#DC2626]' : 'text-[#C2410C]'}`}>
                      {TRANSPORT_ICONS[ev.transportMode || 'car']} Abfahrt {dep}
                      {ev.travelConflict && <span className="ml-1">⚠ Zeitkonflikt!</span>}
                    </span>
                  )}
                  <span className="sr-only">, im Kalender zeigen</span>
                </button>
                <div className="mt-0.5"><ParticipantAvatars memberIds={ev.memberIds} /></div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ─── quick tasks ──────────────────────────────────────────────────────────────

function QuickTasks({ onNavigate }: { onNavigate: Navigate }) {
  const { tasks } = useTaskData();
  const { memberById } = useCalendarData();
  const urgent = tasks.filter(t => t.assigneeId !== null && t.status !== 'done' && t.status !== 'confirmed' && t.priority === 'high').slice(0, 4);
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <CheckSquareIcon size={16} className="text-[#F97316]" />
          Dringende Aufgaben
        </h2>
        <button onClick={() => onNavigate('tasks')} className="text-xs text-[#2563EB] font-medium hover:underline">Alle Aufgaben →</button>
      </div>
      <div className="space-y-2">
        {urgent.length === 0 && <p className="text-sm text-slate-500 py-4 text-center">Keine dringenden Aufgaben 🎉</p>}
        {urgent.map(t => {
          const member = memberById(t.assigneeId ?? '');
          const c = t.status === 'inprogress' ? '#2563EB' : '#F97316';
          const open = () => onNavigate('tasks', { kind: 'task', id: t.id });
          return (
            <div key={t.id} onClick={open}
              className="flex items-center gap-3 p-2.5 rounded-xl hover:bg-slate-50 cursor-pointer has-[[data-open]:focus-visible]:outline-2 has-[[data-open]:focus-visible]:outline-[#2563EB]">
              <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: c }} />
              <button type="button" data-open className="flex-1 min-w-0 text-left focus-visible:outline-none">
                <span className="block text-sm font-medium text-slate-800 truncate">{t.title}</span>
                <span className="block text-xs text-slate-500">{t.dueDate ? `Fällig ${formatDayMonth(t.dueDate)}` : 'Ohne Frist'}</span>
                <span className="sr-only">, bei den Aufgaben zeigen</span>
              </button>
              <AvatarButton member={member} size={24} />
            </div>
          );
        })}
      </div>
      <button onClick={() => onNavigate('tasks')} className="mt-3 w-full py-2 text-[#C2410C] text-xs font-semibold hover:bg-[#FFF7ED] rounded-xl transition-colors">
        Alle Aufgaben →
      </button>
    </div>
  );
}

// ─── meal widget ──────────────────────────────────────────────────────────────

function MealWidget({ onNavigate }: { onNavigate: Navigate }) {
  const { can } = useAuth();
  const { today } = useMealData();
  if (!can('essen', 'ansehen', 'familie')) return null;
  const meal = (type: MealType) => today.find(e => e.type === type && e.status === 'approved')?.name ?? '—';
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <UtensilsIcon size={16} className="text-[#14B8A6]" />
          Mahlzeiten heute
        </h2>
        <button onClick={() => onNavigate('meals')} className="text-xs text-[#2563EB] font-medium hover:underline">Wochenplan →</button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {[
          { label: '🌅 Frühstück',   meal: meal('fruehstueck'), bg: 'bg-[#FFF7ED]', text: 'text-[#C2410C]' },
          { label: '☀️ Mittagessen', meal: meal('mittagessen'), bg: 'bg-[#F0FDFA]', text: 'text-[#0F766E]' },
          { label: '🌙 Abendessen',  meal: meal('abendessen'),  bg: 'bg-[#EFF6FF]', text: 'text-[#2563EB]' },
          { label: '🍎 Snacks',       meal: meal('snacks'),      bg: 'bg-[#FDF4FF]', text: 'text-[#7C3AED]' },
        ].map(({ label, meal, bg, text }) => (
          <div key={label} className={`p-3 rounded-xl ${bg}`}>
            <div className={`text-[10px] font-semibold uppercase tracking-wide ${text} mb-1`}>{label}</div>
            <div className="text-xs font-medium text-slate-700 leading-snug">{meal}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── shopping widget ──────────────────────────────────────────────────────────

function ShoppingWidget({ onNavigate }: { onNavigate: Navigate }) {
  const { can } = useAuth();
  const { items, setChecked } = useShoppingData();
  const { memberById } = useCalendarData();
  const mayCheck = can('einkauf', 'bearbeiten', 'familie');
  // Gerade Abgehaktes bleibt durchgestrichen stehen, bis man die Übersicht verlässt (so kann man es zurücknehmen)
  const [justChecked, setJustChecked] = useState<Set<string>>(() => new Set());
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const remaining = items.filter(i => i.status === 'approved' && !i.checked);
  const shown = items.filter(i => i.status === 'approved' && (!i.checked || justChecked.has(i.id)))
    .sort((a, b) => Number(b.urgent) - Number(a.urgent));
  const urgent    = remaining.filter(i => i.urgent);
  if (!can('einkauf', 'ansehen', 'familie')) return null;

  const toggle = async (id: string, checked: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await setChecked(id, checked);
      setJustChecked(s => new Set(s).add(id));
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusyId(null);
    }
  };
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <ShoppingCartIcon size={16} className="text-[#22C55E]" />
          Einkaufsliste
        </h2>
        <span className="text-xs text-slate-500">{remaining.length} übrig</span>
      </div>
      {urgent.length > 0 && (
        <div className="mb-2 flex items-center gap-1.5 bg-[#FEF2F2] text-[#B91C1C] text-xs font-medium px-3 py-1.5 rounded-lg">
          <AlertTriangleIcon size={12} />{urgent.length} dringend benötigt
        </div>
      )}
      <div className="space-y-1.5">
        {error && <p role="alert" className="text-xs text-[#DC2626]">{error}</p>}
        {shown.length === 0 && <div className="text-sm text-slate-500 py-2">Alles erledigt 🎉</div>}
        {shown.slice(0, 5).map(item => {
          const member = memberById(item.createdBy);
          return (
            <div key={item.id} className="flex items-center gap-2 text-sm">
              {mayCheck ? (
                <button type="button" onClick={() => toggle(item.id, !item.checked)} disabled={busyId === item.id}
                  aria-label={item.checked ? `${item.name} nicht gekauft` : `${item.name} gekauft`} aria-pressed={item.checked}
                  className={`w-4 h-4 rounded border-2 flex-shrink-0 flex items-center justify-center transition-colors disabled:opacity-50 ${item.checked ? 'bg-[#22C55E] border-[#22C55E]' : 'border-slate-300 hover:border-[#22C55E]'}`}>
                  {item.checked && <CheckIcon size={10} className="text-white" strokeWidth={3} />}
                </button>
              ) : (
                <div className="w-4 h-4 rounded border-2 border-slate-200 flex-shrink-0" />
              )}
              <span className={`flex-1 ${item.checked ? 'line-through text-slate-500' : 'text-slate-700'} ${item.urgent && !item.checked ? 'font-semibold' : ''}`}>{item.name}</span>
              {item.urgent && !item.checked && <span className="text-[10px] bg-[#FEF2F2] text-[#B91C1C] px-1.5 py-0.5 rounded-full font-medium">Dringend</span>}
              <div className="w-4 h-4 rounded-full" style={{ backgroundColor: member?.color }} title={member?.name} />
            </div>
          );
        })}
        {shown.length > 5 && <div className="text-xs text-slate-500 pl-6">+{shown.length - 5} weitere</div>}
      </div>
      <button onClick={() => onNavigate('shopping')} className="mt-3 w-full py-2 text-[#15803D] text-xs font-semibold hover:bg-[#F0FDF4] rounded-xl transition-colors">
        Einkaufsliste öffnen →
      </button>
    </div>
  );
}

// ─── points widget ────────────────────────────────────────────────────────────

function PointsWidget({ onNavigate }: { onNavigate: Navigate }) {
  const children  = usePointHolders();
  const maxPoints = Math.max(1, ...children.map(c => c.points));
  if (children.length === 0) return null;
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <StarIcon size={16} className="text-[#F59E0B]" />
          Familienpunkte
        </h2>
        <button onClick={() => onNavigate('rewards')} className="text-xs text-[#2563EB] font-medium hover:underline">Belohnungen →</button>
      </div>
      <div className="space-y-3">
        {children.map(c => (
          <div key={c.id}>
            <div className="flex items-center justify-between mb-1">
              <div className="flex items-center gap-2">
                <div className="w-7 h-7 rounded-full flex items-center justify-center text-white text-xs font-bold" style={{ backgroundColor: readable(c.color) }}>{c.initials[0]}</div>
                <span className="text-sm font-medium text-slate-800">{c.name}</span>
              </div>
              <span className="text-sm font-bold" style={{ color: readable(c.color) }}>{c.points} Pkt.</span>
            </div>
            <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
              <div className="h-full rounded-full relative overflow-hidden transition-all duration-700" style={{ width: `${(c.points / maxPoints) * 100}%`, backgroundColor: c.color }}>
                <div className="absolute inset-0 progress-shimmer" />
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── AI banner ────────────────────────────────────────────────────────────────

function AIBanner({ onNavigate }: { onNavigate: Navigate }) {
  return (
    <div className="bg-gradient-to-r from-[#1E40AF] via-[#2563EB] to-[#14B8A6] rounded-2xl p-4 text-white flex items-center gap-4">
      <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center flex-shrink-0">
        <SparklesIcon size={20} className="text-white" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-sm flex items-center gap-2">
          FamilyHub KI {PLACEHOLDER.assistant && <SoonBadge onDark label="Vorschau" />}
        </div>
        <div className="text-white/80 text-xs mt-0.5 truncate">
          Bald: Tipps der KI für euren Tag, z. B. wer wann wohin muss und was noch fehlt
        </div>
      </div>
      <button onClick={() => onNavigate('assistant')} className="flex-shrink-0 bg-white/20 hover:bg-white/30 text-white text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors">
        {PLACEHOLDER.assistant ? 'Vorschau ansehen' : 'KI fragen'}
      </button>
    </div>
  );
}

// ─── dashboard ────────────────────────────────────────────────────────────────

export default function Dashboard({ onNavigate }: Props) {
  return (
    <div className="p-4 lg:p-6 max-w-[1600px] mx-auto">
      <div className="mb-5">
        <AIBanner onNavigate={onNavigate} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_1.2fr] xl:grid-cols-[360px_1fr] gap-5">
        {/* Left column */}
        <div className="space-y-5">
          <MiniCalendar onNavigate={onNavigate} />
          <WeatherWidget onNavigate={onNavigate} />
          <PointsWidget onNavigate={onNavigate} />
        </div>

        {/* Right column */}
        <div className="space-y-5">
          <TodayAgenda onNavigate={onNavigate} />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            <QuickTasks onNavigate={onNavigate} />
            <MealWidget onNavigate={onNavigate} />
          </div>
          <ShoppingWidget onNavigate={onNavigate} />
        </div>
      </div>
    </div>
  );
}
