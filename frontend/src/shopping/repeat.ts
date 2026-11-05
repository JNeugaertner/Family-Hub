import { WEEKDAYS_SHORT, addDays, formatDayMonth, fromDateKey, startOfToday, toDateKey, weekdayIndex } from '../calendar/dates';
import type { ShoppingItem, ShoppingRepeat } from './api';

// Wiederkehrende Artikel (wie ShoppingRepeat im Backend): bei jedem Einkauf oder n Tage/Wochen/Monate nach dem Kauf

export type IntervalUnit = 'days' | 'weeks' | 'months';

// Zustand der Auswahl im Formular; interval als Text, solange getippt wird
export interface RepeatDraft {
  kind: 'once' | 'trip' | 'interval';
  interval: string;
  unit: IntervalUnit;
}

export const INTERVAL_UNIT_OPTIONS: { value: IntervalUnit; label: string }[] = [
  { value: 'days', label: 'Tage' },
  { value: 'weeks', label: 'Wochen' },
  { value: 'months', label: 'Monate' },
];

export const ONCE: RepeatDraft = { kind: 'once', interval: '7', unit: 'days' };

export function draftOf(repeat: ShoppingRepeat | null | undefined): RepeatDraft {
  if (!repeat) return ONCE;
  if (repeat.unit === 'trip') return { ...ONCE, kind: 'trip' };
  return { kind: 'interval', interval: String(repeat.interval ?? 7), unit: repeat.unit };
}

const validInterval = (text: string) => /^\d+$/.test(text) && Number(text) >= 1 && Number(text) <= 365;

export const isValidDraft = (draft: RepeatDraft) => draft.kind !== 'interval' || validInterval(draft.interval);

export function repeatOf(draft: RepeatDraft): ShoppingRepeat | null {
  if (draft.kind === 'once') return null;
  if (draft.kind === 'trip') return { unit: 'trip' };
  return { interval: Number(draft.interval), unit: draft.unit };
}

const ONE: Record<IntervalUnit, string> = { days: 'jeden Tag', weeks: 'jede Woche', months: 'jeden Monat' };
const MANY: Record<IntervalUnit, string> = { days: 'Tage', weeks: 'Wochen', months: 'Monate' };

// "bei jedem Einkauf", "jede Woche", "alle 3 Tage"
export function describeRepeat(repeat: ShoppingRepeat): string {
  if (repeat.unit === 'trip') return 'bei jedem Einkauf';
  return repeat.interval === 1 ? ONE[repeat.unit] : `alle ${repeat.interval} ${MANY[repeat.unit]}`;
}

const ONE_SPAN: Record<IntervalUnit, string> = { days: 'einen Tag', weeks: 'eine Woche', months: 'einen Monat' };

// Erklärt die Auswahl im Formular
export function repeatHint(draft: RepeatDraft): string | null {
  if (draft.kind === 'once') return null;
  if (draft.kind === 'trip') {
    return '↻ „Abgehakte entfernen“ löscht den Artikel nicht, er steht danach gleich wieder offen auf der Liste.';
  }
  if (!isValidDraft(draft)) return 'Der Abstand muss zwischen 1 und 365 liegen.';
  const n = Number(draft.interval);
  const span = n === 1 ? ONE_SPAN[draft.unit] : `${n} ${MANY[draft.unit]}`;
  return `↻ Kommt ${span} nach dem Kauf wieder offen auf die Liste.`;
}

// Gekauft und vorerst von der Liste genommen
export const isWaiting = (item: ShoppingItem) => !!item.returnsOn;

// "morgen", "übermorgen", "am Fr, 14.11."
export function returnDay(key: string): string {
  const today = startOfToday();
  if (key === toDateKey(addDays(today, 1))) return 'morgen';
  if (key === toDateKey(addDays(today, 2))) return 'übermorgen';
  return `am ${WEEKDAYS_SHORT[weekdayIndex(fromDateKey(key))]}, ${formatDayMonth(key)}`;
}
