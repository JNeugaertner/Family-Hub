import { request } from '../api/client';
import type { GarbagePickup } from '../components/data';

export interface WasteCollection {
  fileName: string | null;
  uploadedAt: string | null;
  points: number;
  assigneeId: string | null;
  pickups: GarbagePickup[];
}

export const getWasteCollection = () => request<WasteCollection>('/api/waste');

export function importWasteCalendar(file: File, points: number, assigneeId: string) {
  const body = new FormData();
  body.set('file', file);
  body.set('points', String(points));
  if (assigneeId) body.set('assigneeId', assigneeId);
  return request<WasteCollection>('/api/waste/import', { method: 'POST', body });
}