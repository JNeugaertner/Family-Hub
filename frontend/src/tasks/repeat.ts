import type { ApiTask, RepeatUnit, TaskRepeat } from './api';

// Aufgaben, die sich nach Erledigung wiederholen (wie TaskRepeat im Backend)

export const REPEAT_UNIT_OPTIONS: { value: RepeatUnit; label: string }[] = [
  { value: 'days', label: 'Tage' },
  { value: 'weeks', label: 'Wochen' },
  { value: 'months', label: 'Monate' },
];

const ONE: Record<RepeatUnit, string> = { days: 'jeden Tag', weeks: 'jede Woche', months: 'jeden Monat' };
const MANY: Record<RepeatUnit, string> = { days: 'Tage', weeks: 'Wochen', months: 'Monate' };

// "jeden Tag", "alle 3 Tage", "jede Woche", "alle 2 Wochen", "jeden Monat"
export function describeRepeat(repeat: TaskRepeat): string {
  return repeat.interval === 1 ? ONE[repeat.unit] : `alle ${repeat.interval} ${MANY[repeat.unit]}`;
}

const IN_ONE: Record<RepeatUnit, string> = { days: 'morgen', weeks: 'in einer Woche', months: 'in einem Monat' };

// Wann die nächste Runde fällig ist, von heute aus: "morgen", "in 3 Tagen", "in 2 Wochen"
export function nextRoundIn(repeat: TaskRepeat): string {
  if (repeat.interval === 1) return IN_ONE[repeat.unit];
  const unit = repeat.unit === 'days' ? 'Tagen' : repeat.unit === 'weeks' ? 'Wochen' : 'Monaten';
  return `in ${repeat.interval} ${unit}`;
}

// Für alle offen (wer zuerst kommt) statt fest für eine Person; ältere Runden ohne Angabe waren immer offen
export const repeatsForAll = (task: Pick<ApiTask, 'repeat'>) => !!task.repeat && task.repeat.openToAll !== false;

// Runde für alle, die noch niemand übernommen hat: wer zuerst kommt
export const isOpenRound = (task: ApiTask) => repeatsForAll(task) && task.assigneeId === null;
