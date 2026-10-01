import {
  useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent, type ReactNode,
} from 'react';
import { useAuth, useMe } from '../auth/AuthContext';
import { useCalendarData, type CalendarMember } from '../calendar/CalendarDataContext';
import { useCalendarPermissions } from '../calendar/permissions';
import { addDays, formatLongDate, fromDateKey, startOfToday, toDateKey, weekdayIndex, WEEKDAYS_SHORT } from '../calendar/dates';
import { useTaskPermissions } from '../tasks/permissions';
import { useShoppingData } from '../shopping/ShoppingDataContext';
import { SHOPPING_CATEGORIES, SHOPPING_CATEGORY_KEYS } from '../shopping/categories';
import type { ShoppingCategory } from '../shopping/api';
import { useMessageData } from '../messages/MessageDataContext';
import * as api from '../messages/api';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { ApiError } from '../api/client';
import { ROLE_NAMES } from '../roles';
import type { Focus } from '../navigation/focus';
import EventFormModal from './EventFormModal';
import { CalendarIcon, CheckSquareIcon, ChevronLeftIcon, SearchIcon, SendIcon, ShoppingCartIcon, XIcon } from './Icons';

type Page = 'dashboard' | 'calendar' | 'tasks' | 'rewards' | 'shopping' | 'meals' | 'assistant' | 'messenger' | 'profiles';
type Navigate = (p: Page, focus?: Focus) => void;
interface Props { onNavigate: Navigate; }

const MAX_LENGTH = 2000;
const FORMER_MEMBER = 'Ehemaliges Mitglied';

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));
const pad = (n: number) => String(n).padStart(2, '0');
const clockTime = (date: Date) => `${pad(date.getHours())}:${pad(date.getMinutes())}`;
// Nachrichtentext als einzeiliger Vorschlag für Titel und Artikel
const asTitle = (text: string, max: number) => text.replace(/\s+/g, ' ').trim().slice(0, max);

// Liste: "14:32" heute, "Gestern", Wochentag in den letzten Tagen, sonst "29.09."
function shortTime(sentAt: string) {
  const date = new Date(sentAt);
  const day = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const daysAgo = Math.round((startOfToday().getTime() - day.getTime()) / 86_400_000);
  if (daysAgo <= 0) return clockTime(date);
  if (daysAgo === 1) return 'Gestern';
  if (daysAgo < 7) return WEEKDAYS_SHORT[weekdayIndex(date)];
  return `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.`;
}

function dayLabel(key: string) {
  if (key === toDateKey(startOfToday())) return 'Heute';
  if (key === toDateKey(addDays(startOfToday(), -1))) return 'Gestern';
  return formatLongDate(fromDateKey(key));
}

// Auf dem Handy sind Liste und Chat getrennte Ansichten, ab lg nebeneinander
function useIsDesktop() {
  const query = '(min-width: 1024px)';
  const [desktop, setDesktop] = useState(() => window.matchMedia(query).matches);
  useEffect(() => {
    const media = window.matchMedia(query);
    const update = () => setDesktop(media.matches);
    media.addEventListener('change', update);
    return () => media.removeEventListener('change', update);
  }, []);
  return desktop;
}

function Avatar({ member, size = 40 }: { member?: CalendarMember; size?: number }) {
  return (
    <div className="rounded-full flex items-center justify-center text-white font-bold flex-shrink-0" aria-hidden="true"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.38), backgroundColor: member?.color ?? '#94A3B8' }}>
      {member?.initials ?? '?'}
    </div>
  );
}

function FamilyAvatar({ size = 40 }: { size?: number }) {
  return (
    <div className="rounded-full flex items-center justify-center flex-shrink-0 bg-gradient-to-br from-[#2563EB] to-[#14B8A6]"
      style={{ width: size, height: size, fontSize: Math.round(size * 0.45) }} aria-hidden="true">
      🏠
    </div>
  );
}

function useConversationTitle() {
  const { memberById } = useCalendarData();
  return useCallback((c: api.Conversation) => (c.kind === 'family'
    ? 'Familiengruppe' : (c.partnerId && memberById(c.partnerId)?.name) || FORMER_MEMBER), [memberById]);
}

// Familiengruppe zuerst, dann nach der letzten Nachricht, Chats ohne Nachricht nach Namen
function sortConversations(list: api.Conversation[], title: (c: api.Conversation) => string) {
  const time = (c: api.Conversation) => (c.lastMessage ? new Date(c.lastMessage.sentAt).getTime() : 0);
  return [...list].sort((a, b) => {
    if (a.kind !== b.kind) return a.kind === 'family' ? -1 : 1;
    return time(b) - time(a) || title(a).localeCompare(title(b), 'de');
  });
}

// ─── Liste der Unterhaltungen ────────────────────────────────────────────────

function ConversationRow({ conversation, title, selected, onSelect }: {
  conversation: api.Conversation; title: string; selected: boolean; onSelect: () => void;
}) {
  const me = useMe();
  const { memberById } = useCalendarData();
  const last = conversation.lastMessage;
  const senderPrefix = !last ? '' : last.senderId === me.id ? 'Du: '
    : conversation.kind === 'family' ? `${memberById(last.senderId)?.name ?? FORMER_MEMBER}: ` : '';
  const unread = conversation.unread > 0;

  return (
    <button type="button" onClick={onSelect} aria-current={selected ? 'true' : undefined}
      className={`w-full flex items-center gap-3 p-3.5 text-left transition-colors ${selected ? 'bg-[#EFF6FF]' : 'hover:bg-slate-50'}`}>
      {conversation.kind === 'family'
        ? <FamilyAvatar />
        : <Avatar member={conversation.partnerId ? memberById(conversation.partnerId) : undefined} />}
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <span className={`text-sm truncate ${unread ? 'font-bold text-slate-900' : 'font-semibold text-slate-800'}`}>{title}</span>
          {last && <span className="text-[10px] text-slate-400 flex-shrink-0">{shortTime(last.sentAt)}</span>}
        </div>
        <div className="flex items-center gap-2 mt-0.5">
          <p className={`text-xs truncate flex-1 ${unread ? 'text-slate-700 font-medium' : 'text-slate-500'}`}>
            {last ? senderPrefix + last.text : 'Noch keine Nachrichten'}
          </p>
          {unread && (
            <span className="min-w-5 h-5 px-1.5 rounded-full bg-[#2563EB] text-white text-[10px] font-bold flex items-center justify-center">
              {conversation.unread}<span className="sr-only"> ungelesen</span>
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

// ─── Artikel aus einer Nachricht auf die Einkaufsliste ───────────────────────

function ShoppingFromMessage({ message, onClose, onDone }: {
  message: api.ChatMessage; onClose: () => void; onDone: (notice: string) => void;
}) {
  const { add } = useShoppingData();
  const [name, setName] = useState(asTitle(message.text, 80));
  const [quantity, setQuantity] = useState('');
  const [category, setCategory] = useState<ShoppingCategory>('vorrat');
  const [urgent, setUrgent] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape' && !busy) onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const item = await add({ name: name.trim(), quantity: quantity.trim() || null, category, urgent });
      onDone(item.status === 'proposed'
        ? `„${item.name}“ ist als Vorschlag bei den Eltern.`
        : `„${item.name}“ steht jetzt auf der Einkaufsliste.`);
    } catch (err) {
      if (err instanceof ApiError && err.problem.errors) {
        setFieldErrors(err.problem.errors);
        setFormError(null);
      } else {
        setFieldErrors({});
        setFormError(errorText(err));
      }
      setBusy(false);
    }
  };

  const input = (hasError: boolean) => `w-full border rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2 ${hasError
    ? 'border-[#EF4444] focus:ring-[#EF4444]/20' : 'border-slate-200 focus:border-[#2563EB] focus:ring-[#2563EB]/20'}`;

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <form onSubmit={submit} noValidate role="dialog" aria-modal="true" aria-labelledby="shopping-from-message-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-5 space-y-4">
        <div className="flex items-center justify-between">
          <h2 id="shopping-from-message-title" className="font-bold text-slate-800">🛒 Auf die Einkaufsliste</h2>
          <button type="button" onClick={onClose} className="text-slate-400 hover:text-slate-600 p-1" aria-label="Schließen">
            <XIcon size={18} />
          </button>
        </div>
        <p className="text-xs text-slate-500 bg-slate-50 rounded-xl px-3 py-2 line-clamp-3">„{message.text}“</p>
        <div>
          <label htmlFor="msg-shopping-name" className="text-xs font-semibold text-slate-600 mb-1.5 block">Artikel</label>
          <input id="msg-shopping-name" value={name} onChange={e => setName(e.target.value)} maxLength={80} autoFocus
            className={input(!!fieldErrors.name)} />
          {fieldErrors.name && <p className="text-xs text-[#DC2626] mt-1">{fieldErrors.name}</p>}
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label htmlFor="msg-shopping-quantity" className="text-xs font-semibold text-slate-600 mb-1.5 block">Menge (optional)</label>
            <input id="msg-shopping-quantity" value={quantity} onChange={e => setQuantity(e.target.value)} maxLength={30}
              placeholder="z. B. 2 × 1 l" className={input(!!fieldErrors.quantity)} />
          </div>
          <div>
            <label htmlFor="msg-shopping-category" className="text-xs font-semibold text-slate-600 mb-1.5 block">Kategorie</label>
            <select id="msg-shopping-category" value={category} onChange={e => setCategory(e.target.value as ShoppingCategory)}
              className={input(false)}>
              {SHOPPING_CATEGORY_KEYS.map(key => (
                <option key={key} value={key}>{SHOPPING_CATEGORIES[key].icon} {SHOPPING_CATEGORIES[key].label}</option>
              ))}
            </select>
          </div>
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input type="checkbox" checked={urgent} onChange={e => setUrgent(e.target.checked)} className="w-4 h-4 accent-[#EF4444]" />
          Dringend
        </label>
        {formError && <p role="alert" className="text-sm text-[#DC2626]">{formError}</p>}
        <div className="flex gap-2">
          <button type="button" onClick={onClose} disabled={busy}
            className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
            Abbrechen
          </button>
          <button type="submit" disabled={busy || !name.trim()}
            className="flex-1 py-2.5 rounded-xl bg-[#22C55E] text-white text-sm font-semibold hover:bg-[#16A34A] disabled:opacity-50">
            {busy ? 'Speichere…' : 'Hinzufügen'}
          </button>
        </div>
      </form>
    </div>
  );
}

// ─── Einzelne Nachricht ──────────────────────────────────────────────────────

interface MessageAction { key: string; label: string; icon: ReactNode; danger?: boolean; run: () => void }

function MessageItem({ message, own, sender, showSender, actions, menuOpen, onToggleMenu }: {
  message: api.ChatMessage;
  own: boolean;
  sender?: CalendarMember;
  // in der Familiengruppe stehen Avatar und Name an fremden Nachrichten
  showSender: boolean;
  actions: MessageAction[];
  menuOpen: boolean;
  onToggleMenu: () => void;
}) {
  return (
    <div className={`group flex items-end gap-2 ${own ? 'justify-end' : 'justify-start'}`}>
      {showSender && !own && <Avatar member={sender} size={28} />}
      <div className={`max-w-[80%] sm:max-w-[70%] flex flex-col ${own ? 'items-end' : 'items-start'}`}>
        {showSender && !own && (
          <span className="text-[11px] font-semibold mb-0.5 ml-1" style={{ color: sender?.color ?? '#64748B' }}>
            {sender?.name ?? FORMER_MEMBER}
          </span>
        )}
        <div className={`relative flex items-center gap-1 ${own ? 'flex-row-reverse' : ''}`}>
          <div data-message-id={message.id}
            className={`px-3.5 py-2 rounded-2xl text-sm leading-relaxed whitespace-pre-wrap break-words min-w-0 ${own
              ? 'bg-[#2563EB] text-white rounded-br-md' : 'bg-slate-100 text-slate-800 rounded-bl-md'}`}>
            {message.text}
          </div>
          {actions.length > 0 && (
            <button type="button" onClick={onToggleMenu} aria-label="Aktionen zur Nachricht" aria-haspopup="menu"
              aria-expanded={menuOpen}
              className={`w-7 h-7 flex-shrink-0 rounded-full text-slate-400 hover:bg-slate-100 hover:text-slate-700 focus:opacity-100 transition-opacity ${menuOpen
                ? 'opacity-100 bg-slate-100' : 'lg:opacity-0 lg:group-hover:opacity-100'}`}>
              ⋯
            </button>
          )}
          {menuOpen && (
            <div role="menu" className={`absolute z-20 top-full mt-1 ${own ? 'right-0' : 'left-0'} w-56 bg-white rounded-xl shadow-xl border border-slate-100 py-1`}>
              {actions.map(a => (
                <button key={a.key} type="button" role="menuitem" onClick={a.run}
                  className={`w-full flex items-center gap-2.5 px-3 py-2 text-sm text-left hover:bg-slate-50 ${a.danger ? 'text-[#DC2626]' : 'text-slate-700'}`}>
                  <span className="w-4 flex justify-center flex-shrink-0">{a.icon}</span>
                  {a.label}
                </button>
              ))}
            </div>
          )}
        </div>
        <span className="text-[10px] text-slate-400 mt-0.5 mx-1">{clockTime(new Date(message.sentAt))}</span>
      </div>
    </div>
  );
}

// ─── Geöffnete Unterhaltung ──────────────────────────────────────────────────

function ChatView({ conversation, title, onBack, onNavigate }: {
  conversation: api.Conversation; title: string; onBack: () => void; onNavigate: Navigate;
}) {
  const me = useMe();
  const { can } = useAuth();
  const { members, memberById } = useCalendarData();
  const { send, remove, markRead } = useMessageData();
  const calendarPerms = useCalendarPermissions();
  const taskPerms = useTaskPermissions();
  const family = conversation.kind === 'family';
  const partner = conversation.partnerId ? memberById(conversation.partnerId) : undefined;

  const [messages, setMessages] = useState<api.ChatMessage[]>([]);
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [loadError, setLoadError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [menuFor, setMenuFor] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);
  const [shoppingFrom, setShoppingFrom] = useState<api.ChatMessage | null>(null);
  const [eventFrom, setEventFrom] = useState<api.ChatMessage | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const latest = useRef(0);

  const reload = useCallback(async () => {
    const call = ++latest.current;
    try {
      const result = await api.listMessages(conversation.id);
      if (call !== latest.current) return;
      setMessages(result);
      setLoadError(null);
      setLoadState('ready');
    } catch (err) {
      if (call !== latest.current) return;
      setLoadError(errorText(err));
      setLoadState('error');
    }
  }, [conversation.id]);

  useEffect(() => { reload(); }, [reload]);
  useAutoRefresh(reload, ['messages']);

  // Ungelesenes gilt als gelesen, sobald die Unterhaltung sichtbar offen ist
  const unread = conversation.unread;
  useEffect(() => {
    if (loadState !== 'ready' || unread === 0) return;
    const markIfVisible = () => {
      if (document.visibilityState === 'visible') markRead(conversation.id).catch(() => {});
    };
    markIfVisible();
    document.addEventListener('visibilitychange', markIfVisible);
    return () => document.removeEventListener('visibilitychange', markIfVisible);
  }, [loadState, unread, conversation.id, markRead]);

  // Neue Nachrichten unten sichtbar halten
  const lastId = messages[messages.length - 1]?.id;
  useLayoutEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [lastId, loadState]);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 6000);
    return () => clearTimeout(timer);
  }, [notice]);

  useEffect(() => {
    if (!menuFor) return;
    const onKey = (e: globalThis.KeyboardEvent) => { if (e.key === 'Escape') { setMenuFor(null); setConfirmDelete(null); } };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuFor]);

  const mayWrite = can('messenger', 'erstellen', family ? 'familie' : 'eigen');
  const mayShop = can('einkauf', 'erstellen', 'familie') || can('einkauf', 'vorschlagen', 'familie');
  const mayDelete = (m: api.ChatMessage) => (m.senderId === me.id
    ? can('messenger', 'loeschen', 'eigen') : family && can('messenger', 'loeschen', 'familie'));

  const closeMenu = () => { setMenuFor(null); setConfirmDelete(null); };

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    const text = draft.trim();
    if (!text || busy) return;
    setBusy(true);
    setActionError(null);
    // Sofort leeren, damit niemand doppelt sendet; bei einem Fehler kommt der Text zurück
    setDraft('');
    try {
      await send(conversation.id, text);
      await reload();
    } catch (err) {
      setDraft(text);
      setActionError(err instanceof ApiError && err.problem.errors?.text ? err.problem.errors.text : errorText(err));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const deleteMessage = async (id: string) => {
    closeMenu();
    try {
      await remove(id);
      await reload();
    } catch (err) {
      setActionError(errorText(err));
    }
  };

  const actionsFor = (m: api.ChatMessage): MessageAction[] => {
    const actions: MessageAction[] = [];
    if (mayShop) {
      actions.push({ key: 'shopping', label: 'Auf die Einkaufsliste', icon: <ShoppingCartIcon size={14} />,
        run: () => { closeMenu(); setShoppingFrom(m); } });
    }
    if (calendarPerms.canAdd) {
      actions.push({ key: 'event', label: calendarPerms.onlyProposals ? 'Als Termin vorschlagen' : 'Als Termin anlegen',
        icon: <CalendarIcon size={14} />, run: () => { closeMenu(); setEventFrom(m); } });
    }
    if (taskPerms.canAdd) {
      actions.push({ key: 'task', label: 'Als Aufgabe anlegen', icon: <CheckSquareIcon size={14} />,
        run: () => onNavigate('tasks', { kind: 'newTask', title: asTitle(m.text, 100) }) });
    }
    if (mayDelete(m)) {
      actions.push(confirmDelete === m.id
        ? { key: 'delete', label: 'Wirklich löschen?', icon: '🗑', danger: true, run: () => deleteMessage(m.id) }
        : { key: 'delete', label: 'Löschen', icon: '🗑', danger: true, run: () => setConfirmDelete(m.id) });
    }
    return actions;
  };

  // Nach Tagen gruppiert
  const days = useMemo(() => {
    const result: { key: string; items: api.ChatMessage[] }[] = [];
    for (const m of messages) {
      const key = toDateKey(new Date(m.sentAt));
      const last = result[result.length - 1];
      if (last?.key === key) last.items.push(m);
      else result.push({ key, items: [m] });
    }
    return result;
  }, [messages]);

  const subtitle = family
    ? members.filter(m => m.effectiveRole !== 'gast').map(m => (m.id === me.id ? 'Du' : m.name)).join(', ')
    : partner ? `${ROLE_NAMES[partner.effectiveRole]} · nur ihr beide seht diesen Chat` : '';

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Kopf */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-slate-100">
        <button type="button" onClick={onBack} className="lg:hidden -ml-1 p-1.5 rounded-lg text-slate-500 hover:bg-slate-100"
          aria-label="Zurück zu den Unterhaltungen">
          <ChevronLeftIcon size={20} />
        </button>
        {family ? <FamilyAvatar size={36} /> : <Avatar member={partner} size={36} />}
        <div className="min-w-0">
          <h2 className="font-semibold text-slate-800 text-sm truncate">{title}</h2>
          <p className="text-xs text-slate-400 truncate">{subtitle}</p>
        </div>
      </div>

      {/* Verlauf */}
      <div ref={listRef} className="flex-1 overflow-y-auto px-4 py-4 space-y-4 min-h-0" aria-live="polite">
        {notice && (
          <div role="status" className="sticky top-0 z-10 flex items-center gap-2 bg-[#F0FDF4] border border-[#BBF7D0] text-[#15803D] text-sm rounded-xl px-3 py-2 shadow-sm">
            <span className="flex-1">✅ {notice}</span>
            <button type="button" onClick={() => setNotice(null)} aria-label="Hinweis schließen" className="p-0.5"><XIcon size={14} /></button>
          </div>
        )}
        {loadState === 'loading' && <p className="text-center text-sm text-slate-400 py-10">Lade Nachrichten…</p>}
        {loadState === 'error' && <p role="alert" className="text-center text-sm text-[#DC2626] py-10">{loadError}</p>}
        {loadState === 'ready' && messages.length === 0 && (
          <div className="text-center text-slate-400 py-16">
            <div className="text-4xl mb-3">💬</div>
            <p className="text-sm">Noch keine Nachrichten.{mayWrite && ' Schreib die erste!'}</p>
          </div>
        )}
        {days.map(day => (
          <div key={day.key} className="space-y-2.5">
            <div className="flex justify-center">
              <span className="text-[11px] font-medium text-slate-500 bg-slate-100 rounded-full px-3 py-0.5">{dayLabel(day.key)}</span>
            </div>
            {day.items.map(m => (
              <MessageItem key={m.id} message={m} own={m.senderId === me.id} sender={memberById(m.senderId)}
                showSender={family} actions={actionsFor(m)} menuOpen={menuFor === m.id}
                onToggleMenu={() => { setConfirmDelete(null); setMenuFor(menuFor === m.id ? null : m.id); }} />
            ))}
          </div>
        ))}
      </div>
      {/* Klick daneben schließt das Aktionsmenü */}
      {menuFor && <div className="fixed inset-0 z-10" onClick={closeMenu} aria-hidden="true" />}

      {/* Eingabe */}
      <div className="border-t border-slate-100 p-3">
        {actionError && <p role="alert" className="text-xs text-[#DC2626] mb-2">{actionError}</p>}
        {mayWrite ? (
          <form onSubmit={submit} className="flex items-end gap-2">
            <label htmlFor="message-input" className="sr-only">Nachricht an {title}</label>
            <textarea id="message-input" ref={inputRef} rows={1} value={draft} maxLength={MAX_LENGTH}
              onChange={e => setDraft(e.target.value)} onKeyDown={onKeyDown}
              placeholder={family ? 'Nachricht an die Familie…' : `Nachricht an ${title}…`}
              className="flex-1 resize-none max-h-32 border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
              style={{ height: `${Math.min(8, Math.max(1, draft.split('\n').length)) * 20 + 22}px` }} />
            <button type="submit" disabled={busy || !draft.trim()} aria-label="Senden"
              className="w-11 h-11 flex-shrink-0 bg-[#2563EB] text-white rounded-xl flex items-center justify-center hover:bg-[#1D4ED8] disabled:opacity-50 transition-colors">
              <SendIcon size={16} />
            </button>
          </form>
        ) : (
          <p className="text-xs text-slate-400 text-center py-2">Hier kannst du nur mitlesen.</p>
        )}
        {mayWrite && draft.length > MAX_LENGTH - 200 && (
          <p className="text-[10px] text-slate-400 text-right mt-1">{draft.length} / {MAX_LENGTH}</p>
        )}
        {mayWrite && <p className="hidden lg:block text-[10px] text-slate-400 mt-1">Enter sendet, Umschalt + Enter für eine neue Zeile.</p>}
      </div>

      {shoppingFrom && (
        <ShoppingFromMessage message={shoppingFrom} onClose={() => setShoppingFrom(null)}
          onDone={text => { setShoppingFrom(null); setNotice(text); }} />
      )}
      {eventFrom && (
        <EventFormModal defaultDate={toDateKey(startOfToday())} defaultTitle={asTitle(eventFrom.text, 100)}
          onClose={() => setEventFrom(null)} />
      )}
    </div>
  );
}

// ─── Seite ───────────────────────────────────────────────────────────────────

export default function Messenger({ onNavigate }: Props) {
  const { can } = useAuth();
  const { conversations, status, error } = useMessageData();
  const title = useConversationTitle();
  const desktop = useIsDesktop();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [chatOpenOnMobile, setChatOpenOnMobile] = useState(false);
  const [search, setSearch] = useState('');

  const sorted = useMemo(() => sortConversations(conversations, title), [conversations, title]);

  // Beim ersten Laden die erste Unterhaltung mit Ungelesenem öffnen, sonst die Familiengruppe
  useEffect(() => {
    if (selectedId !== null || sorted.length === 0) return;
    setSelectedId((sorted.find(c => c.unread > 0) ?? sorted[0]).id);
  }, [selectedId, sorted]);

  if (!can('messenger', 'ansehen', 'eigen')) {
    return (
      <div className="p-4 lg:p-6 max-w-[900px] mx-auto">
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-10 text-center text-slate-400">
          <div className="text-4xl mb-3">💬</div>
          Nachrichten sind für dich nicht freigegeben.
        </div>
      </div>
    );
  }

  const selected = sorted.find(c => c.id === selectedId) ?? sorted[0];
  const query = search.trim().toLowerCase();
  const visible = query
    ? sorted.filter(c => title(c).toLowerCase().includes(query) || c.lastMessage?.text.toLowerCase().includes(query))
    : sorted;
  const showChat = desktop || chatOpenOnMobile;
  const panel = 'bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden flex-col h-[calc(100dvh-170px)] lg:h-[calc(100dvh-125px)] min-h-[420px]';

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {error && <p role="alert" className="mb-4 text-sm text-[#DC2626] bg-[#FEF2F2] border border-[#FECACA] rounded-xl px-3 py-2">{error}</p>}
      <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] gap-5">
        <section aria-label="Unterhaltungen" className={`${panel} ${showChat && !desktop ? 'hidden' : 'flex'}`}>
          <div className="p-3 border-b border-slate-100">
            <div className="flex items-center gap-2 bg-slate-50 rounded-xl px-3 py-2">
              <SearchIcon size={14} className="text-slate-400 flex-shrink-0" />
              <label htmlFor="conversation-search" className="sr-only">Unterhaltungen durchsuchen</label>
              <input id="conversation-search" value={search} onChange={e => setSearch(e.target.value)} placeholder="Suchen…"
                className="flex-1 bg-transparent text-sm text-slate-800 placeholder-slate-400 focus:outline-none" />
            </div>
          </div>
          <div className="flex-1 overflow-y-auto divide-y divide-slate-50">
            {status === 'loading' && <p className="p-6 text-center text-sm text-slate-400">Lade Unterhaltungen…</p>}
            {status !== 'loading' && visible.length === 0 && (
              <p className="p-6 text-center text-sm text-slate-400">{query ? 'Nichts gefunden.' : 'Noch keine Unterhaltungen.'}</p>
            )}
            {visible.map(c => (
              <ConversationRow key={c.id} conversation={c} title={title(c)} selected={desktop && c.id === selected?.id}
                onSelect={() => { setSelectedId(c.id); setChatOpenOnMobile(true); }} />
            ))}
          </div>
        </section>

        {showChat && (
          <section aria-label="Unterhaltung" className={`${panel} flex`}>
            {selected
              ? <ChatView key={selected.id} conversation={selected} title={title(selected)}
                  onBack={() => setChatOpenOnMobile(false)} onNavigate={onNavigate} />
              : (
                <div className="flex-1 flex items-center justify-center text-slate-300 text-sm">
                  {status === 'loading' ? 'Lade…' : 'Wähle eine Unterhaltung.'}
                </div>
              )}
          </section>
        )}
      </div>
    </div>
  );
}
