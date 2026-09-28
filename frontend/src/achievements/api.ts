import { json, request } from '../api/client';
import type { TaskCategory } from '../tasks/api';

export type AchievementRule = 'tasks' | 'category' | 'streak' | 'points';

export interface Achievement {
  id: string;
  key: string;
  icon: string;
  name: string;
  description: string | null;
  rule: AchievementRule;
  category: TaskCategory | null;
  target: number;
  bonus: number;
  active: boolean;
}

// Fortschritt eines Kindes je Erfolg; earnedAt ist gesetzt, sobald der Erfolg erreicht ist.
export interface AchievementItem {
  achievementId: string;
  icon: string;
  name: string;
  description: string | null;
  current: number;
  target: number;
  bonus: number;
  earnedAt: string | null;
}

export interface MemberAchievements {
  memberId: string;
  items: AchievementItem[];
}

export const listAchievements = () => request<Achievement[]>('/api/achievements');

export const listAchievementProgress = () => request<MemberAchievements[]>('/api/achievements/progress');

export const updateAchievement = (id: string, settings: { active: boolean; target: number; bonus: number }) =>
  request<Achievement>(`/api/achievements/${id}`, { method: 'PUT', body: json(settings) });
