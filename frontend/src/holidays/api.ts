import { request } from '../api/client';

// Feiertag mit Countdown; daysLeft 0 heißt heute, negativ liegt der Tag zurück (Backend: /api/holidays)
export interface Holiday {
  key: string;
  name: string;
  emoji: string;
  date: string;
  daysLeft: number;
}

export const getHolidays = (from: string, to: string) =>
  request<Holiday[]>(`/api/holidays?from=${from}&to=${to}`);

export const getUpcomingHolidays = (count: number) =>
  request<Holiday[]>(`/api/holidays/upcoming?count=${count}`);

// "Heute", "Morgen", "noch 43 Tage"
export const countdownText = (daysLeft: number) =>
  daysLeft === 0 ? 'Heute' : daysLeft === 1 ? 'Morgen' : `noch ${daysLeft} Tage`;
