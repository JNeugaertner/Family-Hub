import type { MealType } from './api';

export const MEAL_TYPES: MealType[] = ['fruehstueck', 'mittagessen', 'abendessen', 'snacks'];

export const MEAL_STYLES: Record<MealType, { label: string; icon: string; bg: string; text: string; border: string }> = {
  fruehstueck: { label: 'Frühstück', icon: '🌅', bg: '#FFF7ED', text: '#C2410C', border: '#FED7AA' },
  mittagessen: { label: 'Mittagessen', icon: '☀️', bg: '#F0FDFA', text: '#0F766E', border: '#99F6E4' },
  abendessen: { label: 'Abendessen', icon: '🌙', bg: '#EFF6FF', text: '#1D4ED8', border: '#BFDBFE' },
  snacks: { label: 'Snacks', icon: '🍎', bg: '#FAF5FF', text: '#6D28D9', border: '#DDD6FE' },
};
