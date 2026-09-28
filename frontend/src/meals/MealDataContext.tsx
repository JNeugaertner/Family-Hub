import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAuth } from '../auth/AuthContext';
import { addDays, mondayOf, startOfToday, toDateKey } from '../calendar/dates';
import { useShoppingData } from '../shopping/ShoppingDataContext';
import * as api from './api';

interface MealData {
  // Montag der angezeigten Woche
  weekStart: Date;
  setWeekStart: (monday: Date) => void;
  // Einträge der angezeigten Woche; offene Wünsche nur eigene bzw. für Administratoren alle (filtert das Backend)
  entries: api.MealEntry[];
  // Einträge von heute, für das Dashboard
  today: api.MealEntry[];
  // Offene Wünsche aus allen Wochen
  wishes: api.MealEntry[];
  dishes: api.Dish[];
  status: 'loading' | 'ready' | 'error';
  error: string | null;
  reload: () => Promise<void>;
  addMeal: (input: api.MealInput) => Promise<api.MealEntry>;
  removeMeal: (id: string) => Promise<void>;
  approve: (id: string) => Promise<void>;
  reject: (id: string) => Promise<void>;
  addDish: (input: api.DishInput) => Promise<api.Dish>;
  updateDish: (id: string, input: api.DishInput) => Promise<void>;
  removeDish: (id: string) => Promise<void>;
  // Zutaten auf die Einkaufsliste; lädt danach auch die Einkaufsliste neu
  mealToShopping: (id: string) => Promise<api.ShoppingTransfer>;
  weekToShopping: () => Promise<api.ShoppingTransfer>;
}

const MealDataContext = createContext<MealData | null>(null);

export function MealDataProvider({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  const mayView = can('essen', 'ansehen', 'familie');
  const { reload: reloadShopping } = useShoppingData();
  const [weekStart, setWeekStart] = useState(() => mondayOf(startOfToday()));
  const [entries, setEntries] = useState<api.MealEntry[]>([]);
  const [today, setToday] = useState<api.MealEntry[]>([]);
  const [wishes, setWishes] = useState<api.MealEntry[]>([]);
  const [dishes, setDishes] = useState<api.Dish[]>([]);
  const [status, setStatus] = useState<MealData['status']>('loading');
  const [error, setError] = useState<string | null>(null);
  // Nur die Antwort des letzten Ladevorgangs zählt (schnelles Blättern zwischen Wochen)
  const latest = useRef(0);

  const from = toDateKey(weekStart);
  const to = toDateKey(addDays(weekStart, 6));

  const reload = useCallback(async () => {
    if (!mayView) return;
    const call = ++latest.current;
    const todayKey = toDateKey(startOfToday());
    try {
      const [week, todays, open, all] = await Promise.all([
        api.listMeals(from, to), api.listMeals(todayKey, todayKey), api.listWishes(), api.listDishes(),
      ]);
      if (call !== latest.current) return;
      setEntries(week);
      setToday(todays);
      setWishes(open);
      setDishes(all);
      setError(null);
      setStatus('ready');
    } catch (err) {
      if (call !== latest.current) return;
      setError(err instanceof Error ? err.message : String(err));
      setStatus('error');
    }
  }, [mayView, from, to]);

  useEffect(() => { reload(); }, [reload]);

  const afterChange = useCallback(<A extends unknown[], R>(action: (...args: A) => Promise<R>) =>
    async (...args: A) => {
      const result = await action(...args);
      await reload();
      return result;
    }, [reload]);

  const toShopping = useCallback(<A extends unknown[]>(action: (...args: A) => Promise<api.ShoppingTransfer>) =>
    async (...args: A) => {
      const result = await action(...args);
      await reloadShopping();
      return result;
    }, [reloadShopping]);

  const value = useMemo<MealData>(() => ({
    weekStart, setWeekStart, entries, today, wishes, dishes, status, error, reload,
    addMeal: afterChange(api.addMeal),
    removeMeal: afterChange(api.deleteMeal),
    approve: afterChange(async (id: string) => { await api.approveMeal(id); }),
    reject: afterChange(api.rejectMeal),
    addDish: afterChange(api.addDish),
    updateDish: afterChange(async (id: string, input: api.DishInput) => { await api.updateDish(id, input); }),
    removeDish: afterChange(api.deleteDish),
    mealToShopping: toShopping(api.mealToShopping),
    weekToShopping: toShopping(() => api.rangeToShopping(from, to)),
  }), [weekStart, entries, today, wishes, dishes, status, error, reload, afterChange, toShopping, from, to]);

  return <MealDataContext.Provider value={value}>{children}</MealDataContext.Provider>;
}

export function useMealData(): MealData {
  const ctx = useContext(MealDataContext);
  if (!ctx) throw new Error('useMealData muss innerhalb von MealDataProvider verwendet werden');
  return ctx;
}
