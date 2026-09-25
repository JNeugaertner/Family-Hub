import { useEffect, type CSSProperties } from 'react';

// Richtungen der Sterne, die beim Gutschreiben auseinanderfliegen
const BURST = [[-60, -40], [60, -40], [-80, 10], [80, 10], [-40, 50], [40, 50]];

// Belohnungsanimation (User Story 4.2: "Die Vergabe wird visuell bestätigt"), verschwindet nach kurzer Zeit.
export default function PointsToast({ text, onDone }: { text: string; onDone: () => void }) {
  useEffect(() => {
    const timer = setTimeout(onDone, 2800);
    return () => clearTimeout(timer);
  }, [onDone]);

  return (
    <div role="status" aria-live="polite"
      className="points-pop fixed left-1/2 top-24 z-[60] flex items-center gap-3 rounded-2xl bg-gradient-to-r from-[#F59E0B] to-[#F97316] px-6 py-4 text-white shadow-2xl">
      <span className="relative text-3xl" aria-hidden="true">
        ⭐
        {BURST.map(([dx, dy], i) => (
          <span key={i} className="star-burst absolute left-1 top-1 text-base"
            style={{ '--dx': `${dx}px`, '--dy': `${dy}px` } as CSSProperties}>✨</span>
        ))}
      </span>
      <span className="text-lg font-bold">{text}</span>
    </div>
  );
}
