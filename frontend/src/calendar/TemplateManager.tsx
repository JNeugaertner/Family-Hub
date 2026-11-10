import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { useCalendarPermissions } from './permissions';
import {
  deleteTemplate, describeTime, inputOf, listTemplates, updateTemplate, type EventTemplate,
} from './templates';

// Vorlagen ansehen, umbenennen und löschen. Neue Vorlagen entstehen im Termin-Formular ("Als Vorlage speichern"),
// dort sind alle Felder zur Hand.
export default function TemplateManager({ onClose }: { onClose: () => void }) {
  const { me, mayEditFamily } = useCalendarPermissions();
  const [templates, setTemplates] = useState<EventTemplate[] | null>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => listTemplates().then(setTemplates).catch(err => setError(message(err)));
  useEffect(() => { load(); }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !busy) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const mayChange = (t: EventTemplate) => t.createdBy === me.id || mayEditFamily;

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setRenaming(null);
      setConfirmDelete(null);
      await load();
    } catch (err) {
      setError(message(err));
    } finally {
      setBusy(false);
    }
  };

  const rename = (t: EventTemplate, name: string) => {
    if (!name.trim()) { setError('Der Name darf nicht leer sein.'); return; }
    run(() => updateTemplate(t.id, { ...inputOf(t), name: name.trim() }));
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div role="dialog" aria-modal="true" aria-labelledby="templates-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto p-6 space-y-4">
        <div className="flex items-start justify-between gap-4">
          <h2 id="templates-title" className="text-lg font-bold text-slate-800">Vorlagen für Termine</h2>
          <button type="button" onClick={onClose} className="text-slate-500 hover:text-slate-800 text-xl" aria-label="Schließen">✕</button>
        </div>
        <p className="text-sm text-slate-600">
          Für Termine, die immer am gleichen Ort und zur gleichen Uhrzeit sind, z. B. beim Zahnarzt. Eine neue Vorlage
          legst du in einem Termin mit „☆ Als Vorlage speichern“ an; beim nächsten neuen Termin wählst du sie oben aus.
        </p>

        {error && <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#B91C1C] text-sm rounded-xl p-3">{error}</div>}
        {templates === null && !error && <p className="text-sm text-slate-500">Vorlagen werden geladen…</p>}
        {templates?.length === 0 && <p className="text-sm text-slate-500">Noch keine Vorlagen.</p>}

        <ul className="space-y-2">
          {templates?.map(t => (
            <li key={t.id} className="border border-slate-200 rounded-xl p-3">
              {renaming?.id === t.id ? (
                <div className="flex gap-2">
                  <input aria-label={`Neuer Name für ${t.name}`} autoFocus maxLength={60} disabled={busy}
                    className="flex-1 border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#2563EB]"
                    value={renaming.name} onChange={e => setRenaming({ id: t.id, name: e.target.value })}
                    onKeyDown={e => { if (e.key === 'Enter') rename(t, renaming.name); }} />
                  <button type="button" disabled={busy} onClick={() => rename(t, renaming.name)}
                    className="px-3 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
                    Speichern
                  </button>
                  <button type="button" disabled={busy} onClick={() => setRenaming(null)}
                    className="px-3 rounded-lg border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">
                    Abbrechen
                  </button>
                </div>
              ) : (
                <div className="flex items-start gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-slate-800 truncate">{t.private ? '🔒 ' : ''}{t.name}</p>
                    <p className="text-xs text-slate-500 truncate">
                      {t.title} · {describeTime(t)}{t.location ? ` · ${t.location}` : ''}
                    </p>
                  </div>
                  {mayChange(t) && (
                    <div className="flex gap-2 flex-shrink-0">
                      <button type="button" disabled={busy} onClick={() => { setRenaming({ id: t.id, name: t.name }); setConfirmDelete(null); }}
                        className="text-xs font-semibold text-[#1D4ED8] hover:underline disabled:opacity-50">
                        Umbenennen
                      </button>
                      <button type="button" disabled={busy}
                        onClick={() => confirmDelete === t.id ? run(() => deleteTemplate(t.id)) : setConfirmDelete(t.id)}
                        className={`text-xs font-semibold disabled:opacity-50 ${confirmDelete === t.id ? 'text-white bg-[#DC2626] hover:bg-[#B91C1C] rounded-lg px-2 py-1' : 'text-[#B91C1C] hover:underline'}`}>
                        {confirmDelete === t.id ? 'Wirklich löschen?' : 'Löschen'}
                      </button>
                    </div>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
        <p className="text-xs text-slate-500">
          Termine, die schon aus einer Vorlage entstanden sind, ändern sich nicht, wenn du die Vorlage umbenennst oder
          löschst.
        </p>
      </div>
    </div>
  );
}

function message(err: unknown) {
  if (err instanceof ApiError && err.problem.errors) return Object.values(err.problem.errors).join(' ');
  return err instanceof Error ? err.message : String(err);
}
