import type { CalendarMember } from './CalendarDataContext';

// Farben von Terminen (Entscheidung vom 29.09.2026): Termine einer Person tragen ihre Farbe, Termine mit mehreren
// Beteiligten eine eigene Farbe, die bei keinem Familienmitglied vorkommt (siehe family/colors.ts).

export const SHARED_COLOR = '#3730A3';
const FALLBACK = '#94A3B8';

export function memberColors(memberIds: string[], memberById: (id: string) => CalendarMember | undefined): string[] {
  const colors = memberIds.map(id => memberById(id)?.color).filter((c): c is string => !!c);
  return colors.length ? colors : [FALLBACK];
}

// Die eine Farbe eines Termins: bei einer Person ihre, bei mehreren die Farbe für gemeinsame Termine
export const eventColor = (colors: string[]) => (colors.length === 1 ? colors[0] : SHARED_COLOR);

// Hintergrund eines farbigen Terminblocks (weiße Schrift)
export const blockBackground = (colors: string[]) => eventColor(colors);

// Heller Hintergrund einer Terminkarte mit Farbkante links
export function cardBackground(colors: string[]): string {
  const color = eventColor(colors);
  return `linear-gradient(${color}, ${color}) left / 3px 100% no-repeat, ${color}10`;
}

// Vorschläge: gestrichelt und blass
export function proposalStyle(colors: string[]) {
  const color = eventColor(colors);
  return { background: `${color}33`, color, border: `1px dashed ${color}` };
}

// Punkt in der Monats- und Wochenübersicht
export const dotBackground = (colors: string[]) => eventColor(colors);

// "Ganze Familie", wenn alle außer Gästen dabei sind, sonst die Vornamen
export function participantLabel(memberIds: string[], members: CalendarMember[]): string {
  const family = members.filter(m => m.effectiveRole !== 'gast').map(m => m.id);
  if (family.length > 1 && family.every(id => memberIds.includes(id))
      && memberIds.every(id => family.includes(id))) {
    return 'Ganze Familie';
  }
  return memberIds.map(id => members.find(m => m.id === id)?.name ?? 'unbekannt').join(', ');
}
