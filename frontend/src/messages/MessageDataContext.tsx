import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { useAuth } from '../auth/AuthContext';
import * as api from './api';

// Unterhaltungen mit Ungelesen-Zähler; die Seitenleiste zeigt daraus die Zahl an "Nachrichten".
// Die Nachrichten selbst lädt die geöffnete Unterhaltung (Messenger.tsx).
interface MessageData {
  conversations: api.Conversation[];
  unreadTotal: number;
  status: 'loading' | 'ready' | 'error';
  error: string | null;
  reload: () => Promise<void>;
  send: (conversation: string, text: string) => Promise<api.ChatMessage>;
  remove: (id: string) => Promise<void>;
  markRead: (conversation: string) => Promise<void>;
}

const MessageDataContext = createContext<MessageData | null>(null);

export function MessageDataProvider({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  const mayView = can('messenger', 'ansehen', 'eigen');
  const [conversations, setConversations] = useState<api.Conversation[]>([]);
  const [status, setStatus] = useState<MessageData['status']>('loading');
  const [error, setError] = useState<string | null>(null);

  // Nur die zuletzt gestartete Abfrage zählt (Hintergrund-Aktualisierung und Neuladen nach Änderungen)
  const latest = useRef(0);

  const reload = useCallback(async () => {
    if (!mayView) return;
    const call = ++latest.current;
    try {
      const result = await api.listConversations();
      if (call !== latest.current) return;
      setConversations(result);
      setError(null);
      setStatus('ready');
    } catch (err) {
      if (call !== latest.current) return;
      setError(err instanceof Error ? err.message : String(err));
      setStatus('error');
    }
  }, [mayView]);

  useEffect(() => { reload(); }, [reload]);
  // Neue Mitglieder bekommen einen Einzelchat
  useAutoRefresh(reload, ['messages', 'members']);

  const afterChange = useCallback(<A extends unknown[], R>(action: (...args: A) => Promise<R>) =>
    async (...args: A) => {
      const result = await action(...args);
      await reload();
      return result;
    }, [reload]);

  const value = useMemo<MessageData>(() => ({
    conversations,
    unreadTotal: conversations.reduce((sum, c) => sum + c.unread, 0),
    status, error, reload,
    send: afterChange(api.sendMessage),
    remove: afterChange(api.deleteMessage),
    markRead: afterChange(api.markConversationRead),
  }), [conversations, status, error, reload, afterChange]);

  return <MessageDataContext.Provider value={value}>{children}</MessageDataContext.Provider>;
}

export function useMessageData(): MessageData {
  const ctx = useContext(MessageDataContext);
  if (!ctx) throw new Error('useMessageData muss innerhalb von MessageDataProvider verwendet werden');
  return ctx;
}
