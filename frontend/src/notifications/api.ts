import { json, request } from '../api/client';

// true: Benachrichtigungen dieser Art kommen an (ohne Einstellung ist alles an)
export interface NotificationSettings {
  // Dringende Aufgaben: zugewiesen oder auf „hoch“ gestellt, morgens am Fälligkeitstag
  tasks: boolean;
  // Kurzfristige Termine: neu oder verschoben, Beginn in den nächsten 24 Stunden
  events: boolean;
  // Bestätigungen, die seit 24 Stunden offen sind (nur für alle, die bestätigen dürfen)
  confirmations: boolean;
  // Chat-Nachrichten in FamilyHub
  messages: boolean;
}

export const getNotificationSettings = () => request<NotificationSettings>('/api/notifications/settings');

export const saveNotificationSettings = (settings: NotificationSettings) =>
  request<NotificationSettings>('/api/notifications/settings', { method: 'PUT', body: json(settings) });
