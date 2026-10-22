import { useEffect, useState } from 'react';
import { CheckIcon } from '../components/Icons';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { CATEGORY_LABELS } from '../calendar/categories';
import { MEAL_STYLES, MEAL_TYPES } from '../meals/mealTypes';
import { SELECTABLE_SHOPPING_CATEGORIES, SHOPPING_CATEGORIES } from '../shopping/categories';
import { acceptDraft, discardDraft, type Draft, type DraftItem, type DraftKind } from './api';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

const KINDS: Record<DraftKind, { label: string; icon: string }> = {
  event: { label: 'Kalender', icon: '📅' },
  task: { label: 'Aufgaben', icon: '✅' },
  shopping: { label: 'Einkaufsliste', icon: '🛒' },
  meal: { label: 'Essensplan', icon: '🍽️' },
};

const ACTIONS: Record<DraftItem['action'], { label: string; className: string }> = {
  create: { label: 'Neu', className: 'bg-[#F0FDF4] text-[#15803D] border-[#BBF7D0]' },
  update: { label: 'Ändern', className: 'bg-[#EFF6FF] text-[#1D4ED8] border-[#BFDBFE]' },
  delete: { label: 'Löschen', className: 'bg-[#FEF2F2] text-[#B91C1C] border-[#FECACA]' },
};

// Bezeichnungen wie auf den Seiten Aufgaben, Einkauf und Essensplan
const TASK_PRIORITIES: Record<string, string> = { low: 'Niedrig', medium: 'Mittel', high: 'Hoch' };
const TASK_CATEGORIES: Record<string, string> = {
  chores: 'Haushalt', school: 'Schule', health: 'Gesundheit', errands: 'Besorgungen', family: 'Familie', home: 'Haus & Garten',
};
const TASK_STATUS: Record<string, string> = { todo: 'Offen', inprogress: 'In Arbeit', done: 'Erledigt' };
const SHOPPING_LABELS: Record<string, string> = Object.fromEntries(
  SELECTABLE_SHOPPING_CATEGORIES.map(c => [c, `${SHOPPING_CATEGORIES[c].icon} ${SHOPPING_CATEGORIES[c].label}`]));
const MEAL_LABELS: Record<string, string> = Object.fromEntries(MEAL_TYPES.map(t => [t, MEAL_STYLES[t].label]));

type FieldType = 'text' | 'date' | 'datetime' | 'select' | 'member' | 'members' | 'flag';

interface Field {
  key: string;
  label: string;
  type: FieldType;
  options?: Record<string, string>;
}

// Die Felder je Bereich; shown: was eine Karte zum Anlegen immer zeigt (beim Ändern nur, was sich ändert)
const FIELDS: Record<DraftKind, { fields: Field[]; shown: string[] }> = {
  event: {
    fields: [
      { key: 'title', label: 'Titel', type: 'text' },
      { key: 'start', label: 'Beginn', type: 'datetime' },
      { key: 'end', label: 'Ende', type: 'datetime' },
      { key: 'memberIds', label: 'Personen', type: 'members' },
      { key: 'category', label: 'Kategorie', type: 'select', options: CATEGORY_LABELS },
      { key: 'location', label: 'Ort', type: 'text' },
      { key: 'description', label: 'Beschreibung', type: 'text' },
      { key: 'private', label: 'Privat', type: 'flag' },
    ],
    shown: ['title', 'start', 'end', 'memberIds', 'category', 'location'],
  },
  task: {
    fields: [
      { key: 'title', label: 'Titel', type: 'text' },
      { key: 'assigneeId', label: 'Für wen', type: 'member' },
      { key: 'dueDate', label: 'Fällig am', type: 'date' },
      { key: 'priority', label: 'Priorität', type: 'select', options: TASK_PRIORITIES },
      { key: 'category', label: 'Kategorie', type: 'select', options: TASK_CATEGORIES },
      { key: 'status', label: 'Status', type: 'select', options: TASK_STATUS },
      { key: 'description', label: 'Beschreibung', type: 'text' },
    ],
    shown: ['title', 'assigneeId', 'dueDate', 'priority', 'category'],
  },
  shopping: {
    fields: [
      { key: 'name', label: 'Artikel', type: 'text' },
      { key: 'quantity', label: 'Menge', type: 'text' },
      { key: 'category', label: 'Kategorie', type: 'select', options: SHOPPING_LABELS },
      { key: 'urgent', label: 'Dringend', type: 'flag' },
      { key: 'checked', label: 'Gekauft', type: 'flag' },
    ],
    shown: ['name', 'quantity', 'category', 'urgent'],
  },
  meal: {
    fields: [
      { key: 'date', label: 'Datum', type: 'date' },
      { key: 'type', label: 'Mahlzeit', type: 'select', options: MEAL_LABELS },
      { key: 'name', label: 'Gericht', type: 'text' },
    ],
    shown: ['date', 'type', 'name'],
  },
};

const formatDate = (value: string) =>
  new Date(`${value.slice(0, 10)}T00:00:00`).toLocaleDateString('de-DE', { weekday: 'short', day: '2-digit', month: '2-digit' });

const formatDateTime = (value: string) =>
  `${formatDate(value)}, ${value.slice(11, 16)}`;

// Felder, die auf der Karte die ganze Breite bekommen
const WIDE = new Set(['title', 'name', 'description', 'memberIds']);

const isEmpty = (value: unknown) => value === undefined || value === null || value === '';

// Ein Eintrag, wie er nach dem Übernehmen aussähe (beim Ändern: vorher + Änderungen)
const merged = (item: DraftItem, changes: Record<string, unknown>) => ({ ...item.before, ...item.data, ...changes });

function headline(kind: DraftKind, values: Record<string, unknown>): string {
  switch (kind) {
    case 'event':
      return `${values.title ?? 'Termin'}${typeof values.start === 'string' ? ` · ${formatDateTime(values.start)}` : ''}`;
    case 'task':
      return String(values.title ?? 'Aufgabe');
    case 'shopping':
      return `${values.name ?? 'Artikel'}${isEmpty(values.quantity) ? '' : ` · ${values.quantity}`}`;
    case 'meal':
      return `${MEAL_LABELS[String(values.type)] ?? 'Mahlzeit'}${typeof values.date === 'string' ? ` am ${formatDate(values.date)}` : ''}: ${values.name ?? ''}`;
  }
}

function resultText(kind: DraftKind, item: DraftItem): string {
  if (item.state === 'discarded') return 'Verworfen';
  if (item.outcome === 'proposed') {
    return kind === 'meal' ? 'Als Wunsch gesendet, deine Eltern entscheiden.' : 'Als Vorschlag übernommen, ein Elternteil gibt ihn frei.';
  }
  return item.action === 'create' ? 'Übernommen' : item.action === 'update' ? 'Geändert' : 'Gelöscht';
}

// Ein Entwurf des KI-Assistenten als Karte im Chat (Phase 4, Entscheidungen vom 22.10.2026): Die Person sieht, was
// angelegt, geändert oder gelöscht würde, kann die Felder anpassen und einzelne Einträge abwählen. Erst mit
// „Übernehmen“ ändert sich etwas, mit ihren Rechten (wer nur vorschlagen darf, macht einen Vorschlag).
export default function DraftCard({ draft, onChange }: { draft: Draft; onChange: (draft: Draft) => void }) {
  const { members, memberById } = useCalendarData();
  const openIndexes = draft.items.flatMap((item, i) => (item.state === 'open' ? [i] : []));
  const [selected, setSelected] = useState<Set<number>>(() => new Set(openIndexes));
  const [changes, setChanges] = useState<Record<number, Record<string, unknown>>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const openKey = openIndexes.join(',');

  // Erledigte Einträge (auch von einem anderen Gerät aus) sind nicht mehr wählbar
  useEffect(() => {
    const open = new Set(openKey === '' ? [] : openKey.split(',').map(Number));
    setSelected(s => new Set([...s].filter(i => open.has(i))));
    setChanges(c => Object.fromEntries(Object.entries(c).filter(([i]) => open.has(Number(i)))));
  }, [openKey]);

  const kind = KINDS[draft.kind];
  const spec = FIELDS[draft.kind];
  const memberOptions = Object.fromEntries(members.map(m => [m.id, m.name]));
  const nameOf = (id: unknown) => memberById(String(id))?.name ?? 'ehemaliges Mitglied';

  const display = (field: Field, value: unknown): string => {
    if (isEmpty(value) || (Array.isArray(value) && value.length === 0)) return '—';
    switch (field.type) {
      case 'date': return formatDate(String(value));
      case 'datetime': return formatDateTime(String(value));
      case 'member': return nameOf(value);
      case 'members': return (value as unknown[]).map(nameOf).join(', ');
      case 'flag': return value ? 'ja' : 'nein';
      case 'select': return field.options?.[String(value)] ?? String(value);
      default: return String(value);
    }
  };

  const setField = (index: number, key: string, value: unknown) =>
    setChanges(c => ({ ...c, [index]: { ...c[index], [key]: value } }));

  const toggle = (index: number) => setSelected(s => {
    const next = new Set(s);
    if (next.has(index)) next.delete(index); else next.add(index);
    return next;
  });

  const run = async (action: () => Promise<Draft>) => {
    setBusy(true);
    setError(null);
    try {
      onChange(await action());
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const accept = () => run(() => acceptDraft(draft.id, [...selected].sort((a, b) => a - b).map(index => {
    const edited = changes[index];
    return edited && Object.keys(edited).length > 0 ? { index, changes: edited } : { index };
  })));

  const input = (index: number, field: Field, value: unknown) => {
    const id = `draft-${draft.id}-${index}-${field.key}`;
    const base = 'w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-800 bg-white focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 disabled:opacity-60';
    const label = <label htmlFor={id} className="block text-[11px] font-medium text-slate-500 mb-0.5">{field.label}</label>;
    switch (field.type) {
      case 'members': {
        const ids = Array.isArray(value) ? value.map(String) : [];
        return (
          <fieldset>
            <legend className="block text-[11px] font-medium text-slate-500 mb-1">{field.label}</legend>
            <div className="flex flex-wrap gap-1.5">
              {members.map(m => {
                const on = ids.includes(m.id);
                return (
                  <button key={m.id} type="button" disabled={busy} aria-pressed={on}
                    onClick={() => setField(index, field.key, on ? ids.filter(x => x !== m.id) : [...ids, m.id])}
                    className={`text-xs px-2.5 py-1 rounded-full border font-medium transition-colors disabled:opacity-60 ${on ? 'text-white border-transparent' : 'bg-white text-slate-600 border-slate-200 hover:border-slate-300'}`}
                    style={on ? { backgroundColor: m.color } : undefined}>
                    {m.name}
                  </button>
                );
              })}
            </div>
          </fieldset>
        );
      }
      case 'flag':
        return (
          <label className="flex items-center gap-2 text-sm text-slate-700 self-end pb-1.5">
            <input type="checkbox" checked={Boolean(value)} disabled={busy}
              onChange={e => setField(index, field.key, e.target.checked)}
              className="w-4 h-4 rounded border-slate-300 text-[#2563EB] focus:ring-[#2563EB]" />
            {field.label}
          </label>
        );
      case 'select':
      case 'member': {
        const options = field.type === 'member' ? memberOptions : field.options ?? {};
        return (
          <div>
            {label}
            <select id={id} className={base} value={isEmpty(value) ? '' : String(value)} disabled={busy}
              onChange={e => setField(index, field.key, e.target.value === '' ? null : e.target.value)}>
              {isEmpty(value) && <option value="">Bitte wählen</option>}
              {Object.entries(options).map(([v, text]) => <option key={v} value={v}>{text}</option>)}
            </select>
          </div>
        );
      }
      case 'datetime':
        return (
          <div>
            {label}
            <input id={id} type="datetime-local" className={base} disabled={busy}
              value={typeof value === 'string' ? value.slice(0, 16) : ''}
              onChange={e => setField(index, field.key, e.target.value ? `${e.target.value}:00` : null)} />
          </div>
        );
      case 'date':
        return (
          <div>
            {label}
            <input id={id} type="date" className={base} disabled={busy}
              value={typeof value === 'string' ? value : ''}
              onChange={e => setField(index, field.key, e.target.value || null)} />
          </div>
        );
      default:
        return (
          <div>
            {label}
            <input id={id} type="text" className={base} disabled={busy}
              value={isEmpty(value) ? '' : String(value)}
              onChange={e => setField(index, field.key, e.target.value)} />
          </div>
        );
    }
  };

  const showsCheckboxes = openIndexes.length > 1;

  return (
    <div role="group" aria-label={`Entwurf: ${kind.label}`}
      className="w-full sm:w-[480px] max-w-full bg-white border border-[#BFDBFE] rounded-2xl shadow-sm overflow-hidden">
      <div className="px-4 py-2.5 bg-[#EFF6FF] border-b border-[#BFDBFE] flex items-center gap-2">
        <span aria-hidden="true">{kind.icon}</span>
        <span className="text-sm font-semibold text-[#1D4ED8]">Entwurf · {kind.label}</span>
        {draft.items.length > 1 && <span className="text-xs text-[#1D4ED8]/70">({draft.items.length})</span>}
      </div>

      <ul className="divide-y divide-slate-100">
        {draft.items.map((item, index) => {
          const open = item.state === 'open';
          const edited = changes[index] ?? {};
          const values = merged(item, edited);
          const keys = item.action === 'create'
            ? spec.fields.filter(f => spec.shown.includes(f.key) || !isEmpty(item.data[f.key])).map(f => f.key)
            : item.action === 'update' ? [...new Set([...Object.keys(item.data), ...Object.keys(edited)])] : [];
          const fields = spec.fields.filter(f => keys.includes(f.key));
          const action = ACTIONS[item.action];
          return (
            <li key={index} className={`px-4 py-3 ${open && !selected.has(index) ? 'opacity-60' : ''}`}>
              <div className="flex items-start gap-2.5">
                {open && showsCheckboxes && (
                  <input type="checkbox" checked={selected.has(index)} disabled={busy} onChange={() => toggle(index)}
                    aria-label={`${headline(draft.kind, values)} übernehmen`}
                    className="mt-0.5 w-4 h-4 rounded border-slate-300 text-[#2563EB] focus:ring-[#2563EB] flex-shrink-0" />
                )}
                <div className="flex-1 min-w-0 space-y-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded-full border ${action.className}`}>{action.label}</span>
                    <span className={`text-sm font-medium text-slate-800 ${item.action === 'delete' && item.state === 'applied' ? 'line-through text-slate-400' : ''}`}>
                      {headline(draft.kind, values)}
                    </span>
                  </div>

                  {open && fields.length > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {fields.map(field => (
                        <div key={field.key} className={WIDE.has(field.key) ? 'sm:col-span-2' : ''}>
                          {input(index, field, values[field.key])}
                          {item.action === 'update' && field.key in item.before && (
                            <p className="text-[11px] text-slate-500 mt-0.5">vorher: {display(field, item.before[field.key])}</p>
                          )}
                        </div>
                      ))}
                    </div>
                  )}

                  {!open && fields.length > 0 && item.action !== 'delete' && (
                    <p className="text-xs text-slate-500">
                      {fields.filter(f => f.key !== 'title' && f.key !== 'name').map(f => `${f.label}: ${display(f, values[f.key])}`).join(' · ')}
                    </p>
                  )}

                  {item.error && open && <p role="alert" className="text-xs text-[#DC2626]">{item.error}</p>}
                  {!open && (
                    <p className={`text-xs font-medium flex items-center gap-1 ${item.state === 'applied' ? 'text-[#15803D]' : 'text-slate-500'}`}>
                      {item.state === 'applied' && <CheckIcon size={12} />}
                      {resultText(draft.kind, item)}
                    </p>
                  )}
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      {openIndexes.length > 0 && (
        <div className="px-4 py-3 bg-[#F8FAFC] border-t border-slate-100 space-y-2">
          {error && <p role="alert" className="text-xs text-[#DC2626]">{error}</p>}
          <div className="flex items-center gap-2 flex-wrap">
            <button type="button" onClick={() => void accept()} disabled={busy || selected.size === 0}
              className="text-sm bg-[#2563EB] text-white px-4 py-1.5 rounded-full font-medium hover:bg-[#1D4ED8] transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
              {busy ? 'Wird übernommen …' : showsCheckboxes ? `Übernehmen (${selected.size})` : 'Übernehmen'}
            </button>
            <button type="button" onClick={() => void run(() => discardDraft(draft.id))} disabled={busy}
              className="text-sm bg-white border border-slate-200 text-slate-600 px-4 py-1.5 rounded-full font-medium hover:border-slate-300 transition-colors disabled:opacity-40">
              Verwerfen
            </button>
          </div>
          <p className="text-[11px] text-slate-500">Erst mit „Übernehmen“ ändert sich etwas.</p>
        </div>
      )}
    </div>
  );
}
