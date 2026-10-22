import { request } from '../api/client';

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
