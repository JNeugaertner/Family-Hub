import { useEffect, useState, type FormEvent } from 'react';
import type { ApiMember } from '../family/api';
import { getWasteCollection, importWasteCalendar, type WasteCollection } from './api';

export default function WasteCalendarCard({ members }: { members: ApiMember[] }) {
  const [collection, setCollection] = useState<WasteCollection | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [points, setPoints] = useState(15);
  const [assigneeId, setAssigneeId] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getWasteCollection().then(saved => {
      setCollection(saved);
      setPoints(saved.fileName ? saved.points : 15);
      setAssigneeId(saved.assigneeId ?? '');
    }).catch(err => setError(err instanceof Error ? err.message : String(err)));
  }, []);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!file) {
      setError('Bitte wähle eine .ics-Datei aus.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await importWasteCalendar(file, points, assigneeId);
      setCollection(saved);
      setFile(null);
      setMessage(`${saved.pickups.length} Abholtermine importiert.`);
      const input = document.getElementById('waste-calendar-file') as HTMLInputElement | null;
      if (input) input.value = '';
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="waste-calendar-title">
      <h3 id="waste-calendar-title" className="font-bold text-slate-800 text-base mb-1">Müllabfuhr</h3>
      <p className="text-xs text-slate-500 mb-4">ICS-Abfuhrkalender für euren Wohnort importieren.</p>
      {collection?.fileName && (
        <p className="text-xs text-slate-500 mb-3">{collection.fileName} · {collection.pickups.length} Termine</p>
      )}
      <form onSubmit={submit} className="space-y-3">
        <label htmlFor="waste-calendar-file" className="block text-xs font-semibold text-slate-600">
          ICS-Datei
          <input id="waste-calendar-file" type="file" accept=".ics,text/calendar" onChange={e => setFile(e.target.files?.[0] ?? null)}
            className="mt-1.5 block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-xs file:font-semibold file:text-slate-700 hover:file:bg-slate-200" />
        </label>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label htmlFor="waste-task-points" className="text-xs font-semibold text-slate-600">
            Punkte nach Bestätigung
            <input id="waste-task-points" type="number" min={0} max={1000} value={points}
              onChange={e => setPoints(Number(e.target.value))}
              className="mt-1.5 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-800" />
          </label>
          <label htmlFor="waste-task-assignee" className="text-xs font-semibold text-slate-600">
            Zugewiesene Person
            <select id="waste-task-assignee" value={assigneeId} onChange={e => setAssigneeId(e.target.value)}
              className="mt-1.5 w-full border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-800">
              <option value="">Offen für Kinder und Jugendliche</option>
              {members.filter(member => member.role !== 'gast').map(member => (
                <option key={member.id} value={member.id}>{member.name}</option>
              ))}
            </select>
          </label>
        </div>
        {!assigneeId && <p className="text-xs text-slate-500">Offene Aufgaben benötigen mindestens einen Punkt und können von Kindern übernommen werden.</p>}
        {error && <p role="alert" className="text-sm text-[#DC2626]">{error}</p>}
        {message && <p role="status" className="text-sm text-[#16A34A]">{message}</p>}
        <button type="submit" disabled={busy || !file}
          className="px-4 py-2 rounded-lg bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
          {busy ? 'Importiere…' : 'Kalender importieren'}
        </button>
      </form>
    </section>
  );
}