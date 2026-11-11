import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { DEFAULT_PREFERENCES, getPreferences, savePreferences, type Preferences } from './api';

interface PreferencesValue {
  preferences: Preferences;
  // Speichert sofort; bei einem Fehler bleibt der alte Stand und der Fehler geht an den Aufrufer
  update: (next: Preferences) => Promise<void>;
}

const PreferencesContext = createContext<PreferencesValue>({
  preferences: DEFAULT_PREFERENCES,
  update: async () => {},
});

// Eigene Anzeige-Einstellungen für alle Seiten. Bis sie geladen sind (oder wenn das Laden scheitert), gilt
// "alles an", wie im Backend ohne Einstellung. Andere offene Tabs derselben Person ziehen über die
// Live-Aktualisierung nach.
export function PreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, setPreferences] = useState<Preferences>(DEFAULT_PREFERENCES);

  const reload = useCallback(async () => {
    try {
      setPreferences(await getPreferences());
    } catch {
      // alter Stand bleibt
    }
  }, []);
  useEffect(() => { void reload(); }, [reload]);
  useAutoRefresh(reload, ['preferences']);

  const update = useCallback(async (next: Preferences) => {
    setPreferences(next);
    try {
      setPreferences(await savePreferences(next));
    } catch (err) {
      await reload();
      throw err;
    }
  }, [reload]);

  return <PreferencesContext.Provider value={{ preferences, update }}>{children}</PreferencesContext.Provider>;
}

export const usePreferences = () => useContext(PreferencesContext);
