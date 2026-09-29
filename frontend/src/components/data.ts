// Gemeinsame Typen für Termine und feste Beispieldaten für das, was noch nicht ans Backend angeschlossen ist
// (Nachrichten, Benachrichtigungen, Müllabfuhr). Alles andere kommt aus dem Backend.
import { addDays, mondayOf, startOfToday, toDateKey } from '../calendar/dates';

export type EventCategory = 'school' | 'sports' | 'appointment' | 'family' | 'work' | 'reminder';

export type TransportMode = 'car' | 'transit' | 'bike' | 'walk';

// approved: gültiger Termin; proposed: Vorschlag, wartet auf Freigabe durch einen Administrator
export type EventStatus = 'approved' | 'proposed';

// Termine kommen aus dem Backend (siehe src/calendar). date/time/endTime sind aus start/end abgeleitet,
// damit die Kalenderansichten weiter nach Tag und Uhrzeit gruppieren können.
export interface CalendarEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  date: string;
  time: string;
  endTime?: string;
  // Beteiligte; mehrere bei gemeinsamen Terminen
  memberIds: string[];
  category: EventCategory;
  location?: string;
  description?: string;
  private: boolean;
  status: EventStatus;
  createdBy?: string;
  // Aus Google übernommen: nur ansehen, geändert wird in Google
  source?: 'google';
  // Von 00:00 bis 00:00 eines späteren Tages (z. B. ganztägige Google-Termine)
  allDay?: boolean;
  travelTime?: number;
  transportMode?: TransportMode;
  conflict?: boolean;
  travelConflict?: boolean; // travel time overlaps with previous/next event
}

export interface Message {
  id: number;
  source: 'whatsapp' | 'telegram' | 'family';
  senderName: string;
  senderColor: string;
  content: string;
  time: string;
  unread: boolean;
  actionable?: 'event' | 'shopping' | 'task';
  thread: string;
}

export const MESSAGES: Message[] = [
  { id: 1, source: 'whatsapp', senderName: 'School Group', senderColor: '#25D366', content: "⚠️ Tomorrow's game starts at 10am at City Sports Center. Please bring water!", time: '14:32', unread: true, actionable: 'event', thread: 'School Sports Group' },
  { id: 2, source: 'whatsapp', senderName: 'Sarah Johnson', senderColor: '#2563EB', content: "Can you grab some milk on your way home? We're out again 😅", time: '13:15', unread: true, actionable: 'shopping', thread: 'Family Chat' },
  { id: 3, source: 'telegram', senderName: 'Mike Johnson', senderColor: '#14B8A6', content: "Meeting ran late. Will be home around 7pm. Start dinner without me!", time: '17:45', unread: false, thread: 'Family Chat' },
  { id: 4, source: 'whatsapp', senderName: 'Music Academy', senderColor: '#8B5CF6', content: "Reminder: Lily's piano recital is on Oct 3rd at 3pm. Seats are limited!", time: '10:00', unread: true, actionable: 'event', thread: 'Music Academy' },
  { id: 5, source: 'telegram', senderName: 'Emma Johnson', senderColor: '#8B5CF6', content: "Mum can you pick me up after practice? Coach said we might go till 6.", time: '16:10', unread: false, actionable: 'task', thread: 'Family Chat' },
  { id: 6, source: 'family', senderName: 'Lucas Johnson', senderColor: '#F97316', content: "I finished my homework!! Can I have extra screen time? 🙏", time: '18:20', unread: true, thread: 'Family Chat' },
];

export type WasteType = 'Restmüll' | 'Biomüll' | 'Papier' | 'Gelber Sack';

export interface GarbagePickup {
  id: number;
  type: WasteType;
  date: string;           // ISO date string
  color: string;
  bgColor: string;
  icon: string;
  reminderDayBefore: boolean;
}

// Noch ohne Backend: Abholtermine relativ zur aktuellen Woche (0 = Montag), passend zu den Beispielterminen
const thisWeek = (day: number) => toDateKey(addDays(mondayOf(startOfToday()), day));

export const GARBAGE_PICKUPS: GarbagePickup[] = [
  { id: 1, type: 'Gelber Sack',  date: thisWeek(1),  color: '#CA8A04', bgColor: '#FEF9C3', icon: '🟡', reminderDayBefore: true },
  { id: 2, type: 'Papier',       date: thisWeek(3),  color: '#2563EB', bgColor: '#EFF6FF', icon: '🔵', reminderDayBefore: true },
  { id: 3, type: 'Biomüll',      date: thisWeek(7),  color: '#16A34A', bgColor: '#F0FDF4', icon: '🟢', reminderDayBefore: true },
  { id: 4, type: 'Restmüll',     date: thisWeek(14), color: '#6B7280', bgColor: '#F9FAFB', icon: '⚫', reminderDayBefore: true },
];

export const NOTIFICATIONS = [
  { id: 1, type: 'conflict', message: 'Terminkonflikt: Turnen und Elterngespräch überschneiden sich', time: 'vor 2 Min.', read: false },
  { id: 2, type: 'task', message: 'Lucas hat die Mathe-Hausaufgaben erledigt – 25 Punkte verdient! 🎉', time: 'vor 45 Min.', read: false },
  { id: 3, type: 'reminder', message: 'Morgen wird der Gelbe Sack abgeholt – bis 7 Uhr an die Straße stellen', time: 'vor 1 Std.', read: false },
  { id: 4, type: 'shopping', message: 'Milch und Eier gehen zur Neige – die KI hat sie auf die Einkaufsliste gesetzt', time: 'vor 3 Std.', read: true },
  { id: 5, type: 'message', message: 'Neue Nachricht der Musikschule: Infos zu Lilys Vorspiel', time: 'vor 5 Std.', read: true },
];
