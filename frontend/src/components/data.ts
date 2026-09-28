// Feste Beispieldaten für die Seiten, die noch nicht ans Backend angeschlossen sind (Aufgaben, Punkte,
// Einkauf, Essen, Nachrichten). Kalender, Dashboard-Termine und Profile kommen aus dem Backend.
import { addDays, mondayOf, startOfToday, toDateKey } from '../calendar/dates';

export interface FamilyMember {
  id: number;
  name: string;
  /** Anzeige-Label aus dem urspruenglichen Figma-Export (Mom/Dad/Child). */
  role: 'Mom' | 'Dad' | 'Child';
  initials: string;
  color: string;
  bg: string;
  age: number;
  points: number;
  avatar?: string;
}

export const FAMILY_MEMBERS: FamilyMember[] = [
  { id: 1, name: 'Sarah', role: 'Mom', initials: 'SA', color: '#2563EB', bg: '#EFF6FF', age: 42, points: 0 },
  { id: 2, name: 'Mike', role: 'Dad', initials: 'MI', color: '#14B8A6', bg: '#F0FDFA', age: 44, points: 0 },
  { id: 3, name: 'Emma', role: 'Child', initials: 'EM', color: '#8B5CF6', bg: '#F5F3FF', age: 16, points: 420 },
  { id: 4, name: 'Lucas', role: 'Child', initials: 'LU', color: '#F97316', bg: '#FFF7ED', age: 12, points: 285 },
  { id: 5, name: 'Lily', role: 'Child', initials: 'LI', color: '#EC4899', bg: '#FDF2F8', age: 8, points: 190 },
];

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
  memberId: string;
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

export interface Task {
  id: number;
  title: string;
  description?: string;
  assigneeId: number;
  status: 'todo' | 'inprogress' | 'done';
  priority: 'low' | 'medium' | 'high';
  dueDate: string;
  category: string;
  points: number;
}

export const INITIAL_TASKS: Task[] = [
  { id: 1, title: 'Clean bedroom', description: 'Tidy up, vacuum and dust surfaces', assigneeId: 3, status: 'todo', priority: 'medium', dueDate: '2026-09-22', category: 'chores', points: 20 },
  { id: 2, title: 'Take out trash', description: 'Bins to the kerb before 8am', assigneeId: 4, status: 'todo', priority: 'high', dueDate: '2026-09-21', category: 'chores', points: 15 },
  { id: 3, title: 'Math homework', description: 'Chapter 5 exercises 1–20', assigneeId: 4, status: 'inprogress', priority: 'high', dueDate: '2026-09-21', category: 'school', points: 25 },
  { id: 4, title: 'Book dentist for Lucas', assigneeId: 1, status: 'done', priority: 'high', dueDate: '2026-09-20', category: 'health', points: 0 },
  { id: 5, title: 'Grocery run', description: 'Pick up items from shopping list', assigneeId: 2, status: 'inprogress', priority: 'medium', dueDate: '2026-09-21', category: 'errands', points: 0 },
  { id: 6, title: 'Feed the dog', assigneeId: 5, status: 'done', priority: 'high', dueDate: '2026-09-21', category: 'chores', points: 10 },
  { id: 7, title: 'Water the plants', assigneeId: 3, status: 'todo', priority: 'low', dueDate: '2026-09-23', category: 'chores', points: 10 },
  { id: 8, title: 'Read for 30 min', assigneeId: 5, status: 'inprogress', priority: 'medium', dueDate: '2026-09-21', category: 'school', points: 15 },
  { id: 9, title: 'Plan weekend activities', assigneeId: 1, status: 'todo', priority: 'low', dueDate: '2026-09-24', category: 'family', points: 0 },
  { id: 10, title: 'Fix garage light', assigneeId: 2, status: 'todo', priority: 'medium', dueDate: '2026-09-25', category: 'home', points: 0 },
  { id: 11, title: 'Practice piano', assigneeId: 5, status: 'done', priority: 'medium', dueDate: '2026-09-21', category: 'school', points: 20 },
  { id: 12, title: 'Set up recycling bins', assigneeId: 2, status: 'done', priority: 'low', dueDate: '2026-09-22', category: 'chores', points: 0 },
];

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
  { id: 1, type: 'conflict', message: 'Schedule conflict on Sep 24: Piano lesson overlaps with parent-teacher conf.', time: '2m ago', read: false },
  { id: 2, type: 'task', message: 'Lucas completed Math homework — 25 points earned! 🎉', time: '45m ago', read: false },
  { id: 3, type: 'reminder', message: 'Recycling day tomorrow — bins to the kerb before 7am', time: '1h ago', read: false },
  { id: 4, type: 'shopping', message: 'Running low on milk & eggs — AI added to shopping list', time: '3h ago', read: true },
  { id: 5, type: 'message', message: 'New message from Music Academy: Lily\'s recital details', time: '5h ago', read: true },
];
