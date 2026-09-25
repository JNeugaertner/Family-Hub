import { request } from '../api/client';

export interface Balance {
  memberId: string;
  points: number;
}

// Eine Buchung der Punkte-Historie; taskId ist gesetzt, wenn die Punkte für eine bestätigte Aufgabe kamen.
export interface PointEntry {
  id: string;
  memberId: string;
  amount: number;
  reason: string;
  taskId: string | null;
  createdAt: string;
  createdBy: string | null;
}

export const listBalances = () => request<Balance[]>('/api/points');

export const listHistory = (memberId?: string) =>
  request<PointEntry[]>(`/api/points/history${memberId ? `?memberId=${encodeURIComponent(memberId)}` : ''}`);
