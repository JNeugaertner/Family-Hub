import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { fromDateKey } from '../calendar/dates';
import { getWeather, weatherEmoji, type WeatherReport } from './api';

const WEEKDAYS = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];

// Das Backend fragt OpenWeather höchstens alle 10 Minuten; öfter nachzuladen bringt nichts.
const REFRESH_MS = 10 * 60 * 1000;

type State =
  | { kind: 'loading' }
  | { kind: 'ready'; report: WeatherReport }
  | { kind: 'no-location' }
  | { kind: 'error'; message: string };

const time = (iso: string) =>
  new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' });

function Frame({ children }: { children: ReactNode }) {
  return (
    <section aria-label="Wetter" className="bg-gradient-to-br from-[#2563EB] to-[#14B8A6] rounded-2xl p-4 text-white relative overflow-hidden">
      <div className="absolute top-0 right-0 w-24 h-24 rounded-full bg-white/10 -translate-y-6 translate-x-6 pointer-events-none" />
      <div className="absolute bottom-0 left-0 w-16 h-16 rounded-full bg-white/10 translate-y-4 -translate-x-4 pointer-events-none" />
      <div className="relative">{children}</div>
    </section>
  );
}

// Wetter am Wohnort mit Kleidungsempfehlung (Übersicht). Den Wohnort stellen Administratoren unter "Familie" ein.
export default function WeatherWidget({ onNavigate }: { onNavigate: (page: 'profiles') => void }) {
  const { can } = useAuth();
  const mayView = can('wetter', 'ansehen', 'freigegeben');
  const mayConfigure = can('system', 'verwalten', 'familie');
  const [state, setState] = useState<State>({ kind: 'loading' });

  const load = useCallback(async (isCancelled: () => boolean = () => false) => {
    try {
      const report = await getWeather();
      if (!isCancelled()) setState({ kind: 'ready', report });
    } catch (err) {
      if (isCancelled()) return;
      if (err instanceof ApiError && err.status === 409) setState({ kind: 'no-location' });
      else setState({ kind: 'error', message: err instanceof Error ? err.message : String(err) });
    }
  }, []);

  useEffect(() => {
    if (!mayView) return;
    let cancelled = false;
    load(() => cancelled);
    const timer = setInterval(() => load(() => cancelled), REFRESH_MS);
    return () => { cancelled = true; clearInterval(timer); };
  }, [mayView, load]);

  if (!mayView) return null;

  if (state.kind === 'loading') {
    return <Frame><div className="text-sm text-white/80 py-6 text-center">Wetter wird geladen…</div></Frame>;
  }

  if (state.kind === 'no-location') {
    return (
      <Frame>
        <div className="text-3xl mb-2">🌤️</div>
        <div className="font-semibold text-sm">Noch kein Wohnort eingestellt</div>
        {mayConfigure ? (
          <button onClick={() => onNavigate('profiles')} className="mt-3 text-xs font-semibold bg-white/20 hover:bg-white/30 rounded-full px-3 py-1.5">
            Wohnort unter „Familie“ einstellen →
          </button>
        ) : (
          <p className="text-xs text-white/80 mt-1">Deine Eltern können ihn unter „Familie“ einstellen.</p>
        )}
      </Frame>
    );
  }

  if (state.kind === 'error') {
    return (
      <Frame>
        <div className="text-3xl mb-2">🌡️</div>
        <div className="font-semibold text-sm">Wetter gerade nicht verfügbar</div>
        <p className="text-xs text-white/80 mt-1">{state.message}</p>
        <button onClick={() => { setState({ kind: 'loading' }); load(); }} className="mt-3 text-xs font-semibold bg-white/20 hover:bg-white/30 rounded-full px-3 py-1.5">
          Erneut versuchen
        </button>
      </Frame>
    );
  }

  const { report } = state;
  const { current, advice } = report;
  return (
    <Frame>
      <div className="flex items-center justify-between text-white/70 text-[11px] mb-1">
        <span>📍 {report.location}</span>
        <span>Stand {time(report.observedAt)} Uhr</span>
      </div>

      {/* Jetzt */}
      <div className="flex items-start justify-between">
        <div>
          <div className="text-4xl font-light">{current.temp}°</div>
          <div className="text-white/80 text-sm mt-0.5">{current.description}</div>
          <div className="text-white/60 text-xs mt-1">
            ↑{report.high}° · ↓{report.low}° · gefühlt {current.feelsLike}° · 💧{current.humidity} % · 💨{current.wind} km/h
          </div>
        </div>
        <div className="text-4xl" aria-hidden="true">{weatherEmoji(current.icon)}</div>
      </div>

      {/* Nächste Tage */}
      {report.days.length > 0 && (
        <div className="flex gap-2 mt-3 border-t border-white/20 pt-3">
          {report.days.map(day => (
            <div key={day.date} className="flex-1 flex flex-col items-center gap-0.5" title={day.description}>
              <div className="text-white/60 text-[10px]">{WEEKDAYS[fromDateKey(day.date).getDay()]}</div>
              <div className="text-sm" aria-hidden="true">{weatherEmoji(day.icon)}</div>
              <div className="text-white text-[10px] font-medium">{day.high}° <span className="text-white/60">{day.low}°</span></div>
              {day.rainChance >= 30 && <div className="text-white/70 text-[9px]">💧{day.rainChance} %</div>}
            </div>
          ))}
        </div>
      )}

      {/* Kleidungsempfehlung */}
      <div className="mt-3 pt-3 border-t border-white/20">
        <div className="text-white/70 text-[10px] font-semibold uppercase tracking-wide mb-2">
          {advice.title} · Empfehlungen für heute
        </div>
        <div className="flex flex-wrap gap-1.5">
          {advice.items.map(item => (
            <span key={item.label} className="flex items-center gap-1 bg-white/20 rounded-full px-2.5 py-1 text-xs font-medium text-white">
              <span aria-hidden="true">{item.icon}</span>
              <span>{item.label}</span>
              <span className="text-white/70 font-normal">· {item.reason}</span>
            </span>
          ))}
        </div>
      </div>
    </Frame>
  );
}
