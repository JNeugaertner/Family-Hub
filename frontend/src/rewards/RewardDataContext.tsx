import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useAutoRefresh } from '../api/useAutoRefresh';
import { useAuth } from '../auth/AuthContext';
import { useTaskData } from '../tasks/TaskDataContext';
import * as api from './api';

interface RewardData {
  // Mit Verwaltungsrecht alle Belohnungen, sonst nur aktive (filtert das Backend)
  rewards: api.Reward[];
  // Mit Familienrecht alle Einlösungen, sonst nur die eigenen; neueste zuerst
  redemptions: api.Redemption[];
  error: string | null;
  reload: () => Promise<void>;
  saveReward: (input: api.RewardInput, id?: string) => Promise<void>;
  removeReward: (id: string) => Promise<void>;
  redeem: (rewardId: string, memberId?: string) => Promise<api.Redemption>;
  approve: (id: string) => Promise<void>;
  reject: (id: string, reason: string | null) => Promise<void>;
  withdraw: (id: string) => Promise<void>;
}

const RewardDataContext = createContext<RewardData | null>(null);

export function RewardDataProvider({ children }: { children: ReactNode }) {
  const { can } = useAuth();
  const { reload: reloadPoints } = useTaskData();
  const mayView = can('punkte', 'ansehen', 'eigen');
  const [rewards, setRewards] = useState<api.Reward[]>([]);
  const [redemptions, setRedemptions] = useState<api.Redemption[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Nur die zuletzt gestartete Abfrage zählt (Hintergrund-Aktualisierung und Neuladen nach Änderungen)
  const latest = useRef(0);

  const reload = useCallback(async () => {
    if (!mayView) return;
    const call = ++latest.current;
    try {
      const [r, d] = await Promise.all([api.listRewards(), api.listRedemptions()]);
      if (call !== latest.current) return;
      setRewards(r);
      setRedemptions(d);
      setError(null);
    } catch (err) {
      if (call !== latest.current) return;
      setError(err instanceof Error ? err.message : String(err));
    }
  }, [mayView]);

  useEffect(() => { reload(); }, [reload]);
  useAutoRefresh(reload, ['rewards', 'redemptions']);

  // Einlösungen ändern Punktestände, daher auch die Punkte neu laden
  const afterChange = useCallback(<A extends unknown[], R>(action: (...args: A) => Promise<R>) =>
    async (...args: A) => {
      const result = await action(...args);
      await Promise.all([reload(), reloadPoints()]);
      return result;
    }, [reload, reloadPoints]);

  const value = useMemo<RewardData>(() => ({
    rewards, redemptions, error, reload,
    saveReward: afterChange(async (input: api.RewardInput, id?: string) => {
      await (id ? api.updateReward(id, input) : api.createReward(input));
    }),
    removeReward: afterChange(api.deleteReward),
    redeem: afterChange(api.redeemReward),
    approve: afterChange(async (id: string) => { await api.approveRedemption(id); }),
    reject: afterChange(async (id: string, reason: string | null) => { await api.rejectRedemption(id, reason); }),
    withdraw: afterChange(api.withdrawRedemption),
  }), [rewards, redemptions, error, reload, afterChange]);

  return <RewardDataContext.Provider value={value}>{children}</RewardDataContext.Provider>;
}

export function useRewardData(): RewardData {
  const ctx = useContext(RewardDataContext);
  if (!ctx) throw new Error('useRewardData muss innerhalb von RewardDataProvider verwendet werden');
  return ctx;
}
