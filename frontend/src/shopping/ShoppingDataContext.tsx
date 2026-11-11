import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { useAuth } from '../auth/AuthContext';
import { useUndo } from '../undo/UndoContext';
import * as api from './api';

interface ShoppingData {
  // Offene Vorschläge nur eigene bzw. für Administratoren alle (filtert das Backend)
  items: api.ShoppingItem[];
  status: 'loading' | 'ready' | 'error';
  error: string | null;
  reload: () => Promise<void>;
  add: (input: api.ShoppingInput) => Promise<api.ShoppingItem>;
  update: (id: string, input: api.ShoppingInput) => Promise<void>;
  setChecked: (id: string, checked: boolean) => Promise<void>;
  remove: (id: string) => Promise<void>;
  removeChecked: () => Promise<api.RemovedChecked>;
  approve: (id: string) => Promise<void>;
  reject: (id: string) => Promise<void>;
}

const ShoppingDataContext = createContext<ShoppingData | null>(null);

export function ShoppingDataProvider({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  const mayView = can('einkauf', 'ansehen', 'familie');
  const [items, setItems] = useState<api.ShoppingItem[]>([]);
  const [status, setStatus] = useState<ShoppingData['status']>('loading');
  const [error, setError] = useState<string | null>(null);

  // Nur die zuletzt gestartete Abfrage zählt (Hintergrund-Aktualisierung und Neuladen nach Änderungen)
  const latest = useRef(0);

  const reload = useCallback(async () => {
    if (!mayView) return;
    const call = ++latest.current;
    try {
      const items = await api.listShopping();
      if (call !== latest.current) return;
      setItems(items);
      setError(null);
      setStatus('ready');
    } catch (err) {
      if (call !== latest.current) return;
      setError(err instanceof Error ? err.message : String(err));
      setStatus('error');
    }
  }, [mayView]);

  useEffect(() => { reload(); }, [reload]);
  // Der Essensplan legt Zutaten auf die Einkaufsliste
  useAutoRefresh(reload, ['shopping', 'meals']);

  const { offer } = useUndo();
  // Nach dem Wiederherstellen gleich neu laden (die Live-Meldung kommt ohnehin, aber später)
  const undoAndReload = useCallback(async (token: string) => { await api.undoShopping(token); await reload(); }, [reload]);

  const afterChange = useCallback(<A extends unknown[], R>(action: (...args: A) => Promise<R>) =>
    async (...args: A) => {
      const result = await action(...args);
      await reload();
      return result;
    }, [reload]);

  const value = useMemo<ShoppingData>(() => ({
    items, status, error, reload,
    add: afterChange(api.addShoppingItem),
    update: afterChange(async (id: string, input: api.ShoppingInput) => { await api.updateShoppingItem(id, input); }),
    setChecked: afterChange(async (id: string, checked: boolean) => { await api.checkShoppingItem(id, checked); }),
    remove: afterChange(async (id: string) => {
      const name = items.find(i => i.id === id)?.name;
      const { undoToken } = await api.deleteShoppingItem(id);
      offer(name ? `„${name}“ entfernt.` : 'Artikel entfernt.', undoToken, undoAndReload);
    }),
    removeChecked: afterChange(async () => {
      const { result, undoToken } = await api.deleteCheckedShoppingItems();
      offer('Abgehakte Artikel entfernt.', undoToken, undoAndReload);
      return result;
    }),
    approve: afterChange(async (id: string) => { await api.approveShoppingItem(id); }),
    reject: afterChange(api.rejectShoppingItem),
  }), [items, status, error, reload, afterChange, offer, undoAndReload]);

  return <ShoppingDataContext.Provider value={value}>{children}</ShoppingDataContext.Provider>;
}

export function useShoppingData(): ShoppingData {
  const ctx = useContext(ShoppingDataContext);
  if (!ctx) throw new Error('useShoppingData muss innerhalb von ShoppingDataProvider verwendet werden');
  return ctx;
}
