import { useCallback, useEffect, useState, type FormEvent, type MouseEvent, type ReactNode } from 'react';
import { useFlashFocus } from '../navigation/focus';
import AvatarButton from '../profiles/AvatarButton';
import { PlusIcon, ClockIcon, AlertTriangleIcon, PencilIcon } from './Icons';
import { ApiError } from '../api/client';
import { useCalendarData, type CalendarMember } from '../calendar/CalendarDataContext';
import { formatDayMonth, startOfToday, toDateKey } from '../calendar/dates';
import PointsToast from '../points/PointsToast';
import type { ApiTask, TaskCategory, TaskPriority, TaskStatus } from '../tasks/api';
import { useTaskData } from '../tasks/TaskDataContext';
import { useTaskPermissions } from '../tasks/permissions';

interface Props { onNavigate: (p: any) => void; }

const PRIORITY_COLORS = {
  low: { bg: '#F0FDF4', text: '#16A34A', dot: '#22C55E' },
  medium: { bg: '#FFF7ED', text: '#C2410C', dot: '#F97316' },
  high: { bg: '#FEF2F2', text: '#DC2626', dot: '#EF4444' },
};

const PRIORITY_LABELS: Record<TaskPriority, string> = {
  low: 'Niedrig',
  medium: 'Mittel',
  high: 'Hoch',
};

const CATEGORY_ICONS: Record<TaskCategory, string> = {
  chores: '🧹',
  school: '📚',
  health: '🏥',
  errands: '🛒',
  family: '👨‍👩‍👧‍👦',
  home: '🔧',
};

const CATEGORY_LABELS: Record<TaskCategory, string> = {
  chores: 'Haushalt',
  school: 'Schule',
  health: 'Gesundheit',
  errands: 'Besorgungen',
  family: 'Familie',
  home: 'Haus & Garten',
};

const categoryLabel = (category: TaskCategory) => `${CATEGORY_ICONS[category] || '📋'} ${CATEGORY_LABELS[category] ?? category}`;

type BoardStatus = Exclude<TaskStatus, 'confirmed'>;

const STATUS_LABELS: Record<BoardStatus, string> = {
  todo: 'Offen',
  inprogress: 'In Arbeit',
  done: 'Erledigt',
};

const isFinished = (task: ApiTask) => task.status === 'done' || task.status === 'confirmed';
const isOverdue = (task: ApiTask, todayKey: string) => !isFinished(task) && !!task.dueDate && task.dueDate < todayKey;
// Bonus-Aufgabe, die noch niemand übernommen hat (steht im Bereich "Bonus-Aufgaben", nicht im Board)
const isOpenBonus = (task: ApiTask) => task.bonus && task.assigneeId === null;
const dueLabel = (dueDate: string | null) => (dueDate ? formatDayMonth(dueDate) : 'ohne Frist');
const awaitsConfirmation = (task: ApiTask) => task.status === 'done' && task.points > 0;

function errorText(err: unknown) {
  return err instanceof Error ? err.message : String(err);
}

// ─── Karte ───────────────────────────────────────────────────────────────────

// onEdit öffnet das Formular; mayEdit = false heißt nur ansehen (z. B. bestätigte Aufgaben, Löschen bleibt möglich).
function TaskCard({ task, member, todayKey, mayEdit, onTick, onEdit, onConfirm, onReopen, onRelease }: {
  task: ApiTask;
  member?: CalendarMember;
  todayKey: string;
  mayEdit: boolean;
  onTick?: (status: BoardStatus) => void;
  onEdit?: () => void;
  onConfirm?: () => void;
  onReopen?: () => void;
  onRelease?: () => void;
}) {
  const pri = PRIORITY_COLORS[task.priority];
  const overdue = isOverdue(task, todayKey);
  // Knöpfe in der Karte sollen nicht zusätzlich das Formular öffnen
  const only = (action: () => void) => (e: MouseEvent) => { e.stopPropagation(); action(); };

  return (
    <div className={`bg-white rounded-xl border border-slate-100 p-3.5 shadow-sm hover:shadow-md hover:shadow-slate-100 transition-all group ${onEdit ? 'cursor-pointer' : ''}`}
      data-task={task.title}
      data-focus-id={task.id}
      onClick={onEdit}>
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span
            className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
            style={{ backgroundColor: pri.bg, color: pri.text }}
          >
            {PRIORITY_LABELS[task.priority]}
          </span>
          <span className="text-xs text-slate-400">{categoryLabel(task.category)}</span>
          {task.bonus && <BonusBadge repeatable={task.repeatable} />}
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          {task.points > 0 && (
            <span className="text-[10px] font-bold text-[#F59E0B] flex items-center gap-0.5">
              ⭐ {task.points}
            </span>
          )}
          {onEdit && (
            <button onClick={only(onEdit)}
              className="p-1 -m-1 rounded-md text-slate-400 hover:text-[#2563EB] hover:bg-[#EFF6FF] transition-colors"
              aria-label={`${task.title} ${mayEdit ? 'bearbeiten' : 'ansehen'}`}
              title={mayEdit ? 'Bearbeiten' : 'Ansehen'}>
              <PencilIcon size={14} />
            </button>
          )}
        </div>
      </div>

      <h3 className="font-semibold text-slate-800 text-sm mb-1 leading-snug">{task.title}</h3>
      {task.description && (
        <p className="text-xs text-slate-500 mb-2 leading-relaxed line-clamp-2">{task.description}</p>
      )}

      {awaitsConfirmation(task) && (
        <div className="mt-2 text-[11px] font-semibold text-[#92400E] bg-[#FFFBEB] border border-[#FDE68A] rounded-lg px-2 py-1">
          ⏳ Wartet auf Bestätigung
        </div>
      )}
      {task.status === 'confirmed' && (
        <div className="mt-2 text-[11px] font-semibold text-[#16A34A] bg-[#F0FDF4] rounded-lg px-2 py-1">
          ✅ Bestätigt · +{task.points} Punkte
        </div>
      )}

      <div className="flex items-center justify-between mt-3">
        <div className="flex items-center gap-1.5">
          <AvatarButton member={member} size={24} />
          <span className="text-xs text-slate-500">{member?.name}</span>
        </div>
        <div className={`flex items-center gap-1 text-xs font-medium ${overdue ? 'text-[#EF4444]' : 'text-slate-400'}`}>
          <ClockIcon size={10} />
          {overdue ? 'Überfällig' : dueLabel(task.dueDate)}
        </div>
      </div>

      {/* Bestätigen (Administratoren) */}
      {awaitsConfirmation(task) && onConfirm && onReopen && (
        <div className="flex gap-1.5 mt-3 pt-2.5 border-t border-slate-50">
          <button onClick={only(onReopen)}
            className="flex-1 py-1.5 rounded-lg text-[11px] font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50">
            Zurückgeben
          </button>
          <button onClick={only(onConfirm)}
            className="flex-1 py-1.5 rounded-lg text-[11px] font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A]">
            Bestätigen +{task.points} ⭐
          </button>
        </div>
      )}

      {onRelease && (
        <button onClick={only(onRelease)}
          className="mt-2.5 w-full py-1.5 rounded-lg text-[11px] font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50">
          Bonus-Aufgabe zurückgeben
        </button>
      )}

      {/* Status ändern */}
      {onTick && (
        <div className="flex gap-1.5 mt-3 pt-2.5 border-t border-slate-50 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
          {(['todo', 'inprogress', 'done'] as BoardStatus[]).map(s => (
            <button
              key={s}
              onClick={only(() => task.status !== s && onTick(s))}
              aria-pressed={task.status === s}
              className={`flex-1 py-1 rounded-lg text-[10px] font-semibold transition-colors ${
                task.status === s
                  ? s === 'done' ? 'bg-[#22C55E] text-white'
                  : s === 'inprogress' ? 'bg-[#2563EB] text-white'
                  : 'bg-slate-200 text-slate-700'
                  : 'bg-slate-50 text-slate-400 hover:bg-slate-100'
              }`}
            >
              {STATUS_LABELS[s]}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function BonusBadge({ repeatable }: { repeatable: boolean }) {
  return (
    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-[#FEF3C7] text-[#92400E]"
      title={repeatable ? 'Bonus-Aufgabe, nach der Bestätigung wieder offen' : 'Bonus-Aufgabe'}>
      ⭐ Bonus{repeatable ? ' ↻' : ''}
    </span>
  );
}

// Offene Bonus-Aufgabe: noch niemand hat sie übernommen
function BonusCard({ task, todayKey, onClaim, onEdit, busy }: {
  task: ApiTask;
  todayKey: string;
  onClaim?: () => void;
  onEdit?: () => void;
  busy: boolean;
}) {
  const overdue = isOverdue(task, todayKey);
  return (
    <div data-bonus={task.title} data-focus-id={task.id}
      className={`bg-white rounded-xl border border-[#FDE68A] p-3.5 shadow-sm flex flex-col gap-2 ${onEdit ? 'cursor-pointer hover:shadow-md' : ''}`}
      onClick={onEdit}>
      <div className="flex items-start gap-2">
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-slate-800 text-sm leading-snug">{task.title}</h3>
          {task.description && <p className="text-xs text-slate-500 mt-0.5 line-clamp-2">{task.description}</p>}
        </div>
        <span className="text-lg font-bold text-[#D97706] flex-shrink-0">⭐ {task.points}</span>
      </div>
      <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
        <span>{categoryLabel(task.category)}</span>
        {task.repeatable && <span title="Nach der Bestätigung wieder offen">↻ wiederkehrend</span>}
        <span className={`flex items-center gap-1 ml-auto ${overdue ? 'text-[#EF4444]' : ''}`}>
          <ClockIcon size={10} />{dueLabel(task.dueDate)}
        </span>
      </div>
      {onClaim && (
        <button type="button" disabled={busy} onClick={e => { e.stopPropagation(); onClaim(); }}
          className="py-1.5 rounded-lg text-xs font-semibold bg-[#F59E0B] text-white hover:bg-[#D97706] disabled:opacity-50">
          Übernehmen
        </button>
      )}
    </div>
  );
}

// ─── Formular ────────────────────────────────────────────────────────────────

const INPUT = 'w-full border rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:ring-2';
const inputClass = (hasError: boolean) =>
  `${INPUT} ${hasError ? 'border-[#EF4444] focus:ring-[#EF4444]/20' : 'border-slate-200 focus:border-[#2563EB] focus:ring-[#2563EB]/20'}`;

function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="text-xs font-semibold text-slate-600 mb-1.5 block">{label}</label>
      {children}
      {error && <p className="text-xs text-[#DC2626] mt-1">{error}</p>}
    </div>
  );
}

// readOnly: nur ansehen (z. B. bestätigte Aufgaben), Löschen bleibt möglich, wenn erlaubt
function TaskFormModal({ task, bonusDefault = false, assignable, mayAssignPoints, mayDelete, readOnly, todayKey, onClose }: {
  task?: ApiTask;
  // neue Aufgabe direkt als Bonus-Aufgabe (Knopf im Bereich "Bonus-Aufgaben")
  bonusDefault?: boolean;
  assignable: CalendarMember[];
  mayAssignPoints: boolean;
  mayDelete: boolean;
  readOnly: boolean;
  todayKey: string;
  onClose: () => void;
}) {
  const { saveTask, removeTask } = useTaskData();
  const [title, setTitle] = useState(task?.title ?? '');
  const [description, setDescription] = useState(task?.description ?? '');
  const [assigneeId, setAssigneeId] = useState(task?.assigneeId ?? assignable[0]?.id ?? '');
  const [priority, setPriority] = useState<TaskPriority>(task?.priority ?? 'medium');
  const [category, setCategory] = useState<TaskCategory>(task?.category ?? 'chores');
  // Ob Bonus-Aufgabe, lässt sich nur beim Anlegen wählen
  const [bonus, setBonus] = useState(task?.bonus ?? bonusDefault);
  const [repeatable, setRepeatable] = useState(task?.repeatable ?? false);
  const [dueDate, setDueDate] = useState(task ? task.dueDate ?? '' : bonusDefault ? '' : todayKey);
  const [points, setPoints] = useState(task?.points ?? (mayAssignPoints ? 15 : 0));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const run = async (action: () => Promise<void>) => {
    setBusy(true);
    try {
      await action();
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.problem.errors) {
        setErrors(err.problem.errors);
        setFormError(null);
      } else {
        setErrors({});
        setFormError(errorText(err));
      }
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => saveTask({
      title,
      description: description.trim() || null,
      assigneeId: bonus ? null : assigneeId,
      dueDate: dueDate || null,
      priority,
      category,
      points: mayAssignPoints ? points : task?.points ?? 0,
      bonus,
      repeatable: bonus && repeatable,
    }, task?.id));
  };

  const remove = () => {
    if (!task) return;
    if (!confirmDelete) { setConfirmDelete(true); return; }
    run(() => removeTask(task.id));
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <form onSubmit={submit} noValidate role="dialog" aria-modal="true" aria-labelledby="task-form-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 max-h-[90vh] overflow-y-auto">
        <h2 id="task-form-title" className="font-bold text-slate-800 text-lg mb-5">
          {readOnly ? 'Aufgabe' : task ? (bonus ? 'Bonus-Aufgabe bearbeiten' : 'Aufgabe bearbeiten') : bonus ? 'Neue Bonus-Aufgabe' : 'Neue Aufgabe'}
        </h2>
        {formError && <div role="alert" className="mb-4 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{formError}</div>}
        {readOnly && task?.status === 'confirmed' && (
          <p className="mb-4 text-xs text-slate-500">Bestätigt und abgeschlossen. Die Punkte bleiben auch nach dem Löschen in der Historie.</p>
        )}
        <fieldset disabled={readOnly} className="space-y-4 min-w-0">
          <legend className="sr-only">Angaben zur Aufgabe</legend>
          <Field id="task-title" label="Titel" error={errors.title}>
            <input id="task-title" className={inputClass(!!errors.title)} placeholder="Was ist zu tun?"
              value={title} onChange={e => setTitle(e.target.value)} autoFocus />
          </Field>
          <Field id="task-description" label="Beschreibung (optional)" error={errors.description}>
            <textarea id="task-description" className={`${inputClass(!!errors.description)} resize-none`} rows={2}
              value={description} onChange={e => setDescription(e.target.value)} />
          </Field>
          {mayAssignPoints && !task && (
            <label className="flex items-start gap-2.5 text-sm text-slate-700 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl p-3">
              <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#F59E0B]" checked={bonus}
                onChange={e => { setBonus(e.target.checked); if (e.target.checked && dueDate === todayKey) setDueDate(''); }} />
              <span>
                ⭐ Bonus-Aufgabe
                <span className="block text-xs text-slate-500">Für alle Kinder und Jugendlichen offen: wer sie zuerst übernimmt und erledigt, bekommt die Punkte.</span>
              </span>
            </label>
          )}
          <div className="grid grid-cols-2 gap-3">
            {bonus ? (
              <div>
                <span className="text-xs font-semibold text-slate-600 mb-1.5 block">Zuständig</span>
                <p className="text-sm text-slate-500 py-2.5">
                  {task?.assigneeId ? `übernommen von ${assignable.find(m => m.id === task.assigneeId)?.name ?? 'jemandem'}` : 'wer sie übernimmt'}
                </p>
              </div>
            ) : (
              <Field id="task-assignee" label="Zuständig" error={errors.assigneeId}>
                <select id="task-assignee" className={inputClass(!!errors.assigneeId)} value={assigneeId ?? ''}
                  onChange={e => setAssigneeId(e.target.value)}>
                  {assignable.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
                </select>
              </Field>
            )}
            <Field id="task-priority" label="Priorität" error={errors.priority}>
              <select id="task-priority" className={inputClass(!!errors.priority)} value={priority}
                onChange={e => setPriority(e.target.value as TaskPriority)}>
                {(Object.keys(PRIORITY_LABELS) as TaskPriority[]).map(p => (
                  <option key={p} value={p}>{PRIORITY_LABELS[p]}</option>
                ))}
              </select>
            </Field>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field id="task-dueDate" label={bonus ? 'Fällig am (optional)' : 'Fällig am'} error={errors.dueDate}>
              <input id="task-dueDate" type="date" className={inputClass(!!errors.dueDate)} value={dueDate}
                onChange={e => setDueDate(e.target.value)} />
            </Field>
            <Field id="task-category" label="Kategorie" error={errors.category}>
              <select id="task-category" className={inputClass(!!errors.category)} value={category}
                onChange={e => setCategory(e.target.value as TaskCategory)}>
                {(Object.keys(CATEGORY_ICONS) as TaskCategory[]).map(c => (
                  <option key={c} value={c}>{categoryLabel(c)}</option>
                ))}
              </select>
            </Field>
          </div>
          {mayAssignPoints && (
            <Field id="task-points" label="Punkte nach Bestätigung ⭐" error={errors.points}>
              <input id="task-points" type="number" min={bonus ? 1 : 0} max={1000} className={inputClass(!!errors.points)}
                value={points} onChange={e => setPoints(Number(e.target.value))} />
            </Field>
          )}
          {bonus && mayAssignPoints && (
            <label className="flex items-start gap-2.5 text-sm text-slate-700">
              <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#F59E0B]" checked={repeatable}
                onChange={e => setRepeatable(e.target.checked)} />
              <span>
                ↻ Wiederkehrend
                <span className="block text-xs text-slate-400">Nach der Bestätigung ist sie automatisch wieder offen (ohne Frist).</span>
              </span>
            </label>
          )}
        </fieldset>
        <div className="flex flex-wrap gap-3 mt-6">
          {task && mayDelete && (
            <button type="button" onClick={remove} disabled={busy}
              className={`py-2.5 px-4 rounded-xl text-sm font-semibold disabled:opacity-50 ${confirmDelete ? 'bg-[#EF4444] text-white' : 'border border-[#FECACA] text-[#DC2626] hover:bg-[#FEF2F2]'}`}>
              {confirmDelete ? 'Wirklich löschen?' : 'Löschen'}
            </button>
          )}
          <button type="button" onClick={onClose} disabled={busy}
            className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
            {readOnly ? 'Schließen' : 'Abbrechen'}
          </button>
          {!readOnly && (
            <button type="submit" disabled={busy}
              className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
              {busy ? 'Speichern…' : 'Speichern'}
            </button>
          )}
        </div>
      </form>
    </div>
  );
}

// ─── Seite ───────────────────────────────────────────────────────────────────

export default function Tasks({ onNavigate }: Props) {
  const { status, error, tasks, reload, changeStatus, confirmTask, reopenTask, removeCompletedTasks, claimTask, releaseTask } = useTaskData();
  // Sprung aus der Übersicht: die angeklickte Aufgabe leuchtet kurz auf
  useFlashFocus('task', status === 'ready');
  const { members, memberById } = useCalendarData();
  const perms = useTaskPermissions();
  const [editor, setEditor] = useState<{ task?: ApiTask; bonus?: boolean } | null>(null);
  const [claiming, setClaiming] = useState<string | null>(null);
  const [filterMember, setFilterMember] = useState<string | null>(null);
  const [filterPriority, setFilterPriority] = useState<string>('all');
  const [actionError, setActionError] = useState<string | null>(null);
  const [celebration, setCelebration] = useState<string | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  const [clearedMessage, setClearedMessage] = useState<string | null>(null);
  const hideCelebration = useCallback(() => setCelebration(null), []);
  const todayKey = toDateKey(startOfToday());

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setEditor(null); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const act = async (action: () => Promise<unknown>) => {
    setActionError(null);
    try {
      await action();
    } catch (err) {
      setActionError(errorText(err));
    }
  };

  const confirm = (task: ApiTask) => act(async () => {
    await confirmTask(task.id);
    setCelebration(`+${task.points} Punkte für ${memberById(task.assigneeId ?? '')?.name ?? 'das Kind'}!`);
  });

  const assignableFor = (task?: ApiTask) => members.filter(m => m.effectiveRole !== 'gast'
    && (m.id === task?.assigneeId || perms.canAssignTo(m.id)));

  const openBonus = tasks.filter(isOpenBonus);
  const claim = (task: ApiTask) => {
    setClaiming(task.id);
    act(async () => {
      await claimTask(task.id);
      setClearedMessage(`„${task.title}“ gehört jetzt dir. Viel Erfolg – es gibt ${task.points} Punkte!`);
    }).finally(() => setClaiming(null));
  };

  const filtered = tasks.filter(t => {
    if (isOpenBonus(t)) return false;
    if (filterMember !== null && t.assigneeId !== filterMember) return false;
    if (filterPriority !== 'all' && t.priority !== filterPriority) return false;
    return true;
  });

  const columns: { id: BoardStatus; label: string; color: string; bg: string }[] = [
    { id: 'todo', label: STATUS_LABELS.todo, color: '#64748B', bg: '#F8FAFC' },
    { id: 'inprogress', label: STATUS_LABELS.inprogress, color: '#2563EB', bg: '#EFF6FF' },
    { id: 'done', label: STATUS_LABELS.done, color: '#22C55E', bg: '#F0FDF4' },
  ];

  const stats = {
    total: tasks.length - openBonus.length,
    done: tasks.filter(isFinished).length,
    overdue: tasks.filter(t => isOverdue(t, todayKey)).length,
  };
  const waiting = tasks.filter(awaitsConfirmation);
  // Abgeschlossen: bestätigt oder erledigt ohne Punkte (wie Task.isCompleted im Backend)
  const completed = tasks.filter(t => t.status === 'confirmed' || (t.status === 'done' && t.points === 0));

  const clearCompleted = () => {
    if (!confirmClear) { setConfirmClear(true); return; }
    setConfirmClear(false);
    setClearedMessage(null);
    act(async () => {
      const { deleted } = await removeCompletedTasks();
      setClearedMessage(`${deleted} abgeschlossene Aufgabe${deleted === 1 ? '' : 'n'} gelöscht.`);
    });
  };
  const filterMembers = perms.maySeeFamilyTasks ? members.filter(m => m.effectiveRole !== 'gast') : [];

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Stats bar */}
      <div className="grid grid-cols-3 gap-3 mb-5">
        {[
          { label: 'Aufgaben', value: stats.total, color: '#2563EB', bg: '#EFF6FF' },
          { label: 'Erledigt', value: stats.done, color: '#22C55E', bg: '#F0FDF4' },
          { label: 'Überfällig', value: stats.overdue, color: '#EF4444', bg: '#FEF2F2' },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl border border-slate-100 px-4 py-3 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center text-lg font-bold" style={{ backgroundColor: s.bg, color: s.color }}>
              {s.value}
            </div>
            <span className="text-sm text-slate-600 font-medium">{s.label}</span>
          </div>
        ))}
      </div>

      {status === 'loading' && (
        <div className="mb-5 bg-white border border-slate-100 rounded-xl p-3 text-sm text-slate-500">Aufgaben werden geladen…</div>
      )}
      {status === 'error' && (
        <div className="mb-5 bg-[#FEF2F2] border border-[#FECACA] rounded-xl p-3 flex items-center gap-3">
          <AlertTriangleIcon size={18} className="text-[#EF4444] flex-shrink-0" />
          <span className="text-sm text-[#DC2626] flex-1">{error}</span>
          <button onClick={reload} className="text-sm font-semibold text-[#DC2626] hover:underline">Erneut versuchen</button>
        </div>
      )}
      {actionError && (
        <div role="alert" className="mb-5 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{actionError}</div>
      )}
      {clearedMessage && (
        <div role="status" className="mb-5 bg-[#F0FDF4] border border-[#BBF7D0] text-[#15803D] text-sm rounded-xl p-3">{clearedMessage}</div>
      )}
      {perms.mayConfirm && waiting.length > 0 && (
        <div className="mb-5 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl p-3 text-sm font-semibold text-[#92400E]">
          ⏳ {waiting.length} erledigte Aufgabe{waiting.length > 1 ? 'n warten' : ' wartet'} auf deine Bestätigung
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2 mb-5">
        {filterMembers.length > 0 && (
          <div className="flex gap-1.5 flex-wrap">
            <button
              onClick={() => setFilterMember(null)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${filterMember === null ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
            >
              Alle
            </button>
            {filterMembers.map(m => (
              <button
                key={m.id}
                onClick={() => setFilterMember(filterMember === m.id ? null : m.id)}
                className={`flex items-center gap-1 px-3 py-1.5 rounded-full text-xs font-medium transition-all ${filterMember === m.id ? 'text-white' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'}`}
                style={filterMember === m.id ? { backgroundColor: m.color } : {}}
              >
                {m.name}
              </button>
            ))}
          </div>
        )}

        <div className="flex gap-1.5 ml-auto">
          {['all', 'high', 'medium', 'low'].map(p => (
            <button
              key={p}
              onClick={() => setFilterPriority(p)}
              className={`px-3 py-1.5 rounded-full text-xs font-medium capitalize transition-colors ${
                filterPriority === p
                  ? p === 'high' ? 'bg-[#EF4444] text-white'
                  : p === 'medium' ? 'bg-[#F97316] text-white'
                  : p === 'low' ? 'bg-[#22C55E] text-white'
                  : 'bg-slate-800 text-white'
                  : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
              }`}
            >
              {p === 'all' ? 'Alle' : PRIORITY_LABELS[p as TaskPriority]}
            </button>
          ))}
          {perms.canAdd && (
            <button
              onClick={() => setEditor({})}
              className="flex items-center gap-1.5 bg-[#2563EB] text-white px-3.5 py-1.5 rounded-full text-xs font-semibold hover:bg-[#1D4ED8] transition-colors ml-2"
            >
              <PlusIcon size={14} />
              Neue Aufgabe
            </button>
          )}
        </div>
      </div>

      {/* Bonus-Aufgaben: für alle Kinder und Jugendlichen offen, wer zuerst kommt */}
      {(openBonus.length > 0 || perms.mayConfirm) && (
        <section aria-labelledby="bonus-title" className="mb-5 bg-[#FFFBEB] border border-[#FDE68A] rounded-2xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <h2 id="bonus-title" className="font-bold text-[#92400E] text-sm">⭐ Bonus-Aufgaben</h2>
            <span className="text-xs text-[#B45309]">
              {perms.mayClaim ? 'Wer zuerst übernimmt, bekommt die Punkte.' : 'Offen für alle Kinder und Jugendlichen.'}
            </span>
            {perms.mayConfirm && (
              <button onClick={() => setEditor({ bonus: true })}
                className="ml-auto flex items-center gap-1 text-xs font-semibold text-[#92400E] bg-white border border-[#FDE68A] px-3 py-1.5 rounded-full hover:bg-[#FEF3C7]">
                <PlusIcon size={12} /> Bonus-Aufgabe
              </button>
            )}
          </div>
          {openBonus.length === 0 ? (
            <p className="text-xs text-[#B45309]">Gerade gibt es keine offene Bonus-Aufgabe.</p>
          ) : (
            <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
              {openBonus.map(task => (
                <BonusCard key={task.id} task={task} todayKey={todayKey} busy={claiming === task.id}
                  onClaim={perms.mayClaim ? () => claim(task) : undefined}
                  onEdit={perms.canEdit(task) || perms.canDelete(task) ? () => setEditor({ task }) : undefined} />
              ))}
            </div>
          )}
        </section>
      )}

      {/* Alle Aufgaben */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {columns.map(col => {
          const colTasks = filtered.filter(t => (col.id === 'done' ? isFinished(t) : t.status === col.id));
          return (
            <div key={col.id} className="flex flex-col">
              {/* Column header */}
              <div className="flex items-center gap-2 mb-3 px-1">
                <div className="w-3 h-3 rounded-full" style={{ backgroundColor: col.color }} />
                <h3 className="font-bold text-slate-700 text-sm">{col.label}</h3>
                <span
                  className="ml-auto text-xs font-bold px-2 py-0.5 rounded-full"
                  style={{ backgroundColor: col.bg, color: col.color }}
                >
                  {colTasks.length}
                </span>
              </div>
              {/* Abgeschlossene Aufgaben der ganzen Familie löschen (Administratoren) */}
              {col.id === 'done' && perms.mayDeleteCompleted && completed.length > 0 && (
                <div className="flex items-center gap-2 mb-2 px-1">
                  <button type="button" onClick={clearCompleted}
                    className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors ${confirmClear ? 'bg-[#EF4444] text-white hover:bg-[#DC2626]' : 'border border-[#FECACA] text-[#DC2626] hover:bg-[#FEF2F2]'}`}>
                    {confirmClear ? `Wirklich ${completed.length} löschen?` : `🗑 Erledigte löschen (${completed.length})`}
                  </button>
                  {confirmClear && (
                    <button type="button" onClick={() => setConfirmClear(false)} className="text-xs text-slate-500 hover:underline">
                      Abbrechen
                    </button>
                  )}
                </div>
              )}

              {/* Cards */}
              <div
                className="flex-1 rounded-2xl p-3 space-y-2.5 min-h-[300px]"
                style={{ backgroundColor: col.bg }}
              >
                {colTasks.map(task => (
                  <TaskCard
                    key={task.id}
                    task={task}
                    member={memberById(task.assigneeId ?? '')}
                    todayKey={todayKey}
                    mayEdit={perms.canEdit(task)}
                    onTick={perms.canTick(task) ? s => act(() => changeStatus(task.id, s)) : undefined}
                    onEdit={perms.canEdit(task) || perms.canDelete(task) ? () => setEditor({ task }) : undefined}
                    onConfirm={perms.mayConfirm ? () => confirm(task) : undefined}
                    onReopen={perms.mayConfirm ? () => act(() => reopenTask(task.id)) : undefined}
                    onRelease={perms.canRelease(task) ? () => act(() => releaseTask(task.id)) : undefined}
                  />
                ))}
                {colTasks.length === 0 && (
                  <div className="text-center py-8 text-slate-300 text-sm">
                    <div className="text-2xl mb-2">{col.id === 'done' ? '✅' : '📋'}</div>
                    <div>Keine Aufgaben</div>
                  </div>
                )}
                {perms.canAdd && (
                  <button
                    onClick={() => setEditor({})}
                    className="w-full py-2 border-2 border-dashed border-slate-200 rounded-xl text-slate-400 text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-colors flex items-center justify-center gap-1.5"
                  >
                    <PlusIcon size={13} /> Aufgabe hinzufügen
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {editor && (
        <TaskFormModal
          task={editor.task}
          bonusDefault={editor.bonus}
          assignable={assignableFor(editor.task)}
          mayAssignPoints={perms.mayConfirm}
          mayDelete={editor.task ? perms.canDelete(editor.task) : false}
          readOnly={!!editor.task && !perms.canEdit(editor.task)}
          todayKey={todayKey}
          onClose={() => setEditor(null)}
        />
      )}
      {celebration && <PointsToast text={celebration} onDone={hideCelebration} />}
    </div>
  );
}
