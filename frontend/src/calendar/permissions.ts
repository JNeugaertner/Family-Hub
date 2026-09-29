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
  // Nur ich bin beteiligt: dann gelten die Rechte für eigene Termine, sonst die für Familientermine
  const onlyMe = (memberIds: string[]) => memberIds.length === 1 && isOwn(memberIds[0]);

  return {
    me,
    mayDecide,
    mayEditFamily,
    canAdd: mayCreateOwn || mayCreateFamily || mayPropose,
    // Darf nichts selbst anlegen, nur Termine für andere vorschlagen
    onlyProposals: !mayCreateOwn && !mayCreateFamily && mayPropose,

    // Wen darf ich zu einem Termin eintragen (direkt oder als Vorschlag)?
    canAssignTo: (memberId: string) =>
      isOwn(memberId) ? mayCreateOwn : mayCreateFamily || mayPropose,

    // Termine mit anderen werden zum Vorschlag, wenn ich dort nur vorschlagen darf.
    becomesProposal: (memberIds: string[]) => !onlyMe(memberIds) && !mayCreateFamily && mayPropose,

    // Google-Termine sind für alle schreibgeschützt; geändert wird in Google.
    canEdit: (event: CalendarEvent) =>
      !event.source && (event.status === 'proposed'
        ? (event.createdBy === me.id && mayPropose) || mayEditFamily
        : can('kalender', 'bearbeiten', onlyMe(event.memberIds) ? 'eigen' : 'familie')),

    canDelete: (event: CalendarEvent) =>
      !event.source && (event.status === 'proposed'
        ? event.createdBy === me.id || mayDecide
        : can('kalender', 'loeschen', onlyMe(event.memberIds) ? 'eigen' : 'familie')),
  };
}
