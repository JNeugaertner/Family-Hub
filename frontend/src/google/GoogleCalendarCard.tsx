import { useCallback, useEffect, useState } from 'react';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { CATEGORY_OPTIONS } from '../calendar/categories';
import type { EventCategory } from '../components/data';
import GoogleBadge from './GoogleBadge';
import {
  disconnectGoogle, getGoogleStatus, googleReturnFromUrl, selectGoogleCalendars, setGoogleExport, startGoogleConnect,
  syncGoogle, type GoogleCalendar, type GoogleReturn, type GoogleStatus,
} from './api';

const RETURN_MESSAGES: Record<GoogleReturn, { text: string; ok: boolean }> = {
  verbunden: { text: 'Google Kalender verbunden. Deine Termine werden übernommen.', ok: true },
  abgebrochen: { text: 'Die Verbindung wurde bei Google abgebrochen.', ok: false },
  fehler: { text: 'Die Verbindung mit Google hat nicht geklappt. Bitte versuche es erneut.', ok: false },
  'ohne-eintragen': {
    text: 'Google Kalender verbunden. Das Eintragen hast du bei Google nicht erlaubt, FamilyHub liest daher nur.',
    ok: false,
  },
};

const formatTime = (iso: string) =>
  new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' });

// Eigenen Google Kalender verbinden: Termine aus Google erscheinen schreibgeschützt im FamilyHub-Kalender. Wahlweise
// trägt FamilyHub die eigenen Termine in einen eigenen Google-Kalender „FamilyHub“ ein.
export default function GoogleCalendarCard() {
  const { reload: reloadCalendar } = useCalendarData();
  const [status, setStatus] = useState<GoogleStatus | null>(null);
  // Rückmeldung nach der Rückkehr von Google (?google=…); App entfernt den Parameter danach aus der Adresse
  const [returned] = useState(googleReturnFromUrl);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDisconnect, setConfirmDisconnect] = useState(false);

  useEffect(() => {
    getGoogleStatus().then(setStatus).catch(err => setError(err instanceof Error ? err.message : String(err)));
  }, []);

  const run = useCallback(async (action: () => Promise<GoogleStatus | void>) => {
    setBusy(true);
    setError(null);
    try {
      const next = await action();
      setStatus(next ?? await getGoogleStatus());
      await reloadCalendar();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }, [reloadCalendar]);

  const connect = async (exportToGoogle = false) => {
    setBusy(true);
    setError(null);
    try {
      const { authorizationUrl } = await startGoogleConnect(exportToGoogle);
      window.location.assign(authorizationUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setBusy(false);
    }
  };

  const change = (calendar: GoogleCalendar, update: Partial<Pick<GoogleCalendar, 'enabled' | 'category'>>) => {
    if (!status) return;
    run(() => selectGoogleCalendars(status.calendars.map(c => {
      const next = c.calendarId === calendar.calendarId ? { ...c, ...update } : c;
      return { calendarId: next.calendarId, enabled: next.enabled, category: next.category };
    })));
  };

  // Das Recht zum Eintragen fragt Google erst beim ersten Einschalten ab (Umweg über die Anmeldeseite von Google).
  const changeExport = (enabled: boolean) => {
    if (!status) return;
    if (enabled && !status.writeGranted) { connect(true); return; }
    run(() => setGoogleExport(enabled));
  };

  const disconnect = () => {
    if (!confirmDisconnect) { setConfirmDisconnect(true); return; }
    setConfirmDisconnect(false);
    run(disconnectGoogle);
  };

  const message = returned ? RETURN_MESSAGES[returned] : null;

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 space-y-3" aria-labelledby="google-title">
      <h3 id="google-title" className="font-bold text-slate-800 text-base flex items-center gap-2">
        <GoogleBadge /> Google Kalender
      </h3>
      {message && (
        <p role="status" className={`text-sm rounded-xl p-3 ${message.ok ? 'bg-[#F0FDF4] text-[#15803D]' : 'bg-[#FFFBEB] text-[#92400E]'}`}>
          {message.text}
        </p>
      )}
      {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}

      {!status ? (
        !error && <p className="text-sm text-slate-500">Lädt…</p>
      ) : !status.configured ? (
        <p className="text-sm text-slate-500">
          Die Google-Anbindung ist auf dem Server noch nicht eingerichtet (Zugangsdaten fehlen, siehe README).
        </p>
      ) : !status.connected ? (
        <>
          <p className="text-sm text-slate-500">
            Übernimm die Termine aus deinem Google Kalender in FamilyHub. Deine Google-Kalender ändert FamilyHub nicht;
            auf Wunsch trägt es deine Termine aber in einen eigenen Google-Kalender „FamilyHub“ ein.
          </p>
          <button type="button" onClick={() => connect()} disabled={busy}
            className="py-2 px-4 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
            Google Kalender verbinden
          </button>
        </>
      ) : (
        <>
          <p className="text-sm text-slate-600">
            Verbunden mit <span className="font-semibold">{status.email ?? 'Google-Konto'}</span>
            {status.lastSyncAt && <span className="block text-xs text-slate-500">Zuletzt abgeglichen: {formatTime(status.lastSyncAt)}</span>}
          </p>
          {status.needsReconnect ? (
            <div role="alert" className="bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-sm rounded-xl p-3 space-y-2">
              <p>Der Zugang zu Google ist abgelaufen. Bitte verbinde deinen Kalender neu.</p>
              <button type="button" onClick={() => connect()} disabled={busy}
                className="py-1.5 px-3 rounded-lg bg-[#2563EB] text-white text-xs font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
                Neu verbinden
              </button>
            </div>
          ) : status.lastSyncError && (
            <p role="alert" className="text-sm text-[#DC2626]">Letzter Abgleich fehlgeschlagen: {status.lastSyncError}</p>
          )}

          <fieldset disabled={busy} className="space-y-2">
            <legend className="text-xs font-semibold text-slate-600 mb-1">Welche Kalender übernehmen?</legend>
            {status.calendars.map(c => (
              <div key={c.calendarId} className="flex items-center gap-2">
                <label className="flex items-center gap-2 text-sm text-slate-700 flex-1 min-w-0">
                  <input type="checkbox" className="w-4 h-4 accent-[#2563EB]" checked={c.enabled}
                    onChange={e => change(c, { enabled: e.target.checked })} />
                  <span className="truncate">{c.name}{c.primary && <span className="text-slate-500"> (Hauptkalender)</span>}</span>
                </label>
                <select aria-label={`Kategorie für ${c.name}`} value={c.category}
                  onChange={e => change(c, { category: e.target.value as EventCategory })}
                  className="border border-slate-200 rounded-lg px-2 py-1 text-xs text-slate-600 disabled:bg-slate-50">
                  {CATEGORY_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
                </select>
              </div>
            ))}
          </fieldset>

          <div className="border-t border-slate-100 pt-3">
            <label className="flex items-start gap-2 text-sm text-slate-700">
              <input type="checkbox" className="w-4 h-4 mt-0.5 accent-[#2563EB]" checked={status.exportEnabled}
                disabled={busy} onChange={e => changeExport(e.target.checked)} />
              <span>
                <span className="font-semibold">Meine FamilyHub-Termine in Google eintragen</span>
                <span className="block text-xs text-slate-500 mt-0.5">
                  {status.exportEnabled
                    ? 'Deine Termine stehen in Google im Kalender „FamilyHub“. Ändere sie hier in FamilyHub, Änderungen in Google werden überschrieben. Ausschalten entfernt den Kalender aus Google.'
                    : 'Alle Termine, an denen du beteiligt bist, erscheinen in Google in einem eigenen Kalender „FamilyHub“. Deine übrigen Google-Kalender bleiben unverändert.'}
                </span>
              </span>
            </label>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-1">
            <button type="button" onClick={() => run(syncGoogle)} disabled={busy}
              className="py-2 px-4 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
              {busy ? 'Bitte warten…' : 'Jetzt synchronisieren'}
            </button>
            <button type="button" onClick={disconnect} disabled={busy}
              className={`py-2 px-4 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 ${confirmDisconnect ? 'bg-[#DC2626] text-white hover:bg-[#B91C1C]' : 'border border-[#FECACA] text-[#B91C1C] hover:bg-[#FEF2F2]'}`}>
              {!confirmDisconnect ? 'Verbindung trennen'
                : status.exportEnabled ? 'Wirklich trennen? Google-Termine und der Kalender „FamilyHub“ werden entfernt'
                  : 'Wirklich trennen? Google-Termine werden entfernt'}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
