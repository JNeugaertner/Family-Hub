import { useCallback, useEffect, useState } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { getHolidays, getUpcomingHolidays, type Holiday } from './api';

// Feiertage im sichtbaren Zeitraum des Kalenders (Datumsschlüssel YYYY-MM-DD). Sie ändern sich nie, darum kein
// Nachladen; ohne Antwort zeigt der Kalender einfach keine Feiertage.
export function useHolidays(from: string, to: string): Holiday[] {
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  useEffect(() => {
    let cancelled = false;
    getHolidays(from, to)
      .then(list => { if (!cancelled) setHolidays(list); })
      .catch(() => { if (!cancelled) setHolidays([]); });
    return () => { cancelled = true; };
  }, [from, to]);
  return holidays;
}

// Die nächsten Feiertage für den Countdown. Lädt regelmäßig nach, damit der Countdown nach Mitternacht weiterzählt.
export function useUpcomingHolidays(count: number): Holiday[] {
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const reload = useCallback(async () => {
    try {
      setHolidays(await getUpcomingHolidays(count));
    } catch {
      setHolidays([]);
    }
  }, [count]);
  useEffect(() => { void reload(); }, [reload]);
  useAutoRefresh(reload, []);
  return holidays;
}
