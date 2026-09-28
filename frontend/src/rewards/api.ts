import { json, request } from '../api/client';

export type RewardCategory = 'essen' | 'freizeit' | 'ausflug' | 'geschenk';

export interface Reward {
  id: string;
  emoji: string;
  name: string;
  description: string | null;
  cost: number;
  category: RewardCategory;
  active: boolean;
  // Mehrfach einlösbar; sonst höchstens einmal je Person
  repeatable: boolean;
}

export type RewardInput = Omit<Reward, 'id'>;

export type RedemptionStatus = 'pending' | 'approved' | 'rejected';

// Name, Emoji und Kosten sind beim Einlösen mitgespeichert; die Punkte sind da schon abgezogen.
export interface Redemption {
  id: string;
  rewardId: string;
  rewardName: string;
  rewardEmoji: string;
  cost: number;
  memberId: string;
  status: RedemptionStatus;
  requestedAt: string;
  requestedBy: string;
  decidedAt: string | null;
  decidedBy: string | null;
  rejectReason: string | null;
}

export const listRewards = () => request<Reward[]>('/api/rewards');

export const createReward = (input: RewardInput) =>
  request<Reward>('/api/rewards', { method: 'POST', body: json(input) });

export const updateReward = (id: string, input: RewardInput) =>
  request<Reward>(`/api/rewards/${id}`, { method: 'PUT', body: json(input) });

export const deleteReward = (id: string) => request<void>(`/api/rewards/${id}`, { method: 'DELETE' });

export const listRedemptions = () => request<Redemption[]>('/api/redemptions');

// Ohne memberId für sich selbst; Administratoren auch für ein Kind (dann sofort genehmigt)
export const redeemReward = (rewardId: string, memberId?: string) =>
  request<Redemption>('/api/redemptions', { method: 'POST', body: json({ rewardId, memberId }) });

export const approveRedemption = (id: string) =>
  request<Redemption>(`/api/redemptions/${id}/approve`, { method: 'POST' });

export const rejectRedemption = (id: string, reason: string | null) =>
  request<Redemption>(`/api/redemptions/${id}/reject`, { method: 'POST', body: json({ reason }) });

export const withdrawRedemption = (id: string) => request<void>(`/api/redemptions/${id}`, { method: 'DELETE' });
