// Datums-Hilfen für Kalender und Dashboard. Termine tragen ihr Datum als Schlüssel "JJJJ-MM-TT" in Ortszeit.

const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October',
  'November', 'December'];

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
export const mondayOf = (date: Date) => addDays(date, -((date.getDay() + 6) % 7));

// Kopfzeile im Stil des Figma-UI, z. B. "Friday, 25 September 2026"
export const formatLongDate = (date: Date) =>
  `${WEEKDAYS[date.getDay()]}, ${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`;
