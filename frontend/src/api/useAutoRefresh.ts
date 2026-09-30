import { useEffect } from 'react';
import { EVERYTHING, subscribeLive } from './live';

// Hält Daten aktuell, damit Änderungen anderer Familienmitglieder ohne Neuladen der Seite erscheinen:
// - sofort, wenn das Backend eine Änderung in einem der areas meldet (Live-Verbindung, api/live.ts),
// - sofort, wenn man ins Fenster oder in den Tab zurückkehrt,
// - zur Absicherung alle REFRESH_MS, falls die Live-Verbindung einmal fehlt.
// areas: erste Pfadsegmente der API nach /api/, deren Änderungen diese Daten betreffen, z. B. ['tasks'].
export const REFRESH_MS = 60_000;
// Kehrt man mehrmals kurz hintereinander zurück (Fenster- und Tab-Wechsel zugleich), reicht ein Abruf
const MIN_GAP_MS = 2_000;

export function useAutoRefresh(reload: () => Promise<unknown>, areas: readonly string[]) {
  const key = areas.join(',');
  useEffect(() => {
    const watched = key.split(',');
    const visible = () => document.visibilityState === 'visible';
    let last = Date.now();
    const refresh = () => {
      if (!visible() || Date.now() - last < MIN_GAP_MS) return;
      last = Date.now();
      void reload();
    };
    const timer = setInterval(refresh, REFRESH_MS);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    // Verborgene Tabs laden beim Zurückkehren ohnehin neu
    const unsubscribe = subscribeLive(changed => {
      if (visible() && changed.some(area => area === EVERYTHING || watched.includes(area))) {
        last = Date.now();
        void reload();
      }
    });
    return () => {
      clearInterval(timer);
      window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);
      unsubscribe();
    };
  }, [reload, key]);
}
