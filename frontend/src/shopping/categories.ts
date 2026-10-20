import type { ShoppingCategory } from './api';

export const SHOPPING_CATEGORIES: Record<ShoppingCategory, { label: string; icon: string }> = {
  milchprodukte: { label: 'Milchprodukte & Eier', icon: '🥛' },
  backwaren: { label: 'Backwaren', icon: '🍞' },
  fleisch: { label: 'Fleisch & Fisch', icon: '🥩' },
  gemuese: { label: 'Gemüse', icon: '🥦' },
  obst: { label: 'Obst', icon: '🍎' },
  getraenke: { label: 'Getränke', icon: '🧃' },
  vorrat: { label: 'Vorrat', icon: '🥫' },
  tiefkuehl: { label: 'Tiefkühl', icon: '🧊' },
  snacks: { label: 'Snacks', icon: '🍿' },
  haushalt: { label: 'Haushalt', icon: '🧹' },
  zutaten_essensplanung: { label: 'Zutaten Essensplanung', icon: '🍽️' },
};

export const SHOPPING_CATEGORY_KEYS = Object.keys(SHOPPING_CATEGORIES) as ShoppingCategory[];

// „Zutaten Essensplanung“ füllt nur der Essensplan (sein Abgleich würde von Hand eingetragene Artikel löschen),
// deshalb steht sie in keiner Auswahl.
export const SELECTABLE_SHOPPING_CATEGORIES = SHOPPING_CATEGORY_KEYS.filter(c => c !== 'zutaten_essensplanung');
