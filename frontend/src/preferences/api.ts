import { json, request } from '../api/client';

// Eigene Anzeige-Einstellungen; true: wird angezeigt (ohne Einstellung ist alles an)
export interface Preferences {
  // Countdown bis zum nächsten Feiertag auf der Übersicht und im Kalender
  holidayCountdown: boolean;
  // Feiertage im Kalender an ihrem Tag markieren
  holidaysInCalendar: boolean;
}

export const DEFAULT_PREFERENCES: Preferences = { holidayCountdown: true, holidaysInCalendar: true };

export const getPreferences = () => request<Preferences>('/api/preferences');

export const savePreferences = (preferences: Preferences) =>
  request<Preferences>('/api/preferences', { method: 'PUT', body: json(preferences) });
