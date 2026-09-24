import { useState } from 'react';
import { MESSAGES, FAMILY_MEMBERS, Message } from './data';
import { SendIcon, PlusIcon, ShoppingCartIcon, CalendarIcon, CheckSquareIcon, SearchIcon } from './Icons';

interface Props { onNavigate: (p: any) => void; }

const SOURCE_COLORS: Record<string, { bg: string; text: string; icon: string }> = {
  whatsapp: { bg: '#DCF8C6', text: '#128C7E', icon: '💬' },
  telegram: { bg: '#EFF6FF', text: '#2563EB', icon: '✈️' },
  family: { bg: '#F5F3FF', text: '#7C3AED', icon: '🏠' },
};

const ACTION_LABELS: Record<string, { icon: string; label: string; color: string; bg: string }> = {
  event: { icon: '📅', label: 'Add to Calendar', color: '#2563EB', bg: '#EFF6FF' },
  shopping: { icon: '🛒', label: 'Add to List', color: '#22C55E', bg: '#F0FDF4' },
  task: { icon: '✅', label: 'Create Task', color: '#F97316', bg: '#FFF7ED' },
};

function MessageBubble({ msg, selected, onClick }: { msg: Message; selected: boolean; onClick: () => void }) {
  const source = SOURCE_COLORS[msg.source];
  return (
    <div
      className={`flex items-start gap-3 p-3.5 rounded-xl cursor-pointer transition-all ${
        selected ? 'bg-[#EFF6FF] border border-[#BFDBFE]' : 'hover:bg-slate-50 border border-transparent'
      } ${msg.unread ? 'bg-blue-50/40' : ''}`}
      onClick={onClick}
    >
      <div
        className="w-10 h-10 rounded-full flex items-center justify-center text-lg flex-shrink-0 font-bold"
        style={{ backgroundColor: source.bg, color: source.text }}
      >
        {source.icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2 mb-1">
          <span className="font-semibold text-slate-800 text-sm truncate">{msg.thread}</span>
          <span className="text-[10px] text-slate-400 flex-shrink-0">{msg.time}</span>
        </div>
        <div className="flex items-center gap-1.5 mb-1">
          <span
            className="text-[10px] font-medium px-1.5 py-0.5 rounded-full flex-shrink-0"
            style={{ backgroundColor: source.bg, color: source.text }}
          >
            {msg.source.charAt(0).toUpperCase() + msg.source.slice(1)}
          </span>
          <span className="text-xs text-slate-500 truncate">{msg.senderName}</span>
        </div>
        <p className="text-xs text-slate-600 line-clamp-2 leading-relaxed">{msg.content}</p>
        {msg.actionable && (
          <div className="mt-1.5">
            <span
              className="inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full"
              style={{
                backgroundColor: ACTION_LABELS[msg.actionable].bg,
                color: ACTION_LABELS[msg.actionable].color
              }}
            >
              {ACTION_LABELS[msg.actionable].icon}
              {ACTION_LABELS[msg.actionable].label}
            </span>
          </div>
        )}
      </div>
      {msg.unread && (
        <div className="w-2 h-2 rounded-full bg-[#2563EB] mt-2 flex-shrink-0" />
      )}
    </div>
  );
}

function MessageDetail({ msg, onNavigate, onDismiss }: { msg: Message; onNavigate: (p: any) => void; onDismiss: () => void }) {
  const source = SOURCE_COLORS[msg.source];
  const [replied, setReplied] = useState(false);
  const [reply, setReply] = useState('');

  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="flex items-center gap-3 p-4 border-b border-slate-100">
        <div
          className="w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold flex-shrink-0"
          style={{ backgroundColor: source.bg, color: source.text }}
        >
          {source.icon}
        </div>
        <div>
          <div className="font-semibold text-slate-800 text-sm">{msg.thread}</div>
          <div className="text-xs text-slate-400">{msg.senderName} · via {msg.source.charAt(0).toUpperCase() + msg.source.slice(1)}</div>
        </div>
        <button
          onClick={onDismiss}
          className="ml-auto text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100"
        >
          ✕
        </button>
      </div>

      {/* Message */}
      <div className="flex-1 p-4 overflow-y-auto">
        <div className="flex items-start gap-3 mb-4">
          <div
            className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
            style={{ backgroundColor: msg.senderColor }}
          >
            {msg.senderName.charAt(0)}
          </div>
          <div>
            <div className="flex items-center gap-2 mb-1">
              <span className="text-sm font-semibold text-slate-800">{msg.senderName}</span>
              <span className="text-xs text-slate-400">{msg.time}</span>
            </div>
            <div
              className="px-4 py-3 rounded-2xl rounded-tl-sm text-sm text-slate-800 leading-relaxed"
              style={{ backgroundColor: source.bg }}
            >
              {msg.content}
            </div>
          </div>
        </div>

        {/* Smart action buttons */}
        {msg.actionable && (
          <div className="bg-gradient-to-br from-[#EFF6FF] to-[#F0FDFA] rounded-2xl p-4 border border-[#BFDBFE]">
            <div className="flex items-center gap-2 mb-3">
              <span className="text-lg">🤖</span>
              <span className="font-semibold text-slate-800 text-sm">FamilyHub AI detected an action</span>
            </div>

            {msg.actionable === 'event' && (
              <div className="space-y-3">
                <div className="bg-white rounded-xl p-3 border border-slate-100">
                  <div className="text-xs font-semibold text-slate-500 mb-2">📅 Suggested Event</div>
                  <div className="font-semibold text-slate-800 text-sm">
                    {msg.id === 1 ? 'Basketball game – City Sports Center' : "Lily's Piano Recital – Music Academy"}
                  </div>
                  <div className="text-xs text-slate-500 mt-1">
                    {msg.id === 1 ? 'Thursday, Sep 25 · 10:00 – 12:00' : 'Saturday, Oct 3 · 3:00pm'}
                  </div>
                  <div className="text-xs text-slate-400 mt-1">
                    {msg.id === 1 ? 'Assign to: Lucas' : 'Assign to: All family'}
                  </div>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => { onNavigate('calendar'); }}
                    className="flex-1 flex items-center justify-center gap-2 bg-[#2563EB] text-white text-xs font-semibold px-3 py-2.5 rounded-xl hover:bg-[#1D4ED8] transition-colors"
                  >
                    <CalendarIcon size={13} />
                    Add to Calendar
                  </button>
                  <button className="flex-1 bg-slate-100 text-slate-600 text-xs font-semibold px-3 py-2.5 rounded-xl hover:bg-slate-200 transition-colors">
                    Edit & Save
                  </button>
                </div>
              </div>
            )}

            {msg.actionable === 'shopping' && (
              <div className="space-y-3">
                <div className="bg-white rounded-xl p-3 border border-slate-100">
                  <div className="text-xs font-semibold text-slate-500 mb-2">🛒 Detected Shopping Item</div>
                  <div className="font-semibold text-slate-800">Milk</div>
                  <div className="text-xs text-slate-500 mt-1">Category: Dairy · Added by: Sarah</div>
                </div>
                <button
                  onClick={() => onNavigate('shopping')}
                  className="w-full flex items-center justify-center gap-2 bg-[#22C55E] text-white text-xs font-semibold px-3 py-2.5 rounded-xl hover:bg-[#16A34A] transition-colors"
                >
                  <ShoppingCartIcon size={13} />
                  Add Milk to Shopping List
                </button>
              </div>
            )}

            {msg.actionable === 'task' && (
              <div className="space-y-3">
                <div className="bg-white rounded-xl p-3 border border-slate-100">
                  <div className="text-xs font-semibold text-slate-500 mb-2">✅ Suggested Task</div>
                  <div className="font-semibold text-slate-800">Pick up Emma from soccer practice</div>
                  <div className="text-xs text-slate-500 mt-1">Today · 6:00pm · Assign to: Sarah or Mike</div>
                </div>
                <button
                  onClick={() => onNavigate('tasks')}
                  className="w-full flex items-center justify-center gap-2 bg-[#F97316] text-white text-xs font-semibold px-3 py-2.5 rounded-xl hover:bg-[#EA580C] transition-colors"
                >
                  <CheckSquareIcon size={13} />
                  Create Task
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Quick reply */}
      <div className="p-4 border-t border-slate-100">
        {replied ? (
          <div className="text-sm text-[#22C55E] font-medium text-center py-2">✅ Reply sent!</div>
        ) : (
          <div className="flex gap-2">
            <input
              className="flex-1 border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
              placeholder="Quick reply..."
              value={reply}
              onChange={e => setReply(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter' && reply.trim()) { setReplied(true); setReply(''); } }}
            />
            <button
              onClick={() => { if (reply.trim()) { setReplied(true); setReply(''); } }}
              className="w-10 h-10 bg-[#2563EB] text-white rounded-xl flex items-center justify-center hover:bg-[#1D4ED8] transition-colors"
            >
              <SendIcon size={16} />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Messenger({ onNavigate }: Props) {
  const [messages, setMessages] = useState(MESSAGES);
  const [selected, setSelected] = useState<Message | null>(MESSAGES[0]);
  const [search, setSearch] = useState('');

  const filtered = messages.filter(m =>
    m.thread.toLowerCase().includes(search.toLowerCase()) ||
    m.content.toLowerCase().includes(search.toLowerCase())
  );

  const unreadCount = messages.filter(m => m.unread).length;

  const markRead = (msg: Message) => {
    setMessages(ms => ms.map(m => m.id === msg.id ? { ...m, unread: false } : m));
    setSelected(msg);
  };

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Source legend */}
      <div className="flex flex-wrap gap-3 mb-5">
        {Object.entries(SOURCE_COLORS).map(([src, style]) => (
          <div key={src} className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white border border-slate-100 text-xs font-medium text-slate-600 shadow-sm">
            <span>{style.icon}</span>
            <span className="capitalize">{src}</span>
            <span
              className="w-2 h-2 rounded-full"
              style={{ backgroundColor: style.text }}
            />
          </div>
        ))}
        {unreadCount > 0 && (
          <div className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#FEF2F2] border border-[#FECACA] text-xs font-semibold text-[#DC2626] ml-auto">
            🔴 {unreadCount} unread
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[380px_1fr] gap-5 items-start">
        {/* Message list */}
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
          {/* Search */}
          <div className="p-3 border-b border-slate-100">
            <div className="flex items-center gap-2 bg-slate-50 rounded-xl px-3 py-2">
              <SearchIcon size={14} className="text-slate-400 flex-shrink-0" />
              <input
                className="flex-1 bg-transparent text-sm text-slate-800 placeholder-slate-400 focus:outline-none"
                placeholder="Search messages..."
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
          </div>

          <div className="divide-y divide-slate-50">
            {filtered.map(msg => (
              <MessageBubble
                key={msg.id}
                msg={msg}
                selected={selected?.id === msg.id}
                onClick={() => markRead(msg)}
              />
            ))}
          </div>
        </div>

        {/* Detail panel */}
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden min-h-[400px] lg:h-[600px] flex flex-col">
          {selected ? (
            <MessageDetail msg={selected} onNavigate={onNavigate} onDismiss={() => setSelected(null)} />
          ) : (
            <div className="flex-1 flex items-center justify-center text-slate-300">
              <div className="text-center">
                <div className="text-4xl mb-3">💬</div>
                <div className="text-sm font-medium">Select a message to view</div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Import banner */}
      <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="flex items-center gap-4 bg-[#F0FDF4] border border-[#BBF7D0] rounded-2xl p-4">
          <span className="text-3xl">💬</span>
          <div className="flex-1">
            <div className="font-semibold text-slate-800 text-sm">WhatsApp connected</div>
            <div className="text-xs text-slate-500">2 group chats · Last sync: 2 min ago</div>
          </div>
          <button className="text-xs font-semibold text-[#16A34A] hover:underline">Manage</button>
        </div>
        <div className="flex items-center gap-4 bg-[#EFF6FF] border border-[#BFDBFE] rounded-2xl p-4">
          <span className="text-3xl">✈️</span>
          <div className="flex-1">
            <div className="font-semibold text-slate-800 text-sm">Telegram connected</div>
            <div className="text-xs text-slate-500">Family group · Last sync: 5 min ago</div>
          </div>
          <button className="text-xs font-semibold text-[#2563EB] hover:underline">Manage</button>
        </div>
      </div>
    </div>
  );
}
