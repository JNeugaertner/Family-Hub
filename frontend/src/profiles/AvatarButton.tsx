import type { CSSProperties } from 'react';
import type { CalendarMember } from '../calendar/CalendarDataContext';
import { useOpenProfile } from './ProfileCard';

// Farbiger Kreis mit Initiale, der das Profil der Person öffnet. Klicks und Tasten gehen nicht an die umgebende
// Zeile weiter (z. B. Termin in der Übersicht, Aufgabenkarte).
export default function AvatarButton({ member, size = 24, className = '', style }: {
  member: CalendarMember | undefined;
  size?: number;
  className?: string;
  style?: CSSProperties;
}) {
  const openProfile = useOpenProfile();
  const box = { width: size, height: size, fontSize: Math.round(size * 0.38) };
  if (!member) {
    return <div className={`rounded-full bg-slate-300 flex-shrink-0 ${className}`} style={{ ...box, ...style }} />;
  }
  return (
    <button type="button"
      onClick={e => { e.stopPropagation(); openProfile(member.id, e.currentTarget); }}
      onKeyDown={e => e.stopPropagation()}
      aria-label={`Profil von ${member.name}`}
      title={member.name}
      className={`rounded-full flex items-center justify-center font-bold text-white flex-shrink-0 cursor-pointer hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#2563EB] ${className}`}
      style={{ ...box, backgroundColor: member.color, ...style }}>
      {member.initials[0]}
    </button>
  );
}
