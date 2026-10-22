import { json, request } from '../api/client';

export interface AssistantStatus {
  // false: Auf dem Server fehlt der API-Schlüssel oder das Modell (README, „KI-Assistent einrichten“)
  configured: boolean;
  provider: string;
  model: string | null;
}

// Eine Nachricht im Chat; bei Antworten nennt sources die Bereiche, in denen der Assistent nachgeschlagen hat
export interface ChatTurn {
  role: 'user' | 'assistant';
  text: string;
  at: string;
  sources: string[];
}

export const getAssistantStatus = () => request<AssistantStatus>('/api/assistant/status');

// Eigener Verlauf, älteste Nachricht zuerst; wird 30 Tage nach der letzten Nachricht gelöscht
export const getConversation = () => request<{ turns: ChatTurn[] }>('/api/assistant/conversation');

// Antwortet nach dem Nachschlagen; kann ein paar Sekunden dauern
export const askAssistant = (text: string) =>
  request<ChatTurn>('/api/assistant/conversation/messages', { method: 'POST', body: json({ text }) });

export const clearConversation = () => request<void>('/api/assistant/conversation', { method: 'DELETE' });

export type InsightType = 'conflict' | 'proposal_conflict' | 'task_overdue' | 'task_due_today';

// Ein Hinweis ohne KI (Phase 2): fertig formulierter Text; eventIds bzw. taskId sind Sprungziele
export interface Insight {
  type: InsightType;
  // JJJJ-MM-TT: Tag des Konflikts bzw. Frist der Aufgabe
  date: string;
  text: string;
  memberIds: string[];
  eventIds: string[];
  taskId: string | null;
}

export interface ConflictEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  // offener Vorschlag
  proposal: boolean;
  createdBy: string | null;
}

// Zwei Termine überschneiden sich für dieselben Personen
export interface EventConflict {
  memberIds: string[];
  first: ConflictEvent;
  second: ConflictEvent;
}

// Jede Person bekommt, was sie selbst betrifft, Administratoren alles (Backend: InsightService)
export const getInsights = () => request<Insight[]>('/api/assistant/insights');

// Ohne Zeitraum ab heute für ein Jahr
export const getConflicts = () => request<EventConflict[]>('/api/assistant/conflicts');
