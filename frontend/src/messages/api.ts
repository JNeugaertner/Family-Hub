import { json, request } from '../api/client';

// Kennung der Familiengruppe; Einzelchats haben die Kennung "direct:<id>:<id>" (liefert das Backend)
export const FAMILY = 'family';

export interface ChatMessage {
  id: string;
  conversation: string;
  senderId: string;
  text: string;
  // Ortszeit des Backends ohne Zeitzone, z. B. 2026-10-01T14:32:10.123
  sentAt: string;
}

export interface Conversation {
  id: string;
  kind: 'family' | 'direct';
  // nur bei Einzelchats
  partnerId: string | null;
  // fehlt, solange niemand geschrieben hat
  lastMessage: ChatMessage | null;
  unread: number;
}

export const listConversations = () => request<Conversation[]>('/api/messages/conversations');

export const listMessages = (conversation: string) =>
  request<ChatMessage[]>(`/api/messages?conversation=${encodeURIComponent(conversation)}`);

export const sendMessage = (conversation: string, text: string) =>
  request<ChatMessage>('/api/messages', { method: 'POST', body: json({ conversation, text }) });

export const deleteMessage = (id: string) => request<void>(`/api/messages/${id}`, { method: 'DELETE' });

export const markConversationRead = (conversation: string) =>
  request<void>('/api/messages/read', { method: 'POST', body: json({ conversation }) });
