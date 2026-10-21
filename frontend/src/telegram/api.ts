import { request } from '../api/client';

export interface TelegramStatus {
  // false: Auf dem Server fehlt der Bot-Token (local.properties, siehe README)
  configured: boolean;
  // Benutzername des Bots in Telegram; null, solange er nicht erreichbar ist (Grund in error)
  botUsername: string | null;
  // Eigenes Telegram-Konto verknüpft
  linked: boolean;
  // Name des verknüpften Telegram-Kontos
  linkedName: string | null;
  // Empfangene Telegram-Nachrichten, die man selbst sehen darf
  messageCount: number;
  error: string | null;
}

export const getTelegramStatus = () => request<TelegramStatus>('/api/telegram/status');

// Link zum Bot mit einem Einmal-Code (zehn Minuten gültig); in Telegram auf „Starten“ tippen verknüpft das Konto
export const startTelegramLink = () => request<{ url: string }>('/api/telegram/link', { method: 'POST' });

export const unlinkTelegram = () => request<void>('/api/telegram/link', { method: 'DELETE' });

export const sendTelegramTest = () => request<void>('/api/telegram/test', { method: 'POST' });
