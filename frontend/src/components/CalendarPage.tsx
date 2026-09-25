import { useState } from 'react';
import {
  GARBAGE_PICKUPS,
  CalendarEvent, type GarbagePickup,
} from './data';
import {
  ChevronLeftIcon, ChevronRightIcon, PlusIcon,
  AlertTriangleIcon, ClockIcon, MapPinIcon,
} from './Icons';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { useCalendarPermissions } from '../calendar/permissions';
import EventFormModal from './EventFormModal';
import { addDays, fromDateKey, startOfToday, toDateKey } from '../calendar/dates';

type SelectEvent = (event: CalendarEvent) => void;

type Page = string;
interface Props { onNavigate: (p: any) => void; }

type View = 'month' | 'week' | 'day';
const MONTHS     = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const DAYS_SHORT = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];

const CATEGORY_COLORS: Record<string, string> = {
  school:      '#2563EB',
  sports:      '#22C55E',
  appointment: '#F97316',
  family:      '#8B5CF6',
  work:        '#14B8A6',
  reminder:    '#94A3B8',
};

const CATEGORY_LABELS: Record<string, string> = {
  school:      '📚 School',
  sports:      '⚽ Sports',
  appointment: '🏥 Appointment',
  family:      '👨‍👩‍👧‍👦 Family',
  work:        '💼 Work',
  reminder:    '🔔 Reminder',
};

const TRANSPORT_ICONS: Record<string, string> = {
  car:     '🚗',
  transit: '🚌',
  bike:    '🚲',
  walk:    '🚶',
};

const WASTE_STYLES: Record<string, { dot: string; label: string; bg: string }> = {
  'Gelber Sack': { dot: '#CA8A04', label: '🟡 Gelber Sack', bg: '#FEF9C3' },
  'Papier':      { dot: '#2563EB', label: '🔵 Papier',      bg: '#EFF6FF' },
  'Biomüll':     { dot: '#16A34A', label: '🟢 Biomüll',     bg: '#F0FDF4' },
  'Restmüll':    { dot: '#6B7280', label: '⚫ Restmüll',    bg: '#F9FAFB' },
};

// ─── helpers ─────────────────────────────────────────────────────────────────

function departureMins(ev: CalendarEvent): number | null {
  if (!ev.travelTime) return null;
  const [h, m] = ev.time.split(':').map(Number);
  return h * 60 + m - ev.travelTime;
}

function minsToTime(mins: number): string {
  const h = Math.floor(mins / 60);
  const m = mins % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

function timeToMins(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

// ─── compact event pill (month grid) ─────────────────────────────────────────

// Vorschläge (warten auf Freigabe) und private Termine sollen auf einen Blick erkennbar sein.
const eventMarker = (event: CalendarEvent) =>
  `${event.status === 'proposed' ? '⏳ ' : ''}${event.private ? '🔒 ' : ''}`;

const proposalStyle = (event: CalendarEvent, color: string) =>
  event.status === 'proposed'
    ? { backgroundColor: `${color}33`, color, border: `1px dashed ${color}` }
    : { backgroundColor: color };

function EventPill({ event, compact = false, onSelect }: { event: CalendarEvent; compact?: boolean; onSelect: SelectEvent }) {
  const { memberById } = useCalendarData();
  const member = memberById(event.memberId);
  const color  = member?.color || CATEGORY_COLORS[event.category] || '#94A3B8';

  if (compact) {
    return (
      <div
        className={`text-[9px] font-medium px-1.5 py-0.5 rounded-md truncate flex items-center gap-0.5 cursor-pointer hover:opacity-80 ${event.status === 'proposed' ? '' : 'text-white'} ${event.travelConflict ? 'ring-1 ring-[#EF4444]' : ''}`}
        style={proposalStyle(event, color)}
        title={`${eventMarker(event)}${event.title}${event.status === 'proposed' ? ' (Vorschlag)' : ''}`}
        onClick={e => { e.stopPropagation(); onSelect(event); }}
      >
        {(event.conflict || event.travelConflict) && <span>⚠</span>}
        {event.transportMode && <span>{TRANSPORT_ICONS[event.transportMode]}</span>}
        {eventMarker(event)}{event.title}
      </div>
    );
  }

  return (
    <div
      className={`flex items-start gap-2.5 p-3 rounded-xl cursor-pointer border transition-all hover:shadow-sm ${event.travelConflict ? 'border-[#FECACA] bg-[#FEF2F2]' : 'border-transparent hover:border-slate-100'}`}
      style={{ borderLeft: `3px solid ${event.travelConflict ? '#EF4444' : color}`, background: event.travelConflict ? undefined : `${color}10` }}
      onClick={() => onSelect(event)}
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-sm font-semibold text-slate-800">{event.title}</span>
          {event.status === 'proposed' && (
            <span className="text-[10px] bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A] px-1.5 py-0.5 rounded-full font-medium">⏳ Vorschlag</span>
          )}
          {event.private && (
            <span className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-full font-medium">🔒 Privat</span>
          )}
          {(event.conflict || event.travelConflict) && (
            <span className="text-[10px] bg-[#FEF2F2] text-[#EF4444] px-1.5 py-0.5 rounded-full font-medium flex items-center gap-1">
              <AlertTriangleIcon size={10} />
              {event.travelConflict ? 'Fahrzeit-Konflikt' : 'Konflikt'}
            </span>
          )}
          <span className="text-[10px] px-1.5 py-0.5 rounded-full font-medium text-white" style={{ backgroundColor: CATEGORY_COLORS[event.category] + 'CC' }}>
            {CATEGORY_LABELS[event.category]}
          </span>
        </div>
        <div className="flex items-center gap-3 mt-1 text-xs text-slate-500 flex-wrap">
          <span className="flex items-center gap-1">
            <ClockIcon size={11} />
            {event.time}{event.endTime ? ` – ${event.endTime}` : ''}
          </span>
          {event.location && (
            <span className="flex items-center gap-1">
              <MapPinIcon size={11} />
              {event.location}
            </span>
          )}
        </div>
        {/* Departure info */}
        {event.travelTime && (
          <div className={`mt-1.5 flex items-center gap-1.5 text-xs font-semibold ${event.travelConflict ? 'text-[#EF4444]' : 'text-[#F97316]'}`}>
            <span>{TRANSPORT_ICONS[event.transportMode || 'car']}</span>
            <span>{event.travelTime} min Fahrt</span>
            <span>·</span>
            <span>Abfahrt {minsToTime(departureMins(event)!)}</span>
            {event.travelConflict && <span className="ml-1 text-[#EF4444]">⚠ Zeitkonflikt!</span>}
          </div>
        )}
      </div>
      <div
        className="w-7 h-7 rounded-full flex items-center justify-center text-[10px] font-bold text-white flex-shrink-0"
        style={{ backgroundColor: member?.color || '#94A3B8' }}
        title={member?.name}
      >
        {member?.initials[0]}
      </div>
    </div>
  );
}

// ─── month view ───────────────────────────────────────────────────────────────

function MonthView({ year, month, events: allEvents, onSelect }: { year: number; month: number; events: CalendarEvent[]; onSelect: SelectEvent }) {
  const firstDay    = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const daysInPrev  = new Date(year, month, 0).getDate();
  const today       = startOfToday();

  const garbageByDate: Record<string, GarbagePickup> = {};
  GARBAGE_PICKUPS.forEach(g => { garbageByDate[g.date] = g; });

  const cells: { day: number; type: 'prev' | 'curr' | 'next' }[] = [];
  for (let i = firstDay - 1; i >= 0; i--) cells.push({ day: daysInPrev - i, type: 'prev' });
  for (let d = 1; d <= daysInMonth; d++) cells.push({ day: d, type: 'curr' });
  while (cells.length < 42) cells.push({ day: cells.length - daysInMonth - firstDay + 1, type: 'next' });

  const getEventsForDay = (d: number) => {
    const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    return allEvents.filter(e => e.date === dateStr).sort((a, b) => a.time.localeCompare(b.time));
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      <div className="grid grid-cols-7 border-b border-slate-100">
        {DAYS_SHORT.map(d => (
          <div key={d} className="text-center py-3 text-xs font-semibold text-slate-500 uppercase tracking-wide">{d}</div>
        ))}
      </div>

      <div className="grid grid-cols-7 divide-x divide-slate-50">
        {cells.map((cell, i) => {
          const events  = cell.type === 'curr' ? getEventsForDay(cell.day) : [];
          const isToday = cell.type === 'curr' && cell.day === today.getDate() && month === today.getMonth() && year === today.getFullYear();
          const hasConflict = events.some(e => e.conflict || e.travelConflict);
          const isWeekEnd   = i % 7 === 0 || i % 7 === 6;
          const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(cell.day).padStart(2, '0')}`;
          const garbage = cell.type === 'curr' ? garbageByDate[dateStr] : undefined;

          return (
            <div
              key={i}
              className={`min-h-[90px] lg:min-h-[110px] p-1.5 border-b border-slate-50 hover:bg-slate-50 transition-colors cursor-pointer
                ${cell.type !== 'curr' ? 'bg-slate-50/50' : ''}
                ${isWeekEnd && cell.type === 'curr' ? 'bg-blue-50/20' : ''}`}
            >
              <div className="flex items-center justify-between mb-1">
                <span className={`inline-flex items-center justify-center w-6 h-6 text-xs font-semibold rounded-full
                  ${isToday ? 'bg-[#2563EB] text-white' : cell.type !== 'curr' ? 'text-slate-300' : 'text-slate-700'}`}>
                  {cell.day}
                </span>
                {hasConflict && <AlertTriangleIcon size={11} className="text-[#EF4444]" />}
              </div>

              {/* Garbage badge */}
              {garbage && (
                <div
                  className="text-[8px] font-bold px-1 py-0.5 rounded mb-0.5 truncate"
                  style={{ backgroundColor: WASTE_STYLES[garbage.type]?.bg, color: WASTE_STYLES[garbage.type]?.dot }}
                >
                  {WASTE_STYLES[garbage.type]?.label}
                </div>
              )}

              <div className="space-y-0.5">
                {events.slice(0, 3).map(ev => <EventPill key={ev.id} event={ev} compact onSelect={onSelect} />)}
                {events.length > 3 && <div className="text-[9px] text-slate-400 px-1">+{events.length - 3} more</div>}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── week view (enhanced) ─────────────────────────────────────────────────────

function WeekView({ weekOffset, events, onSelect }: { weekOffset: number; events: CalendarEvent[]; onSelect: SelectEvent }) {
  const { memberById } = useCalendarData();
  const todayKey = toDateKey(startOfToday());
  const startOfWeek = addDays(startOfToday(), weekOffset * 7);
  const dow = startOfWeek.getDay();
  startOfWeek.setDate(startOfWeek.getDate() - dow);

  const weekDays = Array.from({ length: 7 }, (_, i) => {
    const d = new Date(startOfWeek);
    d.setDate(d.getDate() + i);
    return d;
  });

  const hours = Array.from({ length: 15 }, (_, i) => i + 6); // 6am–8pm

  const garbageByDate: Record<string, GarbagePickup> = {};
  GARBAGE_PICKUPS.forEach(g => { garbageByDate[g.date] = g; });

  const getEventsForDay = (date: Date) => {
    const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return events.filter(e => e.date === dateStr);
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      {/* Header */}
      <div className="grid border-b border-slate-100" style={{ gridTemplateColumns: '52px repeat(7, 1fr)' }}>
        <div className="border-r border-slate-100" />
        {weekDays.map(d => {
          const isToday    = toDateKey(d) === todayKey;
          const dateStr    = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
          const events     = getEventsForDay(d);
          const garbage    = garbageByDate[dateStr];
          const hasConflict = events.some(e => e.conflict || e.travelConflict);

          return (
            <div key={d.toISOString()} className={`text-center py-2 border-r border-slate-50 ${isToday ? 'bg-[#EFF6FF]' : ''}`}>
              <div className={`text-[10px] font-semibold ${isToday ? 'text-[#2563EB]' : 'text-slate-400'} uppercase`}>
                {DAYS_SHORT[d.getDay()]}
              </div>
              <div className={`inline-flex items-center justify-center w-8 h-8 rounded-full font-bold text-sm mx-auto mt-1 ${isToday ? 'bg-[#2563EB] text-white' : 'text-slate-700'}`}>
                {d.getDate()}
              </div>
              {/* dots + conflict */}
              <div className="flex justify-center items-center gap-0.5 mt-1 min-h-[8px]">
                {events.slice(0, 3).map(ev => {
                  const m = memberById(ev.memberId);
                  return (
                    <div
                      key={ev.id}
                      className={`w-1.5 h-1.5 rounded-full ${ev.travelConflict ? 'ring-1 ring-[#EF4444]' : ''}`}
                      style={{ backgroundColor: m?.color }}
                    />
                  );
                })}
                {hasConflict && <AlertTriangleIcon size={9} className="text-[#EF4444] ml-0.5" />}
              </div>
              {/* Garbage reminder badge */}
              {garbage && (
                <div
                  className="text-[8px] font-bold mx-1 mt-1 px-1 py-0.5 rounded truncate"
                  style={{ backgroundColor: WASTE_STYLES[garbage.type]?.bg, color: WASTE_STYLES[garbage.type]?.dot }}
                >
                  🗑 {garbage.type}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Time grid */}
      <div className="overflow-y-auto max-h-[520px] scrollbar-hide">
        {hours.map(h => (
          <div key={h} className="grid border-b border-slate-50 min-h-[64px]" style={{ gridTemplateColumns: '52px repeat(7, 1fr)' }}>
            {/* Hour label */}
            <div className="border-r border-slate-100 py-1 pr-2 text-right flex-shrink-0">
              <span className="text-[10px] text-slate-400 font-medium">
                {h > 12 ? h - 12 : h}{h >= 12 ? 'pm' : 'am'}
              </span>
            </div>

            {weekDays.map((d, di) => {
              const allDayEvents = getEventsForDay(d);

              // events whose start hour == h
              const hourEvents = allDayEvents.filter(ev => parseInt(ev.time.split(':')[0]) === h);

              // events whose departure falls in this hour
              const departureEvents = allDayEvents.filter(ev => {
                if (!ev.travelTime) return false;
                const depMins  = departureMins(ev)!;
                const depHour  = Math.floor(depMins / 60);
                const startH   = parseInt(ev.time.split(':')[0]);
                return depHour === h && startH !== h; // show departure row only if event itself is in different row
              });

              return (
                <div key={di} className="border-r border-slate-50 p-0.5 space-y-0.5 relative">
                  {/* Regular event blocks */}
                  {hourEvents.map(ev => {
                    const member = memberById(ev.memberId);
                    const hasTravel = !!ev.travelTime;
                    const hasTravelConflict = ev.travelConflict;

                    return (
                      <div
                        key={ev.id}
                        className={`text-[10px] font-medium px-1.5 py-1 rounded-lg cursor-pointer hover:opacity-80 transition-opacity ${hasTravelConflict ? 'ring-1 ring-[#EF4444]' : ''}`}
                        style={hasTravelConflict ? {
                          backgroundColor: '#FEF2F2',
                          color: '#DC2626',
                          borderLeft: '2px solid #EF4444',
                        } : ev.status === 'proposed'
                          ? proposalStyle(ev, member?.color || '#94A3B8')
                          : { backgroundColor: member?.color || '#94A3B8', color: 'white' }}
                        title={`${eventMarker(ev)}${ev.title} at ${ev.time}`}
                        onClick={() => onSelect(ev)}
                      >
                        <div className="truncate font-semibold">{eventMarker(ev)}{ev.title}</div>
                        <div className="opacity-80 text-[9px]">{ev.time}{ev.endTime ? `–${ev.endTime}` : ''}</div>
                        {hasTravel && (
                          <div className={`text-[9px] font-bold mt-0.5 ${hasTravelConflict ? 'text-[#EF4444]' : 'text-white/90'}`}>
                            {TRANSPORT_ICONS[ev.transportMode || 'car']} Abf. {minsToTime(departureMins(ev)!)}
                            {hasTravelConflict && ' ⚠'}
                          </div>
                        )}
                      </div>
                    );
                  })}

                  {/* Departure-only markers (when departure is in a different hour than the event) */}
                  {departureEvents.map(ev => {
                    const member = memberById(ev.memberId);
                    const depMin = departureMins(ev)!;
                    return (
                      <div
                        key={`dep-${ev.id}`}
                        className="text-[9px] font-semibold px-1.5 py-1 rounded-lg border border-dashed flex items-center gap-1"
                        style={{
                          borderColor: ev.travelConflict ? '#EF4444' : (member?.color || '#F97316'),
                          color: ev.travelConflict ? '#EF4444' : (member?.color || '#F97316'),
                          backgroundColor: ev.travelConflict ? '#FEF2F2' : `${member?.color || '#F97316'}10`,
                        }}
                        title={`Abfahrt für: ${ev.title}`}
                      >
                        <span>{TRANSPORT_ICONS[ev.transportMode || 'car']}</span>
                        <span>Abf. {minsToTime(depMin)}</span>
                        <span className="truncate opacity-70">→ {ev.title}</span>
                        {ev.travelConflict && <span>⚠</span>}
                      </div>
                    );
                  })}
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── day view ─────────────────────────────────────────────────────────────────

function DayView({ day, events, onSelect }: { day: Date; events: CalendarEvent[]; onSelect: SelectEvent }) {
  const dayKey = toDateKey(day);
  const isToday = dayKey === toDateKey(startOfToday());
  const dayEvents = events
    .filter(e => e.date === dayKey)
    .sort((a, b) => a.time.localeCompare(b.time));

  return (
    <div className="space-y-3">
      {dayEvents.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-100 p-10 text-center text-slate-400">
          {isToday ? 'Heute keine Termine 🎉' : 'An diesem Tag keine Termine'}
        </div>
      ) : (
        dayEvents.map(ev => <EventPill key={ev.id} event={ev} onSelect={onSelect} />)
      )}
    </div>
  );
}

// ─── main component ───────────────────────────────────────────────────────────

export default function CalendarPage({ onNavigate }: Props) {
  const [view, setView]               = useState<View>('month');
  const [year, setYear]               = useState(() => startOfToday().getFullYear());
  const [month, setMonth]             = useState(() => startOfToday().getMonth());
  const [weekOffset, setWeekOffset]   = useState(0);
  const [dayOffset, setDayOffset]     = useState(0);
  const [selectedMember, setSelectedMember] = useState<string | null>(null);
  const [editor, setEditor] = useState<{ event?: CalendarEvent } | null>(null);

  const [decisionError, setDecisionError] = useState<string | null>(null);

  const { status, error, members, events, reload, memberById, approveEvent, rejectEvent } = useCalendarData();
  const permissions = useCalendarPermissions();
  const visibleEvents = selectedMember ? events.filter(e => e.memberId === selectedMember) : events;
  const proposals = events.filter(e => e.status === 'proposed');
  const openEditor = (event?: CalendarEvent) => setEditor({ event });

  const decide = async (action: (id: string) => Promise<void>, id: string) => {
    setDecisionError(null);
    try {
      await action(id);
    } catch (err) {
      setDecisionError(err instanceof Error ? err.message : String(err));
    }
  };

  const prev = () => {
    if (view === 'month') {
      if (month === 0) { setYear(y => y - 1); setMonth(11); } else setMonth(m => m - 1);
    } else if (view === 'week') setWeekOffset(w => w - 1);
    else setDayOffset(d => d - 1);
  };
  const next = () => {
    if (view === 'month') {
      if (month === 11) { setYear(y => y + 1); setMonth(0); } else setMonth(m => m + 1);
    } else if (view === 'week') setWeekOffset(w => w + 1);
    else setDayOffset(d => d + 1);
  };

  const today = startOfToday();
  const todayKey = toDateKey(today);
  const shownDay = addDays(today, dayOffset);
  const weekStart = addDays(today, weekOffset * 7 - today.getDay());
  const heading = view === 'day'
    ? shownDay.toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'long', year: 'numeric' })
    : view === 'week' ? `${MONTHS[weekStart.getMonth()]} ${weekStart.getFullYear()}` : `${MONTHS[month]} ${year}`;
  // Rest der Woche (bis Sonntag) nach dem gezeigten Tag, für "Diese Woche" in der Tagesansicht
  const endOfShownWeek = toDateKey(addDays(shownDay, (7 - shownDay.getDay()) % 7));

  const conflicts = visibleEvents.filter(e => e.conflict || e.travelConflict);

  // Upcoming garbage pickups for sidebar
  const upcomingGarbage = GARBAGE_PICKUPS
    .filter(g => g.date >= todayKey)
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(0, 3);

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Controls */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex items-center gap-2">
          <button onClick={prev} className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors">
            <ChevronLeftIcon size={16} />
          </button>
          <h2 className="font-bold text-slate-800 text-lg min-w-[180px] text-center">
            {heading}
          </h2>
          <button onClick={next} className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors">
            <ChevronRightIcon size={16} />
          </button>
        </div>

        <div className="flex bg-white border border-slate-200 rounded-xl overflow-hidden shadow-sm">
          {(['month', 'week', 'day'] as View[]).map(v => (
            <button
              key={v}
              onClick={() => setView(v)}
              className={`px-4 py-2 text-sm font-medium transition-colors capitalize ${view === v ? 'bg-[#2563EB] text-white' : 'text-slate-600 hover:bg-slate-50'}`}
            >
              {v === 'month' ? 'Monat' : v === 'week' ? 'Woche' : 'Tag'}
            </button>
          ))}
        </div>

        {permissions.canAdd && (
          <button
            onClick={() => openEditor()}
            disabled={status !== 'ready'}
            className="ml-auto flex items-center gap-2 bg-[#2563EB] text-white px-4 py-2 rounded-xl text-sm font-semibold hover:bg-[#1D4ED8] transition-colors shadow-sm disabled:opacity-50"
          >
            <PlusIcon size={16} />
            <span className="hidden sm:inline">{permissions.onlyProposals ? 'Termin vorschlagen' : 'Termin hinzufügen'}</span>
          </button>
        )}
      </div>

      {status === 'loading' && (
        <div className="mb-5 bg-white border border-slate-100 rounded-xl p-3 text-sm text-slate-500">Termine werden geladen…</div>
      )}
      {status === 'error' && (
        <div className="mb-5 bg-[#FEF2F2] border border-[#FECACA] rounded-xl p-3 flex items-center gap-3">
          <AlertTriangleIcon size={18} className="text-[#EF4444] flex-shrink-0" />
          <span className="text-sm text-[#DC2626] flex-1">{error}</span>
          <button onClick={reload} className="text-sm font-semibold text-[#DC2626] hover:underline">Erneut versuchen</button>
        </div>
      )}

      {/* Freigabe-Workflow: Administratoren entscheiden, Jugendliche sehen ihre offenen Vorschläge */}
      {proposals.length > 0 && (
        <section aria-label="Offene Vorschläge" className="mb-5 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl p-3">
          <h3 className="text-sm font-semibold text-[#92400E] mb-2">
            ⏳ {permissions.mayDecide ? 'Offene Vorschläge' : 'Deine Vorschläge – warten auf Freigabe'} ({proposals.length})
          </h3>
          {decisionError && <p role="alert" className="text-sm text-[#DC2626] mb-2">{decisionError}</p>}
          <ul className="space-y-2">
            {proposals.map(p => {
              const proposer = p.createdBy ? memberById(p.createdBy)?.name : undefined;
              return (
                <li key={p.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 bg-white rounded-lg px-3 py-2 border border-[#FDE68A]">
                  <button onClick={() => openEditor(p)} className="text-sm font-semibold text-slate-800 hover:underline text-left">
                    {p.title}
                  </button>
                  <span className="text-xs text-slate-500">
                    {p.date.split('-').reverse().join('.')} {p.time}–{p.endTime} · für {memberById(p.memberId)?.name}
                    {proposer && p.createdBy !== permissions.me.id ? ` · vorgeschlagen von ${proposer}` : ''}
                  </span>
                  {permissions.mayDecide && (
                    <span className="ml-auto flex gap-2">
                      <button onClick={() => decide(rejectEvent, p.id)}
                        className="text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-200 text-slate-600 hover:bg-slate-50">
                        Ablehnen
                      </button>
                      <button onClick={() => decide(approveEvent, p.id)}
                        className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-[#22C55E] text-white hover:bg-[#16A34A]">
                        Freigeben
                      </button>
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        </section>
      )}

      {/* Family member filters */}
      <div className="flex flex-wrap items-center gap-2 mb-5">
        <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Filter:</span>
        <button
          onClick={() => setSelectedMember(null)}
          className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${selectedMember === null ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
        >
          Alle
        </button>
        {members.map(m => (
          <button
            key={m.id}
            onClick={() => setSelectedMember(selectedMember === m.id ? null : m.id)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-medium transition-all ${selectedMember === m.id ? 'text-white shadow-sm' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'}`}
            style={selectedMember === m.id ? { backgroundColor: m.color } : {}}
          >
            <div className="w-3 h-3 rounded-full" style={{ backgroundColor: m.color }} />
            {m.name}
          </button>
        ))}
      </div>

      {/* Conflict + travel warning */}
      {conflicts.length > 0 && (
        <div className="mb-5 bg-[#FEF2F2] border border-[#FECACA] rounded-xl p-3 flex items-start gap-3">
          <AlertTriangleIcon size={18} className="text-[#EF4444] flex-shrink-0 mt-0.5" />
          <div className="space-y-1">
            {conflicts.slice(0, 2).map(ev => (
              <div key={ev.id}>
                <span className="text-sm font-semibold text-[#DC2626]">
                  {ev.travelConflict ? '🚗 Fahrzeit-Konflikt' : '⚠ Terminkonflikt'}:
                </span>
                <span className="text-sm text-[#DC2626]/80 ml-2">
                  {ev.title}
                  {ev.travelTime && ` — Abfahrt ${minsToTime(departureMins(ev)!)} überschneidet nächsten Termin`}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Garbage reminder strip */}
      {upcomingGarbage.length > 0 && (
        <div className="mb-5 flex flex-wrap gap-2">
          {upcomingGarbage.map(g => {
            const ws  = WASTE_STYLES[g.type];
            const days  = Math.round((fromDateKey(g.date).getTime() - today.getTime()) / 86_400_000);
            return (
              <div
                key={g.id}
                className="flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-semibold border"
                style={{ backgroundColor: ws.bg, color: ws.dot, borderColor: ws.dot + '40' }}
              >
                <div className="w-2 h-2 rounded-full" style={{ backgroundColor: ws.dot }} />
                {g.type}
                <span className="font-bold opacity-70">
                  {days === 0 ? '· Heute' : days === 1 ? '· Morgen' : `· in ${days} T.`}
                </span>
              </div>
            );
          })}
        </div>
      )}

      {/* Transport legend (week/day only) */}
      {view !== 'month' && (
        <div className="mb-4 flex flex-wrap gap-2 items-center">
          <span className="text-[11px] text-slate-400 font-semibold uppercase tracking-wide">Verkehrsmittel:</span>
          {Object.entries(TRANSPORT_ICONS).map(([mode, icon]) => (
            <span key={mode} className="flex items-center gap-1 text-xs text-slate-500 bg-white border border-slate-100 px-2.5 py-1 rounded-full shadow-sm">
              {icon} {mode === 'car' ? 'Auto' : mode === 'transit' ? 'ÖPNV' : mode === 'bike' ? 'Fahrrad' : 'Zu Fuß'}
            </span>
          ))}
          <span className="flex items-center gap-1 text-xs text-[#EF4444] bg-[#FEF2F2] border border-[#FECACA] px-2.5 py-1 rounded-full ml-auto">
            <AlertTriangleIcon size={11} /> Konflikt
          </span>
        </div>
      )}

      {/* Views */}
      {view === 'month' && <MonthView year={year} month={month} events={visibleEvents} onSelect={openEditor} />}
      {view === 'week'  && <WeekView weekOffset={weekOffset} events={visibleEvents} onSelect={openEditor} />}
      {view === 'day'   && <DayView day={shownDay} events={visibleEvents} onSelect={openEditor} />}

      {/* Upcoming events (day view only) */}
      {view === 'day' && (
        <div className="mt-5">
          <h3 className="font-bold text-slate-800 mb-3">Diese Woche</h3>
          <div className="space-y-2">
            {visibleEvents
              .filter(e => e.date > toDateKey(shownDay) && e.date <= endOfShownWeek)
              .sort((a, b) => a.date.localeCompare(b.date) || a.time.localeCompare(b.time))
              .slice(0, 5)
              .map(ev => <EventPill key={ev.id} event={ev} onSelect={openEditor} />)
            }
          </div>
        </div>
      )}

      {editor && (
        <EventFormModal event={editor.event} defaultDate={view === 'day' ? toDateKey(shownDay) : todayKey}
          onClose={() => setEditor(null)} />
      )}
    </div>
  );
}
