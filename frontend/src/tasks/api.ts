import { json, request } from '../api/client';

// done: erledigt (mit Punkten wartet sie auf Bestätigung), confirmed: bestätigt, Punkte gutgeschrieben
export type TaskStatus = 'todo' | 'inprogress' | 'done' | 'confirmed';
export type TaskPriority = 'low' | 'medium' | 'high';
export type TaskCategory = 'chores' | 'school' | 'health' | 'errands' | 'family' | 'home';

export interface ApiTask {
  id: string;
  title: string;
  description: string | null;
  assigneeId: string;
  dueDate: string;
  priority: TaskPriority;
  category: TaskCategory;
  points: number;
  status: TaskStatus;
  createdBy: string | null;
  confirmedAt: string | null;
  confirmedBy: string | null;
}

export interface TaskInput {
  title: string;
  description: string | null;
  assigneeId: string;
  dueDate: string | null;
  priority: TaskPriority;
  category: TaskCategory;
  points: number;
}

export const listTasks = () => request<ApiTask[]>('/api/tasks');

export const createTask = (input: TaskInput) =>
  request<ApiTask>('/api/tasks', { method: 'POST', body: json(input) });

export const updateTask = (id: string, input: TaskInput) =>
  request<ApiTask>(`/api/tasks/${id}`, { method: 'PUT', body: json(input) });

export const changeTaskStatus = (id: string, status: Exclude<TaskStatus, 'confirmed'>) =>
  request<ApiTask>(`/api/tasks/${id}/status`, { method: 'PATCH', body: json({ status }) });

export const deleteTask = (id: string) => request<void>(`/api/tasks/${id}`, { method: 'DELETE' });

export const confirmTask = (id: string) => request<ApiTask>(`/api/tasks/${id}/confirm`, { method: 'POST' });

export const reopenTask = (id: string) => request<ApiTask>(`/api/tasks/${id}/reopen`, { method: 'POST' });
