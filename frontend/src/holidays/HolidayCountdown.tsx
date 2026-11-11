import { fromDateKey } from '../calendar/dates';
import { countdownText } from './api';
import { usePreferences } from '../preferences/PreferencesContext';
import { useUpcomingHolidays } from './useHolidays';

const dateText = (key: string) =>
  fromDateKey(key).toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'long' });

// Countdown zu den nächsten Feiertagen (Übersicht): der nächste groß, die zwei danach klein. Ausblenden kann jede
// Person für sich unter „Familie“ → „Meine Anzeige“.
export default function HolidayCountdown() {
  const { preferences } = usePreferences();
  const holidays = useUpcomingHolidays(3);
  if (!preferences.holidayCountdown || holidays.length === 0) return null;
  const [next, ...later] = holidays;
  return (
    <section aria-label="Countdown bis zu den nächsten Feiertagen" className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
      <h2 className="font-bold text-slate-800 text-base mb-3">Countdown</h2>
      <div className="flex items-center gap-4 p-3 rounded-xl bg-[#FFF7ED]">
        <span className="text-3xl" aria-hidden="true">{next.emoji}</span>
        <div className="min-w-0">
          <div className="font-semibold text-slate-800 truncate">{next.name}</div>
          <div className="text-xs text-slate-600">{dateText(next.date)}</div>
        </div>
        <div className="ml-auto text-right shrink-0">
          {next.daysLeft === 0 ? (
            <span className="text-lg font-bold text-[#C2410C]">Heute! 🎉</span>
          ) : (
            <>
              <div className="text-2xl font-bold text-[#C2410C] leading-none">{next.daysLeft}</div>
              <div className="text-[11px] font-medium text-[#C2410C]">{next.daysLeft === 1 ? 'Tag' : 'Tage'}</div>
            </>
          )}
        </div>
      </div>
      {later.length > 0 && (
        <ul className="mt-3 space-y-1.5">
          {later.map(h => (
            <li key={h.key} className="flex items-center gap-2 text-sm text-slate-700">
              <span aria-hidden="true">{h.emoji}</span>
              <span className="truncate">{h.name}</span>
              <span className="ml-auto text-xs text-slate-500 shrink-0">{countdownText(h.daysLeft)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
