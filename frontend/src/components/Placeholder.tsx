// Kennzeichnung für Funktionen, die noch Platzhalter sind (siehe placeholders.ts). Bewusst grau: Gelb steht schon
// für Vorschläge, die auf eine Freigabe warten.

export function SoonBadge({ label = 'Demnächst', onDark = false, className = '' }: {
  // kurz halten, wo wenig Platz ist (z. B. "Bald" in der Seitenleiste)
  label?: string;
  // auf farbigem Hintergrund (z. B. Verlaufsknopf)
  onDark?: boolean;
  className?: string;
}) {
  return (
    <span title="Demnächst verfügbar" className={`inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded-full whitespace-nowrap ${
      onDark ? 'bg-white/25 text-white' : 'bg-slate-100 text-slate-500 border border-slate-200'} ${className}`}>
      <span aria-hidden="true">🚧</span> {label}
    </span>
  );
}

// Hinweis oben auf einer Seite, die noch ganz aus Beispieldaten besteht
export function PreviewBanner({ className = '' }: { className?: string }) {
  return (
    <div role="note" className={`flex items-start gap-2.5 bg-slate-50 border border-dashed border-slate-300 text-slate-600 text-sm rounded-xl px-4 py-3 ${className}`}>
      <span aria-hidden="true">🚧</span>
      <span>
        <strong className="font-semibold text-slate-700">Vorschau:</strong> Diese Funktion ist in Planung. Alle Inhalte
        hier sind Beispiele und werden nicht gespeichert.
      </span>
    </div>
  );
}
