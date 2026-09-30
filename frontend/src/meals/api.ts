import { json, request } from '../api/client';
import type { ShoppingCategory } from '../shopping/api';

export type MealType = 'fruehstueck' | 'mittagessen' | 'abendessen' | 'snacks';

export interface Ingredient {
  name: string;
  quantity: string | null;
  category: ShoppingCategory;
}

export interface Dish {
  id: string;
  name: string;
  ingredients: Ingredient[];
  // Kochanleitung, ein Schritt pro Zeile; fehlt, wenn keine hinterlegt ist
  instructions?: string | null;
  // Zubereitungszeit in Minuten
  prepMinutes?: number | null;
  // Für wie viele Personen die Zutatenmengen gedacht sind (nur Anzeige)
  servings?: number | null;
  createdBy: string;
  createdAt: string;
}

export interface DishInput {
  name: string;
  ingredients: Ingredient[];
  instructions?: string | null;
  prepMinutes?: number | null;
  servings?: number | null;
}

export interface MealEntry {
  id: string;
  date: string;
  type: MealType;
  // Gericht aus der Sammlung; null bei Freitext
  dishId: string | null;
  name: string;
  // proposed: Wunsch eines Kindes, wartet auf die Eltern
  status: 'approved' | 'proposed';
  createdBy: string;
  createdAt: string;
}

export interface MealInput {
  date: string;
  type: MealType;
  dishId: string | null;
  name: string | null;
}

// Zutaten, die auf die Einkaufsliste kamen, und solche, die dort schon offen standen
export interface ShoppingTransfer {
  added: string[];
  skipped: string[];
}

export const listMeals = (from: string, to: string) =>
  request<MealEntry[]>(`/api/meals?from=${from}&to=${to}`);

export const listWishes = () => request<MealEntry[]>('/api/meals/wishes');

export const addMeal = (input: MealInput) =>
  request<MealEntry>('/api/meals', { method: 'POST', body: json(input) });

export const deleteMeal = (id: string) => request<void>(`/api/meals/${id}`, { method: 'DELETE' });

export const approveMeal = (id: string) => request<MealEntry>(`/api/meals/${id}/approve`, { method: 'POST' });

export const rejectMeal = (id: string) => request<void>(`/api/meals/${id}/reject`, { method: 'POST' });

export const mealToShopping = (id: string) =>
  request<ShoppingTransfer>(`/api/meals/${id}/shopping`, { method: 'POST' });

export const rangeToShopping = (from: string, to: string) =>
  request<ShoppingTransfer>('/api/meals/shopping', { method: 'POST', body: json({ from, to }) });

export const listDishes = () => request<Dish[]>('/api/dishes');

export const addDish = (input: DishInput) => request<Dish>('/api/dishes', { method: 'POST', body: json(input) });

export const updateDish = (id: string, input: DishInput) =>
  request<Dish>(`/api/dishes/${id}`, { method: 'PUT', body: json(input) });

export const deleteDish = (id: string) => request<void>(`/api/dishes/${id}`, { method: 'DELETE' });
