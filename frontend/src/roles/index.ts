// Rollen- und Rechtemodell von FamilyHub AI. Welche Rechte eine Person hat, entscheidet das Backend
// (GET /api/auth/me liefert sie mit); hier stehen nur die gemeinsamen Typen und Hilfsfunktionen.

export type Module =
  | 'familie'
  | 'kalender'
  | 'aufgaben'
  | 'punkte'
  | 'einkauf'
  | 'essen'
  | 'wetter'
  | 'muell'
  | 'fahrzeit'
  | 'messenger'
  | 'sprachassistent'
  | 'system';

export type Action =
  | 'ansehen'
  | 'erstellen'
  | 'bearbeiten'
  | 'loeschen'
  | 'vorschlagen'
  | 'freigeben'
  | 'verwalten';

// Von eng nach weit: ein weiterer Geltungsbereich deckt die engeren mit ab.
export type Scope = 'eigen' | 'freigegeben' | 'familie';

const SCOPE_RANK: Record<Scope, number> = { eigen: 0, freigegeben: 1, familie: 2 };

export interface Permission {
  module: Module;
  action: Action;
  scope: Scope;
}

export type RoleId = 'administrator' | 'jugendlicher' | 'kind' | 'gast' | 'ki_agent';

export const ROLE_NAMES: Record<RoleId, string> = {
  administrator: 'Administrator',
  jugendlicher: 'Jugendlicher',
  kind: 'Kind',
  gast: 'Gast',
  ki_agent: 'KI-Agent',
};

export function hasPermission(permissions: Permission[], module: Module, action: Action, scope: Scope): boolean {
  return permissions.some(p => p.module === module && p.action === action && SCOPE_RANK[p.scope] >= SCOPE_RANK[scope]);
}

export const permissionKey = (p: Permission) => `${p.module}.${p.action}.${p.scope}`;

// --- Freigabe-Workflow für Module ohne Backend (Essensplanung, Punkte) --------------------------
// Vorschläge durchlaufen den Status vorschlag -> freigegeben/abgelehnt. Ob jemand entscheiden darf,
// prüft die aufrufende Seite über can(modul, 'freigeben', 'familie').

export type ApprovalStatus = 'vorschlag' | 'freigegeben' | 'abgelehnt';

export interface Suggestion<T> {
  id: number;
  module: Module;
  payload: T;
  createdBy: string;
  status: ApprovalStatus;
  decidedBy?: string;
}

let suggestionCounter = 1;

export function createSuggestion<T>(module: Module, payload: T, createdBy: string): Suggestion<T> {
  return { id: suggestionCounter++, module, payload, createdBy, status: 'vorschlag' };
}

export function decide<T>(suggestion: Suggestion<T>, decidedBy: string, approve: boolean): Suggestion<T> {
  return { ...suggestion, status: approve ? 'freigegeben' : 'abgelehnt', decidedBy };
}
