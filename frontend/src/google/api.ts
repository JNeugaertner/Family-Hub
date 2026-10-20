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
  // Google hat erlaubt, den Kalender „FamilyHub“ anzulegen und dort einzutragen
  writeGranted: boolean;
  // FamilyHub-Termine werden in den Google-Kalender „FamilyHub“ eingetragen
  exportEnabled: boolean;
}

export interface CalendarSelection {
  calendarId: string;
  enabled: boolean;
  category: EventCategory;
}

export const getGoogleStatus = () => request<GoogleStatus>('/api/google/status');

// Liefert die Anmeldeseite von Google; der Browser wird dorthin weitergeleitet. exportToGoogle: zusätzlich das
// Eintragen erlauben lassen und nach der Rückkehr einschalten.
export const startGoogleConnect = (exportToGoogle = false) =>
  request<{ authorizationUrl: string }>(`/api/google/connect?export=${exportToGoogle}`, { method: 'POST' });

export const syncGoogle = () => request<GoogleStatus>('/api/google/sync', { method: 'POST' });

export const selectGoogleCalendars = (selection: CalendarSelection[]) =>
  request<GoogleStatus>('/api/google/calendars', { method: 'PUT', body: json(selection) });

// Ein: Kalender „FamilyHub“ in Google anlegen und eintragen; aus: ihn in Google löschen. Braucht writeGranted.
export const setGoogleExport = (enabled: boolean) =>
  request<GoogleStatus>('/api/google/export', { method: 'PUT', body: json({ enabled }) });

export const disconnectGoogle = () => request<void>('/api/google', { method: 'DELETE' });

// Ergebnis der Rückkehr von Google (?google=…), gesetzt vom Backend-Callback.
// ohne-eintragen: verbunden, aber das Eintragen wurde bei Google nicht erlaubt.
const GOOGLE_RETURNS = ['verbunden', 'abgebrochen', 'fehler', 'ohne-eintragen'] as const;
export type GoogleReturn = typeof GOOGLE_RETURNS[number];

export function googleReturnFromUrl(): GoogleReturn | null {
  const value = new URLSearchParams(window.location.search).get('google');
  return GOOGLE_RETURNS.find(r => r === value) ?? null;
}
