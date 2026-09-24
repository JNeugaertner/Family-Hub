import { useState } from 'react';
import { INITIAL_SHOPPING, FAMILY_MEMBERS, ShoppingItem } from './data';
import { PlusIcon, CheckIcon, XIcon, ShoppingCartIcon, SparklesIcon, AlertTriangleIcon } from './Icons';

interface Props { onNavigate: (p: any) => void; }

const CATEGORY_ICONS: Record<string, string> = {
  'Dairy': '🥛',
  'Bakery': '🍞',
  'Meat & Fish': '🥩',
  'Vegetables': '🥦',
  'Fruit': '🍎',
  'Beverages': '🧃',
  'Pantry': '🥫',
  'Frozen': '🧊',
  'Snacks': '🍿',
  'Cleaning': '🧹',
};

const AI_SUGGESTIONS = [
  { name: 'Butter (unsalted)', category: 'Dairy', reason: 'Low stock detected' },
  { name: 'Cereal', category: 'Pantry', reason: 'Weekly staple' },
  { name: 'Laundry detergent', category: 'Cleaning', reason: 'Nearly empty' },
  { name: 'Strawberries', category: 'Fruit', reason: 'Meal plan ingredient' },
];

function CategorySection({ category, items, onToggle, onRemove }: {
  category: string;
  items: ShoppingItem[];
  onToggle: (id: number) => void;
  onRemove: (id: number) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const checked = items.filter(i => i.checked).length;

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      <button
        className="w-full flex items-center gap-3 px-5 py-4 hover:bg-slate-50 transition-colors"
        onClick={() => setCollapsed(!collapsed)}
        aria-expanded={!collapsed}
      >
        <span className="text-xl">{CATEGORY_ICONS[category] || '🛒'}</span>
        <span className="font-semibold text-slate-800 text-sm flex-1 text-left">{category}</span>
        <span className="text-xs text-slate-400">{checked}/{items.length}</span>
        {checked === items.length && items.length > 0 && (
          <span className="w-5 h-5 rounded-full bg-[#22C55E] flex items-center justify-center">
            <CheckIcon size={11} className="text-white" strokeWidth={3} />
          </span>
        )}
        <span className={`text-slate-300 text-xs transition-transform ${collapsed ? '' : 'rotate-90'}`}>›</span>
      </button>

      {!collapsed && (
        <div className="px-5 pb-4 space-y-2">
          {items.map(item => {
            const member = FAMILY_MEMBERS.find(m => m.id === item.addedById);
            return (
              <div
                key={item.id}
                className={`flex items-center gap-3 py-2 px-3 rounded-xl group transition-colors ${item.checked ? 'bg-slate-50' : 'hover:bg-slate-50'}`}
              >
                <button
                  onClick={() => onToggle(item.id)}
                  className={`w-5 h-5 rounded-md flex-shrink-0 border-2 flex items-center justify-center transition-all ${
                    item.checked
                      ? 'bg-[#22C55E] border-[#22C55E]'
                      : 'border-slate-300 hover:border-[#22C55E]'
                  }`}
                  aria-label={item.checked ? 'Mark as not bought' : 'Mark as bought'}
                >
                  {item.checked && <CheckIcon size={12} className="text-white" strokeWidth={3} />}
                </button>

                <span className={`flex-1 text-sm font-medium ${item.checked ? 'line-through text-slate-400' : 'text-slate-800'}`}>
                  {item.name}
                </span>

                {item.urgent && !item.checked && (
                  <span className="text-[10px] bg-[#FEF2F2] text-[#EF4444] px-1.5 py-0.5 rounded-full font-medium flex-shrink-0">
                    Urgent
                  </span>
                )}

                {item.quantity && (
                  <span className="text-xs text-slate-400 flex-shrink-0">{item.quantity}</span>
                )}

                <div
                  className="w-4 h-4 rounded-full flex-shrink-0"
                  style={{ backgroundColor: member?.color || '#94A3B8' }}
                  title={`Added by ${member?.name}`}
                />

                <button
                  onClick={() => onRemove(item.id)}
                  className="opacity-0 group-hover:opacity-100 text-slate-300 hover:text-[#EF4444] transition-all p-0.5 flex-shrink-0"
                  aria-label="Remove item"
                >
                  <XIcon size={14} />
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function Shopping({ onNavigate }: Props) {
  const [items, setItems] = useState<ShoppingItem[]>(INITIAL_SHOPPING);
  const [newItem, setNewItem] = useState('');
  const [newCategory, setNewCategory] = useState('Pantry');
  const [showAI, setShowAI] = useState(true);

  const toggle = (id: number) => setItems(is => is.map(i => i.id === id ? { ...i, checked: !i.checked } : i));
  const remove = (id: number) => setItems(is => is.filter(i => i.id !== id));
  const addAISuggestion = (s: typeof AI_SUGGESTIONS[0]) => {
    setItems(is => [...is, {
      id: Date.now(),
      name: s.name,
      category: s.category,
      checked: false,
      addedById: 1,
    }]);
  };

  const addItem = () => {
    if (!newItem.trim()) return;
    setItems(is => [...is, {
      id: Date.now(),
      name: newItem.trim(),
      category: newCategory,
      checked: false,
      addedById: 1,
    }]);
    setNewItem('');
  };

  const categories = Array.from(new Set(items.map(i => i.category)));
  const totalItems = items.length;
  const checkedItems = items.filter(i => i.checked).length;
  const urgentItems = items.filter(i => i.urgent && !i.checked);

  return (
    <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
      {/* Header stats */}
      <div className="flex flex-wrap items-center gap-3 mb-5">
        <div className="flex items-center gap-2 bg-white rounded-xl border border-slate-100 px-4 py-2.5 shadow-sm">
          <ShoppingCartIcon size={16} className="text-[#22C55E]" />
          <span className="text-sm font-semibold text-slate-800">{totalItems - checkedItems} remaining</span>
          <span className="text-slate-300">/</span>
          <span className="text-xs text-slate-400">{totalItems} total</span>
        </div>

        {urgentItems.length > 0 && (
          <div className="flex items-center gap-2 bg-[#FEF2F2] rounded-xl px-4 py-2.5">
            <AlertTriangleIcon size={14} className="text-[#EF4444]" />
            <span className="text-sm font-semibold text-[#DC2626]">{urgentItems.length} urgent</span>
          </div>
        )}

        {/* Progress */}
        <div className="ml-auto flex items-center gap-3">
          <div className="w-32 h-2 bg-slate-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-[#22C55E] rounded-full transition-all"
              style={{ width: `${(checkedItems / totalItems) * 100}%` }}
            />
          </div>
          <span className="text-xs text-slate-500">{Math.round((checkedItems / totalItems) * 100)}%</span>
        </div>

        <button
          onClick={() => setItems(is => is.filter(i => !i.checked))}
          className="text-xs text-slate-500 hover:text-[#EF4444] font-medium transition-colors"
        >
          Clear checked
        </button>
      </div>

      {/* AI Suggestions */}
      {showAI && (
        <div className="mb-5 bg-gradient-to-r from-[#EFF6FF] to-[#F0FDFA] rounded-2xl border border-[#BFDBFE] p-4">
          <div className="flex items-center gap-2 mb-3">
            <SparklesIcon size={16} className="text-[#2563EB]" />
            <span className="font-semibold text-sm text-slate-800">AI Suggestions</span>
            <span className="text-xs text-slate-400">Based on your meal plan & usage</span>
            <button onClick={() => setShowAI(false)} className="ml-auto text-slate-400 hover:text-slate-600">
              <XIcon size={14} />
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {AI_SUGGESTIONS.map(s => (
              <button
                key={s.name}
                onClick={() => addAISuggestion(s)}
                className="flex items-center gap-2 bg-white border border-slate-200 rounded-xl px-3 py-1.5 text-xs font-medium text-slate-700 hover:border-[#2563EB] hover:text-[#2563EB] transition-all shadow-sm"
              >
                <span>{CATEGORY_ICONS[s.category] || '🛒'}</span>
                <span>{s.name}</span>
                <span className="text-[#2563EB] font-bold">+</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Add new item */}
      <div className="flex gap-2 mb-5">
        <input
          className="flex-1 border border-slate-200 rounded-xl px-4 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#22C55E] focus:ring-2 focus:ring-[#22C55E]/20 bg-white"
          placeholder="Add item to list..."
          value={newItem}
          onChange={e => setNewItem(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && addItem()}
        />
        <select
          className="border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-600 focus:outline-none focus:border-[#22C55E] bg-white"
          value={newCategory}
          onChange={e => setNewCategory(e.target.value)}
        >
          {Object.keys(CATEGORY_ICONS).map(c => <option key={c} value={c}>{c}</option>)}
        </select>
        <button
          onClick={addItem}
          className="w-11 h-11 flex-shrink-0 bg-[#22C55E] text-white rounded-xl flex items-center justify-center hover:bg-[#16A34A] transition-colors shadow-sm"
          aria-label="Add item"
        >
          <PlusIcon size={18} />
        </button>
      </div>

      {/* Shopping list by category */}
      <div className="space-y-3">
        {/* Urgent first */}
        {urgentItems.length > 0 && (
          <div className="bg-[#FEF2F2] rounded-2xl border border-[#FECACA] overflow-hidden">
            <div className="flex items-center gap-3 px-5 py-4">
              <AlertTriangleIcon size={18} className="text-[#EF4444]" />
              <span className="font-semibold text-[#DC2626] text-sm">Urgent Items</span>
            </div>
            <div className="px-5 pb-4 space-y-2">
              {urgentItems.map(item => (
                <div key={item.id} className="flex items-center gap-3 py-2 px-3 rounded-xl group hover:bg-[#FEF9F9] transition-colors">
                  <button
                    onClick={() => toggle(item.id)}
                    className="w-5 h-5 rounded-md flex-shrink-0 border-2 border-[#EF4444] hover:bg-[#EF4444] flex items-center justify-center transition-all"
                  />
                  <span className="flex-1 text-sm font-semibold text-[#DC2626]">{item.name}</span>
                  <span className="text-xs text-[#DC2626]/60">{item.quantity}</span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* By category */}
        {categories.map(cat => (
          <CategorySection
            key={cat}
            category={cat}
            items={items.filter(i => i.category === cat)}
            onToggle={toggle}
            onRemove={remove}
          />
        ))}
      </div>

      {/* Share prompt */}
      <div className="mt-5 p-4 bg-gradient-to-r from-[#14B8A6] to-[#2563EB] rounded-2xl text-white flex items-center gap-4">
        <div className="flex -space-x-2">
          {FAMILY_MEMBERS.slice(0, 3).map(m => (
            <div
              key={m.id}
              className="w-8 h-8 rounded-full border-2 border-white flex items-center justify-center text-xs font-bold"
              style={{ backgroundColor: m.color }}
            >
              {m.initials[0]}
            </div>
          ))}
        </div>
        <div className="flex-1">
          <div className="font-semibold text-sm">Real-time sync active</div>
          <div className="text-white/70 text-xs">5 family members sharing this list</div>
        </div>
        <button className="bg-white/20 hover:bg-white/30 text-white text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors flex-shrink-0">
          Share List
        </button>
      </div>
    </div>
  );
}
