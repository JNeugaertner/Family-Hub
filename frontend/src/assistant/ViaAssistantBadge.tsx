import { SparklesIcon } from '../components/Icons';

const LABEL = 'Über den KI-Assistenten angelegt';

// Kleiner Hinweis an Terminen, Aufgaben, Artikeln und Essen, die über einen Entwurf des KI-Assistenten entstanden
// sind (Entscheidung vom 22.10.2026). compact: nur das Symbol, z. B. in den engen Zellen des Essensplans.
export default function ViaAssistantBadge({ compact = false, className = '' }: { compact?: boolean; className?: string }) {
  return (
    <span title={LABEL} aria-label={LABEL} role="img"
      className={`inline-flex items-center gap-0.5 text-[10px] bg-[#EFF6FF] text-[#1D4ED8] border border-[#BFDBFE] rounded-full font-medium flex-shrink-0 ${compact ? 'p-0.5' : 'px-1.5 py-0.5'} ${className}`}>
      <SparklesIcon size={10} />
      {!compact && 'KI'}
    </span>
  );
}
