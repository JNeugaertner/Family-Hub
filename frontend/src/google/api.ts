import { json, request } from '../api/client';
import type { EventCategory } from '../components/data';

export interface GoogleCalendar {
  calendarId: string;
  name: string;
  primary: boolean;
  enabled: boolean;
  category: EventCategory;
}

export interface GoogleStatus {
  // false: Auf dem Server fehlen die Zugangsdaten (local.properties, siehe README)
  configured: boolean;
  connected: boolean;
  email: string | null;
  calendars: GoogleCalendar[];
  lastSyncAt: string | null;
  lastSyncError: string | null;
  // Google hat den Zugang widerrufen oder er ist abgelaufen
  needsReconnect: boolean;
}

export interface CalendarSelection {
  calendarId: string;
  enabled: boolean;
  category: EventCategory;
}

export const getGoogleStatus = () => request<GoogleStatus>('/api/google/status');

// Liefert die Anmeldeseite von Google; der Browser wird dorthin weitergeleitet.
export const startGoogleConnect = () =>
  request<{ authorizationUrl: string }>('/api/google/connect', { method: 'POST' });

export const syncGoogle = () => request<GoogleStatus>('/api/google/sync', { method: 'POST' });

export const selectGoogleCalendars = (selection: CalendarSelection[]) =>
  request<GoogleStatus>('/api/google/calendars', { method: 'PUT', body: json(selection) });

export const disconnectGoogle = () => request<void>('/api/google', { method: 'DELETE' });

// Ergebnis der Rückkehr von Google (?google=…), gesetzt vom Backend-Callback
export type GoogleReturn = 'verbunden' | 'abgebrochen' | 'fehler';

export function googleReturnFromUrl(): GoogleReturn | null {
  const value = new URLSearchParams(window.location.search).get('google');
  return value === 'verbunden' || value === 'abgebrochen' || value === 'fehler' ? value : null;
}
