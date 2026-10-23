// Farbauswahl für Familienmitglieder (aus dem Figma-Design der Profilseite).
export const FAMILY_COLORS = [
  '#2563EB', '#14B8A6', '#8B5CF6', '#F97316', '#EC4899',
  '#22C55E', '#F59E0B', '#EF4444', '#06B6D4', '#64748B',
];

const HEX = /^#[0-9a-f]{6}$/i;

const channels = (color: string) => [1, 3, 5].map(i => Number.parseInt(color.slice(i, i + 2), 16));

function luminance(color: string): number {
  const [r, g, b] = channels(color).map(v => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

// Kontrast nach WCAG 2 zwischen zwei Farben im Format #RRGGBB, z. B. 4.5 für 4,5:1
export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

const readableCache = new Map<string, string>();

// Mitgliedsfarbe für Stellen mit Schrift (Issue #3, Entscheidung vom 23.10.2026): weiße Schrift auf der Farbe oder
// Schrift in der Farbe auf hellem Grund. Dieselbe Farbe, nur so weit abgedunkelt, dass sie mit Weiß mindestens min:1
// erreicht (WCAG AA: 4,5:1). Punkte, Balken und Ränder behalten die Originalfarbe.
export function readable(color: string, min = 4.5): string {
  if (!HEX.test(color)) return color;
  const key = `${color}:${min}`;
  const cached = readableCache.get(key);
  if (cached) return cached;
  const [r, g, b] = channels(color);
  let result = color;
  for (let factor = 1; contrastRatio(result, '#FFFFFF') < min && factor > 0.05;) {
    factor -= 0.05;
    result = `#${[r, g, b].map(v => Math.round(v * factor).toString(16).padStart(2, '0')).join('')}`.toUpperCase();
  }
  readableCache.set(key, result);
  return result;
}
