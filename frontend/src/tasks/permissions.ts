import { useAuth, useMe } from '../auth/AuthContext';
import type { ApiTask } from './api';
import { isOpenRound } from './repeat';

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
  // Bonus-Aufgaben übernehmen Kinder und Jugendliche (wie TaskAccess.canClaim)
  const mayClaim = (me.effectiveRole === 'kind' || me.effectiveRole === 'jugendlicher') && mayTickOwn;
  const isOwnTask = (task: ApiTask) => isAssigned(task) && task.createdBy === me.id;

  return {
    me,
    mayConfirm,
    maySeeFamilyTasks: can('aufgaben', 'ansehen', 'familie'),
    // Abgeschlossene Aufgaben der ganzen Familie gesammelt löschen
    mayDeleteCompleted: mayDeleteFamily,
    canAdd: mayCreateOwn || mayCreateFamily,
    canAssignTo: (memberId: string) => (memberId === me.id ? mayCreateOwn : mayCreateFamily),
    canEdit: (task: ApiTask) => task.status !== 'confirmed' && (isOwnTask(task) ? mayCreateOwn : mayCreateFamily),
    // Offene Runden wiederkehrender Aufgaben hakt jeder ab, der eigene Aufgaben abhaken darf (übernimmt sie damit)
    canTick: (task: ApiTask) => task.status !== 'confirmed'
      && (isOpenRound(task) ? mayTickOwn
        : task.assigneeId !== null && (isAssigned(task) ? mayTickOwn : mayTickFamily)),
    mayClaim,
    // Offene Runden übernehmen alle, die eigene Aufgaben abhaken dürfen, auch Erwachsene (TaskAccess.canTakeRounds)
    mayTakeRounds: mayTickOwn,
    // Wiederholung einstellen nur für die ganze Familie, weil jede neue Runde für alle offen ist
    mayRepeat: mayCreateFamily,
    // Zurückgeben: wer sie übernommen hat, oder ein Administrator, solange sie nicht erledigt ist
    canRelease: (task: ApiTask) => (task.bonus || !!task.repeat) && task.assigneeId !== null
      && (task.status === 'todo' || task.status === 'inprogress')
      && (isAssigned(task) || (task.bonus ? mayConfirm : mayCreateFamily)),
    canDelete: (task: ApiTask) => (isOwnTask(task) ? mayDeleteOwn : mayDeleteFamily),
  };
}
