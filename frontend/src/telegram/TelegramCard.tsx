import { useCallback, useEffect, useState } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { SendIcon } from '../components/Icons';
import NotificationToggles from '../notifications/NotificationToggles';
import { getTelegramStatus, sendTelegramTest, startTelegramLink, unlinkTelegram, type TelegramStatus } from './api';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

const BUTTON = 'py-2 px-4 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50';

// Eigenes Telegram-Konto mit dem Bot der Familie verknüpfen. Danach landen eigene Nachrichten an den Bot im
// Eingang, und FamilyHub schickt einem Benachrichtigungen (welche, stellt man darunter ein). Die Karte springt von
// selbst auf „verbunden“, sobald man in Telegram auf „Starten“ getippt hat (Live-Aktualisierung, Bereich "telegram").
export default function TelegramCard() {
  const [status, setStatus] = useState<TelegramStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Link zum Bot mit Einmal-Code; als Knopf angezeigt, weil Browser nachträglich geöffnete Fenster blockieren
  const [linkUrl, setLinkUrl] = useState<string | null>(null);
  const [testSent, setTestSent] = useState(false);
  const [confirmUnlink, setConfirmUnlink] = useState(false);

  const reload = useCallback(async () => {
    try {
      const next = await getTelegramStatus();
      setStatus(next);
      if (next.linked) setLinkUrl(null);
    } catch (err) {
      setError(errorText(err));
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);
  useAutoRefresh(reload, ['telegram']);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      await reload();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const connect = () => run(async () => setLinkUrl((await startTelegramLink()).url));

  const test = () => run(async () => {
    await sendTelegramTest();
    setTestSent(true);
    window.setTimeout(() => setTestSent(false), 3000);
  });

  const unlink = () => {
    if (!confirmUnlink) { setConfirmUnlink(true); return; }
    setConfirmUnlink(false);
    run(unlinkTelegram);
  };

  return (
    <section aria-labelledby="telegram-title" className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 space-y-3">
      <h3 id="telegram-title" className="font-bold text-slate-800 text-base flex items-center gap-2">
        <SendIcon size={18} className="text-[#229ED9]" /> Telegram
      </h3>

      {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}

      {!status ? (
        !error && <p className="text-sm text-slate-400">Lädt…</p>
      ) : !status.configured ? (
        <p className="text-sm text-slate-500">
          Telegram ist auf dem Server noch nicht eingerichtet: Es fehlt der Bot-Token von @BotFather (siehe README,
          „Telegram einrichten“).
        </p>
      ) : status.error ? (
        <p role="alert" className="text-sm bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] rounded-xl px-3 py-2">
          Der Telegram-Bot ist gerade nicht erreichbar: {status.error}
        </p>
      ) : status.linked ? (
        <>
          <p className="text-sm text-[#15803D] bg-[#F0FDF4] border border-[#BBF7D0] rounded-xl px-3 py-2">
            ✓ Verbunden{status.linkedName && <> als <span className="font-semibold">{status.linkedName}</span></>}
          </p>
          <p className="text-sm text-slate-600">
            Schreib dem Bot <span className="font-semibold">@{status.botUsername}</span>, deine Nachrichten landen in
            FamilyHub ({status.messageCount} bisher). Benachrichtigungen von FamilyHub kommen dort an. Antworten
            darauf landen ebenfalls im Eingang, nicht im Chat.
          </p>
          <div className="flex flex-wrap items-center gap-3 pt-1">
            <button type="button" onClick={test} disabled={busy}
              className={`${BUTTON} bg-[#229ED9] text-white hover:bg-[#1C8BC0]`}>
              {testSent ? '✓ Gesendet' : 'Testnachricht senden'}
            </button>
            <button type="button" onClick={unlink} disabled={busy}
              className={`${BUTTON} ${confirmUnlink ? 'bg-[#EF4444] text-white hover:bg-[#DC2626]' : 'border border-[#FECACA] text-[#DC2626] hover:bg-[#FEF2F2]'}`}>
              {confirmUnlink ? 'Wirklich trennen?' : 'Trennen'}
            </button>
          </div>
          <NotificationToggles />
        </>
      ) : (
        <>
          <p className="text-sm text-slate-500">
            Verbinde dein Telegram-Konto mit dem Bot der Familie: Was du ihm schreibst, landet in FamilyHub, und
            FamilyHub kann dir Nachrichten schicken.
          </p>
          {linkUrl ? (
            <div className="space-y-2">
              <a href={linkUrl} target="_blank" rel="noreferrer"
                className={`${BUTTON} inline-block bg-[#229ED9] text-white hover:bg-[#1C8BC0]`}>
                In Telegram öffnen
              </a>
              <p className="text-xs text-slate-400">
                Tippe dort auf „Starten“. Diese Karte zeigt danach von selbst „Verbunden“. Der Link gilt zehn Minuten.
              </p>
            </div>
          ) : (
            <button type="button" onClick={connect} disabled={busy}
              className={`${BUTTON} bg-[#229ED9] text-white hover:bg-[#1C8BC0]`}>
              Mit Telegram verbinden
            </button>
          )}
        </>
      )}
    </section>
  );
}
