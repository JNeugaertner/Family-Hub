// Kalendertage wie FamilyHub sie sieht (Zeitzone Europe/Berlin), als "JJJJ-MM-TT"
export const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Berlin' }).format(new Date());

export function addDays(day, days) {
  const date = new Date(day + 'T12:00:00Z');
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}
