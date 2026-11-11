import { useState } from 'react';
import { usePreferences } from './PreferencesContext';
import type { Preferences } from './api';

const OPTIONS: { key: keyof Preferences; label: string; hint: string }[] = [
  { key: 'holidayCountdown', label: 'Countdown bis zum nächsten Feiertag', hint: 'Auf der Übersicht und oben im Kalender' },
  { key: 'holidaysInCalendar', label: 'Feiertage im Kalender', hint: 'Neujahr, Ostern, Weihnachten usw. an ihrem Tag markieren' },
];

// "Meine Anzeige" unter "Familie": Jede Person entscheidet selbst, was sie sieht; jede Änderung wird sofort gespeichert.
export default function DisplayCard() {
  const { preferences, update } = usePreferences();
  const [error, setError] = useState<string | null>(null);

  const toggle = async (key: keyof Preferences) => {
    setError(null);
    try {
      await update({ ...preferences, [key]: !preferences[key] });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="display-title">
      <h3 id="display-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
        <span aria-hidden="true">🎄</span> Meine Anzeige
      </h3>
      <p className="text-sm text-slate-500 mb-3">Gilt nur für dich, die anderen entscheiden selbst.</p>
      {error && <p role="alert" className="text-sm text-[#DC2626] mb-2">{error}</p>}
      <div className="space-y-2">
        {OPTIONS.map(option => (
          <label key={option.key} className="flex items-start gap-2 text-sm text-slate-700 cursor-pointer">
            <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#2563EB]" checked={preferences[option.key]}
              onChange={() => void toggle(option.key)} />
            <span>
              {option.label}
              <span className="block text-xs text-slate-500">{option.hint}</span>
            </span>
          </label>
        ))}
      </div>
    </section>
  );
}
