// Datums-Hilfen für Kalender und Dashboard. Termine tragen ihr Datum als Schlüssel "JJJJ-MM-TT" in Ortszeit.

// Index wie Date.getDay(): 0 = Sonntag
const WEEKDAYS = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
export const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober',
  'November', 'Dezember'];
// Kalenderwochen beginnen am Montag
export const WEEKDAYS_SHORT = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
// Position des Tages in der Woche: 0 = Montag … 6 = Sonntag
export const weekdayIndex = (date: Date) => (date.getDay() + 6) % 7;

export function startOfToday(): Date {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
}

export function addDays(date: Date, days: number): Date {
  const result = new Date(date);
  result.setDate(result.getDate() + days);
  return result;
}

export const toDateKey = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;

// Liest "JJJJ-MM-TT" als Tag in Ortszeit (new Date("JJJJ-MM-TT") wäre Mitternacht UTC).
export function fromDateKey(key: string): Date {
  const [year, month, day] = key.split('-').map(Number);
  return new Date(year, month - 1, day);
}

// Montag der Woche, in der das Datum liegt
export const mondayOf = (date: Date) => addDays(date, -weekdayIndex(date));

// Kopfzeile, z. B. "Dienstag, 29. September 2026"
export const formatLongDate = (date: Date) =>
  `${WEEKDAYS[date.getDay()]}, ${date.getDate()}. ${MONTHS[date.getMonth()]} ${date.getFullYear()}`;

// "JJJJ-MM-TT" als Tag und Monat, z. B. "29.09."
export const formatDayMonth = (key: string) => {
  const [, month, day] = key.split('-');
  return `${day}.${month}.`;
};
