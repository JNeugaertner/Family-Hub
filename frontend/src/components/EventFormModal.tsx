import { useEffect, useState, useMemo, type FormEvent, type ReactNode } from 'react';
import type { CalendarEvent, EventCategory, TransportMode } from './data';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { useCalendarPermissions } from '../calendar/permissions';
import { ApiError } from '../api/client';
import { CATEGORY_OPTIONS } from '../calendar/categories';
import GoogleBadge from '../google/GoogleBadge';
import { eventColor, memberColors } from '../calendar/eventStyle';
import {findPlaces, OrsLocation, getTravelTime, getSettings} from './OrsApi';
import { readable } from '../family/colors';
import OrsAttribution from './OrsAttribution';
import {
  FREQUENCY_OPTIONS, WEEKDAYS, describeRecurrence, sameRecurrence, weekdayOf,
  type Frequency, type Recurrence, type SeriesScope, type Weekday,
} from '../calendar/recurrence';

// Das Backend meldet "Ende nach Beginn" unter endAfterStart; im Formular gehört es zum Feld "Ende".
// Fehler der Wiederholung landen beim Abstand, beim Ende der Wiederholung oder bei der Auswahl selbst.
const FIELD_ALIASES: Record<string, string> = {
  endAfterStart: 'end',
  'recurrence.interval': 'interval',
  'recurrence.until': 'recurrenceEnd',
  'recurrence.count': 'recurrenceEnd',
  'recurrence.endUnambiguous': 'recurrenceEnd',
  recurrenceEndAfterStart: 'recurrenceEnd',
  'recurrence.frequency': 'recurrence',
  'recurrence.weekdaysOnlyWeekly': 'recurrence',
};

type EndMode = 'never' | 'until' | 'count';

const capitalize = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);
const formatDate = (dateKey: string) => dateKey.split('-').reverse().join('.');

const INPUT = 'w-full border rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-600';
const inputClass = (hasError: boolean) =>
  `${INPUT} ${hasError ? 'border-[#EF4444] focus:border-[#EF4444] focus:ring-[#EF4444]/20' : 'border-slate-200 focus:border-[#2563EB] focus:ring-[#2563EB]/20'}`;

// Label per htmlFor verknüpft und Fehlertext außerhalb, damit der Feldname für Screenreader nur das Label ist.
function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="text-xs font-semibold text-slate-600 mb-1.5 block">{label}</label>
      {children}
      {error && <p id={`${id}-error`} className="text-xs text-[#DC2626] mt-1">{error}</p>}
    </div>
  );
}

interface Props {
  event?: CalendarEvent;
  defaultDate: string;
  // Vorschlag für den Titel eines neuen Termins (z. B. aus einer Nachricht)
  defaultTitle?: string;
  onClose: () => void;
}

export default function EventFormModal({ event, defaultDate, defaultTitle, onClose }: Props) {
  const { members, events, memberById, saveEvent, removeEvent, approveEvent, rejectEvent } = useCalendarData();
  const permissions = useCalendarPermissions();
  const { me } = permissions;

  const isEdit = !!event;
  const readOnly = isEdit && !permissions.canEdit(event);
  const isProposal = event?.status === 'proposed';
  // Bestehende, freigegebene Termine darf nur umhängen, wer Familientermine bearbeiten darf.
  const canReassign = !isEdit || isProposal || permissions.mayEditFamily;

  // Wen ich eintragen darf; Gäste nie. Bereits Beteiligte bleiben wählbar, damit man sie austragen kann.
  const assignable = members.filter(m => event?.memberIds.includes(m.id) || (m.effectiveRole !== 'gast'
    && (canReassign ? permissions.canAssignTo(m.id) : m.id === me.id)));
  const initialMembers = event?.memberIds
    ?? (permissions.canAssignTo(me.id) ? [me.id] : assignable[0] ? [assignable[0].id] : []);

  const [title, setTitle] = useState(event?.title ?? defaultTitle ?? '');
  const [departure, setDeparture] = useState(event?.departure ?? null);
  const [start, setStart] = useState(event?.start.slice(0, 16) ?? `${defaultDate}T09:00`);
  const [end, setEnd] = useState(event?.end.slice(0, 16) ?? `${defaultDate}T10:00`);
  const [memberIds, setMemberIds] = useState<string[]>(initialMembers);
  const [category, setCategory] = useState<EventCategory>(event?.category ?? 'family');
  const [location, setLocation] = useState(event?.location ?? '');
  const [lat, setLat] = useState<number | null>(event?.lat ?? null);
  const [lon, setLon] = useState<number | null>(event?.lon ?? null);
  const [transportMode, setTransportMode] = useState<TransportMode>(event?.transportMode ?? 'driving-car');
  const [travelTime, setTravelTime] = useState<number | null>(event?.travelTime ?? null);
  const [description, setDescription] = useState(event?.description ?? '');
  const [privateEvent, setPrivateEvent] = useState(event?.private ?? false);

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OrsLocation[] | null>(null);  
  const [individuallocation, setIndividuallocation] = useState<string | null>(null);

  // Wiederholung; bei Serienterminen vorbelegt mit der Regel der Serie
  const original = event?.recurrence ?? null;
  const [frequency, setFrequency] = useState<Frequency | 'none'>(original?.frequency ?? 'none');
  const [repeatEvery, setRepeatEvery] = useState(String(original?.interval ?? 1));
  const [weekdays, setWeekdays] = useState<Weekday[]>(original?.weekdays ?? []);
  const [endMode, setEndMode] = useState<EndMode>(original?.until ? 'until' : original?.count ? 'count' : 'never');
  const [until, setUntil] = useState(original?.until ?? '');
  const [count, setCount] = useState(String(original?.count ?? 10));

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  // Serientermin: Bevor gespeichert oder gelöscht wird, fragt das Formular nach dem Umfang
  const [askScope, setAskScope] = useState<'save' | 'delete' | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !busy) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  // Weiße Schrift im Kopf: abgedunkelt, damit sie auch am blasseren Ende des Verlaufs lesbar bleibt
  const color = readable(memberIds.length ? eventColor(memberColors(memberIds, memberById)) : '#2563EB', 6);
  const headerBackground = `linear-gradient(135deg, ${color}, ${color}DD)`;
  const willBeProposal = !readOnly && (isProposal || (!isEdit && permissions.becomesProposal(memberIds)));

  // "Ganze Familie" = alle, die ich eintragen darf, außer Gästen
  const familyIds = assignable.filter(m => m.effectiveRole !== 'gast').map(m => m.id);
  const wholeFamily = familyIds.length > 1 && familyIds.every(id => memberIds.includes(id));
  const toggle = (id: string) => {
    const next = memberIds.includes(id) ? memberIds.filter(x => x !== id) : [...memberIds, id];
    setMemberIds(members.map(m => m.id).filter(x => next.includes(x)));
  };
  const proposer = event?.createdBy ? memberById(event.createdBy)?.name : undefined;

  const fieldProps = (name: string) => ({
    id: `event-${name}`,
    className: inputClass(!!fieldErrors[name]),
    disabled: readOnly || busy,
    'aria-invalid': fieldErrors[name] ? true : undefined,
    'aria-describedby': fieldErrors[name] ? `event-${name}-error` : undefined,
  });

  const showError = (err: unknown) => {
    if (err instanceof ApiError && err.problem.errors) {
      const mapped: Record<string, string> = {};
      Object.entries(err.problem.errors).forEach(([field, msg]) => { mapped[FIELD_ALIASES[field] ?? field] = msg; });
      setFieldErrors(mapped);
      setFormError(null);
    } else {
      setFieldErrors({});
      setFormError(err instanceof Error ? err.message : String(err));
    }
  };

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    try {
      await action();
      onClose();
    } catch (err) {
      showError(err);
      setBusy(false);
    }
  };

  const startDay = start.slice(0, 10);
  const isSeries = !!event?.series;
  // Vorschläge betreffen immer die ganze Serie, dort fragt das Formular nicht nach
  const asksScope = isSeries && !isProposal;
  // Erster Termin der Serie (soweit sichtbar): "diesen und alle folgenden" ist dann die ganze Serie
  const firstOfSeries = isSeries && !events.some(e => e.series?.seriesId === event.series!.seriesId
    && e.series.date < event.series!.date);
  const effectiveWeekdays = weekdays.length || !startDay ? weekdays : [weekdayOf(startDay)];

  const recurrence: Recurrence | null = frequency === 'none' ? null : {
    frequency,
    interval: Number(repeatEvery),
    weekdays: frequency === 'weekly' ? weekdays : [],
    until: endMode === 'until' ? until || null : null,
    count: endMode === 'count' ? Number(count) : null,
  };
  const ruleChanged = isSeries && !sameRecurrence(original, recurrence);
  const unit = FREQUENCY_OPTIONS.find(f => f.value === frequency)?.unit;

  const toggleWeekday = (day: Weekday) => {
    const next = effectiveWeekdays.includes(day) ? effectiveWeekdays.filter(d => d !== day) : [...effectiveWeekdays, day];
    setWeekdays(WEEKDAYS.map(d => d.value).filter(d => next.includes(d)));
  };

  const save = (scope?: SeriesScope) => run(() => saveEvent({
    title,
    departure: departure || null,
    start: start || null,
    end: end || null,
    memberIds,
    category,
    location: location.trim() || null,
    lat,
    lon,
    transportMode,
    travelTime,
    description: description.trim() || null,
    private: privateEvent,
    recurrence,
  }, event?.id, scope));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (readOnly) return;
    if (frequency !== 'none' && endMode === 'until' && !until) {
      setFieldErrors({ recurrenceEnd: 'Bitte den letzten Tag der Wiederholung wählen' });
      return;
    }
    if (asksScope) { setAskScope('save'); return; }
    save();
  };

  const remove = () => {
    if (!event) return;
    if (asksScope) { setAskScope('delete'); return; }
    if (!confirmDelete) { setConfirmDelete(true); return; }
    run(() => removeEvent(event.id));
  };

  const setLocationAsIndividual = () => {
    setLocation(individuallocation ?? '');
    setLat(null);
    setLon(null);
    setTravelTime(null);
  }

  const search = async () => {
    if (!query.trim()) return;
    try{
    const places = await findPlaces(query.trim());
    setResults(places);}
    catch(err){showError(err)}
  };

  const gettravelTime =async (mode: 'driving-car' | 'cycling-regular' | 'foot-walking', place: OrsLocation) => {
    try{
    setResults(null);
    setQuery('');
    const settings = await getSettings();
    if (!settings.homeLocation) {setFieldErrors({travelTime: 'Bitte zuerst den Wohnort in den Einstellungen eingeben.'}); return;}
    if(!place.lat || !place.lon){return}
    const time = await getTravelTime(mode, settings.homeLocation.lat, settings.homeLocation.lon, place.lat, place.lon);
    setTravelTime(time);
    setFieldErrors({});
  }catch(err){showError(err), setTravelTime(null)}
  };

  const choose = async (place: OrsLocation) => {
    setLocation(place.label);
    setLat(place.lat);
    setLon(place.lon);
    await gettravelTime(transportMode, place);
  }; 

  const removeLocation = () => {
    setLocation('');
    setLat(null);
    setLon(null);
    setTravelTime(null);
  }
  
  const getTimeLayout = (time: number) => {
    const totalMinutes = Math.round(time / 60);
    const days = Math.floor(totalMinutes / 1440);
    const hours = Math.floor((totalMinutes % 1440) / 60);
    const minutes = (totalMinutes % 60);
    if (days == 0 && hours == 0 && minutes == 0){
      return '';}
    if (days >=1){
      return `${days}d ${hours}h ${minutes}m`;}
    if (hours >= 1){
      return `${hours}h ${minutes}m`;}
    return `${minutes}m`;}

  const getDeparture = useMemo(() => {
    if (!start || !travelTime) return null;
    const date = new Date (start);
    date.setMinutes(date.getMinutes()-travelTime/60-15);
    const startDay = new Date(start).toLocaleDateString("de-DE",{day: "2-digit", month: "2-digit", year: "numeric"});
    const departureDay = date.toLocaleDateString("de-DE",{day: "2-digit", month: "2-digit", year: "numeric"});
    if (startDay !== departureDay){
      const departureString = date.toLocaleString("de-DE",{day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"});
      setDeparture(departureString);
      return departureString;
    }
    const departureString = date.toLocaleTimeString("de-DE",{hour: "2-digit", minute: "2-digit"});
    setDeparture(departureString);
    return departureString;},[start, travelTime]);

  const chooseScope = (scope: SeriesScope) => {
    if (!event) return;
    const action = askScope;
    // Bei einem Fehler soll das Formular mit den Fehlern wieder sichtbar sein
    setAskScope(null);
    if (action === 'delete') run(() => removeEvent(event.id, scope));
    else save(scope);
  };

  const heading = readOnly ? 'Termin' : isProposal ? 'Vorschlag bearbeiten' : isEdit ? 'Termin bearbeiten'
    : willBeProposal ? 'Neuer Vorschlag' : 'Neuer Termin';

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <form
        onSubmit={submit}
        noValidate
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-form-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto"
      >
        <div
          className="h-20 rounded-t-2xl relative flex items-end p-5"
          style={{ background: headerBackground }}
        >
          <h2 id="event-form-title" className="text-white font-bold text-lg">{heading}</h2>
          <button type="button" onClick={onClose} className="absolute top-3 right-3 text-white/80 hover:text-white text-xl" aria-label="Schließen">
            ✕
          </button>
        </div>

        <div className="p-6 space-y-4">
          {isProposal && (
            <div className="bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-sm rounded-xl p-3">
              ⏳ Vorschlag{proposer ? ` von ${proposer}` : ''} – wartet auf Freigabe durch einen Administrator.
            </div>
          )}
          {!isEdit && willBeProposal && (
            <div className="bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-sm rounded-xl p-3">
              Mit anderen Familienmitgliedern wird der Termin als <strong>Vorschlag</strong> gespeichert. Er gilt
              erst, wenn ein Administrator ihn freigibt.
            </div>
          )}
          {event?.source === 'google' ? (
            <div className="bg-slate-50 border border-slate-200 text-slate-600 text-sm rounded-xl p-3">
              <GoogleBadge /> Aus Google Kalender übernommen. Ändern oder löschen kannst du ihn nur in Google;
              FamilyHub übernimmt Änderungen beim nächsten Abgleich.
            </div>
          ) : readOnly && (
            <p className="text-xs text-slate-500">Du kannst diesen Termin ansehen, aber nicht ändern.</p>
          )}
          {isSeries && original && (
            <div className="bg-slate-50 border border-slate-200 text-slate-700 text-sm rounded-xl p-3">
              ↻ Serientermin: {describeRecurrence(original, event!.series!.date)}.
              {event!.series!.changed && ' Dieser Termin wurde einzeln geändert.'}
              {asksScope && !readOnly && (
                <span className="block text-xs text-slate-500 mt-1">
                  Beim Speichern oder Löschen wählst du, ob es nur diesen oder auch alle folgenden Termine betrifft.
                </span>
              )}
            </div>
          )}
          {formError && (
            <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#B91C1C] text-sm rounded-xl p-3">{formError}</div>
          )}

          <Field id="event-title" label="Titel" error={fieldErrors.title}>
            <input {...fieldProps('title')} value={title} onChange={e => setTitle(e.target.value)} autoFocus={!readOnly} />
          </Field>

        <Field id="event-departure" label="Abfahrt" error={fieldErrors.departure}>
          {getDeparture}
        </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field id="event-start" label="Beginn" error={fieldErrors.start}>
              <input type="datetime-local" {...fieldProps('start')} value={start} onChange={e => setStart(e.target.value)} />
            </Field>
            <Field id="event-end" label="Ende" error={fieldErrors.end}>
              <input type="datetime-local" {...fieldProps('end')} value={end} onChange={e => setEnd(e.target.value)} />
            </Field>
          </div>

          {!readOnly && (
            <Field id="event-repeat" label="Wiederholen" error={fieldErrors.recurrence}>
              <select {...fieldProps('repeat')} className={inputClass(!!fieldErrors.recurrence)}
                aria-invalid={fieldErrors.recurrence ? true : undefined}
                aria-describedby={fieldErrors.recurrence ? 'event-repeat-error' : undefined}
                value={frequency} onChange={e => setFrequency(e.target.value as Frequency | 'none')}>
                <option value="none">Nie</option>
                {FREQUENCY_OPTIONS.map(f => <option key={f.value} value={f.value}>{f.label}</option>)}
              </select>
            </Field>
          )}

          {!readOnly && recurrence && unit && (
            <div className="rounded-xl border border-slate-200 p-3 space-y-3">
              <div>
                <div className="flex items-center gap-2 text-sm text-slate-700">
                  <label htmlFor="event-interval">Alle</label>
                  <input id="event-interval" type="number" min={1} max={99} inputMode="numeric"
                    className={`${inputClass(!!fieldErrors.interval)} w-20!`} disabled={busy}
                    aria-invalid={fieldErrors.interval ? true : undefined}
                    aria-describedby={fieldErrors.interval ? 'event-interval-error' : undefined}
                    value={repeatEvery} onChange={e => setRepeatEvery(e.target.value)} />
                  <span>{recurrence.interval === 1 ? unit[0] : unit[1]}</span>
                </div>
                {fieldErrors.interval && <p id="event-interval-error" className="text-xs text-[#DC2626] mt-1">{fieldErrors.interval}</p>}
              </div>

              {frequency === 'weekly' && (
                <fieldset>
                  <legend className="text-xs font-semibold text-slate-600 mb-1.5">An diesen Tagen</legend>
                  <div className="flex flex-wrap gap-1.5">
                    {WEEKDAYS.map(d => {
                      const on = effectiveWeekdays.includes(d.value);
                      return (
                        <button key={d.value} type="button" aria-pressed={on} title={d.long}
                          disabled={busy} onClick={() => toggleWeekday(d.value)}
                          className={`w-9 h-9 rounded-lg text-xs font-semibold border transition-colors ${on ? 'bg-[#2563EB] text-white border-transparent' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}>
                          {/* Sichtbar die Abkürzung, vorgelesen der ganze Name (ohne aria-label, sonst passt der Name nicht zum Text) */}
                          <span aria-hidden="true">{d.short}</span><span className="sr-only">{d.long}</span>
                        </button>
                      );
                    })}
                  </div>
                </fieldset>
              )}

              <fieldset aria-describedby={fieldErrors.recurrenceEnd ? 'event-recurrence-end-error' : undefined}>
                <legend className="text-xs font-semibold text-slate-600 mb-1.5">Ende der Wiederholung</legend>
                <div className="space-y-2 text-sm text-slate-700">
                  <label className="flex items-center gap-2">
                    <input type="radio" name="event-repeat-end" className="accent-[#2563EB]" disabled={busy}
                      checked={endMode === 'never'} onChange={() => setEndMode('never')} />
                    Nie
                  </label>
                  <div className="flex items-center gap-2">
                    <label className="flex items-center gap-2">
                      <input type="radio" name="event-repeat-end" className="accent-[#2563EB]" disabled={busy}
                        checked={endMode === 'until'} onChange={() => setEndMode('until')} />
                      Am
                    </label>
                    <input type="date" aria-label="Letzter Tag der Wiederholung" min={startDay} disabled={busy}
                      className={`${inputClass(endMode === 'until' && !!fieldErrors.recurrenceEnd)} w-auto!`}
                      value={until} onFocus={() => setEndMode('until')} onChange={e => { setEndMode('until'); setUntil(e.target.value); }} />
                  </div>
                  <div className="flex items-center gap-2">
                    <label className="flex items-center gap-2">
                      <input type="radio" name="event-repeat-end" className="accent-[#2563EB]" disabled={busy}
                        checked={endMode === 'count'} onChange={() => setEndMode('count')} />
                      Nach
                    </label>
                    <input type="number" aria-label="Anzahl der Termine" min={1} max={999} inputMode="numeric" disabled={busy}
                      className={`${inputClass(endMode === 'count' && !!fieldErrors.recurrenceEnd)} w-20!`}
                      value={count} onFocus={() => setEndMode('count')} onChange={e => { setEndMode('count'); setCount(e.target.value); }} />
                    <span>Terminen</span>
                  </div>
                </div>
                {fieldErrors.recurrenceEnd && (
                  <p id="event-recurrence-end-error" className="text-xs text-[#DC2626] mt-1">{fieldErrors.recurrenceEnd}</p>
                )}
              </fieldset>

              {startDay && recurrence.interval >= 1 && (
                <p className="text-xs text-slate-600" aria-live="polite">
                  ↻ {capitalize(describeRecurrence(recurrence, startDay))}, ab {formatDate(startDay)}
                </p>
              )}
            </div>
          )}

          <fieldset aria-describedby={fieldErrors.memberIds ? 'event-memberIds-error' : undefined}>
            <legend className="text-xs font-semibold text-slate-600 mb-1.5">Wer ist dabei?</legend>
            <div className="flex flex-wrap gap-2">
              {(readOnly ? members.filter(m => memberIds.includes(m.id)) : assignable).map(m => {
                const on = memberIds.includes(m.id);
                return (
                  <button key={m.id} type="button" aria-pressed={on} disabled={readOnly || busy} onClick={() => toggle(m.id)}
                    className={`flex items-center gap-1.5 pl-1 pr-2.5 py-1 rounded-full text-xs font-semibold border transition-colors disabled:cursor-default ${on ? 'text-white border-transparent' : 'bg-white text-slate-600 border-slate-200 hover:bg-slate-50'}`}
                    style={on ? { backgroundColor: readable(m.color) } : undefined}>
                    <span className="w-5 h-5 rounded-full flex items-center justify-center text-[9px] text-white"
                      style={{ backgroundColor: on ? 'rgba(255,255,255,0.3)' : readable(m.color) }}>
                      {on ? '✓' : m.initials[0]}
                    </span>
                    {m.name}{m.id === me.id ? ' (ich)' : ''}
                  </button>
                );
              })}
              {!readOnly && familyIds.length > 1 && (
                <button type="button" disabled={busy} onClick={() => setMemberIds(wholeFamily ? [] : familyIds)}
                  className="px-2.5 py-1 rounded-full text-xs font-semibold border border-dashed border-slate-300 text-slate-600 hover:bg-slate-50">
                  {wholeFamily ? 'Auswahl aufheben' : '👨‍👩‍👧‍👦 Ganze Familie'}
                </button>
              )}
            </div>
            {fieldErrors.memberIds && <p id="event-memberIds-error" className="text-xs text-[#DC2626] mt-1">{fieldErrors.memberIds}</p>}
          </fieldset>

          <Field id="event-category" label="Kategorie" error={fieldErrors.category}>
            <select {...fieldProps('category')} value={category} onChange={e => setCategory(e.target.value as EventCategory)}>
              {CATEGORY_OPTIONS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
            </select>
          </Field>

          <Field id="event-location" label="Ort (optional)" error={fieldErrors.location}>
            {location?.trim() && (<div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-2 py-2">
            <span className="text-sm text-slate-800 flex-1">📍 {location}</span>
            <button type="button" disabled={busy} onClick={removeLocation} className="text-xs text-slate-500 hover:text-[#EF4444] disabled:opacity-50">
               Entfernen
            </button>
            </div>)}

            <div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-2 py-2">
              <span className="text-xs text-slate-500">Adresse suchen</span>
            <input {...fieldProps('location')} value={query} onChange={e => {setQuery(e.target.value)}} onKeyDown={e => {if(e.key === 'Enter'){e.preventDefault(); search();}}}
               placeholder="Ort suchen ..."/>
            <button onClick={search} type="button" disabled={!query.trim()}
               className="px-4 py-2 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
               Suchen
            </button>
            </div>
            {results && (
            <div className="mt-2 space-y-1" aria-label="Suchergebnisse">
              {results.length === 0 && <p className="text-xs text-slate-500 px-1">Kein Ort gefunden.</p>}
              {results.map(p => (
              <button key={`${p.lat},${p.lon}`} type="button" disabled={busy} onClick={() => choose(p)}
                className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 text-sm text-slate-700 disabled:opacity-50">
                {p.label}
              </button>
                ))}
            </div>
            )}
            <div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-2 py-2">
              <span className="text-xs text-slate-500">individuellen Ort eingeben</span>
              <input {...fieldProps('individuallocation')} value={individuallocation ?? ''} onChange={e => {setIndividuallocation(e.target.value)}} 
                onKeyDown={e => {if(e.key === 'Enter'){e.preventDefault(); setLocationAsIndividual();}}} placeholder="Ort eingeben ..."/>
              <button onClick={setLocationAsIndividual} type="button" disabled={!individuallocation?.trim()}
               className="px-4 py-2 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
               Übernehmen
              </button>
            </div>
          </Field>
                    
          <Field id="event-transportMode" label="Verkehrsmittel" error={fieldErrors.transportMode}>
            <select {...fieldProps('transportMode')} value={transportMode} onChange={e => {setTransportMode(e.target.value as TransportMode);
                gettravelTime(e.target.value as TransportMode, { label: location, lat: lat ?? 0, lon: lon ?? 0 })}}>
              <option value="driving-car">Auto</option>
              <option value="cycling-regular">Fahrrad</option>
              <option value="foot-walking">Zu Fuß</option>
            </select>
          </Field>

        <Field id="event-travel-time" label="Fahrzeit" error={fieldErrors.travelTime}>
          {getTimeLayout( travelTime ?? 0)}
        </Field>
        <OrsAttribution className="-mt-2" />

          <Field id="event-description" label="Beschreibung (optional)" error={fieldErrors.description}>
            <textarea rows={3} {...fieldProps('description')} value={description} onChange={e => setDescription(e.target.value)} />
          </Field>

          <label className="flex items-start gap-2.5 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-0.5 w-4 h-4 accent-[#2563EB]"
              checked={privateEvent}
              disabled={readOnly || busy}
              onChange={e => setPrivateEvent(e.target.checked)}
            />
            <span>
              🔒 Privat
              <span className="block text-xs text-slate-500">Nur für die Beteiligten, wer den Termin anlegt, und Administratoren sichtbar.</span>
            </span>
          </label>

          {askScope ? (
            <div role="group" aria-labelledby="event-scope-title" className="bg-slate-50 border border-slate-200 rounded-xl p-3 space-y-3">
              <p id="event-scope-title" className="text-sm font-semibold text-slate-800">
                {askScope === 'delete' ? 'Serientermin löschen' : 'Serientermin ändern'}
              </p>
              <p className="text-xs text-slate-600">
                {askScope === 'save' && ruleChanged
                  ? recurrence
                    ? 'Die geänderte Wiederholung gilt ab diesem Termin für alle folgenden.'
                    : 'Ohne Wiederholung endet die Serie vor diesem Termin; er bleibt als einzelner Termin.'
                  : askScope === 'delete'
                    ? 'Nur diesen Termin löschen oder auch alle folgenden?'
                    : 'Nur diesen Termin ändern oder auch alle folgenden? Einzeln geänderte Termine bleiben, wie sie sind.'}
              </p>
              <div className="flex flex-wrap gap-2">
                {!(askScope === 'save' && ruleChanged) && (
                  <button type="button" disabled={busy} onClick={() => chooseScope('single')} autoFocus
                    className="py-2 px-3 rounded-xl text-sm font-semibold border border-slate-200 bg-white text-slate-700 hover:bg-slate-100 disabled:opacity-50">
                    Nur diesen Termin
                  </button>
                )}
                <button type="button" disabled={busy} onClick={() => chooseScope('following')}
                  autoFocus={askScope === 'save' && ruleChanged}
                  className={`py-2 px-3 rounded-xl text-sm font-semibold text-white disabled:opacity-50 ${askScope === 'delete' ? 'bg-[#DC2626] hover:bg-[#B91C1C]' : 'bg-[#2563EB] hover:bg-[#1D4ED8]'}`}>
                  {firstOfSeries ? 'Ganze Serie' : 'Diesen und alle folgenden'}
                </button>
                <button type="button" disabled={busy} onClick={() => setAskScope(null)}
                  className="py-2 px-3 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100 disabled:opacity-50">
                  Zurück
                </button>
              </div>
            </div>
          ) : (
          <div className="flex flex-wrap gap-3 pt-2">
            {isEdit && permissions.canDelete(event) && (
              <button
                type="button"
                onClick={remove}
                disabled={busy}
                className={`py-2.5 px-4 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 ${confirmDelete ? 'bg-[#DC2626] text-white hover:bg-[#B91C1C]' : 'border border-[#FECACA] text-[#B91C1C] hover:bg-[#FEF2F2]'}`}
              >
                {confirmDelete ? 'Wirklich löschen?' : isProposal && event.createdBy === me.id ? 'Zurückziehen' : 'Löschen'}
              </button>
            )}
            {isProposal && permissions.mayDecide && (
              <>
                <button type="button" disabled={busy} onClick={() => run(() => rejectEvent(event.id))}
                  className="py-2.5 px-4 rounded-xl text-sm font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                  Ablehnen
                </button>
                <button type="button" disabled={busy} onClick={() => run(() => approveEvent(event.id))}
                  className="py-2.5 px-4 rounded-xl text-sm font-semibold bg-[#15803D] text-white hover:bg-[#166534] disabled:opacity-50">
                  Freigeben
                </button>
              </>
            )}
            <button type="button" onClick={onClose} disabled={busy} className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
              {readOnly ? 'Schließen' : 'Abbrechen'}
            </button>
            {!readOnly && (
              <button type="submit" disabled={busy} className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
                {busy ? 'Speichern…' : willBeProposal && !isEdit ? 'Vorschlagen' : 'Speichern'}
              </button>
            )}
          </div>
          )}
        </div>
      </form>
    </div>
  );
}
