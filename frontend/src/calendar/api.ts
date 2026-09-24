import { json, request } from '../api/client';
import type { EventCategory, EventStatus } from '../components/data';

export interface ApiEvent {
  id: string;
  title: string;
  start: string;
  end: string;
  memberId: string;
  category: EventCategory;
  location: string | null;
  description: string | null;
  private: boolean;
  status: EventStatus;
  createdBy: string | null;
}

export interface EventInput {
  title: string;
  start: string | null;
  end: string | null;
  memberId: string;
  category: EventCategory;
  location: string | null;
  description: string | null;
  private: boolean;
}

export const listEvents = () => request<ApiEvent[]>('/api/events');

export const createEvent = (input: EventInput) =>
  request<ApiEvent>('/api/events', { method: 'POST', body: json(input) });

export const updateEvent = (id: string, input: EventInput) =>
  request<ApiEvent>(`/api/events/${id}`, { method: 'PUT', body: json(input) });

export const deleteEvent = (id: string) => request<void>(`/api/events/${id}`, { method: 'DELETE' });

export const approveEvent = (id: string) => request<ApiEvent>(`/api/events/${id}/approve`, { method: 'POST' });

export const rejectEvent = (id: string) => request<void>(`/api/events/${id}/reject`, { method: 'POST' });
