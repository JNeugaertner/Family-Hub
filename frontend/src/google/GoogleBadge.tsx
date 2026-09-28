// Kleines "G" für Termine aus dem Google Kalender
export default function GoogleBadge({ className = '' }: { className?: string }) {
  return (
    <span
      className={`inline-flex items-center justify-center w-4 h-4 rounded-full bg-white border border-slate-200 text-[9px] font-bold text-[#4285F4] align-middle ${className}`}
      title="Aus Google Kalender"
      aria-label="Aus Google Kalender"
    >
      G
    </span>
  );
}
