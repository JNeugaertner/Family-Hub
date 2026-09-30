import { useEffect, useState } from 'react';
import { PlusIcon, ShoppingCartIcon, ChevronLeftIcon, ChevronRightIcon, XIcon, PencilIcon, BookOpenIcon } from './Icons';
import { ApiError } from '../api/client';
import { useAuth, useMe } from '../auth/AuthContext';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { addDays, fromDateKey, mondayOf, startOfToday, toDateKey } from '../calendar/dates';
import type { Dish, Ingredient, MealEntry, MealType, ShoppingTransfer } from '../meals/api';
import { useMealData } from '../meals/MealDataContext';
import { MEAL_STYLES, MEAL_TYPES } from '../meals/mealTypes';
import type { ShoppingCategory } from '../shopping/api';
import { SHOPPING_CATEGORIES, SHOPPING_CATEGORY_KEYS } from '../shopping/categories';

interface Props { onNavigate: (p: any) => void; }

const WEEKDAYS = ['Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag'];
const WEEKDAYS_SHORT = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

const INPUT = 'border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#14B8A6] focus:ring-2 focus:ring-[#14B8A6]/20 bg-white';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));

// Rechte wie im Backend (MealAccess): Eltern und Jugendliche planen, Kinder wünschen sich etwas, Eltern entscheiden.
function useMealPermissions() {
  const { can } = useAuth();
  return {
    mayView: can('essen', 'ansehen', 'familie'),
    mayEdit: can('essen', 'bearbeiten', 'familie'),
    mayWish: can('essen', 'vorschlagen', 'familie'),
    mayDecide: can('essen', 'freigeben', 'familie'),
    mayShop: can('einkauf', 'erstellen', 'familie'),
  };
}

// Tag im Wochenplan, z. B. "Mittwoch, 7.10."
const dayLabel = (key: string) => {
  const d = fromDateKey(key);
  return `${WEEKDAYS[(d.getDay() + 6) % 7]}, ${d.getDate()}.${d.getMonth() + 1}.`;
};

function isoWeek(date: Date): number {
  const thursday = addDays(date, 3 - ((date.getDay() + 6) % 7));
  const firstThursday = addDays(new Date(thursday.getFullYear(), 0, 4), 3 - ((new Date(thursday.getFullYear(), 0, 4).getDay() + 6) % 7));
  return 1 + Math.round((thursday.getTime() - firstThursday.getTime()) / (7 * 864e5));
}

function transferText(t: ShoppingTransfer): string {
  if (t.added.length === 0 && t.skipped.length === 0) {
    return 'Keine Zutaten übernommen: Hier stehen keine Gerichte aus der Sammlung.';
  }
  const parts: string[] = [];
  if (t.added.length > 0) {
    parts.push(`${t.added.length} ${t.added.length === 1 ? 'Zutat' : 'Zutaten'} auf die Einkaufsliste gesetzt: ${t.added.join(', ')}.`);
  }
  if (t.skipped.length > 0) parts.push(`Stand schon auf der Liste: ${t.skipped.join(', ')}.`);
  return parts.join(' ');
}

function IngredientChips({ ingredients }: { ingredients: Ingredient[] }) {
  if (ingredients.length === 0) return <p className="text-xs text-slate-400">Keine Zutaten hinterlegt.</p>;
  return (
    <div className="flex flex-wrap gap-1.5">
      {ingredients.map((ing, i) => (
        <span key={i} className="text-[11px] bg-slate-50 border border-slate-100 text-slate-600 px-2 py-0.5 rounded-lg">
          {SHOPPING_CATEGORIES[ing.category].icon} {ing.name}{ing.quantity && <span className="text-slate-400"> · {ing.quantity}</span>}
        </span>
      ))}
    </div>
  );
}

// Kochanleitung: ein Schritt pro Zeile. Eingefügte Nummerierung ("1.", "-") fällt weg, die Ansicht nummeriert selbst.
function recipeSteps(instructions?: string | null): string[] {
  return (instructions ?? '').split('\n').map(line => line.replace(/^\s*(\d+[.)]|[-*•])\s*/, '').trim()).filter(Boolean);
}

const formatMinutes = (minutes: number) =>
  minutes < 60 ? `${minutes} Min.` : `${Math.floor(minutes / 60)} Std.${minutes % 60 ? ` ${minutes % 60} Min.` : ''}`;

function RecipeMeta({ dish }: { dish: Dish }) {
  const parts = [
    dish.prepMinutes ? `⏱ ${formatMinutes(dish.prepMinutes)}` : null,
    dish.servings ? `🍽 ${dish.servings} ${dish.servings === 1 ? 'Portion' : 'Portionen'}` : null,
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return <p className="text-[11px] text-slate-500">{parts.join(' · ')}</p>;
}

// Rezept eines Gerichts: Zutaten und nummerierte Schritte, für alle, die den Essensplan sehen (auch zum Mitkochen)
function RecipeView({ dish, onClose }: { dish: Dish; onClose: () => void }) {
  const steps = recipeSteps(dish.instructions);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby="recipe-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-start gap-3 mb-1">
          <h2 id="recipe-title" className="font-bold text-slate-800 text-lg flex-1">📖 {dish.name}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1" aria-label="Rezept schließen">
            <XIcon size={16} />
          </button>
        </div>
        <RecipeMeta dish={dish} />
        <div className="overflow-y-auto min-h-0 flex-1 mt-4 space-y-5">
          <section aria-labelledby="recipe-ingredients">
            <h3 id="recipe-ingredients" className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Zutaten</h3>
            {dish.ingredients.length === 0 ? (
              <p className="text-sm text-slate-400">Keine Zutaten hinterlegt.</p>
            ) : (
              <ul className="space-y-1">
                {dish.ingredients.map((ing, i) => (
                  <li key={i} className="text-sm text-slate-700 flex gap-2">
                    <span>{SHOPPING_CATEGORIES[ing.category].icon}</span>
                    <span className="flex-1">{ing.name}</span>
                    {ing.quantity && <span className="text-slate-400">{ing.quantity}</span>}
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-labelledby="recipe-steps">
            <h3 id="recipe-steps" className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2">Zubereitung</h3>
            {steps.length === 0 ? (
              <p className="text-sm text-slate-400">Noch keine Kochanleitung hinterlegt.</p>
            ) : (
              <ol className="space-y-2.5">
                {steps.map((step, i) => (
                  <li key={i} className="flex gap-3 text-sm text-slate-700 leading-relaxed">
                    <span className="w-6 h-6 rounded-full bg-[#CCFBF1] text-[#0F766E] text-xs font-bold flex items-center justify-center flex-shrink-0">
                      {i + 1}
                    </span>
                    <span className="pt-0.5">{step}</span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>
    </div>
  );
}

// ─── Mahlzeit eintragen oder wünschen ─────────────────────────────────────────

function MealEditor({ date, type, entry, onClose, onDone }: {
  date: string;
  type: MealType;
  entry: MealEntry | undefined;
  onClose: () => void;
  onDone: (text: string) => void;
}) {
  const perms = useMealPermissions();
  const { dishes, addMeal, removeMeal } = useMealData();
  const onlyWishes = !perms.mayEdit && perms.mayWish;
  // Wer plant, sieht den bisherigen Eintrag; wer sich etwas wünscht, fängt leer an.
  const current = onlyWishes ? undefined : entry;
  const [dishId, setDishId] = useState<string | null>(current?.dishId ?? null);
  const [text, setText] = useState(current && !current.dishId ? current.name : '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const style = MEAL_STYLES[type];
  const selected = dishes.find(d => d.id === dishId);
  const query = text.trim().toLowerCase();
  const matches = query ? dishes.filter(d => d.name.toLowerCase().includes(query)) : dishes;

  const run = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      onDone(done);
    } catch (err) {
      setError(errorText(err));
      setBusy(false);
    }
  };

  const save = () => {
    const name = selected?.name ?? text.trim();
    run(() => addMeal({ date, type, dishId: selected ? selected.id : null, name: selected ? null : text.trim() }),
      onlyWishes ? `Wunsch „${name}“ gesendet. Deine Eltern entscheiden.` : `„${name}“ eingetragen.`);
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby="meal-editor-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <h2 id="meal-editor-title" className="font-bold text-slate-800">
          {style.icon} {style.label} · {dayLabel(date)}
        </h2>
        <p className="text-xs text-slate-400 mb-4">
          {onlyWishes ? 'Was wünschst du dir? Deine Eltern entscheiden.' : 'Gericht aus der Sammlung wählen oder frei eintragen.'}
        </p>

        <input className={INPUT} autoFocus maxLength={80} aria-label="Gericht suchen oder frei eintragen"
          placeholder="Gericht suchen oder frei eintragen…" value={selected ? selected.name : text}
          onChange={e => { setDishId(null); setText(e.target.value); }}
          onKeyDown={e => e.key === 'Enter' && (selected || text.trim()) && !busy && save()} />

        <div className="mt-3 overflow-y-auto flex-1 min-h-0 space-y-1" aria-label="Gerichte-Sammlung">
          {!selected && matches.map(d => (
            <button key={d.id} type="button" onClick={() => setDishId(d.id)}
              className="w-full text-left px-3 py-2 rounded-xl hover:bg-slate-50 text-sm text-slate-700 flex items-center gap-2">
              <span className="flex-1">{d.name}</span>
              <span className="text-[10px] text-slate-400">{d.ingredients.length} Zutaten</span>
            </button>
          ))}
          {!selected && query && matches.length === 0 && (
            <p className="text-xs text-slate-400 px-3 py-2">Kein Gericht gefunden, wird als Freitext eingetragen.</p>
          )}
          {selected && (
            <div className="rounded-xl border border-slate-100 p-3">
              <div className="flex items-center gap-2 mb-2">
                <span className="text-sm font-semibold text-slate-800 flex-1">{selected.name}</span>
                <button type="button" onClick={() => setDishId(null)} className="text-xs text-slate-500 hover:underline">Anderes wählen</button>
              </div>
              <IngredientChips ingredients={selected.ingredients} />
            </div>
          )}
        </div>

        {error && <div role="alert" className="mt-3 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{error}</div>}

        <div className="flex gap-3 mt-4">
          {perms.mayEdit && entry && (
            <button type="button" disabled={busy} onClick={() => run(() => removeMeal(entry.id), `„${entry.name}“ entfernt.`)}
              className="py-2.5 px-3 rounded-xl text-[#EF4444] text-sm font-semibold hover:bg-[#FEF2F2] disabled:opacity-50">
              Entfernen
            </button>
          )}
          <button type="button" onClick={onClose}
            className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">
            Abbrechen
          </button>
          <button type="button" disabled={busy || (!selected && !text.trim())} onClick={save}
            className="flex-1 py-2.5 rounded-xl bg-[#14B8A6] text-white text-sm font-semibold hover:bg-[#0D9488] disabled:opacity-50">
            {onlyWishes ? 'Wunsch senden' : 'Speichern'}
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Gericht anlegen oder ändern ──────────────────────────────────────────────

interface IngredientRow { name: string; quantity: string; category: ShoppingCategory }

function DishForm({ dish, onClose, onDone }: { dish: Dish | null; onClose: () => void; onDone: (text: string) => void }) {
  const { addDish, updateDish } = useMealData();
  const [name, setName] = useState(dish?.name ?? '');
  const [rows, setRows] = useState<IngredientRow[]>(
    dish?.ingredients.map(i => ({ name: i.name, quantity: i.quantity ?? '', category: i.category })) ?? [{ name: '', quantity: '', category: 'vorrat' }]);
  const [instructions, setInstructions] = useState(dish?.instructions ?? '');
  const [prepMinutes, setPrepMinutes] = useState(dish?.prepMinutes ? String(dish.prepMinutes) : '');
  const [servings, setServings] = useState(dish?.servings ? String(dish.servings) : '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const setRow = (index: number, change: Partial<IngredientRow>) =>
    setRows(rs => rs.map((r, i) => (i === index ? { ...r, ...change } : r)));

  const save = async () => {
    setBusy(true);
    setError(null);
    const input = {
      name: name.trim(),
      ingredients: rows.filter(r => r.name.trim()).map(r => ({ name: r.name.trim(), quantity: r.quantity.trim() || null, category: r.category })),
      instructions: instructions.trim() || null,
      prepMinutes: prepMinutes ? Number(prepMinutes) : null,
      servings: servings ? Number(servings) : null,
    };
    try {
      if (dish) await updateDish(dish.id, input);
      else await addDish(input);
      onDone(dish ? `„${input.name}“ gespeichert.` : `„${input.name}“ zur Sammlung hinzugefügt.`);
    } catch (err) {
      // Feldfehler des Servers (z. B. Zubereitungszeit) als Text zeigen
      setError(err instanceof ApiError && err.problem.errors ? Object.values(err.problem.errors).join(' ') : errorText(err));
      setBusy(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4" onClick={onClose}>
      <div role="dialog" aria-modal="true" aria-labelledby="dish-form-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <h2 id="dish-form-title" className="font-bold text-slate-800 mb-4">{dish ? 'Gericht bearbeiten' : 'Neues Gericht'}</h2>
        <label htmlFor="dish-name" className="text-xs font-semibold text-slate-600 mb-1.5 block">Name</label>
        <input id="dish-name" className={INPUT} maxLength={60} value={name} onChange={e => setName(e.target.value)} autoFocus />

        <div className="overflow-y-auto flex-1 min-h-0 mt-4 -mx-1 px-1">
        <div className="text-xs font-semibold text-slate-600 mb-1.5">Zutaten</div>
        <div className="space-y-2">
          {rows.map((row, i) => (
            <div key={i} className="flex flex-wrap gap-2 items-center">
              <input className={`${INPUT} flex-1 min-w-[120px] py-1.5`} maxLength={80} placeholder="Zutat" aria-label={`Zutat ${i + 1}`}
                value={row.name} onChange={e => setRow(i, { name: e.target.value })} />
              <input className={`${INPUT} w-24 py-1.5`} maxLength={30} placeholder="Menge" aria-label={`Menge ${i + 1}`}
                value={row.quantity} onChange={e => setRow(i, { quantity: e.target.value })} />
              <select className={`${INPUT} py-1.5`} aria-label={`Kategorie ${i + 1}`} value={row.category}
                onChange={e => setRow(i, { category: e.target.value as ShoppingCategory })}>
                {SHOPPING_CATEGORY_KEYS.map(c => <option key={c} value={c}>{SHOPPING_CATEGORIES[c].icon} {SHOPPING_CATEGORIES[c].label}</option>)}
              </select>
              <button type="button" onClick={() => setRows(rs => rs.filter((_, j) => j !== i))}
                className="text-slate-300 hover:text-[#EF4444] p-1" aria-label={`Zutat ${i + 1} entfernen`}>
                <XIcon size={14} />
              </button>
            </div>
          ))}
          {rows.length < 30 && (
            <button type="button" onClick={() => setRows(rs => [...rs, { name: '', quantity: '', category: 'vorrat' }])}
              className="text-xs font-semibold text-[#0F766E] hover:underline flex items-center gap-1">
              <PlusIcon size={12} /> Zutat hinzufügen
            </button>
          )}
        </div>

        <div className="grid grid-cols-2 gap-3 mt-4">
          <div>
            <label htmlFor="dish-prep" className="text-xs font-semibold text-slate-600 mb-1.5 block">Zubereitungszeit (Min.)</label>
            <input id="dish-prep" type="number" min={1} max={1440} className={`${INPUT} w-full`} placeholder="z. B. 30"
              value={prepMinutes} onChange={e => setPrepMinutes(e.target.value)} />
          </div>
          <div>
            <label htmlFor="dish-servings" className="text-xs font-semibold text-slate-600 mb-1.5 block">Portionen</label>
            <input id="dish-servings" type="number" min={1} max={50} className={`${INPUT} w-full`} placeholder="z. B. 4"
              value={servings} onChange={e => setServings(e.target.value)} />
          </div>
        </div>

        <label htmlFor="dish-instructions" className="text-xs font-semibold text-slate-600 mt-4 mb-1.5 block">
          Kochanleitung <span className="font-normal text-slate-400">(ein Schritt pro Zeile)</span>
        </label>
        <textarea id="dish-instructions" rows={6} maxLength={4000} className={`${INPUT} w-full resize-y`}
          placeholder={'Nudeln in Salzwasser kochen\nHackfleisch anbraten\nTomaten dazugeben und 20 Minuten köcheln lassen'}
          value={instructions} onChange={e => setInstructions(e.target.value)} />
        </div>

        {error && <div role="alert" className="mt-3 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{error}</div>}

        <div className="flex gap-3 mt-4">
          <button type="button" onClick={onClose}
            className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">
            Abbrechen
          </button>
          <button type="button" disabled={busy || !name.trim()} onClick={save}
            className="flex-1 py-2.5 rounded-xl bg-[#14B8A6] text-white text-sm font-semibold hover:bg-[#0D9488] disabled:opacity-50">
            Speichern
          </button>
        </div>
      </div>
    </div>
  );
}

// ─── Gerichte-Sammlung ────────────────────────────────────────────────────────

function DishCollection({ onMessage }: { onMessage: (text: string, ok: boolean) => void }) {
  const perms = useMealPermissions();
  const { dishes, removeDish } = useMealData();
  const [editing, setEditing] = useState<Dish | null | 'new'>(null);
  const [recipe, setRecipe] = useState<Dish | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const remove = async (dish: Dish) => {
    setConfirmDelete(null);
    try {
      await removeDish(dish.id);
      onMessage(`„${dish.name}“ gelöscht.`, true);
    } catch (err) {
      onMessage(errorText(err), false);
    }
  };

  return (
    <div>
      {perms.mayEdit && (
        <button onClick={() => setEditing('new')}
          className="mb-4 flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-[#14B8A6] text-white hover:bg-[#0D9488]">
          <PlusIcon size={15} /> Neues Gericht
        </button>
      )}
      {dishes.length === 0 && (
        <div className="bg-white rounded-2xl border border-slate-100 p-10 text-center text-slate-400 text-sm">Noch keine Gerichte in der Sammlung.</div>
      )}
      <div className="grid gap-3 sm:grid-cols-2">
        {dishes.map(dish => (
          <div key={dish.id} data-dish={dish.name} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
            <div className="flex items-center gap-2 mb-2">
              <span className="font-semibold text-slate-800 text-sm flex-1">{dish.name}</span>
              {perms.mayEdit && (
                <>
                  <button onClick={() => setEditing(dish)} className="text-slate-300 hover:text-[#2563EB] p-0.5" aria-label={`${dish.name} bearbeiten`}>
                    <PencilIcon size={13} />
                  </button>
                  {confirmDelete === dish.id ? (
                    <span className="flex items-center gap-2">
                      <button onClick={() => remove(dish)} className="text-xs font-semibold px-2 py-1 rounded-lg bg-[#EF4444] text-white hover:bg-[#DC2626]">
                        Wirklich löschen?
                      </button>
                      <button onClick={() => setConfirmDelete(null)} className="text-xs text-slate-500 hover:underline">Nein</button>
                    </span>
                  ) : (
                    <button onClick={() => setConfirmDelete(dish.id)} className="text-slate-300 hover:text-[#EF4444] p-0.5" aria-label={`${dish.name} löschen`}>
                      <XIcon size={14} />
                    </button>
                  )}
                </>
              )}
            </div>
            <IngredientChips ingredients={dish.ingredients} />
            <div className="flex items-center gap-2 mt-2.5 min-h-[20px]">
              <RecipeMeta dish={dish} />
              {recipeSteps(dish.instructions).length > 0 && (
                <button onClick={() => setRecipe(dish)} aria-label={`Rezept für ${dish.name} ansehen`}
                  className="ml-auto text-xs font-semibold text-[#0F766E] hover:underline flex items-center gap-1">
                  <BookOpenIcon size={13} /> Rezept
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
      {editing && (
        <DishForm dish={editing === 'new' ? null : editing} onClose={() => setEditing(null)}
          onDone={text => { setEditing(null); onMessage(text, true); }} />
      )}
      {recipe && <RecipeView dish={recipe} onClose={() => setRecipe(null)} />}
    </div>
  );
}

// ─── Seite ────────────────────────────────────────────────────────────────────

export default function MealPlanning({ onNavigate }: Props) {
  const me = useMe();
  const perms = useMealPermissions();
  const { memberById } = useCalendarData();
  const { weekStart, setWeekStart, entries, wishes, dishes, status, error, reload, removeMeal, approve, reject,
    mealToShopping, weekToShopping } = useMealData();
  const [tab, setTab] = useState<'plan' | 'dishes'>('plan');
  const [recipe, setRecipe] = useState<Dish | null>(null);
  const [editing, setEditing] = useState<{ date: string; type: MealType } | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; ok: boolean; shopping?: boolean } | null>(null);

  const onlyWishes = !perms.mayEdit && perms.mayWish;
  const todayKey = toDateKey(startOfToday());
  const days = Array.from({ length: 7 }, (_, i) => toDateKey(addDays(weekStart, i)));
  const thisMonday = mondayOf(startOfToday());
  const weekDiff = Math.round((weekStart.getTime() - thisMonday.getTime()) / (7 * 864e5));
  const weekLabel = weekDiff === 0 ? 'Diese Woche' : weekDiff === 1 ? 'Nächste Woche' : weekDiff === -1 ? 'Letzte Woche' : `KW ${isoWeek(weekStart)}`;
  const sunday = addDays(weekStart, 6);
  const rangeLabel = `${weekStart.getDate()}.${weekStart.getMonth() + 1}. – ${sunday.getDate()}.${sunday.getMonth() + 1}.${sunday.getFullYear()}`;

  const planned = (date: string, type: MealType) => entries.find(e => e.date === date && e.type === type && e.status === 'approved');
  const cellWishes = (date: string, type: MealType) => entries.filter(e => e.date === date && e.type === type && e.status === 'proposed');
  const dishById = new Map(dishes.map(d => [d.id, d]));

  const run = async (action: () => Promise<unknown>, success?: string | ((result: any) => string), shopping = false) => {
    setBusy(true);
    setMessage(null);
    try {
      const result = await action();
      if (success) setMessage({ text: typeof success === 'function' ? success(result) : success, ok: true, shopping });
    } catch (err) {
      setMessage({ text: errorText(err), ok: false });
    } finally {
      setBusy(false);
    }
  };

  const openCell = (date: string, type: MealType) => {
    if (perms.mayEdit || perms.mayWish) setEditing({ date, type });
  };

  if (!perms.mayView) {
    return (
      <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-10 text-center text-slate-400">
          <div className="text-4xl mb-3">🍽️</div>
          Der Essensplan ist für dich nicht freigegeben.
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Kopfzeile */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex bg-white border border-slate-200 rounded-xl p-1 shadow-sm" role="tablist">
          {([['plan', '📅 Wochenplan'], ['dishes', '📖 Gerichte']] as const).map(([id, label]) => (
            <button key={id} role="tab" aria-selected={tab === id} onClick={() => setTab(id)}
              className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-colors ${tab === id ? 'bg-[#14B8A6] text-white' : 'text-slate-600 hover:bg-slate-50'}`}>
              {label}
            </button>
          ))}
        </div>

        {tab === 'plan' && (
          <div className="flex items-center gap-2">
            <button onClick={() => setWeekStart(addDays(weekStart, -7))} aria-label="Vorige Woche"
              className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors">
              <ChevronLeftIcon size={16} />
            </button>
            <div className="min-w-[150px] text-center">
              <div className="font-bold text-slate-800 text-base leading-tight">{weekLabel}</div>
              <div className="text-[11px] text-slate-400">{rangeLabel}</div>
            </div>
            <button onClick={() => setWeekStart(addDays(weekStart, 7))} aria-label="Nächste Woche"
              className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors">
              <ChevronRightIcon size={16} />
            </button>
            {weekDiff !== 0 && (
              <button onClick={() => setWeekStart(thisMonday)} className="text-xs font-semibold text-[#0F766E] hover:underline">Heute</button>
            )}
          </div>
        )}

        {tab === 'plan' && perms.mayShop && (
          <button disabled={busy} onClick={() => run(weekToShopping, transferText, true)}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] transition-colors ml-auto disabled:opacity-50">
            <ShoppingCartIcon size={15} />
            Zutaten der Woche auf die Einkaufsliste
          </button>
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
          className={`mb-5 text-sm rounded-xl p-3 border flex flex-wrap items-center gap-3 ${message.ok ? 'bg-[#F0FDF4] border-[#BBF7D0] text-[#15803D]' : 'bg-[#FEF2F2] border-[#FECACA] text-[#DC2626]'}`}>
          <span className="flex-1">{message.text}</span>
          {message.shopping && (
            <button onClick={() => onNavigate('shopping')} className="text-xs font-semibold hover:underline">Zur Einkaufsliste →</button>
          )}
        </div>
      )}

      {/* Wünsche der Kinder (Eltern entscheiden) */}
      {perms.mayDecide && wishes.length > 0 && (
        <div className="mb-5 bg-[#FFFBEB] rounded-2xl border border-[#FDE68A] p-4" aria-labelledby="wishes-title">
          <h3 id="wishes-title" className="font-semibold text-sm text-[#92400E] mb-2">💡 Essenswünsche der Kinder</h3>
          <div className="space-y-2">
            {wishes.map(w => {
              const current = entries.find(e => e.date === w.date && e.type === w.type && e.status === 'approved');
              return (
                <div key={w.id} data-wish={w.name} className="flex flex-wrap items-center gap-3 bg-white rounded-xl px-3 py-2">
                  <span className="text-lg">{MEAL_STYLES[w.type].icon}</span>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-slate-800">{w.name}</div>
                    <div className="text-xs text-slate-400">
                      {MEAL_STYLES[w.type].label} · {dayLabel(w.date)} · von {memberById(w.createdBy)?.name ?? 'unbekannt'}
                      {current && <> · ersetzt „{current.name}“</>}
                    </div>
                  </div>
                  <button disabled={busy} onClick={() => run(() => reject(w.id), `Wunsch „${w.name}“ abgelehnt.`)}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                    Ablehnen
                  </button>
                  <button disabled={busy} onClick={() => run(() => approve(w.id), `„${w.name}“ steht jetzt im Plan.`)}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] disabled:opacity-50">
                    Übernehmen
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {tab === 'dishes' && <DishCollection onMessage={(text, ok) => setMessage({ text, ok })} />}
      {recipe && <RecipeView dish={recipe} onClose={() => setRecipe(null)} />}

      {tab === 'plan' && (
        <>
          {status === 'loading' && <div className="text-sm text-slate-400 mb-4">Essensplan wird geladen…</div>}
          {onlyWishes && (
            <p className="text-xs text-slate-400 mb-3">Tippe auf eine Mahlzeit, um dir etwas zu wünschen. Deine Eltern entscheiden.</p>
          )}
          <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-x-auto">
            <div className="min-w-[900px]">
              {/* Tage */}
              <div className="grid grid-cols-8 border-b border-slate-100">
                <div className="px-3 py-3 text-[11px] font-semibold text-slate-400 uppercase tracking-wide border-r border-slate-100">Mahlzeit</div>
                {days.map((day, i) => {
                  const isToday = day === todayKey;
                  const date = fromDateKey(day);
                  return (
                    <div key={day} className={`px-2 py-3 text-center border-r border-slate-50 last:border-r-0 ${isToday ? 'bg-[#EFF6FF]' : ''}`}>
                      <div className={`text-xs font-bold ${isToday ? 'text-[#2563EB]' : 'text-slate-600'}`}>{WEEKDAYS_SHORT[i]} {date.getDate()}.{date.getMonth() + 1}.</div>
                      {isToday && <div className="text-[10px] text-[#2563EB] font-medium">Heute</div>}
                    </div>
                  );
                })}
              </div>

              {/* Mahlzeiten */}
              {MEAL_TYPES.map(type => {
                const style = MEAL_STYLES[type];
                return (
                  <div key={type} className="grid grid-cols-8 border-b border-slate-50 last:border-b-0">
                    <div className="flex items-center gap-2 px-3 py-3 border-r border-slate-100" style={{ backgroundColor: style.bg }}>
                      <span className="text-sm">{style.icon}</span>
                      <span className="text-xs font-semibold" style={{ color: style.text }}>{style.label}</span>
                    </div>
                    {days.map(day => {
                      const entry = planned(day, type);
                      const dish = entry?.dishId ? dishById.get(entry.dishId) : undefined;
                      const hasRecipe = !!dish && recipeSteps(dish.instructions).length > 0;
                      const hasCart = perms.mayShop && !!dish;
                      const wishesHere = cellWishes(day, type);
                      const canOpen = perms.mayEdit || perms.mayWish;
                      return (
                        <div key={day} data-cell={`${day} ${type}`}
                          className={`p-2 border-r border-slate-50 last:border-r-0 space-y-1.5 ${day === todayKey ? 'bg-[#EFF6FF]/30' : ''}`}>
                          {entry ? (
                            <div
                              className={`group relative min-h-[56px] rounded-xl p-2.5 transition-all ${canOpen ? 'cursor-pointer hover:shadow-md' : ''}`}
                              style={{ backgroundColor: style.bg, border: `1px solid ${style.border}` }}
                              onClick={() => openCell(day, type)}
                              role={canOpen ? 'button' : undefined}
                              aria-label={canOpen ? `${style.label} am ${dayLabel(day)}: ${entry.name}${onlyWishes ? ', etwas anderes wünschen' : ', ändern'}` : undefined}
                            >
                              <p className="text-xs font-medium leading-snug break-words" style={{ color: style.text }}>{entry.name}</p>
                              {(hasRecipe || hasCart) && (
                                <div className="flex justify-end gap-1 mt-1.5">
                                  {hasRecipe && dish && (
                                    <button
                                      onClick={e => { e.stopPropagation(); setRecipe(dish); }}
                                      className="w-6 h-6 rounded-md bg-white/80 text-slate-400 hover:text-[#0F766E] flex items-center justify-center"
                                      aria-label={`Rezept für ${entry.name}`}
                                      title="Rezept ansehen"
                                    >
                                      <BookOpenIcon size={12} />
                                    </button>
                                  )}
                                  {hasCart && (
                                    <button
                                      onClick={e => { e.stopPropagation(); run(() => mealToShopping(entry.id), transferText, true); }}
                                      disabled={busy}
                                      className="w-6 h-6 rounded-md bg-white/80 text-slate-400 hover:text-[#16A34A] flex items-center justify-center disabled:opacity-50"
                                      aria-label={`Zutaten für ${entry.name} auf die Einkaufsliste`}
                                      title="Zutaten auf die Einkaufsliste"
                                    >
                                      <ShoppingCartIcon size={12} />
                                    </button>
                                  )}
                                </div>
                              )}
                            </div>
                          ) : canOpen ? (
                            <button onClick={() => openCell(day, type)}
                              className="w-full min-h-[56px] rounded-xl border border-dashed border-slate-200 text-slate-300 hover:text-[#14B8A6] hover:border-[#14B8A6] flex items-center justify-center transition-colors"
                              aria-label={`${style.label} am ${dayLabel(day)} ${onlyWishes ? 'wünschen' : 'eintragen'}`}>
                              <PlusIcon size={14} />
                            </button>
                          ) : (
                            <div className="min-h-[56px] rounded-xl flex items-center justify-center text-slate-200 text-xs">—</div>
                          )}
                          {wishesHere.map(w => {
                            const own = w.createdBy === me.id;
                            return (
                              <div key={w.id} data-wish-cell={w.name}
                                className="rounded-lg border border-dashed border-[#FCD34D] bg-[#FFFBEB] px-2 py-1 text-[11px] text-[#92400E] flex items-start gap-1">
                                <span className="flex-1">⏳ {own ? 'Dein Wunsch' : `Wunsch von ${memberById(w.createdBy)?.name ?? 'unbekannt'}`}: {w.name}</span>
                                {own && (
                                  <button disabled={busy} onClick={() => run(() => removeMeal(w.id), `Wunsch „${w.name}“ zurückgezogen.`)}
                                    className="text-[#B45309] hover:text-[#EF4444] disabled:opacity-50" aria-label={`Wunsch ${w.name} zurückziehen`}>
                                    <XIcon size={11} />
                                  </button>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          </div>
        </>
      )}

      {editing && (
        <MealEditor date={editing.date} type={editing.type} entry={planned(editing.date, editing.type)}
          onClose={() => setEditing(null)}
          onDone={text => { setEditing(null); setMessage({ text, ok: true }); }} />
      )}
    </div>
  );
}
