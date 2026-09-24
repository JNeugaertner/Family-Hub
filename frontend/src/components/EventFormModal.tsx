import { useEffect, useState, type FormEvent, type ReactNode } from 'react';
import type { CalendarEvent, EventCategory } from './data';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { useCalendarPermissions } from '../calendar/permissions';
import { ApiError } from '../api/client';
import { CATEGORY_OPTIONS } from '../calendar/categories';

// Das Backend meldet "Ende nach Beginn" unter endAfterStart; im Formular gehört es zum Feld "Ende".
const FIELD_ALIASES: Record<string, string> = { endAfterStart: 'end' };

const INPUT = 'w-full border rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 disabled:bg-slate-50 disabled:text-slate-600';
const inputClass = (hasError: boolean) =>
  `${INPUT} ${hasError ? 'border-[#EF4444] focus:border-[#EF4444] focus:ring-[#EF4444]/20' : 'border-slate-200 focus:border-[#2563EB] focus:ring-[#2563EB]/20'}`;

// Label per htmlFor verknüpft und Fehlertext außerhalb, damit der Feldname für Screenreader nur das Label ist.
function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="text-xs font-semibold text-slate-600 mb-1.5 block">{label}</label>
      {children}
      {error && <p id={`${id}-error`} className="text-xs text-[#DC2626] mt-1">{error}</p>}
    </div>
  );
}

interface Props {
  event?: CalendarEvent;
  defaultDate: string;
  onClose: () => void;
}

export default function EventFormModal({ event, defaultDate, onClose }: Props) {
  const { members, memberById, saveEvent, removeEvent, approveEvent, rejectEvent } = useCalendarData();
  const permissions = useCalendarPermissions();
  const { me } = permissions;

  const isEdit = !!event;
  const readOnly = isEdit && !permissions.canEdit(event);
  const isProposal = event?.status === 'proposed';
  // Bestehende, freigegebene Termine darf nur umhängen, wer Familientermine bearbeiten darf.
  const canReassign = !isEdit || isProposal || permissions.mayEditFamily;

  const assignable = members.filter(m => m.effectiveRole !== 'gast' && (
    m.id === event?.memberId || (canReassign ? permissions.canAssignTo(m.id) : m.id === me.id)));
  const initialMember = event?.memberId
    ?? (permissions.canAssignTo(me.id) ? me.id : assignable[0]?.id ?? '');

  const [title, setTitle] = useState(event?.title ?? '');
  const [start, setStart] = useState(event?.start.slice(0, 16) ?? `${defaultDate}T09:00`);
  const [end, setEnd] = useState(event?.end.slice(0, 16) ?? `${defaultDate}T10:00`);
  const [memberId, setMemberId] = useState(initialMember);
  const [category, setCategory] = useState<EventCategory>(event?.category ?? 'family');
  const [location, setLocation] = useState(event?.location ?? '');
  const [description, setDescription] = useState(event?.description ?? '');
  const [privateEvent, setPrivateEvent] = useState(event?.private ?? false);

  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !busy) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const color = memberById(memberId)?.color ?? '#2563EB';
  const willBeProposal = !readOnly && (isProposal || (!isEdit && permissions.becomesProposal(memberId)));
  const proposer = event?.createdBy ? memberById(event.createdBy)?.name : undefined;

  const fieldProps = (name: string) => ({
    id: `event-${name}`,
    className: inputClass(!!fieldErrors[name]),
    disabled: readOnly || busy,
    'aria-invalid': fieldErrors[name] ? true : undefined,
    'aria-describedby': fieldErrors[name] ? `event-${name}-error` : undefined,
  });

  const showError = (err: unknown) => {
    if (err instanceof ApiError && err.problem.errors) {
      const mapped: Record<string, string> = {};
      Object.entries(err.problem.errors).forEach(([field, msg]) => { mapped[FIELD_ALIASES[field] ?? field] = msg; });
      setFieldErrors(mapped);
      setFormError(null);
    } else {
      setFieldErrors({});
      setFormError(err instanceof Error ? err.message : String(err));
    }
  };

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    try {
      await action();
      onClose();
    } catch (err) {
      showError(err);
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (readOnly) return;
    run(() => saveEvent({
      title,
      start: start || null,
      end: end || null,
      memberId,
      category,
      location: location.trim() || null,
      description: description.trim() || null,
      private: privateEvent,
    }, event?.id));
  };

  const remove = () => {
    if (!event) return;
    if (!confirmDelete) { setConfirmDelete(true); return; }
    run(() => removeEvent(event.id));
  };

  const heading = readOnly ? 'Termin' : isProposal ? 'Vorschlag bearbeiten' : isEdit ? 'Termin bearbeiten'
    : willBeProposal ? 'Neuer Vorschlag' : 'Neuer Termin';

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <form
        onSubmit={submit}
        noValidate
        role="dialog"
        aria-modal="true"
        aria-labelledby="event-form-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto"
      >
        <div
          className="h-20 rounded-t-2xl relative flex items-end p-5"
          style={{ background: `linear-gradient(135deg, ${color}, ${color}BB)` }}
        >
          <h2 id="event-form-title" className="text-white font-bold text-lg">{heading}</h2>
          <button type="button" onClick={onClose} className="absolute top-3 right-3 text-white/80 hover:text-white text-xl" aria-label="Schließen">
            ✕
          </button>
        </div>

        <div className="p-6 space-y-4">
          {isProposal && (
            <div className="bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-sm rounded-xl p-3">
              ⏳ Vorschlag{proposer ? ` von ${proposer}` : ''} – wartet auf Freigabe durch einen Administrator.
            </div>
          )}
          {!isEdit && willBeProposal && (
            <div className="bg-[#FFFBEB] border border-[#FDE68A] text-[#92400E] text-sm rounded-xl p-3">
              Für andere Familienmitglieder wird der Termin als <strong>Vorschlag</strong> gespeichert. Er gilt erst,
              wenn ein Administrator ihn freigibt.
            </div>
          )}
          {readOnly && (
            <p className="text-xs text-slate-500">Du kannst diesen Termin ansehen, aber nicht ändern.</p>
          )}
          {formError && (
            <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{formError}</div>
          )}

          <Field id="event-title" label="Titel" error={fieldErrors.title}>
            <input {...fieldProps('title')} value={title} onChange={e => setTitle(e.target.value)} autoFocus={!readOnly} />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field id="event-start" label="Beginn" error={fieldErrors.start}>
              <input type="datetime-local" {...fieldProps('start')} value={start} onChange={e => setStart(e.target.value)} />
            </Field>
            <Field id="event-end" label="Ende" error={fieldErrors.end}>
              <input type="datetime-local" {...fieldProps('end')} value={end} onChange={e => setEnd(e.target.value)} />
            </Field>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Field id="event-memberId" label="Familienmitglied" error={fieldErrors.memberId}>
              <select {...fieldProps('memberId')} value={memberId} onChange={e => setMemberId(e.target.value)}>
                {(readOnly ? members.filter(m => m.id === memberId) : assignable).map(m => (
                  <option key={m.id} value={m.id}>{m.name}{m.id === me.id ? ' (ich)' : ''}</option>
                ))}
              </select>
            </Field>
            <Field id="event-category" label="Kategorie" error={fieldErrors.category}>
              <select {...fieldProps('category')} value={category} onChange={e => setCategory(e.target.value as EventCategory)}>
                {CATEGORY_OPTIONS.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            </Field>
          </div>

          <Field id="event-location" label="Ort (optional)" error={fieldErrors.location}>
            <input {...fieldProps('location')} value={location} onChange={e => setLocation(e.target.value)} />
          </Field>

          <Field id="event-description" label="Beschreibung (optional)" error={fieldErrors.description}>
            <textarea rows={3} {...fieldProps('description')} value={description} onChange={e => setDescription(e.target.value)} />
          </Field>

          <label className="flex items-start gap-2.5 text-sm text-slate-700">
            <input
              type="checkbox"
              className="mt-0.5 w-4 h-4 accent-[#2563EB]"
              checked={privateEvent}
              disabled={readOnly || busy}
              onChange={e => setPrivateEvent(e.target.checked)}
            />
            <span>
              🔒 Privat
              <span className="block text-xs text-slate-400">Nur für die Person selbst, wer den Termin anlegt, und Administratoren sichtbar.</span>
            </span>
          </label>

          <div className="flex flex-wrap gap-3 pt-2">
            {isEdit && permissions.canDelete(event) && (
              <button
                type="button"
                onClick={remove}
                disabled={busy}
                className={`py-2.5 px-4 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 ${confirmDelete ? 'bg-[#EF4444] text-white hover:bg-[#DC2626]' : 'border border-[#FECACA] text-[#DC2626] hover:bg-[#FEF2F2]'}`}
              >
                {confirmDelete ? 'Wirklich löschen?' : isProposal && event.createdBy === me.id ? 'Zurückziehen' : 'Löschen'}
              </button>
            )}
            {isProposal && permissions.mayDecide && (
              <>
                <button type="button" disabled={busy} onClick={() => run(() => rejectEvent(event.id))}
                  className="py-2.5 px-4 rounded-xl text-sm font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                  Ablehnen
                </button>
                <button type="button" disabled={busy} onClick={() => run(() => approveEvent(event.id))}
                  className="py-2.5 px-4 rounded-xl text-sm font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] disabled:opacity-50">
                  Freigeben
                </button>
              </>
            )}
            <button type="button" onClick={onClose} disabled={busy} className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
              {readOnly ? 'Schließen' : 'Abbrechen'}
            </button>
            {!readOnly && (
              <button type="submit" disabled={busy} className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
                {busy ? 'Speichern…' : willBeProposal && !isEdit ? 'Vorschlagen' : 'Speichern'}
              </button>
            )}
          </div>
        </div>
      </form>
    </div>
  );
}
