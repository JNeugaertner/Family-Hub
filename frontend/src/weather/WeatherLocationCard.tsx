import { useEffect, useState, type FormEvent } from 'react';
import { getSettings } from '../family/api';
import { findPlaces, removeWeatherLocation, setWeatherLocation, type WeatherLocation } from './api';

const INPUT = 'flex-1 min-w-0 border border-slate-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

const placeLabel =(p: WeatherLocation) => [p.name, p.state, p.country].filter(Boolean).join(', ');

// Wohnort für das Wetter (nur Administratoren): Ort bei OpenWeather suchen und einen Treffer übernehmen.
export default function WeatherLocationCard() {
  const [location, setLocation] = useState<WeatherLocation | null | undefined>(undefined);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<WeatherLocation[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSettings().then(s => setLocation(s.weatherLocation)).catch(err => setError(errorText(err)));
  }, []);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      await action();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const search = (e: FormEvent) => {
    e.preventDefault();
    if (!query.trim()) return;
    run(async () => setResults(await findPlaces(query.trim())));
  };

  const choose = (place: WeatherLocation) => run(async () => {
    setLocation(await setWeatherLocation(place));
    setResults(null);
    setQuery('');
    setMessage(`Wohnort „${place.name}“ gespeichert. Die Übersicht zeigt jetzt das Wetter dort.`);
  });

  const remove = () => run(async () => {
    await removeWeatherLocation();
    setLocation(null);
    setMessage('Wohnort entfernt.');
  });

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="weather-location-title">
      <h3 id="weather-location-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
        <span aria-hidden="true">🌤️</span> Wohnort für das Wetter
      </h3>
      <p className="text-xs text-slate-500 mb-3">Die Übersicht zeigt allen das Wetter an diesem Ort (Daten von OpenWeather).</p>

      {location === undefined && !error && <p className="text-sm text-slate-400">Wird geladen…</p>}
      {location === null && <p className="text-sm text-slate-500 mb-3">Noch kein Wohnort eingestellt.</p>}
      {location && (
        <div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-3 py-2">
          <span className="text-sm text-slate-800 flex-1">📍 {placeLabel(location)}</span>
          <button type="button" disabled={busy} onClick={remove} className="text-xs text-slate-500 hover:text-[#EF4444] disabled:opacity-50">
            Entfernen
          </button>
        </div>
      )}

      <form onSubmit={search} className="flex gap-2">
        <input className={INPUT} value={query} onChange={e => setQuery(e.target.value)} maxLength={100}
          placeholder={location ? 'Anderen Ort suchen…' : 'Ort suchen, z. B. Stuttgart'} aria-label="Ort suchen" />
        <button type="submit" disabled={busy || !query.trim()}
          className="px-4 py-2 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
          Suchen
        </button>
      </form>

      {results && (
        <div className="mt-2 space-y-1" aria-label="Suchergebnisse">
          {results.length === 0 && <p className="text-xs text-slate-400 px-1">Kein Ort gefunden.</p>}
          {results.map(p => (
            <button key={`${p.lat},${p.lon}`} type="button" disabled={busy} onClick={() => choose(p)}
              className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 text-sm text-slate-700 disabled:opacity-50">
              📍 {placeLabel(p)}
            </button>
          ))}
        </div>
      )}

      {error && <p role="alert" className="text-sm text-[#DC2626] mt-2">{error}</p>}
      {message && <p role="status" className="text-xs text-[#16A34A] mt-2">{message}</p>}
    </section>
  );
}
