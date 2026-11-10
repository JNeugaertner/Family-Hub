import { json, request } from '../api/client';

export type ShoppingCategory =
  'milchprodukte' | 'backwaren' | 'fleisch' | 'gemuese' | 'obst' | 'getraenke' | 'vorrat' | 'tiefkuehl' | 'snacks' | 'haushalt' | 'zutaten_essensplanung';

// Wiederkehrende Artikel: trip = bei jedem Einkauf, sonst interval Tage/Wochen/Monate nach dem Kauf
export type ShoppingRepeatUnit = 'trip' | 'days' | 'weeks' | 'months';

export interface ShoppingRepeat {
  interval?: number | null;
  unit: ShoppingRepeatUnit;
}

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
  // Fehlt bei einmaligen Artikeln
  repeat?: ShoppingRepeat | null;
  // Tag des Abhakens (gekauft)
  checkedOn?: string | null;
  // Gekauft und vorerst von der Liste genommen: kommt an diesem Tag ("JJJJ-MM-TT") offen wieder
  returnsOn?: string | null;
}

export interface ShoppingInput {
  name: string;
  quantity: string | null;
  category: ShoppingCategory;
  urgent: boolean;
  repeat?: ShoppingRepeat | null;
}

// "Abgehakte entfernen": wiederkehrende Artikel bleiben, wieder offen (backOnList) oder unter "Kommt wieder" (waiting)
export interface RemovedChecked {
  deleted: number;
  backOnList: number;
  waiting: number;
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
  request<RemovedChecked>('/api/shopping/checked', { method: 'DELETE' });

export const approveShoppingItem = (id: string) =>
  request<ShoppingItem>(`/api/shopping/${id}/approve`, { method: 'POST' });

export const rejectShoppingItem = (id: string) => request<void>(`/api/shopping/${id}/reject`, { method: 'POST' });
