import {useEffect, useState} from 'react';
import {findPlaces, getSettings, OrsLocation, setHomeLocation} from './OrsApi';
import { error } from 'console';

const INPUT = 'flex-1 min-w-0 border border-slate-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20';

export default function OrslocationCard(){

    const [query, setQuery] = useState('');
    const [results, setResults] = useState<OrsLocation[] | null>(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [location, setLocation] = useState<String | null | undefined>(undefined);
    const [lat, setLat] = useState<number | null>(null);
    const [lon, setLon] = useState<number | null>(null);

      useEffect(() => {
        getSettings().then(s => setLocation(s.homeLocation?.label));
      }, []);

    const search = async () => {
    if (!query.trim()) return;
    const places = await findPlaces(query.trim());
    setResults(places);
    }

    const choose = async (place: OrsLocation) => {
    await setHomeLocation(place);
    setLocation(place.label);
    setLat(place.lat);
    setLon(place.lon);
    }; 


  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="weather-location-title">
      <h3 id="weather-location-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
        <span aria-hidden="true"></span> Adresse
      </h3>
      <p className="text-xs text-slate-500 mb-3">Eingabe der genauen Wohnadresse zur Fahrzeitberechnung.</p>

      {location === null && <p className="text-sm text-slate-500 mb-3">Noch kein Wohnort eingestellt.</p>}
      {location && (
        <div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-3 py-2">
          <span className="text-sm text-slate-800 flex-1">📍 {location}</span>
        </div>
      )}

      <div className="flex gap-2">
        <input className={INPUT} value={query} onChange={e => setQuery(e.target.value)} maxLength={100}
          placeholder={location ? 'Anderen Ort suchen… Straße | Hausnr. | Stadt' : 'Adresse suchen, Straße | Hausnr. | Stadt'} aria-label="Ort suchen" />
        <button onClick={search} type="submit" disabled={busy || !query.trim()}
          className="px-4 py-2 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
          Suchen
        </button>
            </div>
      

      {results && (
        <div className="mt-2 space-y-1" aria-label="Suchergebnisse">
          {results.length === 0 && <p className="text-xs text-slate-400 px-1">Kein Ort gefunden.</p>}
          {results.map(p => (
            <button key={`${p.lat},${p.lon}`} type="button" disabled={busy} onClick={() => choose(p)}
              className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 text-sm text-slate-700 disabled:opacity-50">
              📍 {p.label}
            </button>
          ))}
        </div>
      )}

      {error && <p role="alert" className="text-sm text-[#DC2626] mt-2">{error}</p>}
      {message && <p role="status" className="text-xs text-[#16A34A] mt-2">{message}</p>}
    </section>
  );
}