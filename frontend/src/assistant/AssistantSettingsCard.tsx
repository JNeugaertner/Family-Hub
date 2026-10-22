import { useCallback, useEffect, useState } from 'react';
import { SparklesIcon } from '../components/Icons';
import { ApiError } from '../api/client';
import {
  getAssistantSettings, getAssistantStatus, getAssistantUsage, testAssistant, updateAssistantSettings,
  type AssistantArea, type AssistantSettings, type AssistantStatus, type AssistantUsage, type ConnectionTestResult,
  type DailyLimits,
} from './api';

const AREAS: { value: AssistantArea; label: string }[] = [
  { value: 'kalender', label: '📅 Kalender' },
  { value: 'aufgaben', label: '✅ Aufgaben' },
  { value: 'einkauf', label: '🛒 Einkauf' },
  { value: 'essen', label: '🍽️ Essensplan' },
  { value: 'wetter_muell', label: '🌦️ Wetter und Müll' },
  { value: 'nachrichten', label: '💬 Nachrichten' },
];

const LIMITS: { key: keyof DailyLimits; label: string }[] = [
  { key: 'kind', label: 'Kinder' },
  { key: 'jugendlicher', label: 'Jugendliche' },
  { key: 'administrator', label: 'Administratoren' },
];

const errorText = (err: unknown) => {
  if (err instanceof ApiError && err.problem.errors) return Object.values(err.problem.errors).join(' ');
  return err instanceof Error ? err.message : String(err);
};

const number = new Intl.NumberFormat('de-DE');

// Admin-Karte des KI-Assistenten (Phase 5, Entscheidungen vom 22.10.2026): Ein/Aus für alle, Bereiche, automatische
// Auswertung von WhatsApp und Telegram, Tageslimit je Rolle, Verbrauch der letzten 7 Tage, Verbindungstest und was an
// Groq geht. Nur für Administratoren (Recht „Sprachassistent verwalten“).
export default function AssistantSettingsCard() {
  const [status, setStatus] = useState<AssistantStatus | null>(null);
  const [saved, setSaved] = useState<AssistantSettings | null>(null);
  const [settings, setSettings] = useState<AssistantSettings | null>(null);
  const [usage, setUsage] = useState<AssistantUsage | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [test, setTest] = useState<ConnectionTestResult | null>(null);
  const [testError, setTestError] = useState<string | null>(null);
  const [testing, setTesting] = useState(false);

  const load = useCallback(async () => {
    try {
      const [st, se, us] = await Promise.all([getAssistantStatus(), getAssistantSettings(), getAssistantUsage()]);
      setStatus(st);
      setSaved(se);
      setSettings(se);
      setUsage(us);
    } catch (err) {
      setError(errorText(err));
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const change = (patch: Partial<AssistantSettings>) => {
    setMessage(null);
    setSettings(s => (s ? { ...s, ...patch } : s));
  };

  const toggleArea = (area: AssistantArea) => {
    if (!settings) return;
    change({ areas: settings.areas.includes(area) ? settings.areas.filter(a => a !== area) : [...settings.areas, area] });
  };

  const setLimit = (key: keyof DailyLimits, value: string) => {
    if (!settings) return;
    const parsed = Number.parseInt(value, 10);
    change({ dailyLimits: { ...settings.dailyLimits, [key]: Number.isNaN(parsed) ? 0 : parsed } });
  };

  const save = async () => {
    if (!settings) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await updateAssistantSettings(settings);
      setSaved(updated);
      setSettings(updated);
      setStatus(s => (s ? { ...s, enabled: updated.enabled } : s));
      setMessage('Gespeichert.');
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const runTest = async () => {
    setTesting(true);
    setTest(null);
    setTestError(null);
    try {
      setTest(await testAssistant());
    } catch (err) {
      setTestError(errorText(err));
    } finally {
      setTesting(false);
    }
  };

  const dirty = JSON.stringify(settings) !== JSON.stringify(saved);

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 space-y-4" aria-labelledby="assistant-settings-title">
      <div>
        <h3 id="assistant-settings-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
          <SparklesIcon size={16} className="text-[#2563EB]" /> KI-Assistent
        </h3>
        {status && (
          <p className="text-xs text-slate-500">
            {status.configured
              ? `Verbunden mit ${status.provider}, Modell ${status.model}.`
              : 'Nicht eingerichtet: Auf dem Server fehlt der API-Schlüssel von Groq (README, „KI-Assistent einrichten“).'}
          </p>
        )}
      </div>

      {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}

      {settings && (
        <>
          <label className="flex items-start gap-2.5 text-sm text-slate-700">
            <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#2563EB]" checked={settings.enabled}
              onChange={e => change({ enabled: e.target.checked })} />
            <span>
              <span className="font-medium">Für die Familie eingeschaltet</span>
              <span className="block text-xs text-slate-500">Aus: niemand kann fragen. Die Hinweise ohne KI in der Übersicht bleiben.</span>
            </span>
          </label>

          <fieldset>
            <legend className="text-sm font-medium text-slate-700 mb-1">Bereiche</legend>
            <p className="text-xs text-slate-500 mb-2">Worin der Assistent nachschlagen und wofür er Entwürfe vorbereiten darf.</p>
            <div className="flex flex-wrap gap-2">
              {AREAS.map(a => {
                const active = settings.areas.includes(a.value);
                return (
                  <button key={a.value} type="button" onClick={() => toggleArea(a.value)} aria-pressed={active}
                    className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${active ? 'bg-[#EFF6FF] border-[#2563EB] text-[#2563EB]' : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50'}`}>
                    {active && '✓ '}{a.label}
                  </button>
                );
              })}
            </div>
          </fieldset>

          <label className="flex items-start gap-2.5 text-sm text-slate-700">
            <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#2563EB]" checked={settings.inboxDrafts}
              onChange={e => change({ inboxDrafts: e.target.checked })} />
            <span>
              <span className="font-medium">WhatsApp- und Telegram-Nachrichten automatisch auswerten</span>
              <span className="block text-xs text-slate-500">
                Schreibt ein Familienmitglied FamilyHub, geht die Nachricht an Groq. Steht etwas für Kalender, Aufgaben,
                Einkauf oder Essen darin, liegt danach ein Entwurf in seinem Chat mit dem Assistenten. Nachrichten von
                Unbekannten werden nie ausgewertet.
              </span>
            </span>
          </label>

          <fieldset>
            <legend className="text-sm font-medium text-slate-700 mb-1">Fragen pro Tag und Person</legend>
            <p className="text-xs text-slate-500 mb-2">0 = unbegrenzt. Automatische Auswertungen zählen mit.</p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
              {LIMITS.map(l => (
                <div key={l.key}>
                  <label htmlFor={`assistant-limit-${l.key}`} className="block text-[11px] font-medium text-slate-500 mb-0.5">{l.label}</label>
                  <input id={`assistant-limit-${l.key}`} type="number" min={0} max={1000} inputMode="numeric"
                    value={settings.dailyLimits[l.key]} onChange={e => setLimit(l.key, e.target.value)}
                    className="w-full border border-slate-200 rounded-lg px-2.5 py-1.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20" />
                </div>
              ))}
            </div>
          </fieldset>

          <div className="flex items-center gap-3 flex-wrap">
            <button type="button" onClick={() => void save()} disabled={!dirty || busy}
              className="text-sm bg-[#2563EB] text-white px-4 py-1.5 rounded-full font-medium hover:bg-[#1D4ED8] transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
              {busy ? 'Speichert …' : 'Speichern'}
            </button>
            {message && <span className="text-xs text-[#16A34A]">{message}</span>}
          </div>
        </>
      )}

      <div className="border-t border-slate-100 pt-4">
        <h4 className="text-sm font-medium text-slate-700 mb-2">Verbrauch der letzten 7 Tage</h4>
        {usage && usage.members.length === 0 && <p className="text-xs text-slate-500">Noch keine Fragen.</p>}
        {usage && usage.members.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead className="text-slate-500">
                <tr>
                  <th scope="col" className="py-1 pr-3 font-medium">Person</th>
                  <th scope="col" className="py-1 pr-3 font-medium text-right">Fragen</th>
                  <th scope="col" className="py-1 pr-3 font-medium text-right">Aufrufe</th>
                  <th scope="col" className="py-1 font-medium text-right">Tokens</th>
                </tr>
              </thead>
              <tbody className="text-slate-700">
                {usage.members.map(m => (
                  <tr key={m.memberId} className="border-t border-slate-50">
                    <td className="py-1 pr-3">{m.name}</td>
                    <td className="py-1 pr-3 text-right">{m.questions}</td>
                    <td className="py-1 pr-3 text-right">
                      {m.calls}{m.failedCalls > 0 && <span className="text-[#DC2626]"> ({m.failedCalls} fehlgeschlagen)</span>}
                    </td>
                    <td className="py-1 text-right">{number.format(m.inputTokens + m.outputTokens)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="mt-3 flex items-center gap-3 flex-wrap">
          <button type="button" onClick={() => void runTest()} disabled={testing || !status?.configured}
            className="text-xs bg-white border border-slate-200 text-slate-600 px-3 py-1.5 rounded-full font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-all disabled:opacity-40">
            {testing ? 'Teste …' : 'Verbindung testen'}
          </button>
          {test && (
            <span className="text-xs text-[#16A34A]">
              ✓ Groq antwortet ({test.model}){test.toolCalling ? ', Werkzeuge funktionieren.' : ', aber ohne Werkzeuge.'}
            </span>
          )}
          {testError && <span role="alert" className="text-xs text-[#DC2626]">{testError}</span>}
        </div>
      </div>

      <p className="text-[11px] text-slate-500 border-t border-slate-100 pt-3">
        Datenschutz: Was der Assistent für eine Frage nachschlägt (Termine, Aufgaben, Listen, Nachrichten, aber nur, was
        die fragende Person sieht) geht an Groq in den USA. Schaltet ihr in der Groq-Konsole unter „Data Controls“ die
        Zero Data Retention ein, speichert Groq die Anfragen nicht. Verlauf und Entwürfe löscht FamilyHub nach 30 Tagen.
      </p>
    </section>
  );
}
