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

export interface ShoppingItem {
  id: number;
  name: string;
  category: string;
  checked: boolean;
  quantity?: string;
  addedById: number;
  urgent?: boolean;
}

export const INITIAL_SHOPPING: ShoppingItem[] = [
  { id: 1, name: 'Organic milk', category: 'Dairy', checked: false, quantity: '2 × 2L', addedById: 1, urgent: true },
  { id: 2, name: 'Whole grain bread', category: 'Bakery', checked: false, quantity: '1 loaf', addedById: 1 },
  { id: 3, name: 'Chicken breast', category: 'Meat & Fish', checked: false, quantity: '1 kg', addedById: 2 },
  { id: 4, name: 'Broccoli', category: 'Vegetables', checked: true, quantity: '1 head', addedById: 1 },
  { id: 5, name: 'Apple juice', category: 'Beverages', checked: false, quantity: '1.5L', addedById: 4 },
  { id: 6, name: 'Greek yogurt', category: 'Dairy', checked: false, quantity: '4 packs', addedById: 3 },
  { id: 7, name: 'Penne pasta', category: 'Pantry', checked: true, quantity: '500g', addedById: 2 },
  { id: 8, name: 'Tomato sauce', category: 'Pantry', checked: false, quantity: '2 jars', addedById: 2 },
  { id: 9, name: 'Bananas', category: 'Fruit', checked: false, quantity: '1 bunch', addedById: 5 },
  { id: 10, name: 'Cheddar cheese', category: 'Dairy', checked: false, quantity: '200g', addedById: 1 },
  { id: 11, name: 'Free-range eggs', category: 'Dairy', checked: false, quantity: '12', addedById: 1, urgent: true },
  { id: 12, name: 'Orange juice', category: 'Beverages', checked: true, quantity: '1L', addedById: 3 },
  { id: 13, name: 'Salmon fillet', category: 'Meat & Fish', checked: false, quantity: '400g', addedById: 2 },
  { id: 14, name: 'Spinach', category: 'Vegetables', checked: false, quantity: '200g', addedById: 1 },
];

export const MEALS: Record<string, { breakfast: string; lunch: string; dinner: string; snacks: string }> = {
  Mon: { breakfast: 'Oatmeal with berries', lunch: 'Turkey sandwich & salad', dinner: 'Spaghetti Bolognese', snacks: 'Apple slices & peanut butter' },
  Tue: { breakfast: 'Scrambled eggs on toast', lunch: 'Chicken Caesar wrap', dinner: 'Grilled salmon & veggies', snacks: 'Yogurt parfait' },
  Wed: { breakfast: 'Banana pancakes', lunch: 'Tomato soup & grilled cheese', dinner: 'Chicken stir-fry & rice', snacks: 'Carrot sticks & hummus' },
  Thu: { breakfast: 'Greek yogurt & granola', lunch: 'Tuna salad sandwich', dinner: 'Beef tacos', snacks: 'Banana & almond butter' },
  Fri: { breakfast: 'Avocado toast & eggs', lunch: 'Pasta salad', dinner: '🍕 Pizza night!', snacks: 'Fruit smoothie' },
  Sat: { breakfast: 'French toast & bacon', lunch: 'Picnic in the park', dinner: 'Homemade burgers', snacks: '🍦 Ice cream' },
  Sun: { breakfast: '🥞 Big family brunch', lunch: 'Light leftovers', dinner: 'Roast chicken & roasties', snacks: '🍪 Cookies & milk' },
};

export interface Achievement {
  id: number;
  name: string;
  icon: string;
  description: string;
  memberId?: number;
  earned: boolean;
  points: number;
}

export const ACHIEVEMENTS: Achievement[] = [
  { id: 1, name: 'Early Bird', icon: '🌅', description: 'Completed 5 morning tasks', earned: true, points: 50 },
  { id: 2, name: 'Helping Hand', icon: '🤝', description: 'Helped a sibling 3 times', earned: true, points: 75 },
  { id: 3, name: 'Scholar', icon: '📚', description: 'Homework on time all week', earned: false, points: 100 },
  { id: 4, name: 'Chef Jr.', icon: '👨‍🍳', description: 'Helped cook 3 meals', earned: false, points: 80 },
  { id: 5, name: 'Super Clean', icon: '✨', description: 'Cleaned room 7 days straight', earned: true, points: 120 },
  { id: 6, name: 'Star Reader', icon: '⭐', description: 'Read 10 books this month', earned: false, points: 150 },
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

export const WEATHER = {
  today: { temp: 18, condition: 'Partly Cloudy', icon: '⛅', humidity: 65, wind: 14, high: 21, low: 12 },
  forecast: [
    { day: 'Tue', icon: '🌤', high: 22, low: 13 },
    { day: 'Wed', icon: '🌧', high: 16, low: 11 },
    { day: 'Thu', icon: '⛅', high: 19, low: 12 },
    { day: 'Fri', icon: '☀️', high: 24, low: 14 },
    { day: 'Sat', icon: '☀️', high: 25, low: 15 },
  ],
};

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

export interface ClothingRecommendation {
  icon: string;
  label: string;
  reason: string;
}

export type WeatherConditionKey = 'rain' | 'sun' | 'heat' | 'cold' | 'mild';

export const CLOTHING_RECOMMENDATIONS: Record<WeatherConditionKey, { title: string; emoji: string; color: string; bg: string; border: string; items: ClothingRecommendation[] }> = {
  rain: {
    title: 'Regenausrüstung', emoji: '🌧️', color: '#1D4ED8', bg: '#EFF6FF', border: '#BFDBFE',
    items: [
      { icon: '☂️', label: 'Regenschirm', reason: 'Regen ab 14 Uhr' },
      { icon: '🧥', label: 'Regenjacke', reason: 'Windgeschwindigkeit 14 km/h' },
      { icon: '🥾', label: 'Gummistiefel', reason: 'Nasse Straßen erwartet' },
    ],
  },
  sun: {
    title: 'Sonnenschutz', emoji: '☀️', color: '#D97706', bg: '#FFFBEB', border: '#FDE68A',
    items: [
      { icon: '🧴', label: 'Sonnencreme', reason: 'UV-Index: hoch' },
      { icon: '👒', label: 'Sonnenhut', reason: 'Direkte Sonneneinstrahlung' },
      { icon: '💧', label: 'Trinkflasche', reason: 'Bleib hydratisiert' },
    ],
  },
  heat: {
    title: 'Hitzeschutz', emoji: '🌡️', color: '#DC2626', bg: '#FEF2F2', border: '#FECACA',
    items: [
      { icon: '👕', label: 'Leichte Kleidung', reason: 'Über 30°C erwartet' },
      { icon: '💧', label: 'Viel Wasser', reason: 'Mindestens 2 Liter trinken' },
      { icon: '🧴', label: 'Sonnenschutz', reason: 'UV-Index: sehr hoch' },
    ],
  },
  cold: {
    title: 'Kälteschutz', emoji: '🥶', color: '#0369A1', bg: '#F0F9FF', border: '#BAE6FD',
    items: [
      { icon: '🧥', label: 'Winterjacke', reason: 'Unter 8°C' },
      { icon: '🧣', label: 'Schal', reason: 'Kalter Wind' },
      { icon: '🧢', label: 'Mütze', reason: 'Wärmeverlust über Kopf' },
      { icon: '🧤', label: 'Handschuhe', reason: 'Frostgefahr' },
    ],
  },
  mild: {
    title: 'Gemischtes Wetter', emoji: '⛅', color: '#0F766E', bg: '#F0FDFA', border: '#99F6E4',
    items: [
      { icon: '🧥', label: 'Leichte Jacke', reason: 'Wechselhaftes Wetter' },
      { icon: '👟', label: 'Feste Schuhe', reason: 'Evtl. nasse Wege' },
      { icon: '💧', label: 'Trinkflasche', reason: 'Aktiver Tag' },
    ],
  },
};

export function getWeatherCondition(temp: number, condition: string): WeatherConditionKey {
  const lower = condition.toLowerCase();
  if (lower.includes('rain') || lower.includes('shower') || lower.includes('drizzle')) return 'rain';
  if (temp >= 30) return 'heat';
  if (temp <= 8) return 'cold';
  if (temp >= 22 && (lower.includes('sun') || lower.includes('clear'))) return 'sun';
  return 'mild';
}

export const NOTIFICATIONS = [
  { id: 1, type: 'conflict', message: 'Schedule conflict on Sep 24: Piano lesson overlaps with parent-teacher conf.', time: '2m ago', read: false },
  { id: 2, type: 'task', message: 'Lucas completed Math homework — 25 points earned! 🎉', time: '45m ago', read: false },
  { id: 3, type: 'reminder', message: 'Recycling day tomorrow — bins to the kerb before 7am', time: '1h ago', read: false },
  { id: 4, type: 'shopping', message: 'Running low on milk & eggs — AI added to shopping list', time: '3h ago', read: true },
  { id: 5, type: 'message', message: 'New message from Music Academy: Lily\'s recital details', time: '5h ago', read: true },
];
