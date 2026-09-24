import { json, request } from '../api/client';
import type { EventCategory } from '../components/data';
import type { Permission, RoleId } from '../roles';

export interface ApiMember {
  id: string;
  name: string;
  color: string;
  // Nur für Administratoren und die Person selbst gefüllt
  username: string | null;
  birthDate: string | null;
  role: RoleId;
  effectiveRole: RoleId;
  roleFixed: boolean;
  // Nur für Administratoren gefüllt
  extraPermissions: Permission[] | null;
  revokedPermissions: Permission[] | null;
}

export interface MemberInput {
  name: string;
  color: string;
  username: string;
  // null: Passwort beim Ändern unverändert lassen
  password: string | null;
  role: RoleId;
  birthDate: string | null;
  roleFixed: boolean;
  extraPermissions: Permission[];
  revokedPermissions: Permission[];
}

export interface RoleInfo {
  id: RoleId;
  name: string;
  assignable: boolean;
  permissions: Permission[];
}

export interface Settings {
  guestCategories: EventCategory[];
  // Alter, ab dem ein Kind automatisch Jugendlicher wird (nur lesbar, aus der Backend-Konfiguration)
  teenAge: number;
}

export const listMembers = () => request<ApiMember[]>('/api/members');

export const createMember = (input: MemberInput) =>
  request<ApiMember>('/api/members', { method: 'POST', body: json(input) });

export const updateMember = (id: string, input: MemberInput) =>
  request<ApiMember>(`/api/members/${id}`, { method: 'PUT', body: json(input) });

export const deleteMember = (id: string) => request<void>(`/api/members/${id}`, { method: 'DELETE' });

export const listRoles = () => request<RoleInfo[]>('/api/roles');

export const getSettings = () => request<Settings>('/api/settings');

export const updateSettings = (settings: Pick<Settings, 'guestCategories'>) =>
  request<Settings>('/api/settings', { method: 'PUT', body: json(settings) });
