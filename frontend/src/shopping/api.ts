import { json, request } from '../api/client';

export type ShoppingCategory =
  'milchprodukte' | 'backwaren' | 'fleisch' | 'gemuese' | 'obst' | 'getraenke' | 'vorrat' | 'tiefkuehl' | 'snacks' | 'haushalt' | 'zutaten_essensplanung';

export interface ShoppingItem {
  id: string;
  name: string;
  quantity: string | null;
  category: ShoppingCategory;
  urgent: boolean;
  checked: boolean;
  // proposed: Vorschlag eines Kindes, wartet auf die Eltern
  status: 'approved' | 'proposed';
  createdBy: string;
  createdAt: string;
  checkedBy: string | null;
}

export interface ShoppingInput {
  name: string;
  quantity: string | null;
  category: ShoppingCategory;
  urgent: boolean;
}

export const listShopping = () => request<ShoppingItem[]>('/api/shopping');

export const addShoppingItem = (input: ShoppingInput) =>
  request<ShoppingItem>('/api/shopping', { method: 'POST', body: json(input) });

export const updateShoppingItem = (id: string, input: ShoppingInput) =>
  request<ShoppingItem>(`/api/shopping/${id}`, { method: 'PUT', body: json(input) });

export const checkShoppingItem = (id: string, checked: boolean) =>
  request<ShoppingItem>(`/api/shopping/${id}/checked`, { method: 'PATCH', body: json({ checked }) });

export const deleteShoppingItem = (id: string) => request<void>(`/api/shopping/${id}`, { method: 'DELETE' });

export const deleteCheckedShoppingItems = () =>
  request<{ deleted: number }>('/api/shopping/checked', { method: 'DELETE' });

export const approveShoppingItem = (id: string) =>
  request<ShoppingItem>(`/api/shopping/${id}/approve`, { method: 'POST' });

export const rejectShoppingItem = (id: string) => request<void>(`/api/shopping/${id}/reject`, { method: 'POST' });
