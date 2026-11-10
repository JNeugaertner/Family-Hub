// Quellenangabe, die die Nutzungsbedingungen von openrouteservice für Ortssuche und Fahrzeit verlangen
export default function OrsAttribution({ className = '' }: { className?: string }) {
  const link = 'underline hover:text-slate-700';
  return (
    <p className={`text-[11px] text-slate-500 ${className}`}>
      © <a className={link} href="https://openrouteservice.org/" target="_blank" rel="noreferrer">openrouteservice.org</a> by
      HeiGIT | Map data © <a className={link} href="https://www.openstreetmap.org/copyright" target="_blank"
        rel="noreferrer">OpenStreetMap contributors</a>
    </p>
  );
}
