import { json, request } from '../api/client';

export interface AssistantStatus {
  // false: Auf dem Server fehlt der API-Schlüssel oder das Modell (README, „KI-Assistent einrichten“)
  configured: boolean;
  // false: Ein Administrator hat den Assistenten für alle abgeschaltet (Admin-Karte unter „Familie“)
  enabled: boolean;
  provider: string;
  model: string | null;
  // Fragen pro Tag für die angemeldete Person, 0 = unbegrenzt
  dailyLimit: number;
}

// Eine Nachricht im Chat; bei Antworten nennt sources die Bereiche, in denen der Assistent nachgeschlagen hat, und
// drafts die Entwürfe, die er vorbereitet hat (abgelaufene fehlen). inbound: eine Nachricht, die die Person FamilyHub
// per WhatsApp oder Telegram geschickt hat; sources nennt dann den Kanal.
export interface ChatTurn {
  role: 'user' | 'assistant' | 'inbound';
  text: string;
  at: string;
  sources: string[];
  drafts: Draft[];
}

export type DraftKind = 'event' | 'task' | 'shopping' | 'meal' | 'message';

// Ein Eintrag eines Entwurfs (Backend: DraftItem). Die Felder heißen wie in der API des Bereichs, z. B. title, start
// und memberIds bei Terminen oder name und quantity bei der Einkaufsliste.
export interface DraftItem {
  action: 'create' | 'update' | 'delete';
  // update und delete: der betroffene Eintrag
  targetId: string | null;
  // create: der neue Eintrag; update: nur die geänderten Felder; delete: leer
  data: Record<string, unknown>;
  // update und delete: der Eintrag, wie er beim Entwerfen war
  before: Record<string, unknown>;
  // open: noch zu entscheiden; applied: übernommen; discarded: verworfen
  state: 'open' | 'applied' | 'discarded';
  // nach dem Übernehmen: proposed, wenn daraus ein Vorschlag (Wunsch) wurde
  outcome: 'approved' | 'proposed' | null;
  // warum das letzte Übernehmen nicht ging; der Eintrag bleibt dann offen
  error: string | null;
}

// Ein Entwurf des Assistenten: Erst wenn die Person ihn übernimmt, ändert sich etwas, mit ihren Rechten
export interface Draft {
  id: string;
  kind: DraftKind;
  items: DraftItem[];
  createdAt: string;
}

// Ein Eintrag, der übernommen werden soll, mit den auf der Karte geänderten Feldern
export interface DraftChoice {
  index: number;
  changes?: Record<string, unknown>;
}

export const getAssistantStatus = () => request<AssistantStatus>('/api/assistant/status');

// Eigener Verlauf, älteste Nachricht zuerst; wird 30 Tage nach der letzten Nachricht gelöscht
export const getConversation = () => request<{ turns: ChatTurn[] }>('/api/assistant/conversation');

// Antwortet nach dem Nachschlagen; kann ein paar Sekunden dauern
export const askAssistant = (text: string) =>
  request<ChatTurn>('/api/assistant/conversation/messages', { method: 'POST', body: json({ text }) });

export const clearConversation = () => request<void>('/api/assistant/conversation', { method: 'DELETE' });

// Übernimmt die gewählten Einträge; nicht gewählte offene werden verworfen
export const acceptDraft = (id: string, items: DraftChoice[]) =>
  request<Draft>(`/api/assistant/drafts/${id}/accept`, { method: 'POST', body: json({ items }) });

export const discardDraft = (id: string) => request<Draft>(`/api/assistant/drafts/${id}/discard`, { method: 'POST' });

// ─── Admin-Karte (nur Administratoren) ───────────────────────────────────────

export type AssistantArea = 'kalender' | 'aufgaben' | 'einkauf' | 'essen' | 'wetter_muell' | 'nachrichten';

// Fragen pro Tag und Person je Rolle; 0 = unbegrenzt
export interface DailyLimits {
  kind: number;
  jugendlicher: number;
  administrator: number;
}

export interface AssistantSettings {
  enabled: boolean;
  areas: AssistantArea[];
  // WhatsApp- und Telegram-Nachrichten von Familienmitgliedern automatisch zu Entwürfen auswerten
  inboxDrafts: boolean;
  dailyLimits: DailyLimits;
}

export interface MemberUsage {
  memberId: string;
  name: string;
  questions: number;
  // Aufrufe beim Anbieter; eine Frage braucht oft mehrere
  calls: number;
  failedCalls: number;
  inputTokens: number;
  outputTokens: number;
}

export interface AssistantUsage {
  from: string;
  to: string;
  members: MemberUsage[];
}

export interface ConnectionTestResult {
  reply: string;
  // kann das Modell Werkzeuge aufrufen?
  toolCalling: boolean;
  model: string;
  inputTokens: number;
  outputTokens: number;
}

export const getAssistantSettings = () => request<AssistantSettings>('/api/assistant/settings');

export const updateAssistantSettings = (settings: AssistantSettings) =>
  request<AssistantSettings>('/api/assistant/settings', { method: 'PUT', body: json(settings) });

// Die letzten 7 Tage, ohne Inhalte
export const getAssistantUsage = () => request<AssistantUsage>('/api/assistant/usage');

// Kostet zwei kleine Aufrufe beim Anbieter
export const testAssistant = () => request<ConnectionTestResult>('/api/assistant/test', { method: 'POST' });

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
