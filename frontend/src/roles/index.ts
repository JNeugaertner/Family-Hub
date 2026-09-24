// Rollen- und Berechtigungsmodell von FamilyHub AI, uebertragen aus dem
// Java-Konzeptprototyp (FamilyHub-Rollenkonzept-Prototyp) in dieses
// React/TypeScript-Frontend. Quelle der fachlichen Regeln: README des
// Family-Hub-Repos, Abschnitte 2-7.

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

export type Scope = 'eigen' | 'freigegeben' | 'familie' | 'system';

// Rangfolge der Geltungsbereiche: eine Berechtigung auf breiterem Scope
// deckt automatisch jeden engeren Scope mit ab.
const SCOPE_RANK: Record<Scope, number> = {
  eigen: 0,
  freigegeben: 1,
  familie: 2,
  system: 3,
};

function scopeCovers(granted: Scope, requested: Scope): boolean {
  return SCOPE_RANK[granted] >= SCOPE_RANK[requested];
}

export interface Permission {
  module: Module;
  action: Action;
  scope: Scope;
}

function perm(module: Module, action: Action, scope: Scope): Permission {
  return { module, action, scope };
}

export type RoleId = 'administrator' | 'jugendlicher' | 'kind' | 'gast';

export interface Role {
  id: RoleId;
  name: string;
  permissions: Permission[];
}

function hasPermission(permissions: Permission[], module: Module, action: Action, scope: Scope): boolean {
  return permissions.some(p => p.module === module && p.action === action && scopeCovers(p.scope, scope));
}

export function roleHasPermission(role: Role, module: Module, action: Action, scope: Scope): boolean {
  return hasPermission(role.permissions, module, action, scope);
}

const ALL_MODULES: Module[] = [
  'familie', 'kalender', 'aufgaben', 'punkte', 'einkauf', 'essen',
  'wetter', 'muell', 'fahrzeit', 'messenger', 'sprachassistent', 'system',
];
const ALL_ACTIONS: Action[] = [
  'ansehen', 'erstellen', 'bearbeiten', 'loeschen', 'vorschlagen', 'freigeben', 'verwalten',
];

// Standardrollen aus der Berechtigungsmatrix. Der Administrator bekommt
// Vollzugriff (Scope 'system') auf jede Modul/Aktion-Kombination.
export const STANDARD_ROLES: Record<RoleId, Role> = {
  administrator: {
    id: 'administrator',
    name: 'Administrator',
    permissions: ALL_MODULES.flatMap(module => ALL_ACTIONS.map(action => perm(module, action, 'system'))),
  },
  jugendlicher: {
    id: 'jugendlicher',
    name: 'Jugendlicher',
    permissions: [
      perm('kalender', 'ansehen', 'eigen'),
      perm('kalender', 'erstellen', 'eigen'),
      perm('kalender', 'bearbeiten', 'eigen'),
      perm('kalender', 'loeschen', 'eigen'),
      perm('kalender', 'vorschlagen', 'familie'),
      perm('aufgaben', 'ansehen', 'eigen'),
      perm('aufgaben', 'bearbeiten', 'eigen'),
      perm('aufgaben', 'erstellen', 'eigen'),
      perm('punkte', 'ansehen', 'familie'),
      perm('einkauf', 'bearbeiten', 'familie'),
      perm('essen', 'bearbeiten', 'familie'),
      perm('essen', 'vorschlagen', 'familie'),
      perm('wetter', 'ansehen', 'familie'),
      perm('muell', 'ansehen', 'familie'),
    ],
  },
  kind: {
    id: 'kind',
    name: 'Kind',
    permissions: [
      perm('kalender', 'ansehen', 'eigen'),
      perm('aufgaben', 'ansehen', 'eigen'),
      perm('aufgaben', 'bearbeiten', 'eigen'),
      perm('punkte', 'ansehen', 'eigen'),
      perm('einkauf', 'vorschlagen', 'eigen'),
      perm('essen', 'vorschlagen', 'eigen'),
      perm('wetter', 'ansehen', 'eigen'),
      perm('muell', 'ansehen', 'freigegeben'),
    ],
  },
  gast: {
    id: 'gast',
    name: 'Gast',
    permissions: [
      perm('kalender', 'ansehen', 'freigegeben'),
      perm('aufgaben', 'ansehen', 'freigegeben'),
      perm('wetter', 'ansehen', 'freigegeben'),
      perm('muell', 'ansehen', 'freigegeben'),
    ],
  },
};

export interface Actor {
  name: string;
  roleId: RoleId;
  overrides?: Permission[];
}

// Zentrale Pruefregel: eine Aktion ist erlaubt, wenn die Basisrolle sie
// abdeckt ODER ein individueller Einzel-Override (README Abschnitt 3) sie
// abdeckt.
export function isAllowed(actor: Actor, module: Module, action: Action, scope: Scope): boolean {
  const role = STANDARD_ROLES[actor.roleId];
  if (roleHasPermission(role, module, action, scope)) return true;
  return hasPermission(actor.overrides ?? [], module, action, scope);
}

// Altersuebergang Kind -> Jugendlicher, automatisiert ab konfigurierbarem
// Alter (Entscheidung vom 2026-09-22, Richtwert 13). Eltern koennen die
// vorgeschlagene Rolle jederzeit manuell ueberschreiben.
export const JUGENDLICHER_AB_ALTER = 13;

export function calculateAge(birthDateIso: string, today: Date = new Date('2026-09-21')): number {
  const birth = new Date(birthDateIso);
  let age = today.getFullYear() - birth.getFullYear();
  const monthDiff = today.getMonth() - birth.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < birth.getDate())) {
    age--;
  }
  return age;
}

export function deriveRoleId(birthDateIso: string | undefined, manualOverride?: RoleId): RoleId {
  if (manualOverride) return manualOverride;
  if (!birthDateIso) return 'kind';
  return calculateAge(birthDateIso) >= JUGENDLICHER_AB_ALTER ? 'jugendlicher' : 'kind';
}

// --- Freigabe-Workflow -----------------------------------------------
// Vorschlaege (von der KI oder von Jugendlichen) durchlaufen einen
// Status: entwurf -> vorschlag -> freigegeben/abgelehnt. Nur wer im
// betroffenen Modul die 'freigeben'-Berechtigung im Scope 'familie' hat,
// darf einen Vorschlag entscheiden.

export type ApprovalStatus = 'entwurf' | 'vorschlag' | 'freigegeben' | 'abgelehnt';

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
  return {
    id: suggestionCounter++,
    module,
    payload,
    createdBy,
    status: 'vorschlag',
  };
}

export function canDecide(actor: Actor, suggestion: Suggestion<unknown>): boolean {
  return isAllowed(actor, suggestion.module, 'freigeben', 'familie');
}

export function decide<T>(suggestion: Suggestion<T>, actor: Actor, approve: boolean): Suggestion<T> {
  if (!canDecide(actor, suggestion)) {
    throw new Error(`${actor.name} (${STANDARD_ROLES[actor.roleId].name}) darf in ${suggestion.module} keine Vorschlaege entscheiden.`);
  }
  return { ...suggestion, status: approve ? 'freigegeben' : 'abgelehnt', decidedBy: actor.name };
}
