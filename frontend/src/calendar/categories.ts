import type { EventCategory } from '../components/data';

export const CATEGORY_OPTIONS: { value: EventCategory; label: string }[] = [
  { value: 'family', label: '👨‍👩‍👧‍👦 Familie' },
  { value: 'school', label: '📚 Schule' },
  { value: 'sports', label: '⚽ Sport' },
  { value: 'appointment', label: '🏥 Termin' },
  { value: 'work', label: '💼 Arbeit' },
  { value: 'reminder', label: '🔔 Erinnerung' },
];

export const CATEGORY_LABELS = Object.fromEntries(CATEGORY_OPTIONS.map(o => [o.value, o.label])) as
  Record<EventCategory, string>;
