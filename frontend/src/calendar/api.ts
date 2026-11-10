import { json, request } from '../api/client';
import type { EventCategory, EventStatus, TransportMode } from '../components/data';
import type { Recurrence, SeriesRef, SeriesScope } from './recurrence';

export interface ApiEvent {
  id: string;
  title: string;
  departure: string;
  start: string;
  end: string;
  // Beteiligte, mindestens eine Person
  memberIds: string[];
  category: EventCategory;
  location: string | null;
  lat: number | null;
  lon: number | null;
  transportMode: TransportMode;
  travelTime: number | null;
  description: string | null;
  private: boolean;
  status: EventStatus;
  createdBy: string | null;
  // Nur bei importierten Terminen (Google Kalender); diese sind schreibgeschützt
  external: { provider: string; calendarId: string; eventId: string } | null;
  // Nur bei Serienterminen
  recurrence: Recurrence | null;
  series: SeriesRef | null;
}

export interface EventInput {
  title: string;
  departure: string | null;
  start: string | null;
  end: string | null;
  memberIds: string[];
  category: EventCategory;
  location: string | null;
  lat: number | null;
  lon: number | null;
  transportMode: TransportMode;
  travelTime: number | null;
  description: string | null;
  private: boolean;
  // Mit Regel entsteht eine Serie; bei Serienterminen zählt sie nur mit scope "following"
  recurrence: Recurrence | null;
}

const withScope = (path: string, scope?: SeriesScope) => (scope ? `${path}?scope=${scope}` : path);

export const listEvents = () => request<ApiEvent[]>('/api/events');

export const createEvent = (input: EventInput) =>
  request<ApiEvent>('/api/events', { method: 'POST', body: json(input) });

export const updateEvent = (id: string, input: EventInput, scope?: SeriesScope) =>
  request<ApiEvent>(withScope(`/api/events/${id}`, scope), { method: 'PUT', body: json(input) });

export const deleteEvent = (id: string, scope?: SeriesScope) =>
  request<void>(withScope(`/api/events/${id}`, scope), { method: 'DELETE' });

export const approveEvent = (id: string) => request<ApiEvent>(`/api/events/${id}/approve`, { method: 'POST' });

export const rejectEvent = (id: string) => request<void>(`/api/events/${id}/reject`, { method: 'POST' });
