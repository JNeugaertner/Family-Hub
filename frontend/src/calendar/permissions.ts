import { useAuth, useMe } from '../auth/AuthContext';
import type { CalendarEvent } from '../components/data';

// Spiegelt die Regeln aus CalendarAccess (Backend), damit das UI nur anbietet, was erlaubt ist.
// Maßgeblich bleibt das Backend: Es prüft jede Anfrage selbst.
export function useCalendarPermissions() {
  const me = useMe();
  const { can } = useAuth();

  const mayCreateOwn = can('kalender', 'erstellen', 'eigen');
  const mayCreateFamily = can('kalender', 'erstellen', 'familie');
  const mayPropose = can('kalender', 'vorschlagen', 'familie');
  const mayDecide = can('kalender', 'freigeben', 'familie');
  const mayEditFamily = can('kalender', 'bearbeiten', 'familie');

  const isOwn = (memberId: string) => memberId === me.id;

  return {
    me,
    mayDecide,
    mayEditFamily,
    canAdd: mayCreateOwn || mayCreateFamily || mayPropose,
    // Darf nichts selbst anlegen, nur Termine für andere vorschlagen
    onlyProposals: !mayCreateOwn && !mayCreateFamily && mayPropose,

    // Für wen darf ich einen Termin eintragen (direkt oder als Vorschlag)?
    canAssignTo: (memberId: string) =>
      isOwn(memberId) ? mayCreateOwn : mayCreateFamily || mayPropose,

    // Termine für andere werden zum Vorschlag, wenn ich dort nur vorschlagen darf.
    becomesProposal: (memberId: string) => !isOwn(memberId) && !mayCreateFamily && mayPropose,

    canEdit: (event: CalendarEvent) =>
      event.status === 'proposed'
        ? (event.createdBy === me.id && mayPropose) || mayEditFamily
        : can('kalender', 'bearbeiten', isOwn(event.memberId) ? 'eigen' : 'familie'),

    canDelete: (event: CalendarEvent) =>
      event.status === 'proposed'
        ? event.createdBy === me.id || mayDecide
        : can('kalender', 'loeschen', isOwn(event.memberId) ? 'eigen' : 'familie'),
  };
}
