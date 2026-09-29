import { json, request } from '../api/client';

// done: erledigt (mit Punkten wartet sie auf Bestätigung), confirmed: bestätigt, Punkte gutgeschrieben
export type TaskStatus = 'todo' | 'inprogress' | 'done' | 'confirmed';
export type TaskPriority = 'low' | 'medium' | 'high';
export type TaskCategory = 'chores' | 'school' | 'health' | 'errands' | 'family' | 'home';

export interface ApiTask {
  id: string;
  title: string;
  description: string | null;
  // null bei einer offenen Bonus-Aufgabe (noch niemand hat sie übernommen)
  assigneeId: string | null;
  // bei Bonus-Aufgaben optional
  dueDate: string | null;
  priority: TaskPriority;
  category: TaskCategory;
  points: number;
  status: TaskStatus;
  createdBy: string | null;
  confirmedAt: string | null;
  confirmedBy: string | null;
  // Bonus-Aufgabe: offen für alle Kinder und Jugendlichen; repeatable: nach der Bestätigung wieder offen
  bonus: boolean;
  repeatable: boolean;
}

export interface TaskInput {
  title: string;
  description: string | null;
  assigneeId: string | null;
  dueDate: string | null;
  priority: TaskPriority;
  category: TaskCategory;
  points: number;
  bonus: boolean;
  repeatable: boolean;
}

export const listTasks = () => request<ApiTask[]>('/api/tasks');

export const createTask = (input: TaskInput) =>
  request<ApiTask>('/api/tasks', { method: 'POST', body: json(input) });

export const updateTask = (id: string, input: TaskInput) =>
  request<ApiTask>(`/api/tasks/${id}`, { method: 'PUT', body: json(input) });

export const changeTaskStatus = (id: string, status: Exclude<TaskStatus, 'confirmed'>) =>
  request<ApiTask>(`/api/tasks/${id}/status`, { method: 'PATCH', body: json({ status }) });

export const deleteTask = (id: string) => request<void>(`/api/tasks/${id}`, { method: 'DELETE' });

// Nur Administratoren: bestätigte und erledigte Aufgaben ohne Punkte löschen (wartende Bestätigungen bleiben)
export const deleteCompletedTasks = () =>
  request<{ deleted: number }>('/api/tasks/completed', { method: 'DELETE' });

export const confirmTask =(id: string) => request<ApiTask>(`/api/tasks/${id}/confirm`, { method: 'POST' });

export const reopenTask = (id: string) => request<ApiTask>(`/api/tasks/${id}/reopen`, { method: 'POST' });

// Bonus-Aufgaben: übernehmen (wer zuerst kommt) und zurückgeben, solange sie nicht erledigt sind
export const claimTask = (id: string) => request<ApiTask>(`/api/tasks/${id}/claim`, { method: 'POST' });

export const releaseTask = (id: string) => request<ApiTask>(`/api/tasks/${id}/release`, { method: 'POST' });
