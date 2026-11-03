import { MONTHS, fromDateKey, weekdayIndex } from './dates';

// Wiederkehrende Termine (Entscheidung vom 03.11.2026): Das Backend legt die einzelnen Termine einer Serie an;
// die Oberfläche zeigt die Regel an und schickt sie beim Anlegen oder bei "diesen und alle folgenden" mit.

export type Frequency = 'daily' | 'weekly' | 'monthly' | 'yearly';
export type Weekday = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY';
// Was eine Änderung oder ein Löschen an einem Serientermin betrifft
export type SeriesScope = 'single' | 'following';

export interface Recurrence {
  frequency: Frequency;
  interval: number;
  // Nur wöchentlich; leer = Wochentag des Beginns
  weekdays: Weekday[];
  // Letzter möglicher Tag "JJJJ-MM-TT" oder Anzahl der Termine; beides leer = ohne Ende
  until: string | null;
  count: number | null;
}

export interface SeriesRef {
  seriesId: string;
  // Tag des Termins laut Regel ("JJJJ-MM-TT"), auch wenn er einzeln verschoben wurde
  date: string;
  // Einzeln geändert: übernimmt keine Änderungen an der Serie
  changed: boolean;
}

// Montag zuerst, wie die Kalenderwochen
export const WEEKDAYS: { value: Weekday; short: string; long: string }[] = [
  { value: 'MONDAY', short: 'Mo', long: 'Montag' },
  { value: 'TUESDAY', short: 'Di', long: 'Dienstag' },
  { value: 'WEDNESDAY', short: 'Mi', long: 'Mittwoch' },
  { value: 'THURSDAY', short: 'Do', long: 'Donnerstag' },
  { value: 'FRIDAY', short: 'Fr', long: 'Freitag' },
  { value: 'SATURDAY', short: 'Sa', long: 'Samstag' },
  { value: 'SUNDAY', short: 'So', long: 'Sonntag' },
];

export const FREQUENCY_OPTIONS: { value: Frequency; label: string; unit: [string, string] }[] = [
  { value: 'daily', label: 'Täglich', unit: ['Tag', 'Tage'] },
  { value: 'weekly', label: 'Wöchentlich', unit: ['Woche', 'Wochen'] },
  { value: 'monthly', label: 'Monatlich', unit: ['Monat', 'Monate'] },
  { value: 'yearly', label: 'Jährlich', unit: ['Jahr', 'Jahre'] },
];

export const weekdayOf = (dateKey: string): Weekday => WEEKDAYS[weekdayIndex(fromDateKey(dateKey))].value;

const formatDate = (dateKey: string) => dateKey.split('-').reverse().join('.');

function weekdayNames(rule: Recurrence, firstDateKey: string): string {
  const days = rule.weekdays.length ? rule.weekdays : [weekdayOf(firstDateKey)];
  const names = WEEKDAYS.filter(d => days.includes(d.value)).map(d => d.long);
  return names.length === 1 ? names[0] : `${names.slice(0, -1).join(', ')} und ${names[names.length - 1]}`;
}

// Wie im Backend (Recurrence.describe), z. B. "jeden Dienstag und Donnerstag bis 31.07.2027"
export function describeRecurrence(rule: Recurrence, firstDateKey: string): string {
  const first = fromDateKey(firstDateKey);
  const n = rule.interval;
  let rhythm: string;
  switch (rule.frequency) {
    case 'daily':
      rhythm = n === 1 ? 'täglich' : `alle ${n} Tage`;
      break;
    case 'weekly':
      rhythm = `${n === 1 ? 'jeden ' : `alle ${n} Wochen am `}${weekdayNames(rule, firstDateKey)}`;
      break;
    case 'monthly':
      rhythm = `${n === 1 ? 'jeden Monat' : `alle ${n} Monate`} am ${first.getDate()}.`;
      break;
    case 'yearly':
      rhythm = `${n === 1 ? 'jedes Jahr' : `alle ${n} Jahre`} am ${first.getDate()}. ${MONTHS[first.getMonth()]}`;
      break;
  }
  if (rule.until) return `${rhythm} bis ${formatDate(rule.until)}`;
  return rule.count ? `${rhythm}, ${rule.count}-mal` : rhythm;
}

export const sameRecurrence = (a: Recurrence | null | undefined, b: Recurrence | null | undefined) =>
  JSON.stringify(normalized(a)) === JSON.stringify(normalized(b));

function normalized(rule: Recurrence | null | undefined) {
  if (!rule) return null;
  const weekdays = WEEKDAYS.map(d => d.value).filter(d => rule.weekdays.includes(d));
  return { frequency: rule.frequency, interval: rule.interval, weekdays, until: rule.until, count: rule.count };
}
