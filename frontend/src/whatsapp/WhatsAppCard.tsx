import { useEffect, useState } from 'react';
import { getWhatsAppStatus, type WhatsAppStatus } from './api';
import { WhatsAppIcon } from '../components/Icons';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

// Konfig-Vorlage für local.properties (siehe README, „WhatsApp einrichten")
const CONFIG_TEMPLATE = [
  'familyhub.whatsapp.access-token=<ACCESS_TOKEN>',
  'familyhub.whatsapp.phone-number-id=<PHONE_NUMBER_ID>',
  'familyhub.whatsapp.verify-token=<VERIFY_TOKEN>',
].join('\n');

// WhatsApp-Anbindung unter den Einstellungen. „Verbunden" heißt: Der Server hat Zugangsdaten.
// Eingehende Nachrichten werden nur roh gespeichert – noch keine KI-Antworten oder Analyse.
export default function WhatsAppCard() {
  const [status, setStatus] = useState<WhatsAppStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    getWhatsAppStatus().then(setStatus).catch(err => setError(errorText(err)));
  }, []);

  const copyConfig = async () => {
    try {
      await navigator.clipboard.writeText(CONFIG_TEMPLATE);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      setError('Zwischenablage ist nicht verfügbar.');
    }
  };

  return (
    <section data-focus-id="whatsapp" aria-labelledby="whatsapp-title"
      className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 space-y-3">
      <h3 id="whatsapp-title" className="font-bold text-slate-800 text-base flex items-center gap-2">
        <WhatsAppIcon size={18} className="text-[#25D366]" /> WhatsApp
      </h3>

      {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}

      {!status && !error ? (
        <p className="text-sm text-slate-400">Lädt…</p>
      ) : status && !status.configured ? (
        <div className="space-y-3">
          <p className="text-sm text-slate-500 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl px-3 py-2">
            WhatsApp ist noch nicht verbunden.
          </p>
          <ol className="text-sm text-slate-600 list-decimal list-inside space-y-1.5">
            <li>
              Im{' '}
              <a className="text-[#2563EB] underline" href="https://developers.facebook.com/apps" target="_blank" rel="noreferrer">
                Meta Developer Portal
              </a>{' '}
              eine WhatsApp-App mit Cloud-API anlegen.
            </li>
            <li>
              Access-Token, Phone-Number-ID und Verify-Token in{' '}
              <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">local.properties</code> eintragen (Details in der README).
            </li>
            <li>
              Den Webhook <code className="text-xs bg-slate-100 px-1 py-0.5 rounded">/api/whatsapp</code> im Portal hinterlegen und die Verifizierung bestätigen.
            </li>
          </ol>
          <button type="button" onClick={copyConfig}
            className="py-2 px-4 rounded-xl border border-[#25D366]/40 text-[#128C7E] text-sm font-semibold hover:bg-[#25D366]/10 transition-colors">
            {copied ? '✓ Konfig-Vorlage kopiert' : 'Konfig-Vorlage kopieren'}
          </button>
          <p className="text-xs text-slate-400">
            Nach dem Eintragen und Neustart des Servers wird WhatsApp automatisch verbunden.
          </p>
        </div>
      ) : status ? (
        <div className="space-y-2">
          <p className="text-sm text-[#15803D] bg-[#F0FDF4] border border-[#BBF7D0] rounded-xl px-3 py-2">
            ✓ WhatsApp verbunden
          </p>
          <p className="text-sm text-slate-600">
            {status.messageCount} empfangene Nachricht{status.messageCount === 1 ? '' : 'en'} gespeichert.
          </p>
          <p className="text-xs text-slate-400">
            Eingehende Nachrichten werden nur gespeichert – noch keine KI-Antworten oder Analyse.
          </p>
        </div>
      ) : null}
    </section>
  );
}
