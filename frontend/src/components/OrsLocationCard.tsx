import {useEffect, useState} from 'react';
import {findPlaces, getSettings, OrsLocation, setHomeLocation, removeHomeLocation} from './OrsApi';
import { ApiError } from '@/api/client';
import OrsAttribution from './OrsAttribution';

const INPUT = 'flex-1 min-w-0 border border-slate-200 rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20';

export default function OrslocationCard(){

    const [query, setQuery] = useState('');
    const [results, setResults] = useState<OrsLocation[] | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [location, setLocation] = useState<String | null | undefined>(undefined);
    const [lat, setLat] = useState<number | null>(null);
    const [lon, setLon] = useState<number | null>(null);

    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [formError, setFormError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [confirmDelete, setConfirmDelete] = useState(false);

      useEffect(() => {
        getSettings().then(s => setLocation(s.homeLocation?.label));
      }, []);

    const showError = (err: unknown) => {
      if (err instanceof ApiError && err.problem.errors) {
        const mapped: Record<string, string> = {};
        Object.entries(err.problem.errors).forEach(([field, msg]) => { mapped[field] = msg; });
        setFieldErrors(mapped);
        setFormError(null);
      } else {
        setFieldErrors({});
        setFormError(err instanceof Error ? err.message : String(err));
      }};

    const search = async () => {
    if (!query.trim()) return;
    try{
    const places = await findPlaces(query.trim());
    setResults(places);}
    catch(err){showError(err);}
    }

    const choose = async (place: OrsLocation) => {
    await setHomeLocation(place);
    setLocation(place.label);
    setLat(place.lat);
    setLon(place.lon);
    setResults(null);
    setQuery('');
    }; 

    const remove = async () => {
        await removeHomeLocation();
        setLocation(null);
    };

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="home-location-title">
      <h3 id="home-location-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
          Adresse
      </h3>
      <p className="text-xs text-slate-500 mb-3">Eingabe der genauen Wohnadresse zur Fahrzeitberechnung.</p>

      {location === null && <p className="text-sm text-slate-500 mb-3">Noch kein Wohnort eingestellt.</p>}
      {location && (
        <div className="flex items-center gap-2 mb-3 bg-slate-50 rounded-xl px-3 py-2">
          <span className="text-sm text-slate-800 flex-1">📍 {location}</span>
          <button type="button" disabled={busy} onClick={remove} className="text-xs text-slate-500 hover:text-[#EF4444] disabled:opacity-50">
            Entfernen
          </button>
        </div>
      )}

      <div className="flex gap-2">
        <input className={INPUT} value={query} onChange={e => setQuery(e.target.value)} 
        onKeyDown={e => {if(e.key === 'Enter'){e.preventDefault(); search();}}} maxLength={100}
          placeholder={location ? 'Anderen Ort suchen… Straße | Hausnr. | Stadt' : 'Adresse suchen, Straße | Hausnr. | Stadt'} aria-label="Ort suchen" />
        <button onClick={search} type="submit" disabled={busy || !query.trim()}
          className="px-4 py-2 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
          Suchen
        </button>
            </div>
            
      {fieldErrors.location && <p role="alert" className="text-sm text-[#DC2626] mt-2">{fieldErrors.location}</p>}
      

      {results && (
        <div className="mt-2 space-y-1" aria-label="Suchergebnisse">
          {results.length === 0 && <p className="text-xs text-slate-500 px-1">Kein Ort gefunden.</p>}
          {results.map(p => (
            <button key={`${p.lat},${p.lon}`} type="button" disabled={busy} onClick={() => choose(p)}
              className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 text-sm text-slate-700 disabled:opacity-50">
              📍 {p.label}
            </button>
          ))}
        </div>
      )}

      {formError && <p role="alert" className="text-sm text-[#DC2626] mt-2">{formError}</p>}
      {message && <p role="status" className="text-xs text-[#16A34A] mt-2">{message}</p>}
      <OrsAttribution className="mt-3" />
    </section>
  );
}