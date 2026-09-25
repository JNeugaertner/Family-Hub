import { useAuth, useMe } from '../auth/AuthContext';
import type { ApiTask } from './api';

// Spiegelt die Regeln aus TaskAccess (Backend), damit das UI nur anbietet, was erlaubt ist.
// Eigene Aufgabe zum Abhaken: mir zugewiesen. Zum Ändern und Löschen: mir zugewiesen und von mir angelegt.
export function useTaskPermissions() {
  const me = useMe();
  const { can } = useAuth();

  const mayCreateOwn = can('aufgaben', 'erstellen', 'eigen');
  const mayCreateFamily = can('aufgaben', 'erstellen', 'familie');
  const mayTickOwn = can('aufgaben', 'bearbeiten', 'eigen');
  const mayTickFamily = can('aufgaben', 'bearbeiten', 'familie');
  const mayDeleteOwn = can('aufgaben', 'loeschen', 'eigen');
  const mayDeleteFamily = can('aufgaben', 'loeschen', 'familie');
  const mayConfirm = can('punkte', 'freigeben', 'familie');

  const isAssigned = (task: ApiTask) => task.assigneeId === me.id;
  const isOwnTask = (task: ApiTask) => isAssigned(task) && task.createdBy === me.id;

  return {
    me,
    mayConfirm,
    maySeeFamilyTasks: can('aufgaben', 'ansehen', 'familie'),
    canAdd: mayCreateOwn || mayCreateFamily,
    canAssignTo: (memberId: string) => (memberId === me.id ? mayCreateOwn : mayCreateFamily),
    canEdit: (task: ApiTask) => task.status !== 'confirmed' && (isOwnTask(task) ? mayCreateOwn : mayCreateFamily),
    canTick: (task: ApiTask) => task.status !== 'confirmed' && (isAssigned(task) ? mayTickOwn : mayTickFamily),
    canDelete: (task: ApiTask) => (isOwnTask(task) ? mayDeleteOwn : mayDeleteFamily),
  };
}
