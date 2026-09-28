import { useCalendarData, type CalendarMember } from '../calendar/CalendarDataContext';
import { useTaskData } from '../tasks/TaskDataContext';

export interface PointHolder extends CalendarMember {
  points: number;
}

// Kinder und Jugendliche mit ihrem Punktestand, soweit die angemeldete Person ihn sehen darf
// (Kinder nur sich selbst, Jugendliche und Administratoren alle), höchster Stand zuerst.
export function usePointHolders(): PointHolder[] {
  const { members } = useCalendarData();
  const { balances } = useTaskData();
  return members
    .filter(m => (m.effectiveRole === 'kind' || m.effectiveRole === 'jugendlicher') && m.id in balances)
    .map(m => ({ ...m, points: balances[m.id] }))
    .sort((a, b) => b.points - a.points);
}

// "vor 2 Std.", "Gestern", "vor 5 Tagen" für die Punkte-Historie
export function formatAgo(iso: string, now = new Date()): string {
  const minutes = Math.round((now.getTime() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'gerade eben';
  if (minutes < 60) return `vor ${minutes} Min.`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `vor ${hours} Std.`;
  const days = Math.round(hours / 24);
  return days === 1 ? 'Gestern' : `vor ${days} Tagen`;
}
