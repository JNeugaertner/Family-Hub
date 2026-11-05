import { useState } from 'react';
import { PlusIcon, CheckIcon, XIcon, ShoppingCartIcon, AlertTriangleIcon, PencilIcon } from './Icons';
import { useAuth, useMe } from '../auth/AuthContext';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { ApiError } from '../api/client';
import type { RemovedChecked, ShoppingCategory, ShoppingInput, ShoppingItem } from '../shopping/api';
import { SELECTABLE_SHOPPING_CATEGORIES, SHOPPING_CATEGORIES, SHOPPING_CATEGORY_KEYS } from '../shopping/categories';
import {
  INTERVAL_UNIT_OPTIONS, ONCE, describeRepeat, draftOf, isValidDraft, isWaiting, repeatHint, repeatOf, returnDay,
  type IntervalUnit, type RepeatDraft,
} from '../shopping/repeat';
import { useShoppingData } from '../shopping/ShoppingDataContext';

interface Props { onNavigate: (p: any) => void; }

// Feldfehler (z. B. beim Abstand) stehen unter "errors", die allgemeine Meldung sagt nur "ungültig"
const errorText = (err: unknown) => {
  if (err instanceof ApiError && err.problem.errors) return Object.values(err.problem.errors).join(' ');
  return err instanceof Error ? err.message : String(err);
};

// Rückmeldung nach „Abgehakte entfernen“: Wiederkehrende Artikel bleiben
function removedText(r: RemovedChecked): string {
  const parts: string[] = [];
  if (r.deleted) parts.push(`${r.deleted} abgehakte Artikel entfernt.`);
  if (r.backOnList) {
    parts.push(r.backOnList === 1 ? '1 wiederkehrender Artikel steht wieder offen auf der Liste.'
      : `${r.backOnList} wiederkehrende Artikel stehen wieder offen auf der Liste.`);
  }
  if (r.waiting) {
    parts.push(r.waiting === 1 ? '1 wiederkehrender Artikel kommt später wieder (unten unter „Kommt wieder“).'
      : `${r.waiting} wiederkehrende Artikel kommen später wieder (unten unter „Kommt wieder“).`);
  }
  return parts.join(' ') || 'Es war nichts abgehakt.';
}

const INPUT = 'border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#22C55E] focus:ring-2 focus:ring-[#22C55E]/20 bg-white';

// Rechte wie im Backend (ShoppingAccess): Kinder schlagen vor, Jugendliche und Eltern bearbeiten, Eltern entscheiden.
function useShoppingPermissions() {
  const { can } = useAuth();
  return {
    mayView: can('einkauf', 'ansehen', 'familie'),
    mayAdd: can('einkauf', 'erstellen', 'familie'),
    mayPropose: can('einkauf', 'vorschlagen', 'familie'),
    mayEdit: can('einkauf', 'bearbeiten', 'familie'),
    mayDelete: can('einkauf', 'loeschen', 'familie'),
    mayDecide: can('einkauf', 'freigeben', 'familie'),
  };
}

// Einmalig, bei jedem Einkauf oder n Tage/Wochen/Monate nach dem Kauf
function RepeatPicker({ draft, onChange, small = false }: {
  draft: RepeatDraft;
  onChange: (draft: RepeatDraft) => void;
  small?: boolean;
}) {
  const size = small ? 'py-1.5' : '';
  return (
    <span className="flex items-center gap-1.5">
      <select className={`${INPUT} ${size}`} value={draft.kind} aria-label="Wiederholen"
        onChange={e => onChange({ ...draft, kind: e.target.value as RepeatDraft['kind'] })}>
        <option value="once">Einmalig</option>
        <option value="trip">↻ Bei jedem Einkauf</option>
        <option value="interval">↻ Nach Abstand</option>
      </select>
      {draft.kind === 'interval' && (
        <>
          <input className={`${INPUT} ${size} w-16`} type="number" min={1} max={365} inputMode="numeric" aria-label="Abstand"
            value={draft.interval} onChange={e => onChange({ ...draft, interval: e.target.value })} />
          <select className={`${INPUT} ${size}`} value={draft.unit} aria-label="Einheit"
            onChange={e => onChange({ ...draft, unit: e.target.value as IntervalUnit })}>
            {INTERVAL_UNIT_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
          </select>
        </>
      )}
    </span>
  );
}

function RepeatBadge({ item }: { item: ShoppingItem }) {
  if (!item.repeat) return null;
  return (
    <span className="text-[10px] bg-[#F0FDF4] text-[#15803D] border border-[#BBF7D0] px-1.5 py-0.5 rounded-full font-medium flex-shrink-0"
      title="Wiederkehrender Artikel">
      ↻ {describeRepeat(item.repeat)}
    </span>
  );
}

function EditRow({ item, onSave, onCancel }: {
  item: ShoppingItem;
  onSave: (input: ShoppingInput) => void;
  onCancel: () => void;
}) {
  const [name, setName] = useState(item.name);
  const [quantity, setQuantity] = useState(item.quantity ?? '');
  const [category, setCategory] = useState<ShoppingCategory>(item.category);
  const [urgent, setUrgent] = useState(item.urgent);
  const [repeat, setRepeat] = useState<RepeatDraft>(draftOf(item.repeat));
  // Zutaten aus dem Essensplan setzt der Plan selbst wieder auf die Liste
  const fromMealPlan = item.category === 'zutaten_essensplanung';
  const hint = fromMealPlan ? null : repeatHint(repeat);
  return (
    <div className="flex flex-wrap items-center gap-2 py-2 px-3 rounded-xl bg-slate-50">
      <input className={`${INPUT} flex-1 min-w-[140px] py-1.5`} value={name} onChange={e => setName(e.target.value)} aria-label="Artikel" autoFocus />
      <input className={`${INPUT} w-24 py-1.5`} value={quantity} onChange={e => setQuantity(e.target.value)} aria-label="Menge" placeholder="Menge" />
      <select className={`${INPUT} py-1.5`} value={category} onChange={e => setCategory(e.target.value as ShoppingCategory)} aria-label="Kategorie">
        {/* Artikel aus dem Essensplan behalten ihre Kategorie */}
        {(item.category === 'zutaten_essensplanung' ? SHOPPING_CATEGORY_KEYS : SELECTABLE_SHOPPING_CATEGORIES)
          .map(c => <option key={c} value={c}>{SHOPPING_CATEGORIES[c].label}</option>)}
      </select>
      <label className="flex items-center gap-1 text-xs text-slate-600">
        <input type="checkbox" className="accent-[#EF4444]" checked={urgent} onChange={e => setUrgent(e.target.checked)} /> Dringend
      </label>
      {!fromMealPlan && <RepeatPicker draft={repeat} onChange={setRepeat} small />}
      <button type="button" onClick={onCancel} className="text-xs text-slate-500 hover:underline">Abbrechen</button>
      <button type="button" disabled={!name.trim() || !isValidDraft(repeat)}
        onClick={() => onSave({ name: name.trim(), quantity: quantity.trim() || null, category, urgent, repeat: fromMealPlan ? null : repeatOf(repeat) })}
        className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] disabled:opacity-50">
        Speichern
      </button>
      {hint && <p className="basis-full text-xs text-slate-500">{hint}</p>}
    </div>
  );
}

function ItemRow({ item, busy, onToggle, onEdit, onRemove }: {
  item: ShoppingItem;
  busy: boolean;
  onToggle?: () => void;
  onEdit?: () => void;
  onRemove?: () => void;
}) {
  const { memberById } = useCalendarData();
  const member = memberById(item.createdBy);
  const proposal = item.status === 'proposed';
  return (
    <div data-item={item.name}
      className={`flex items-center gap-3 py-2 px-3 rounded-xl group transition-colors ${item.checked ? 'bg-slate-50' : 'hover:bg-slate-50'}`}>
      {onToggle ? (
        <button
          onClick={onToggle}
          disabled={busy}
          className={`w-5 h-5 rounded-md flex-shrink-0 border-2 flex items-center justify-center transition-all disabled:opacity-50 ${
            item.checked ? 'bg-[#22C55E] border-[#22C55E]' : 'border-slate-300 hover:border-[#22C55E]'
          }`}
          aria-label={item.checked ? `${item.name} nicht gekauft` : `${item.name} gekauft`}
          aria-pressed={item.checked}
        >
          {item.checked && <CheckIcon size={12} className="text-white" strokeWidth={3} />}
        </button>
      ) : (
        <span className={`w-5 h-5 rounded-md flex-shrink-0 border-2 flex items-center justify-center ${item.checked ? 'bg-[#22C55E] border-[#22C55E]' : 'border-slate-200'}`}>
          {item.checked && <CheckIcon size={12} className="text-white" strokeWidth={3} />}
        </span>
      )}

      <span className={`flex-1 text-sm font-medium ${item.checked ? 'line-through text-slate-400' : 'text-slate-800'}`}>
        {item.name}
      </span>

      <RepeatBadge item={item} />
      {proposal && (
        <span className="text-[10px] bg-[#FFFBEB] text-[#92400E] border border-[#FDE68A] px-1.5 py-0.5 rounded-full font-medium flex-shrink-0">⏳ Vorschlag</span>
      )}
      {item.urgent && !item.checked && (
        <span className="text-[10px] bg-[#FEF2F2] text-[#EF4444] px-1.5 py-0.5 rounded-full font-medium flex-shrink-0">Dringend</span>
      )}
      {item.quantity && <span className="text-xs text-slate-400 flex-shrink-0">{item.quantity}</span>}

      <div className="w-4 h-4 rounded-full flex-shrink-0" style={{ backgroundColor: member?.color || '#94A3B8' }}
        title={`Hinzugefügt von ${member?.name ?? 'unbekannt'}`} />

      {onEdit && (
        <button onClick={onEdit} className="text-slate-300 hover:text-[#2563EB] p-0.5 flex-shrink-0" aria-label={`${item.name} bearbeiten`}>
          <PencilIcon size={13} />
        </button>
      )}
      {onRemove && (
        <button onClick={onRemove} disabled={busy} className="text-slate-300 hover:text-[#EF4444] transition-all p-0.5 flex-shrink-0 disabled:opacity-50"
          aria-label={proposal ? `Vorschlag ${item.name} zurückziehen` : `${item.name} entfernen`}>
          <XIcon size={14} />
        </button>
      )}
    </div>
  );
}

export default function Shopping({ onNavigate }: Props) {
  const me = useMe();
  const perms = useShoppingPermissions();
  const { memberById } = useCalendarData();
  const { items, status, error, reload, add, update, setChecked, remove, removeChecked, approve, reject } = useShoppingData();
  const [newItem, setNewItem] = useState('');
  const [newQuantity, setNewQuantity] = useState('');
  const [newCategory, setNewCategory] = useState<ShoppingCategory>('vorrat');
  const [newUrgent, setNewUrgent] = useState(false);
  const [newRepeat, setNewRepeat] = useState<RepeatDraft>(ONCE);
  const [editing, setEditing] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);

  const run = async <R,>(action: () => Promise<R>, success?: string | ((result: R) => string)) => {
    setBusy(true);
    setMessage(null);
    try {
      const result = await action();
      if (success) setMessage({ text: typeof success === 'function' ? success(result) : success, ok: true });
      return true;
    } catch (err) {
      setMessage({ text: errorText(err), ok: false });
      return false;
    } finally {
      setBusy(false);
    }
  };

  const onlyProposes = !perms.mayAdd && perms.mayPropose;
  // Wiederkehrende Artikel legt an, wer direkt auf die Liste schreibt (Kinder schlagen nur einzelne vor)
  const repeat = perms.mayAdd ? newRepeat : ONCE;
  const newHint = repeatHint(repeat);
  const addItem = async () => {
    if (!newItem.trim() || !isValidDraft(repeat)) return;
    const ok = await run(() => add({ name: newItem.trim(), quantity: newQuantity.trim() || null, category: newCategory, urgent: newUrgent, repeat: repeatOf(repeat) }),
      onlyProposes ? `„${newItem.trim()}“ vorgeschlagen. Deine Eltern übernehmen ihn auf die Liste.` : undefined);
    if (ok) { setNewItem(''); setNewQuantity(''); setNewUrgent(false); setNewRepeat(ONCE); }
  };

  // Offene Vorschläge: Eltern entscheiden oben, alle anderen sehen ihre eigenen in der Liste.
  // Gekaufte wiederkehrende Artikel, die schon entfernt wurden, stehen unten unter „Kommt wieder“.
  const proposals = items.filter(i => i.status === 'proposed');
  const waiting = items.filter(i => i.status === 'approved' && isWaiting(i))
    .sort((a, b) => (a.returnsOn ?? '').localeCompare(b.returnsOn ?? ''));
  const onList = items.filter(i => !isWaiting(i));
  const listed = perms.mayDecide ? onList.filter(i => i.status === 'approved') : onList;
  const approved = onList.filter(i => i.status === 'approved');
  const checkedCount = approved.filter(i => i.checked).length;
  const checkedRepeating = approved.filter(i => i.checked && i.repeat).length;
  const urgentItems = approved.filter(i => i.urgent && !i.checked);
  const categories = SHOPPING_CATEGORY_KEYS.filter(c => listed.some(i => i.category === c));
  const percent = approved.length ? Math.round((checkedCount / approved.length) * 100) : 0;

  const rowProps = (item: ShoppingItem) => {
    const ownProposal = item.status === 'proposed' && item.createdBy === me.id;
    return {
      item,
      busy,
      onToggle: perms.mayEdit && item.status === 'approved' ? () => run(() => setChecked(item.id, !item.checked)) : undefined,
      onEdit: perms.mayEdit ? () => setEditing(item.id) : undefined,
      onRemove: perms.mayDelete || ownProposal ? () => run(() => remove(item.id)) : undefined,
    };
  };

  const renderRow = (item: ShoppingItem) => editing === item.id ? (
    <EditRow key={item.id} item={item} onCancel={() => setEditing(null)}
      onSave={input => run(() => update(item.id, input)).then(ok => ok && setEditing(null))} />
  ) : <ItemRow key={item.id} {...rowProps(item)} />;

  if (!perms.mayView) {
    return (
      <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-10 text-center text-slate-400">
          <div className="text-4xl mb-3">🛒</div>
          Die Einkaufsliste ist für dich nicht freigegeben.
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
      {/* Header stats */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex items-center gap-2 bg-white rounded-xl border border-slate-100 px-4 py-2.5 shadow-sm">
          <ShoppingCartIcon size={16} className="text-[#22C55E]" />
          <span className="text-sm font-semibold text-slate-800">{approved.length - checkedCount} offen</span>
          <span className="text-slate-300">/</span>
          <span className="text-xs text-slate-400">{approved.length} gesamt</span>
        </div>

        {urgentItems.length > 0 && (
          <div className="flex items-center gap-2 bg-[#FEF2F2] rounded-xl px-4 py-2.5">
            <AlertTriangleIcon size={14} className="text-[#EF4444]" />
            <span className="text-sm font-semibold text-[#DC2626]">{urgentItems.length} dringend</span>
          </div>
        )}

        <div className="ml-auto flex items-center gap-3">
          <div className="w-32 h-2 bg-slate-100 rounded-full overflow-hidden">
            <div className="h-full bg-[#22C55E] rounded-full transition-all" style={{ width: `${percent}%` }} />
          </div>
          <span className="text-xs text-slate-500">{percent}%</span>
        </div>

        {perms.mayDelete && checkedCount > 0 && (
          confirmClear ? (
            <span className="flex items-center gap-2">
              <button onClick={() => { setConfirmClear(false); run(removeChecked, removedText); }}
                className="text-xs font-semibold px-2.5 py-1.5 rounded-lg bg-[#EF4444] text-white hover:bg-[#DC2626]">
                Wirklich {checkedCount} entfernen?
              </button>
              {checkedRepeating > 0 && (
                <span className="text-xs text-slate-500">↻ {checkedRepeating} wiederkehrende {checkedRepeating === 1 ? 'bleibt' : 'bleiben'}</span>
              )}
              <button onClick={() => setConfirmClear(false)} className="text-xs text-slate-500 hover:underline">Abbrechen</button>
            </span>
          ) : (
            <button onClick={() => setConfirmClear(true)} className="text-xs text-slate-500 hover:text-[#EF4444] font-medium transition-colors">
              Abgehakte entfernen
            </button>
          )
        )}
      </div>

      {status === 'error' && (
        <div className="mb-5 bg-[#FEF2F2] border border-[#FECACA] rounded-xl p-3 flex items-center gap-3">
          <span className="text-sm text-[#DC2626] flex-1">{error}</span>
          <button onClick={reload} className="text-sm font-semibold text-[#DC2626] hover:underline">Erneut versuchen</button>
        </div>
      )}
      {message && (
        <div role={message.ok ? 'status' : 'alert'}
          className={`mb-5 text-sm rounded-xl p-3 border ${message.ok ? 'bg-[#F0FDF4] border-[#BBF7D0] text-[#15803D]' : 'bg-[#FEF2F2] border-[#FECACA] text-[#DC2626]'}`}>
          {message.text}
        </div>
      )}

      {/* Vorschläge der Kinder (Eltern entscheiden) */}
      {perms.mayDecide && proposals.length > 0 && (
        <div className="mb-5 bg-[#FFFBEB] rounded-2xl border border-[#FDE68A] p-4" aria-labelledby="proposals-title">
          <h3 id="proposals-title" className="font-semibold text-sm text-[#92400E] mb-2">💡 Vorschläge der Kinder</h3>
          <div className="space-y-2">
            {proposals.map(p => (
              <div key={p.id} data-proposal={p.name} className="flex flex-wrap items-center gap-3 bg-white rounded-xl px-3 py-2">
                <span className="text-lg">{SHOPPING_CATEGORIES[p.category].icon}</span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-slate-800">{p.name}{p.quantity && <span className="text-slate-400 font-normal"> · {p.quantity}</span>}</div>
                  <div className="text-xs text-slate-400">von {memberById(p.createdBy)?.name ?? 'unbekannt'}</div>
                </div>
                <button disabled={busy} onClick={() => run(() => reject(p.id), `„${p.name}“ abgelehnt.`)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                  Ablehnen
                </button>
                <button disabled={busy} onClick={() => run(() => approve(p.id), `„${p.name}“ steht jetzt auf der Liste.`)}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] disabled:opacity-50">
                  Übernehmen
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add new item */}
      {(perms.mayAdd || perms.mayPropose) && (
        <div className="mb-5">
          <div className="flex flex-wrap gap-2">
            <input
              className={`${INPUT} flex-1 min-w-[180px]`}
              placeholder={onlyProposes ? 'Artikel vorschlagen…' : 'Artikel hinzufügen…'}
              aria-label="Neuer Artikel"
              maxLength={80}
              value={newItem}
              onChange={e => setNewItem(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && addItem()}
            />
            <input className={`${INPUT} w-28`} placeholder="Menge" aria-label="Menge" maxLength={30}
              value={newQuantity} onChange={e => setNewQuantity(e.target.value)} onKeyDown={e => e.key === 'Enter' && addItem()} />
            <select className={INPUT} value={newCategory} aria-label="Kategorie" onChange={e => setNewCategory(e.target.value as ShoppingCategory)}>
              {SELECTABLE_SHOPPING_CATEGORIES.map(c => <option key={c} value={c}>{SHOPPING_CATEGORIES[c].icon} {SHOPPING_CATEGORIES[c].label}</option>)}
            </select>
            <label className="flex items-center gap-1.5 text-xs text-slate-600 px-1">
              <input type="checkbox" className="w-4 h-4 accent-[#EF4444]" checked={newUrgent} onChange={e => setNewUrgent(e.target.checked)} />
              Dringend
            </label>
            {perms.mayAdd && <RepeatPicker draft={newRepeat} onChange={setNewRepeat} />}
            <button
              onClick={addItem}
              disabled={busy || !newItem.trim() || !isValidDraft(repeat)}
              className="h-11 px-4 flex-shrink-0 bg-[#22C55E] text-white rounded-xl flex items-center justify-center gap-1.5 text-sm font-semibold hover:bg-[#16A34A] transition-colors shadow-sm disabled:opacity-50"
            >
              <PlusIcon size={16} /> {onlyProposes ? 'Vorschlagen' : 'Hinzufügen'}
            </button>
          </div>
          {onlyProposes && (
            <p className="text-xs text-slate-400 mt-1.5">Deine Vorschläge sehen die Eltern und übernehmen sie auf die Liste.</p>
          )}
          {newHint && <p className="text-xs text-slate-500 mt-1.5">{newHint}</p>}
        </div>
      )}

      {status === 'loading' && <div className="text-sm text-slate-400 mb-4">Einkaufsliste wird geladen…</div>}
      {status === 'ready' && listed.length === 0 && (
        <div className="bg-white rounded-2xl border border-slate-100 p-10 text-center text-slate-400 text-sm">Die Einkaufsliste ist leer. 🎉</div>
      )}

      <div className="space-y-3">
        {urgentItems.length > 0 && (
          <div className="bg-[#FEF2F2] rounded-2xl border border-[#FECACA] overflow-hidden">
            <div className="flex items-center gap-3 px-5 py-4">
              <AlertTriangleIcon size={18} className="text-[#EF4444]" />
              <span className="font-semibold text-[#DC2626] text-sm">Dringend</span>
            </div>
            <div className="px-5 pb-4 space-y-1">
              {urgentItems.map(item => (
                <div key={item.id} className="flex items-center gap-3 py-1.5 px-3 text-sm">
                  <span className="flex-1 font-semibold text-[#DC2626]">{item.name}</span>
                  <span className="text-xs text-[#DC2626]/60">{item.quantity}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {categories.map(cat => {
          const catItems = listed.filter(i => i.category === cat);
          const done = catItems.filter(i => i.checked).length;
          return (
            <div key={cat} className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
              <div className="w-full flex items-center gap-3 px-5 py-4">
                <span className="text-xl">{SHOPPING_CATEGORIES[cat].icon}</span>
                <span className="font-semibold text-slate-800 text-sm flex-1 text-left">{SHOPPING_CATEGORIES[cat].label}</span>
                <span className="text-xs text-slate-400">{done}/{catItems.length}</span>
                {done === catItems.length && (
                  <span className="w-5 h-5 rounded-full bg-[#22C55E] flex items-center justify-center">
                    <CheckIcon size={11} className="text-white" strokeWidth={3} />
                  </span>
                )}
              </div>
              <div className="px-5 pb-4 space-y-1">{catItems.map(renderRow)}</div>
            </div>
          );
        })}

        {/* Gekaufte wiederkehrende Artikel: kommen am angegebenen Tag von selbst offen zurück */}
        {waiting.length > 0 && (
          <section className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden" aria-labelledby="waiting-title">
            <div className="flex items-center gap-3 px-5 py-4">
              <span className="text-xl text-[#15803D]" aria-hidden="true">↻</span>
              <h3 id="waiting-title" className="font-semibold text-slate-800 text-sm flex-1">Kommt wieder</h3>
              <span className="text-xs text-slate-400">{waiting.length}</span>
            </div>
            <div className="px-5 pb-4 space-y-1">
              {waiting.map(item => editing === item.id ? (
                <EditRow key={item.id} item={item} onCancel={() => setEditing(null)}
                  onSave={input => run(() => update(item.id, input)).then(ok => ok && setEditing(null))} />
              ) : (
                <div key={item.id} data-waiting={item.name} className="flex flex-wrap items-center gap-3 py-2 px-3 rounded-xl hover:bg-slate-50">
                  <span className="text-lg" aria-hidden="true">{SHOPPING_CATEGORIES[item.category].icon}</span>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium text-slate-700">
                      {item.name}{item.quantity && <span className="text-slate-400 font-normal"> · {item.quantity}</span>}
                    </div>
                    <div className="text-xs text-slate-400">
                      {item.repeat && <>↻ {describeRepeat(item.repeat)} · </>}kommt {returnDay(item.returnsOn ?? '')} wieder
                    </div>
                  </div>
                  {perms.mayEdit && (
                    <button disabled={busy} onClick={() => run(() => setChecked(item.id, false), `„${item.name}“ steht wieder auf der Liste.`)}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                      Jetzt auf die Liste
                    </button>
                  )}
                  {perms.mayEdit && (
                    <button onClick={() => setEditing(item.id)} className="text-slate-300 hover:text-[#2563EB] p-0.5 flex-shrink-0" aria-label={`${item.name} bearbeiten`}>
                      <PencilIcon size={13} />
                    </button>
                  )}
                  {perms.mayDelete && (
                    <button disabled={busy} onClick={() => run(() => remove(item.id), `„${item.name}“ kommt nicht mehr wieder.`)}
                      className="text-slate-300 hover:text-[#EF4444] p-0.5 flex-shrink-0 disabled:opacity-50"
                      aria-label={`${item.name} nicht mehr kaufen`} title="Nicht mehr kaufen">
                      <XIcon size={14} />
                    </button>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}
