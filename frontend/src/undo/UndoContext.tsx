import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';

// „Rückgängig“ nach Löschungen (Entscheidung vom 11.11.2026): Unten erscheint kurz ein Hinweis mit Knopf. Ein Klick
// stellt den alten Stand über das Backend wieder her (UndoService, 5 Minuten lang gültig). Ein neuer Hinweis ersetzt
// den alten; der alte lässt sich dann nicht mehr über die Oberfläche zurückholen.

interface Offer {
  id: number;
  message: string;
  undo: () => Promise<unknown>;
}

interface UndoApi {
  // Bietet "Rückgängig" an; ohne Marke (z. B. Serientermin) passiert nichts
  offer: (message: string, token: string | null, undo: (token: string) => Promise<unknown>) => void;
}

const UndoContext = createContext<UndoApi>({ offer: () => {} });

export const useUndo = () => useContext(UndoContext);

const SHOWN_MS = 8000;

export function UndoProvider({ children }: { children: ReactNode }) {
  const [current, setCurrent] = useState<Offer | null>(null);
  const [state, setState] = useState<'idle' | 'busy' | 'done' | 'failed'>('idle');
  const [failure, setFailure] = useState('');
  const counter = useRef(0);

  const offer = useCallback<UndoApi['offer']>((message, token, undo) => {
    if (!token) return;
    counter.current += 1;
    setCurrent({ id: counter.current, message, undo: () => undo(token) });
    setState('idle');
  }, []);

  // Nach einiger Zeit von selbst ausblenden, außer solange gerade wiederhergestellt wird
  useEffect(() => {
    if (!current || state === 'busy') return;
    const timer = setTimeout(() => setCurrent(null), state === 'idle' ? SHOWN_MS : 4000);
    return () => clearTimeout(timer);
  }, [current, state]);

  const run = async () => {
    if (!current || state !== 'idle') return;
    setState('busy');
    try {
      await current.undo();
      setState('done');
    } catch (err) {
      setFailure(err instanceof ApiError && err.status === 404
        ? 'Das lässt sich nicht mehr rückgängig machen.'
        : err instanceof Error ? err.message : String(err));
      setState('failed');
    }
  };

  return (
    <UndoContext.Provider value={{ offer }}>
      {children}
      {current && (
        <div role="status" aria-live="polite" key={current.id}
          className="fixed bottom-20 lg:bottom-6 left-1/2 -translate-x-1/2 z-[60] flex items-center gap-3 max-w-[calc(100vw-2rem)] rounded-xl bg-slate-800 text-white text-sm shadow-2xl pl-4 pr-2 py-2">
          <span className="min-w-0 truncate">
            {state === 'done' ? 'Wiederhergestellt.' : state === 'failed' ? failure : current.message}
          </span>
          {(state === 'idle' || state === 'busy') && (
            <button type="button" onClick={run} disabled={state === 'busy'}
              className="flex-shrink-0 rounded-lg px-3 py-1.5 font-semibold text-[#93C5FD] hover:bg-white/10 disabled:opacity-60">
              {state === 'busy' ? 'Wird wiederhergestellt…' : 'Rückgängig'}
            </button>
          )}
          <button type="button" onClick={() => setCurrent(null)} aria-label="Hinweis schließen"
            className="flex-shrink-0 rounded-lg px-2 py-1.5 text-white/60 hover:text-white hover:bg-white/10">✕</button>
        </div>
      )}
    </UndoContext.Provider>
  );
}
