import { useCalendarData } from './CalendarDataContext';
import { participantLabel } from './eventStyle';
import AvatarButton from '../profiles/AvatarButton';

// Beteiligte eines Termins als überlappende Kreise mit Initiale; ab vier Personen "+n". Ein Klick auf einen Kreis
// öffnet das Profil der Person.
export default function ParticipantAvatars({ memberIds, size = 24 }: { memberIds: string[]; size?: number }) {
  const { members, memberById } = useCalendarData();
  const people = memberIds.map(memberById).filter((m): m is NonNullable<typeof m> => !!m);
  const circle = 'rounded-full flex items-center justify-center font-bold ring-2 ring-white';
  const style = { width: size, height: size, fontSize: Math.round(size * 0.38), marginLeft: 0 };
  const label = participantLabel(memberIds, members);
  return (
    <div className="flex flex-shrink-0" title={label} aria-label={label} role="group">
      {people.slice(0, 3).map((m, i) => (
        <AvatarButton key={m.id} member={m} size={size} className="ring-2 ring-white"
          style={{ marginLeft: i > 0 ? -size / 3 : 0 }} />
      ))}
      {people.length > 3 && (
        <div className={`${circle} text-slate-600 bg-slate-200`} style={{ ...style, marginLeft: -size / 3 }}>
          +{people.length - 3}
        </div>
      )}
    </div>
  );
}
