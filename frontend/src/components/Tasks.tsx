import { useState } from 'react';
import { INITIAL_TASKS, FAMILY_MEMBERS, Task } from './data';
import { PlusIcon, ClockIcon } from './Icons';

interface Props { onNavigate: (p: any) => void; }

const PRIORITY_COLORS = {
  low: { bg: '#F0FDF4', text: '#16A34A', dot: '#22C55E' },
  medium: { bg: '#FFF7ED', text: '#C2410C', dot: '#F97316' },
  high: { bg: '#FEF2F2', text: '#DC2626', dot: '#EF4444' },
};

const CATEGORY_ICONS: Record<string, string> = {
  chores: '🧹',
  school: '📚',
  health: '🏥',
  errands: '🛒',
  family: '👨‍👩‍👧‍👦',
  home: '🔧',
};

function TaskCard({ task, onStatusChange }: { task: Task; onStatusChange: (id: number, status: Task['status']) => void }) {
  const member = FAMILY_MEMBERS.find(m => m.id === task.assigneeId);
  const pri = PRIORITY_COLORS[task.priority];
  const overdue = task.status !== 'done' && new Date(task.dueDate) < new Date('2026-09-21');

  return (
    <div className="bg-white rounded-xl border border-slate-100 p-3.5 shadow-sm hover:shadow-md hover:shadow-slate-100 transition-all cursor-grab active:cursor-grabbing group">
      <div className="flex items-start justify-between gap-2 mb-2">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span
            className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
            style={{ backgroundColor: pri.bg, color: pri.text }}
          >
            {task.priority.charAt(0).toUpperCase() + task.priority.slice(1)}
          </span>
          <span className="text-xs text-slate-400">{CATEGORY_ICONS[task.category] || '📋'} {task.category}</span>
        </div>
        {task.points > 0 && (
          <span className="text-[10px] font-bold text-[#F59E0B] flex items-center gap-0.5 flex-shrink-0">
            ⭐ {task.points}
          </span>
        )}
      </div>

      <h3 className="font-semibold text-slate-800 text-sm mb-1 leading-snug">{task.title}</h3>
      {task.description && (
        <p className="text-xs text-slate-500 mb-2 leading-relaxed line-clamp-2">{task.description}</p>
      )}

      <div className="flex items-center justify-between mt-3">
        <div className="flex items-center gap-1.5">
          <div
            className="w-6 h-6 rounded-full flex items-center justify-center text-white text-[9px] font-bold"
            style={{ backgroundColor: member?.color }}
            title={member?.name}
          >
            {member?.initials[0]}
          </div>
          <span className="text-xs text-slate-500">{member?.name}</span>
        </div>
        <div className={`flex items-center gap-1 text-xs font-medium ${overdue ? 'text-[#EF4444]' : 'text-slate-400'}`}>
          <ClockIcon size={10} />
          {overdue ? 'Overdue' : task.dueDate.split('-').slice(1).join('/')}
        </div>
      </div>

      {/* Quick status change */}
      <div className="flex gap-1.5 mt-3 pt-2.5 border-t border-slate-50 opacity-0 group-hover:opacity-100 transition-opacity">
        {(['todo', 'inprogress', 'done'] as Task['status'][]).map(s => (
          <button
            key={s}
            onClick={() => onStatusChange(task.id, s)}
            className={`flex-1 py-1 rounded-lg text-[10px] font-semibold transition-colors ${
              task.status === s
                ? s === 'done' ? 'bg-[#22C55E] text-white'
                : s === 'inprogress' ? 'bg-[#2563EB] text-white'
                : 'bg-slate-200 text-slate-700'
                : 'bg-slate-50 text-slate-400 hover:bg-slate-100'
            }`}
          >
            {s === 'todo' ? 'To Do' : s === 'inprogress' ? 'Doing' : 'Done'}
          </button>
        ))}
      </div>
    </div>
  );
}

function AddTaskModal({ onClose, onAdd }: { onClose: () => void; onAdd: (t: Omit<Task, 'id'>) => void }) {
  const [form, setForm] = useState({
    title: '',
    description: '',
    assigneeId: 1,
    priority: 'medium' as Task['priority'],
    dueDate: '2026-09-25',
    category: 'chores',
    points: 15,
  });

  const submit = () => {
    if (!form.title.trim()) return;
    onAdd({ ...form, status: 'todo' });
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6">
        <h2 className="font-bold text-slate-800 text-lg mb-5">New Task</h2>
        <div className="space-y-4">
          <div>
            <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Task title</label>
            <input
              className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
              placeholder="What needs to be done?"
              value={form.title}
              onChange={e => setForm({ ...form, title: e.target.value })}
              autoFocus
            />
          </div>
          <div>
            <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Description (optional)</label>
            <textarea
              className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none"
              rows={2}
              placeholder="More details..."
              value={form.description}
              onChange={e => setForm({ ...form, description: e.target.value })}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Assign to</label>
              <select
                className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB]"
                value={form.assigneeId}
                onChange={e => setForm({ ...form, assigneeId: Number(e.target.value) })}
              >
                {FAMILY_MEMBERS.map(m => (
                  <option key={m.id} value={m.id}>{m.name} ({m.role})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Priority</label>
              <select
                className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB]"
                value={form.priority}
                onChange={e => setForm({ ...form, priority: e.target.value as Task['priority'] })}
              >
                <option value="low">Low</option>
                <option value="medium">Medium</option>
                <option value="high">High</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Due date</label>
              <input
                type="date"
                className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB]"
                value={form.dueDate}
                onChange={e => setForm({ ...form, dueDate: e.target.value })}
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Points reward</label>
              <input
                type="number"
                className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm text-slate-800 focus:outline-none focus:border-[#2563EB]"
                value={form.points}
                onChange={e => setForm({ ...form, points: Number(e.target.value) })}
                min={0}
                max={100}
              />
            </div>
          </div>
        </div>
        <div className="flex gap-3 mt-6">
          <button
            onClick={onClose}
            className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={submit}
            className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] transition-colors"
          >
            Add Task
          </button>
        </div>
      </div>
    </div>
  );
}

export default function Tasks({ onNavigate }: Props) {
  const [tasks, setTasks] = useState<Task[]>(INITIAL_TASKS);
  const [showModal, setShowModal] = useState(false);
  const [filterMember, setFilterMember] = useState<number | null>(null);
  const [filterPriority, setFilterPriority] = useState<string>('all');

  const handleStatusChange = (id: number, status: Task['status']) => {
    setTasks(ts => ts.map(t => t.id === id ? { ...t, status } : t));
  };

  const handleAdd = (t: Omit<Task, 'id'>) => {
    setTasks(ts => [...ts, { ...t, id: Date.now() }]);
  };

  const filtered = tasks.filter(t => {
    if (filterMember !== null && t.assigneeId !== filterMember) return false;
    if (filterPriority !== 'all' && t.priority !== filterPriority) return false;
    return true;
  });

  const columns: { id: Task['status']; label: string; color: string; bg: string }[] = [
    { id: 'todo', label: 'To Do', color: '#64748B', bg: '#F8FAFC' },
    { id: 'inprogress', label: 'In Progress', color: '#2563EB', bg: '#EFF6FF' },
    { id: 'done', label: 'Done', color: '#22C55E', bg: '#F0FDF4' },
  ];

  const stats = {
    total: tasks.length,
    done: tasks.filter(t => t.status === 'done').length,
    overdue: tasks.filter(t => t.status !== 'done' && new Date(t.dueDate) < new Date('2026-09-21')).length,
  };

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Stats bar */}
      <div className="grid grid-cols-3 gap-3 mb-5">
        {[
          { label: 'Total Tasks', value: stats.total, color: '#2563EB', bg: '#EFF6FF' },
          { label: 'Completed', value: stats.done, color: '#22C55E', bg: '#F0FDF4' },
          { label: 'Overdue', value: stats.overdue, color: '#EF4444', bg: '#FEF2F2' },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl border border-slate-100 px-4 py-3 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center text-lg font-bold" style={{ backgroundColor: s.bg, color: s.color }}>
              {s.value}
            </div>
            <span className="text-sm text-slate-600 font-medium">{s.label}</span>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center gap-2 mb-5">
        <div className="flex gap-1.5">
          <button
            onClick={() => setFilterMember(null)}
            className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${filterMember === null ? 'bg-slate-800 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
          >
            Everyone
          </button>
          {FAMILY_MEMBERS.map(m => (
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
              {p === 'all' ? 'All' : p}
            </button>
          ))}
          <button
            onClick={() => setShowModal(true)}
            className="flex items-center gap-1.5 bg-[#2563EB] text-white px-3.5 py-1.5 rounded-full text-xs font-semibold hover:bg-[#1D4ED8] transition-colors ml-2"
          >
            <PlusIcon size={14} />
            New Task
          </button>
        </div>
      </div>

      {/* Kanban board */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {columns.map(col => {
          const colTasks = filtered.filter(t => t.status === col.id);
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

              {/* Cards */}
              <div
                className="flex-1 rounded-2xl p-3 space-y-2.5 min-h-[300px]"
                style={{ backgroundColor: col.bg }}
              >
                {colTasks.map(task => (
                  <TaskCard key={task.id} task={task} onStatusChange={handleStatusChange} />
                ))}
                {colTasks.length === 0 && (
                  <div className="text-center py-8 text-slate-300 text-sm">
                    <div className="text-2xl mb-2">{col.id === 'done' ? '✅' : '📋'}</div>
                    <div>No tasks here</div>
                  </div>
                )}
                <button
                  onClick={() => setShowModal(true)}
                  className="w-full py-2 border-2 border-dashed border-slate-200 rounded-xl text-slate-400 text-xs font-medium hover:border-[#2563EB] hover:text-[#2563EB] transition-colors flex items-center justify-center gap-1.5"
                >
                  <PlusIcon size={13} /> Add task
                </button>
              </div>
            </div>
          );
        })}
      </div>

      {showModal && <AddTaskModal onClose={() => setShowModal(false)} onAdd={handleAdd} />}
    </div>
  );
}
