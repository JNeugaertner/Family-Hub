import { json, request } from '../api/client';
import type { Permission, RoleId } from '../roles';

export interface Me {
  id: string;
  name: string;
  color: string;
  username: string;
  role: RoleId;
  effectiveRole: RoleId;
  birthDate: string | null;
  permissions: Permission[];
}

export interface SetupInput {
  name: string;
  color: string;
  username: string;
  password: string;
}

export const getStatus = () => request<{ setupRequired: boolean }>('/api/auth/status');

export const getMe = () => request<Me>('/api/auth/me');

// Spring Security erwartet die Anmeldedaten als Formularfelder.
export const login = (username: string, password: string) =>
  request<Me>('/api/auth/login', { method: 'POST', body: new URLSearchParams({ username, password }) });

export const logout = () => request<void>('/api/auth/logout', { method: 'POST' });

export const setup = (input: SetupInput) =>
  request<Me>('/api/auth/setup', { method: 'POST', body: json(input) });

export const changePassword = (currentPassword: string, newPassword: string) =>
  request<void>('/api/auth/password', { method: 'PUT', body: json({ currentPassword, newPassword }) });
