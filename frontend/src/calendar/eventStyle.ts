import type { CalendarMember } from './CalendarDataContext';

// Darstellung von Terminen mit mehreren Beteiligten (Entscheidung vom 29.09.2026): Termine einer Person tragen ihre
// Farbe, gemeinsame Termine eine neutrale Grundfarbe mit einem Streifen aus den Farben aller Beteiligten.

export const SHARED_BASE = '#475569';
const FALLBACK = '#94A3B8';

export function memberColors(memberIds: string[], memberById: (id: string) => CalendarMember | undefined): string[] {
  const colors = memberIds.map(id => memberById(id)?.color).filter((c): c is string => !!c);
  return colors.length ? colors : [FALLBACK];
}

// Harte Farbübergänge: jede Person bekommt ein gleich großes Stück
export function stripe(colors: string[], angle = 180): string {
  const step = 100 / colors.length;
  return `linear-gradient(${angle}deg, ${colors.map((c, i) => `${c} ${i * step}% ${(i + 1) * step}%`).join(', ')})`;
}

// Hintergrund eines farbigen Terminblocks (weiße Schrift)
export function blockBackground(colors: string[], stripeWidth = 4): string {
  if (colors.length === 1) return colors[0];
  return `${stripe(colors)} left / ${stripeWidth}px 100% no-repeat, ${SHARED_BASE}`;
}

// Heller Hintergrund einer Terminkarte mit Farbkante links
export function cardBackground(colors: string[]): string {
  const edge = colors.length === 1 ? colors[0] : stripe(colors);
  const tint = colors.length === 1 ? `${colors[0]}10` : '#F1F5F9';
  return `${colors.length === 1 ? `linear-gradient(${edge}, ${edge})` : edge} left / 3px 100% no-repeat, ${tint}`;
}

// Vorschläge: gestrichelt und blass, gemeinsame mit Farbstreifen
export function proposalStyle(colors: string[]) {
  const color = colors.length === 1 ? colors[0] : SHARED_BASE;
  return {
    background: colors.length === 1 ? `${color}33` : `${stripe(colors)} left / 4px 100% no-repeat, ${SHARED_BASE}22`,
    color,
    border: `1px dashed ${color}`,
  };
}

// Punkt in der Monats- und Wochenübersicht: bei mehreren Beteiligten ein kleines Tortendiagramm
export function dotBackground(colors: string[]): string {
  if (colors.length === 1) return colors[0];
  const step = 360 / colors.length;
  return `conic-gradient(${colors.map((c, i) => `${c} ${i * step}deg ${(i + 1) * step}deg`).join(', ')})`;
}

// "Ganze Familie", wenn alle außer Gästen dabei sind, sonst die Vornamen
export function participantLabel(memberIds: string[], members: CalendarMember[]): string {
  const family = members.filter(m => m.effectiveRole !== 'gast').map(m => m.id);
  if (family.length > 1 && family.every(id => memberIds.includes(id))
      && memberIds.every(id => family.includes(id))) {
    return 'Ganze Familie';
  }
  return memberIds.map(id => members.find(m => m.id === id)?.name ?? 'unbekannt').join(', ');
}
