import { useState } from 'react';
import { MEALS } from './data';
import { PlusIcon, ShoppingCartIcon, SparklesIcon, ChevronLeftIcon, ChevronRightIcon, CheckIcon, XIcon } from './Icons';
import { useAuth, useMe } from '../auth/AuthContext';
import { Suggestion, createSuggestion, decide } from '../roles';

interface Props { onNavigate: (p: any) => void; }

type MealType = 'breakfast' | 'lunch' | 'dinner' | 'snacks';

const MEAL_COLORS: Record<MealType, { bg: string; text: string; border: string; icon: string }> = {
  breakfast: { bg: '#FFF7ED', text: '#C2410C', border: '#FED7AA', icon: '🌅' },
  lunch: { bg: '#F0FDFA', text: '#0F766E', border: '#99F6E4', icon: '☀️' },
  dinner: { bg: '#EFF6FF', text: '#1D4ED8', border: '#BFDBFE', icon: '🌙' },
  snacks: { bg: '#FAF5FF', text: '#6D28D9', border: '#DDD6FE', icon: '🍎' },
};

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;
const MEAL_TYPES: MealType[] = ['breakfast', 'lunch', 'dinner', 'snacks'];

const RECIPE_SUGGESTIONS: Record<string, { name: string; time: string; difficulty: string; missing?: string[] }[]> = {
  breakfast: [
    { name: 'Shakshuka', time: '20 min', difficulty: 'Easy', missing: ['Feta cheese'] },
    { name: 'Smoothie Bowl', time: '10 min', difficulty: 'Easy' },
    { name: 'Eggs Benedict', time: '30 min', difficulty: 'Medium', missing: ['English muffins', 'Ham'] },
  ],
  lunch: [
    { name: 'Buddha Bowl', time: '25 min', difficulty: 'Easy', missing: ['Chickpeas'] },
    { name: 'Club Sandwich', time: '15 min', difficulty: 'Easy' },
    { name: 'Pho Soup', time: '45 min', difficulty: 'Medium', missing: ['Rice noodles', 'Bean sprouts'] },
  ],
  dinner: [
    { name: 'Butter Chicken', time: '40 min', difficulty: 'Medium', missing: ['Heavy cream', 'Garam masala'] },
    { name: 'Pasta Primavera', time: '30 min', difficulty: 'Easy' },
    { name: 'Fish Tacos', time: '25 min', difficulty: 'Easy', missing: ['Corn tortillas', 'Lime'] },
  ],
  snacks: [
    { name: 'Trail Mix', time: '5 min', difficulty: 'Easy' },
    { name: 'Cheese & Crackers', time: '5 min', difficulty: 'Easy' },
    { name: 'Fruit Kabobs', time: '10 min', difficulty: 'Easy', missing: ['Melon'] },
  ],
};

function MealCell({ meal, day, type, onEdit }: { meal: string; day: string; type: MealType; onEdit: () => void }) {
  const style = MEAL_COLORS[type];
  return (
    <div
      className="group relative min-h-[60px] rounded-xl p-2.5 cursor-pointer transition-all hover:shadow-md"
      style={{ backgroundColor: style.bg, border: `1px solid ${style.border}` }}
      onClick={onEdit}
      role="button"
      aria-label={`Edit ${type} for ${day}`}
    >
      <p className="text-xs font-medium leading-snug" style={{ color: style.text }}>
        {meal}
      </p>
      <button
        className="absolute top-1.5 right-1.5 w-5 h-5 rounded-md bg-white/70 text-slate-400 hover:text-[#2563EB] opacity-0 group-hover:opacity-100 transition-all flex items-center justify-center"
        aria-label="Edit meal"
      >
        ✏️
      </button>
    </div>
  );
}

function RecipeSuggestionPanel({ type, onUse }: { type: MealType; onUse: (name: string) => void }) {
  const suggestions = RECIPE_SUGGESTIONS[type] || [];
  const style = MEAL_COLORS[type];

  return (
    <div className="space-y-2">
      {suggestions.map((r, i) => (
        <div
          key={i}
          className="flex items-start gap-3 p-3.5 rounded-xl bg-white border border-slate-100 hover:border-slate-200 transition-all cursor-pointer group"
        >
          <div className="flex-1 min-w-0">
            <div className="font-semibold text-slate-800 text-sm">{r.name}</div>
            <div className="flex items-center gap-2 mt-1 flex-wrap">
              <span className="text-xs text-slate-400">⏱ {r.time}</span>
              <span
                className="text-[10px] px-1.5 py-0.5 rounded-full font-medium"
                style={{ backgroundColor: style.bg, color: style.text }}
              >
                {r.difficulty}
              </span>
              {r.missing && r.missing.length > 0 && (
                <span className="text-[10px] text-[#F97316] font-medium">
                  Missing: {r.missing.join(', ')}
                </span>
              )}
            </div>
          </div>
          <div className="flex gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity">
            <button
              onClick={() => onUse(r.name)}
              className="text-xs bg-[#EFF6FF] text-[#2563EB] px-2 py-1 rounded-lg font-medium hover:bg-[#DBEAFE] transition-colors flex-shrink-0"
            >
              Use
            </button>
            {r.missing && (
              <button className="text-xs bg-[#FFF7ED] text-[#F97316] px-2 py-1 rounded-lg font-medium hover:bg-[#FFEDD5] transition-colors flex-shrink-0 flex items-center gap-1">
                <ShoppingCartIcon size={10} />
                Add
              </button>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

export default function MealPlanning({ onNavigate }: Props) {
  const [selectedType, setSelectedType] = useState<MealType>('dinner');
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [editingCell, setEditingCell] = useState<{ day: string; type: MealType } | null>(null);
  const [meals, setMeals] = useState(MEALS);
  const [weekOffset, setWeekOffset] = useState(0);
  const me = useMe();
  const { can } = useAuth();
  const mayDecide = can('essen', 'freigeben', 'familie');

  // Freigabe-Workflow (README Abschnitt 5.1): "Use" legt einen Vorschlag an.
  // Wer essen/freigeben/familie hat (Administrator), wendet ihn sofort an;
  // alle anderen erzeugen nur einen Vorschlag, der auf Freigabe wartet.
  type RecipeSuggestionPayload = { recipeName: string; day: string; type: MealType };
  const [pendingSuggestions, setPendingSuggestions] = useState<Suggestion<RecipeSuggestionPayload>[]>([]);

  const applyRecipe = (payload: RecipeSuggestionPayload) => {
    setMeals(m => ({ ...m, [payload.day]: { ...m[payload.day], [payload.type]: payload.recipeName } }));
  };

  const useRecipe = (recipeName: string) => {
    const payload: RecipeSuggestionPayload = { recipeName, day: todayDay, type: selectedType };
    const suggestion = createSuggestion('essen', payload, me.name);
    if (mayDecide) {
      applyRecipe(payload);
      setPendingSuggestions(ss => [...ss, decide(suggestion, me.name, true)]);
    } else {
      setPendingSuggestions(ss => [...ss, suggestion]);
    }
  };

  const decideSuggestion = (id: number, approve: boolean) => {
    if (!mayDecide) return;
    setPendingSuggestions(ss => ss.map(s => {
      if (s.id !== id || s.status !== 'vorschlag') return s;
      const decided = decide(s, me.name, approve);
      if (approve) applyRecipe(decided.payload);
      return decided;
    }));
  };

  const editValue = editingCell ? meals[editingCell.day]?.[editingCell.type] || '' : '';

  const todayDay = 'Mon'; // Sep 21 is Monday

  const missingIngredients = [
    { name: 'Heavy cream', for: 'Butter Chicken (Thu dinner)' },
    { name: 'Garam masala', for: 'Butter Chicken (Thu dinner)' },
    { name: 'Corn tortillas', for: 'Fish Tacos (Fri dinner)' },
  ];

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Header controls */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setWeekOffset(w => w - 1)}
            className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors"
          >
            <ChevronLeftIcon size={16} />
          </button>
          <span className="font-bold text-slate-800 text-base min-w-[140px] text-center">
            {weekOffset === 0 ? 'This Week' : weekOffset === 1 ? 'Next Week' : weekOffset === -1 ? 'Last Week' : `Week ${weekOffset > 0 ? '+' : ''}${weekOffset}`}
          </span>
          <button
            onClick={() => setWeekOffset(w => w + 1)}
            className="p-2 rounded-xl hover:bg-white border border-slate-200 text-slate-500 shadow-sm transition-colors"
          >
            <ChevronRightIcon size={16} />
          </button>
        </div>

        <button
          onClick={() => setShowSuggestions(!showSuggestions)}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${showSuggestions ? 'bg-[#2563EB] text-white' : 'bg-white border border-slate-200 text-slate-700 hover:bg-slate-50'}`}
        >
          <SparklesIcon size={15} />
          Recipe Ideas
        </button>

        <button
          onClick={() => onNavigate('shopping')}
          className="flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] transition-colors ml-auto"
        >
          <ShoppingCartIcon size={15} />
          Add to Shopping
        </button>
      </div>

      {/* Missing ingredients alert */}
      {missingIngredients.length > 0 && (
        <div className="mb-5 bg-[#FFF7ED] border border-[#FED7AA] rounded-xl p-4 flex flex-wrap items-start gap-4">
          <div>
            <div className="font-semibold text-[#C2410C] text-sm mb-1.5 flex items-center gap-2">
              🛒 {missingIngredients.length} missing ingredients for this week's plan
            </div>
            <div className="flex flex-wrap gap-2">
              {missingIngredients.map(ing => (
                <span key={ing.name} className="text-xs bg-white border border-[#FED7AA] text-[#92400E] px-2.5 py-1 rounded-lg font-medium">
                  {ing.name} · <span className="text-[#B45309]">{ing.for}</span>
                </span>
              ))}
            </div>
          </div>
          <button
            onClick={() => onNavigate('shopping')}
            className="ml-auto flex-shrink-0 bg-[#F97316] text-white text-xs font-semibold px-3 py-1.5 rounded-lg hover:bg-[#EA580C] transition-colors"
          >
            Add all to list
          </button>
        </div>
      )}

      {/* Freigabe-Workflow: offene und entschiedene Rezeptvorschlaege */}
      {pendingSuggestions.length > 0 && (
        <div className="mb-5 bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
          <div className="font-semibold text-sm text-slate-800 mb-3 flex items-center gap-2">
            <SparklesIcon size={15} className="text-[#2563EB]" />
            Essensvorschläge
          </div>
          <div className="space-y-2">
            {pendingSuggestions.map(s => (
              <div key={s.id} className="flex items-center gap-3 p-2.5 rounded-xl bg-slate-50">
                <span className="text-sm flex-1">
                  <span className="font-medium text-slate-800">{s.payload.recipeName}</span>
                  <span className="text-slate-400"> · {s.payload.type} · {s.payload.day} · vorgeschlagen von {s.createdBy}</span>
                </span>
                {s.status === 'vorschlag' && can('essen', 'freigeben', 'familie') && (
                  <div className="flex gap-1.5 flex-shrink-0">
                    <button
                      onClick={() => decideSuggestion(s.id, true)}
                      className="w-7 h-7 rounded-lg bg-[#22C55E] text-white flex items-center justify-center hover:bg-[#16A34A]"
                      aria-label="Freigeben"
                    >
                      <CheckIcon size={13} strokeWidth={3} />
                    </button>
                    <button
                      onClick={() => decideSuggestion(s.id, false)}
                      className="w-7 h-7 rounded-lg bg-slate-200 text-slate-600 flex items-center justify-center hover:bg-slate-300"
                      aria-label="Ablehnen"
                    >
                      <XIcon size={13} strokeWidth={3} />
                    </button>
                  </div>
                )}
                {s.status === 'vorschlag' && !can('essen', 'freigeben', 'familie') && (
                  <span className="text-[10px] text-[#F59E0B] font-semibold flex-shrink-0">Wartet auf Freigabe</span>
                )}
                {s.status === 'freigegeben' && (
                  <span className="text-[10px] text-[#16A34A] font-semibold flex-shrink-0">Freigegeben von {s.decidedBy}</span>
                )}
                {s.status === 'abgelehnt' && (
                  <span className="text-[10px] text-slate-400 font-semibold flex-shrink-0">Abgelehnt von {s.decidedBy}</span>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Meal type filter tabs */}
      <div className="flex gap-2 mb-5 overflow-x-auto scrollbar-hide pb-1">
        {MEAL_TYPES.map(type => {
          const style = MEAL_COLORS[type];
          const active = selectedType === type;
          return (
            <button
              key={type}
              onClick={() => setSelectedType(type)}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold whitespace-nowrap transition-all flex-shrink-0 ${
                active ? 'text-white shadow-sm' : 'bg-white border border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
              style={active ? { backgroundColor: style.text } : {}}
            >
              <span>{style.icon}</span>
              <span className="capitalize">{type}</span>
            </button>
          );
        })}
        <button
          onClick={() => setSelectedType(selectedType)}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-sm font-semibold bg-white border border-slate-200 text-slate-600 hover:bg-slate-50 whitespace-nowrap flex-shrink-0"
        >
          <span>📊</span> All meals
        </button>
      </div>

      {/* Weekly planner grid */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        {/* Day headers */}
        <div className="grid grid-cols-8 border-b border-slate-100">
          <div className="px-3 py-3 text-[11px] font-semibold text-slate-400 uppercase tracking-wide border-r border-slate-100">Meal</div>
          {DAYS.map(day => {
            const isToday = day === todayDay && weekOffset === 0;
            return (
              <div
                key={day}
                className={`px-2 py-3 text-center border-r border-slate-50 last:border-r-0 ${isToday ? 'bg-[#EFF6FF]' : ''}`}
              >
                <div className={`text-xs font-bold ${isToday ? 'text-[#2563EB]' : 'text-slate-600'}`}>{day}</div>
                {isToday && <div className="text-[10px] text-[#2563EB] font-medium">Today</div>}
              </div>
            );
          })}
        </div>

        {/* Meal rows */}
        {MEAL_TYPES.map(type => {
          const style = MEAL_COLORS[type];
          return (
            <div key={type} className="grid grid-cols-8 border-b border-slate-50 last:border-b-0">
              {/* Row label */}
              <div
                className="flex items-center gap-2 px-3 py-3 border-r border-slate-100"
                style={{ backgroundColor: style.bg }}
              >
                <span className="text-sm">{style.icon}</span>
                <span className="text-xs font-semibold capitalize" style={{ color: style.text }}>{type}</span>
              </div>
              {/* Day cells */}
              {DAYS.map(day => {
                const isToday = day === todayDay && weekOffset === 0;
                return (
                  <div
                    key={day}
                    className={`p-2 border-r border-slate-50 last:border-r-0 ${isToday ? 'bg-[#EFF6FF]/30' : ''}`}
                  >
                    <MealCell
                      meal={meals[day]?.[type] || '—'}
                      day={day}
                      type={type}
                      onEdit={() => setEditingCell({ day, type })}
                    />
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>

      {/* Recipe suggestions panel */}
      {showSuggestions && (
        <div className="mt-5 bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
          <div className="flex items-center gap-2 mb-4">
            <SparklesIcon size={18} className="text-[#2563EB]" />
            <h3 className="font-bold text-slate-800 text-base">Recipe Suggestions</h3>
            <div className="flex gap-1.5 ml-auto">
              {MEAL_TYPES.map(type => {
                const style = MEAL_COLORS[type];
                const active = selectedType === type;
                return (
                  <button
                    key={type}
                    onClick={() => setSelectedType(type)}
                    className="px-3 py-1 rounded-lg text-xs font-semibold capitalize transition-all"
                    style={active ? { backgroundColor: style.text, color: 'white' } : { backgroundColor: style.bg, color: style.text }}
                  >
                    {style.icon} {type}
                  </button>
                );
              })}
            </div>
          </div>
          <RecipeSuggestionPanel type={selectedType} onUse={useRecipe} />
        </div>
      )}

      {/* Edit modal */}
      {editingCell && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm p-6">
            <h2 className="font-bold text-slate-800 mb-1">
              {MEAL_COLORS[editingCell.type].icon} {editingCell.type.charAt(0).toUpperCase() + editingCell.type.slice(1)} · {editingCell.day}
            </h2>
            <p className="text-xs text-slate-400 mb-4">What's on the menu?</p>
            <textarea
              className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none"
              rows={3}
              defaultValue={editValue}
              autoFocus
              id="meal-edit"
            />
            <div className="flex gap-3 mt-4">
              <button
                onClick={() => setEditingCell(null)}
                className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                onClick={() => {
                  const val = (document.getElementById('meal-edit') as HTMLTextAreaElement)?.value;
                  if (val && editingCell) {
                    setMeals(m => ({
                      ...m,
                      [editingCell.day]: { ...m[editingCell.day], [editingCell.type]: val },
                    }));
                  }
                  setEditingCell(null);
                }}
                className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8]"
              >
                Save
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
