import { useState } from 'react';
import {
  FAMILY_MEMBERS, INITIAL_TASKS, INITIAL_SHOPPING,
  MEALS, WEATHER, CLOTHING_RECOMMENDATIONS, getWeatherCondition,
  type CalendarEvent,
} from './data';
import { useCalendarData } from '../calendar/CalendarDataContext';
import {
  CalendarIcon, CheckSquareIcon, ShoppingCartIcon, UtensilsIcon,
  StarIcon, ChevronLeftIcon, ChevronRightIcon, ClockIcon,
  MapPinIcon, AlertTriangleIcon, SparklesIcon,
} from './Icons';

type Page = 'dashboard' | 'calendar' | 'tasks' | 'rewards' | 'shopping' | 'meals' | 'assistant' | 'messenger' | 'profiles';
interface Props { onNavigate: (p: Page) => void; }

const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const DAYS   = ['Su','Mo','Tu','We','Th','Fr','Sa'];

const TRANSPORT_ICONS: Record<string, string> = {
  car: '🚗', transit: '🚌', bike: '🚲', walk: '🚶',
};

// ─── mini calendar ───────────────────────────────────────────────────────────

function MiniCalendar({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const { events: calendarEvents, memberById } = useCalendarData();
  const today    = new Date(2026, 8, 21);
  const [viewDate, setViewDate] = useState(new Date(2026, 8, 1));
  const year     = viewDate.getFullYear();
  const month    = viewDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInM  = new Date(year, month + 1, 0).getDate();
  const daysInP  = new Date(year, month, 0).getDate();

  const cells: { day: number; type: 'prev' | 'curr' | 'next' }[] = [];
  for (let i = firstDay - 1; i >= 0; i--) cells.push({ day: daysInP - i, type: 'prev' });
  for (let d = 1; d <= daysInM; d++) cells.push({ day: d, type: 'curr' });
  while (cells.length % 7 !== 0) cells.push({ day: cells.length - daysInM - firstDay + 1, type: 'next' });

  const getEvents = (d: number) => {
    const s = `${year}-${String(month + 1).padStart(2,'0')}-${String(d).padStart(2,'0')}`;
    return calendarEvents.filter(e => e.date === s);
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base">{MONTHS[month]} {year}</h2>
        <div className="flex gap-1">
          <button onClick={() => setViewDate(new Date(year, month - 1, 1))} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 transition-colors" aria-label="Prev month"><ChevronLeftIcon size={16} /></button>
          <button onClick={() => setViewDate(new Date(year, month + 1, 1))} className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 transition-colors" aria-label="Next month"><ChevronRightIcon size={16} /></button>
        </div>
      </div>

      <div className="grid grid-cols-7 mb-2">
        {DAYS.map(d => <div key={d} className="text-center text-[10px] font-semibold text-slate-400 py-1 uppercase tracking-wide">{d}</div>)}
      </div>

      <div className="grid grid-cols-7 gap-y-0.5">
        {cells.map((cell, i) => {
          const events  = cell.type === 'curr' ? getEvents(cell.day) : [];
          const isToday = cell.type === 'curr' && cell.day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
          const hasConflict = events.some(e => e.conflict || e.travelConflict);
          return (
            <button
              key={i}
              onClick={() => onNavigate('calendar')}
              className={`flex flex-col items-center py-1 rounded-lg transition-colors relative
                ${cell.type !== 'curr' ? 'opacity-25' : 'hover:bg-slate-50'}
                ${isToday ? 'bg-[#2563EB] hover:bg-[#2563EB]' : ''}`}
            >
              <span className={`text-xs font-medium leading-5 ${isToday ? 'text-white' : 'text-slate-700'} ${cell.type !== 'curr' ? 'text-slate-300' : ''}`}>
                {cell.day}
              </span>
              {events.length > 0 && !isToday && (
                <div className="flex gap-0.5 mt-0.5">
                  {events.slice(0, 3).map((ev, ei) => {
                    const m = memberById(ev.memberId);
                    return <div key={ei} className="w-1 h-1 rounded-full" style={{ backgroundColor: m?.color || '#94A3B8' }} />;
                  })}
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

// ─── weather + integrated clothing chips ─────────────────────────────────────

function WeatherWidget() {
  const w         = WEATHER;
  const condition = getWeatherCondition(w.today.temp, w.today.condition);
  const rec       = CLOTHING_RECOMMENDATIONS[condition];

  return (
    <div className="bg-gradient-to-br from-[#2563EB] to-[#14B8A6] rounded-2xl p-4 text-white relative overflow-hidden">
      {/* decorative circles */}
      <div className="absolute top-0 right-0 w-24 h-24 rounded-full bg-white/10 -translate-y-6 translate-x-6 pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-16 h-16 rounded-full bg-white/10 translate-y-4 -translate-x-4 pointer-events-none" />

      <div className="relative">
        {/* Temperature row */}
        <div className="flex items-start justify-between">
          <div>
            <div className="text-4xl font-light">{w.today.temp}°</div>
            <div className="text-white/80 text-sm mt-0.5">{w.today.condition}</div>
            <div className="text-white/60 text-xs mt-1">H:{w.today.high}° · L:{w.today.low}° · 💧{w.today.humidity}% · 💨{w.today.wind} km/h</div>
          </div>
          <div className="text-4xl">{w.today.icon}</div>
        </div>

        {/* 4-day forecast */}
        <div className="flex gap-2 mt-3 border-t border-white/20 pt-3">
          {w.forecast.slice(0, 4).map(f => (
            <div key={f.day} className="flex-1 flex flex-col items-center gap-0.5">
              <div className="text-white/60 text-[10px]">{f.day}</div>
              <div className="text-sm">{f.icon}</div>
              <div className="text-white text-[10px] font-medium">{f.high}°</div>
            </div>
          ))}
        </div>

        {/* Clothing recommendation chips */}
        <div className="mt-3 pt-3 border-t border-white/20">
          <div className="text-white/70 text-[10px] font-semibold uppercase tracking-wide mb-2">
            {rec.emoji} Empfehlungen für heute
          </div>
          <div className="flex flex-wrap gap-1.5">
            {rec.items.map(item => (
              <span
                key={item.label}
                className="flex items-center gap-1 bg-white/20 hover:bg-white/30 transition-colors rounded-full px-2.5 py-1 text-xs font-medium text-white cursor-default"
                title={item.reason}
              >
                <span>{item.icon}</span>
                <span>{item.label}</span>
              </span>
            ))}
          </div>
        </div>
      </div>
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

function TodayAgenda({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const { events, memberById } = useCalendarData();
  const todayEvents = events
    .filter(e => e.date === '2026-09-21')
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
        <p className="text-sm text-slate-400 py-4 text-center">Heute keine Termine 🎉</p>
      ) : (
        <div className="space-y-2.5">
          {todayEvents.map(ev => {
            const member = memberById(ev.memberId);
            const dep    = departureStr(ev);
            return (
              <div
                key={ev.id}
                className={`flex items-start gap-3 p-3 rounded-xl transition-colors cursor-pointer ${ev.travelConflict ? 'bg-[#FEF2F2]' : 'hover:bg-slate-50'}`}
                style={{ borderLeft: `3px solid ${ev.travelConflict ? '#EF4444' : (member?.color || '#94A3B8')}` }}
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="text-sm font-semibold text-slate-800">{ev.title}</span>
                    {(ev.conflict || ev.travelConflict) && (
                      <span className="text-[10px] bg-[#FEF2F2] text-[#EF4444] px-1.5 py-0.5 rounded-full font-medium flex items-center gap-1">
                        <AlertTriangleIcon size={10} /> Konflikt
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5 text-xs text-slate-400 flex-wrap">
                    <span className="flex items-center gap-1"><ClockIcon size={10} />{ev.time}{ev.endTime ? ` – ${ev.endTime}` : ''}</span>
                    {ev.location && <span className="flex items-center gap-1"><MapPinIcon size={10} />{ev.location}</span>}
                  </div>
                  {dep && (
                    <div className={`mt-1 text-[11px] font-semibold flex items-center gap-1 ${ev.travelConflict ? 'text-[#EF4444]' : 'text-[#F97316]'}`}>
                      {TRANSPORT_ICONS[ev.transportMode || 'car']} Abfahrt {dep}
                      {ev.travelConflict && <span className="ml-1">⚠ Zeitkonflikt!</span>}
                    </div>
                  )}
                </div>
                <div
                  className="w-6 h-6 rounded-full flex items-center justify-center text-[9px] font-bold text-white flex-shrink-0 mt-0.5"
                  style={{ backgroundColor: member?.color }}
                  title={member?.name}
                >
                  {member?.initials[0]}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ─── quick tasks ──────────────────────────────────────────────────────────────

function QuickTasks({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const urgent = INITIAL_TASKS.filter(t => t.status !== 'done' && t.priority === 'high').slice(0, 4);
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <CheckSquareIcon size={16} className="text-[#F97316]" />
          Dringende Aufgaben
        </h2>
        <button onClick={() => onNavigate('tasks')} className="text-xs text-[#2563EB] font-medium hover:underline">Kanban →</button>
      </div>
      <div className="space-y-2">
        {urgent.map(t => {
          const member = FAMILY_MEMBERS.find(m => m.id === t.assigneeId);
          const c = { todo: '#F97316', inprogress: '#2563EB', done: '#22C55E' }[t.status];
          return (
            <div key={t.id} className="flex items-center gap-3 p-2.5 rounded-xl hover:bg-slate-50 cursor-pointer">
              <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ backgroundColor: c }} />
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium text-slate-800 truncate">{t.title}</div>
                <div className="text-xs text-slate-400">Fällig {t.dueDate.split('-').slice(1).join('/')}</div>
              </div>
              <div className="w-6 h-6 rounded-full flex items-center justify-center text-[9px] font-bold text-white flex-shrink-0" style={{ backgroundColor: member?.color }}>{member?.initials[0]}</div>
            </div>
          );
        })}
      </div>
      <button onClick={() => onNavigate('tasks')} className="mt-3 w-full py-2 text-[#F97316] text-xs font-semibold hover:bg-[#FFF7ED] rounded-xl transition-colors">
        Alle Aufgaben →
      </button>
    </div>
  );
}

// ─── meal widget ──────────────────────────────────────────────────────────────

function MealWidget({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const today = MEALS['Mon'];
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
          { label: '🌅 Frühstück',   meal: today.breakfast, bg: 'bg-[#FFF7ED]', text: 'text-[#F97316]' },
          { label: '☀️ Mittagessen', meal: today.lunch,     bg: 'bg-[#F0FDFA]', text: 'text-[#14B8A6]' },
          { label: '🌙 Abendessen',  meal: today.dinner,    bg: 'bg-[#EFF6FF]', text: 'text-[#2563EB]' },
          { label: '🍎 Snacks',       meal: today.snacks,   bg: 'bg-[#FDF4FF]', text: 'text-[#8B5CF6]' },
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

function ShoppingWidget({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const remaining = INITIAL_SHOPPING.filter(i => !i.checked);
  const urgent    = remaining.filter(i => i.urgent);
  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <div className="flex items-center justify-between mb-3">
        <h2 className="font-bold text-slate-800 text-base flex items-center gap-2">
          <ShoppingCartIcon size={16} className="text-[#22C55E]" />
          Einkaufsliste
        </h2>
        <span className="text-xs text-slate-400">{remaining.length} übrig</span>
      </div>
      {urgent.length > 0 && (
        <div className="mb-2 flex items-center gap-1.5 bg-[#FEF2F2] text-[#EF4444] text-xs font-medium px-3 py-1.5 rounded-lg">
          <AlertTriangleIcon size={12} />{urgent.length} dringend benötigt
        </div>
      )}
      <div className="space-y-1.5">
        {remaining.slice(0, 5).map(item => {
          const member = FAMILY_MEMBERS.find(m => m.id === item.addedById);
          return (
            <div key={item.id} className="flex items-center gap-2 text-sm">
              <div className="w-4 h-4 rounded border-2 border-slate-200 flex-shrink-0" />
              <span className={`flex-1 text-slate-700 ${item.urgent ? 'font-semibold' : ''}`}>{item.name}</span>
              {item.urgent && <span className="text-[10px] bg-[#FEF2F2] text-[#EF4444] px-1.5 py-0.5 rounded-full font-medium">Dringend</span>}
              <div className="w-4 h-4 rounded-full" style={{ backgroundColor: member?.color }} title={member?.name} />
            </div>
          );
        })}
        {remaining.length > 5 && <div className="text-xs text-slate-400 pl-6">+{remaining.length - 5} weitere</div>}
      </div>
      <button onClick={() => onNavigate('shopping')} className="mt-3 w-full py-2 text-[#22C55E] text-xs font-semibold hover:bg-[#F0FDF4] rounded-xl transition-colors">
        Einkaufsliste öffnen →
      </button>
    </div>
  );
}

// ─── points widget ────────────────────────────────────────────────────────────

function PointsWidget({ onNavigate }: { onNavigate: (p: Page) => void }) {
  const children  = FAMILY_MEMBERS.filter(m => m.role === 'Child');
  const maxPoints = Math.max(...children.map(c => c.points));
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
                <div className="w-7 h-7 rounded-full flex items-center justify-center text-white text-xs font-bold" style={{ backgroundColor: c.color }}>{c.initials[0]}</div>
                <span className="text-sm font-medium text-slate-800">{c.name}</span>
              </div>
              <span className="text-sm font-bold" style={{ color: c.color }}>{c.points} Pkt.</span>
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

function AIBanner({ onNavigate }: { onNavigate: (p: Page) => void }) {
  return (
    <div className="bg-gradient-to-r from-[#1E40AF] via-[#2563EB] to-[#14B8A6] rounded-2xl p-4 text-white flex items-center gap-4">
      <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center flex-shrink-0">
        <SparklesIcon size={20} className="text-white" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-sm">FamilyHub KI</div>
        <div className="text-white/80 text-xs mt-0.5 truncate">
          Lucas hat Mathe noch nicht begonnen · Soccer 16:30 · Morgen: Gelber Sack rausstellen ♻️
        </div>
      </div>
      <button onClick={() => onNavigate('assistant')} className="flex-shrink-0 bg-white/20 hover:bg-white/30 text-white text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors">
        KI fragen
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
          <WeatherWidget />
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
