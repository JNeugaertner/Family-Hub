import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { useAuth } from '../auth/AuthContext';
import { listBalances } from '../points/api';
import * as api from './api';

type Status = 'loading' | 'ready' | 'error';

interface TaskData {
  status: Status;
  error: string | null;
  // Nur die Aufgaben, die die angemeldete Person sehen darf (filtert das Backend)
  tasks: api.ApiTask[];
  // Punktestand je Mitglieds-Id, nur für sichtbare Mitglieder
  balances: Record<string, number>;
  reload: () => Promise<void>;
  saveTask: (input: api.TaskInput, id?: string) => Promise<void>;
  changeStatus: (id: string, status: Exclude<api.TaskStatus, 'confirmed'>) => Promise<void>;
  removeTask: (id: string) => Promise<void>;
  removeCompletedTasks: () => Promise<{ deleted: number }>;
  confirmTask: (id: string) => Promise<api.ApiTask>;
  reopenTask: (id: string) => Promise<void>;
  claimTask: (id: string) => Promise<void>;
  releaseTask: (id: string) => Promise<void>;
}

const TaskDataContext = createContext<TaskData | null>(null);

export function TaskDataProvider({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  const mayViewPoints = can('punkte', 'ansehen', 'eigen');
  const [status, setStatus] = useState<Status>('loading');
  const [error, setError] = useState<string | null>(null);
  const [tasks, setTasks] = useState<api.ApiTask[]>([]);
  const [balances, setBalances] = useState<Record<string, number>>({});

  // Nur die zuletzt gestartete Abfrage zählt (Hintergrund-Aktualisierung und Neuladen nach Änderungen)
  const latest = useRef(0);

  const reload = useCallback(async () => {
    const call = ++latest.current;
    try {
      const [t, b] = await Promise.all([api.listTasks(), mayViewPoints ? listBalances() : Promise.resolve([])]);
      if (call !== latest.current) return;
      setTasks(t);
      setBalances(Object.fromEntries(b.map(x => [x.memberId, x.points])));
      setError(null);
      setStatus('ready');
    } catch (err) {
      if (call !== latest.current) return;
      setError(err instanceof Error ? err.message : String(err));
      setStatus('error');
    }
  }, [mayViewPoints]);

  useEffect(() => { reload(); }, [reload]);
  // Einlösungen und Erfolge ändern Punktestände
  useAutoRefresh(reload, ['tasks', 'redemptions', 'achievements', 'members']);

  const afterChange = useCallback(<A extends unknown[], R>(action: (...args: A) => Promise<R>) =>
    async (...args: A) => {
      const result = await action(...args);
      await reload();
      return result;
    }, [reload]);

  const value = useMemo<TaskData>(() => ({
    status, error, tasks, balances, reload,
    saveTask: afterChange(async (input: api.TaskInput, id?: string) => {
      await (id ? api.updateTask(id, input) : api.createTask(input));
    }),
    changeStatus: afterChange(async (id: string, s: Exclude<api.TaskStatus, 'confirmed'>) => {
      await api.changeTaskStatus(id, s);
    }),
    removeTask: afterChange(api.deleteTask),
    removeCompletedTasks: afterChange(api.deleteCompletedTasks),
    confirmTask: afterChange(api.confirmTask),
    reopenTask: afterChange(async (id: string) => { await api.reopenTask(id); }),
    claimTask: afterChange(async (id: string) => { await api.claimTask(id); }),
    releaseTask: afterChange(async (id: string) => { await api.releaseTask(id); }),
  }), [status, error, tasks, balances, reload, afterChange]);

  return <TaskDataContext.Provider value={value}>{children}</TaskDataContext.Provider>;
}

export function useTaskData(): TaskData {
  const ctx = useContext(TaskDataContext);
  if (!ctx) throw new Error('useTaskData muss innerhalb von TaskDataProvider verwendet werden');
  return ctx;
}
