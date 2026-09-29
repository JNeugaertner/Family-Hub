import { useCalendarData } from './CalendarDataContext';
import { participantLabel } from './eventStyle';

// Beteiligte eines Termins als überlappende Kreise mit Initiale; ab vier Personen "+n".
export default function ParticipantAvatars({ memberIds, size = 24 }: { memberIds: string[]; size?: number }) {
  const { members, memberById } = useCalendarData();
  const people = memberIds.map(memberById).filter((m): m is NonNullable<typeof m> => !!m);
  const circle = 'rounded-full flex items-center justify-center font-bold ring-2 ring-white';
  const style = { width: size, height: size, fontSize: Math.round(size * 0.38), marginLeft: 0 };
  const label = participantLabel(memberIds, members);
  return (
    <div className="flex flex-shrink-0" title={label} aria-label={label} role="img">
      {people.slice(0, 3).map((m, i) => (
        <div key={m.id} className={`${circle} text-white`}
          style={{ ...style, backgroundColor: m.color, marginLeft: i > 0 ? -size / 3 : 0 }}>
          {m.initials[0]}
        </div>
      ))}
      {people.length > 3 && (
        <div className={`${circle} text-slate-600 bg-slate-200`} style={{ ...style, marginLeft: -size / 3 }}>
          +{people.length - 3}
        </div>
      )}
    </div>
  );
}
