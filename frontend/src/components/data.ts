// Gemeinsame Typen für Termine und feste Beispieldaten für das, was noch nicht ans Backend angeschlossen ist.

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

export type WasteType = 'Restmüll' | 'Biomüll' | 'Papier' | 'Gelber Sack';

export interface GarbagePickup {
  id: string;
  type: string;
  date: string;
}

export const NOTIFICATIONS = [
  { id: 1, type: 'conflict', message: 'Terminkonflikt: Turnen und Elterngespräch überschneiden sich', time: 'vor 2 Min.', read: false },
  { id: 2, type: 'task', message: 'Lucas hat die Mathe-Hausaufgaben erledigt – 25 Punkte verdient! 🎉', time: 'vor 45 Min.', read: false },
  { id: 3, type: 'reminder', message: 'Morgen wird der Gelbe Sack abgeholt – bis 7 Uhr an die Straße stellen', time: 'vor 1 Std.', read: false },
  { id: 4, type: 'shopping', message: 'Milch und Eier gehen zur Neige – die KI hat sie auf die Einkaufsliste gesetzt', time: 'vor 3 Std.', read: true },
  { id: 5, type: 'message', message: 'Neue Nachricht der Musikschule: Infos zu Lilys Vorspiel', time: 'vor 5 Std.', read: true },
];
