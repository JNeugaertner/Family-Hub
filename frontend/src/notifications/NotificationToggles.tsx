import { useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { getNotificationSettings, saveNotificationSettings, type NotificationSettings } from './api';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

const KINDS: { key: keyof NotificationSettings; label: string; hint: string }[] = [
  { key: 'tasks', label: 'Dringende Aufgaben', hint: 'Wenn dir eine zugewiesen wird und morgens am Fälligkeitstag' },
  { key: 'events', label: 'Kurzfristige Termine', hint: 'Neu oder verschoben, Beginn in den nächsten 24 Stunden' },
  { key: 'confirmations', label: 'Offene Bestätigungen', hint: 'Wenn etwas seit 24 Stunden auf dich wartet' },
  { key: 'messages', label: 'Chat-Nachrichten', hint: 'Einzelchats und Familiengruppe' },
];

// Eigene Benachrichtigungen an- und abschalten; jede Änderung wird sofort gespeichert. Offene Bestätigungen gibt es
// nur für alle, die etwas bestätigen dürfen (wie im Backend, PendingConfirmations).
export default function NotificationToggles() {
  const { can } = useAuth();
  const [settings, setSettings] = useState<NotificationSettings | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mayConfirm = can('punkte', 'freigeben', 'familie') || can('kalender', 'freigeben', 'familie')
    || can('einkauf', 'freigeben', 'familie') || can('essen', 'freigeben', 'familie');

  useEffect(() => {
    getNotificationSettings().then(setSettings).catch(err => setError(errorText(err)));
  }, []);

  const toggle = async (key: keyof NotificationSettings) => {
    if (!settings) return;
    const next = { ...settings, [key]: !settings[key] };
    setSettings(next);
    setError(null);
    try {
      setSettings(await saveNotificationSettings(next));
    } catch (err) {
      setSettings(settings);
      setError(errorText(err));
    }
  };

  return (
    <fieldset className="border-t border-slate-100 pt-3 space-y-2">
      <legend className="text-sm font-semibold text-slate-700 pb-1">Benachrichtigungen</legend>
      {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}
      {!settings ? (
        !error && <p className="text-sm text-slate-400">Lädt…</p>
      ) : (
        KINDS.filter(kind => kind.key !== 'confirmations' || mayConfirm).map(kind => (
          <label key={kind.key} className="flex items-start gap-2 text-sm text-slate-700 cursor-pointer">
            <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#229ED9]" checked={settings[kind.key]}
              onChange={() => void toggle(kind.key)} />
            <span>
              {kind.label}
              <span className="block text-xs text-slate-400">{kind.hint}</span>
            </span>
          </label>
        ))
      )}
    </fieldset>
  );
}
