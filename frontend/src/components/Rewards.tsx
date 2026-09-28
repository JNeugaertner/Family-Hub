import { useEffect, useState } from 'react';
import { StarIcon, TrophyIcon, PlusIcon, CheckIcon, XIcon } from './Icons';
import { useAuth, useMe } from '../auth/AuthContext';
import {
  listAchievementProgress, listAchievements, updateAchievement, type Achievement, type MemberAchievements,
} from '../achievements/api';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { listHistory, type PointEntry } from '../points/api';
import { formatAgo, usePointHolders, type PointHolder } from '../points/usePointHolders';
import type { Redemption, Reward, RewardCategory, RewardInput } from '../rewards/api';
import { useRewardData } from '../rewards/RewardDataContext';
import { useTaskData } from '../tasks/TaskDataContext';

interface Props { onNavigate: (p: any) => void; }

const CATEGORY_LABELS: Record<RewardCategory, { label: string; icon: string }> = {
  essen: { label: 'Essen', icon: '🍴' },
  freizeit: { label: 'Freizeit', icon: '🎉' },
  ausflug: { label: 'Ausflug', icon: '🗺️' },
  geschenk: { label: 'Geschenk', icon: '🎁' },
};

const EMOJIS = ['🍽️','📱','🎬','🌙','🎮','🍕','🎪','🏊','🎁','🛍️','⚽','🎨','📚','🍦','🎠','🏖️','🎡','🎯','🏆','✈️'];

const errorText = (err: unknown) => (err instanceof Error ? err.message : String(err));
const formatDate = (iso: string) => new Date(iso).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });

// ─── helpers ─────────────────────────────────────────────────────────────────

function ProgressRing({ value, max, color, size = 72 }: { value: number; max: number; color: string; size?: number }) {
  const r = (size - 10) / 2;
  const c = 2 * Math.PI * r;
  const d = Math.min(value / max, 1) * c;
  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="-rotate-90">
      <circle cx={size/2} cy={size/2} r={r} fill="none" stroke="#F1F5F9" strokeWidth={7} />
      <circle cx={size/2} cy={size/2} r={r} fill="none" stroke={color} strokeWidth={7}
        strokeDasharray={`${d} ${c - d}`} strokeLinecap="round" />
    </svg>
  );
}

function StatusBadge({ redemption }: { redemption: Redemption }) {
  const styles = {
    pending: 'bg-[#FFFBEB] text-[#D97706]',
    approved: 'bg-[#F0FDF4] text-[#16A34A]',
    rejected: 'bg-[#FEF2F2] text-[#DC2626]',
  };
  const labels = { pending: '⏳ Ausstehend', approved: '✅ Genehmigt', rejected: '✕ Abgelehnt' };
  return (
    <span className={`text-xs font-bold px-2.5 py-1 rounded-full flex-shrink-0 ${styles[redemption.status]}`}>
      {labels[redemption.status]}
    </span>
  );
}

// ─── tabs ─────────────────────────────────────────────────────────────────────

type Tab = 'overview' | 'shop' | 'achievements' | 'manage';

const TABS: { id: Tab; label: string; emoji: string }[] = [
  { id: 'overview',     label: 'Übersicht',      emoji: '📊' },
  { id: 'shop',         label: 'Belohnungsshop', emoji: '🛍️' },
  { id: 'achievements', label: 'Erfolge',        emoji: '🏆' },
  { id: 'manage',       label: 'Verwalten',      emoji: '⚙️' },
];

// ─── overview tab ─────────────────────────────────────────────────────────────

function OverviewTab({ rewards, kids: children, history, confirmedTasks, redeemedCount }: {
  rewards: Reward[];
  kids: PointHolder[];
  history: PointEntry[];
  confirmedTasks: number;
  redeemedCount: number;
}) {
  const totalPoints = children.reduce((s, c) => s + c.points, 0);
  const topKid      = children[0];
  const maxPoints   = Math.max(1, topKid.points);

  return (
    <div className="space-y-6">
      {/* Stat tiles */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: 'Punkte gesamt', value: totalPoints, icon: '⭐', color: '#F59E0B', bg: '#FFFBEB' },
          { label: 'Beste(r)',      value: topKid.name, icon: '🥇', color: '#F97316', bg: '#FFF7ED' },
          { label: 'Aufgaben bestätigt', value: confirmedTasks, icon: '✅', color: '#22C55E', bg: '#F0FDF4' },
          { label: 'Eingelöst',    value: redeemedCount, icon: '🎁', color: '#8B5CF6', bg: '#F5F3FF' },
        ].map(s => (
          <div key={s.label} className="bg-white rounded-xl border border-slate-100 px-4 py-4">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-lg flex items-center justify-center" style={{ backgroundColor: s.bg }}>{s.icon}</div>
              <span className="text-xs text-slate-500 font-medium">{s.label}</span>
            </div>
            <div className="text-xl font-bold" style={{ color: s.color }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* Leaderboard (nur mit Familienrecht; Kinder sehen nur den eigenen Stand) */}
      {children.length > 1 && <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
        <h3 className="font-bold text-slate-800 text-base mb-4 flex items-center gap-2">
          <TrophyIcon size={16} className="text-[#F59E0B]" />
          Rangliste
        </h3>
        <div className="space-y-3">
          {children.map((c, i) => {
            const medals = ['🥇','🥈','🥉'];
            const nextReward = [...rewards].filter(r => r.active && r.cost > c.points).sort((a, b) => a.cost - b.cost)[0];
            return (
              <div key={c.id} className="flex items-center gap-3">
                <span className="text-xl w-7 text-center flex-shrink-0">{medals[i] || `${i+1}`}</span>
                <div className="w-9 h-9 rounded-full flex items-center justify-center text-white font-bold text-sm flex-shrink-0" style={{ backgroundColor: c.color }}>{c.initials[0]}</div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-sm font-semibold text-slate-800">{c.name}</span>
                    <span className="text-sm font-bold" style={{ color: c.color }}>{c.points} Pkt.</span>
                  </div>
                  <div className="h-2.5 bg-slate-100 rounded-full overflow-hidden">
                    <div className="h-full rounded-full relative overflow-hidden transition-all duration-1000" style={{ width: `${(Math.max(0, c.points) / maxPoints) * 100}%`, backgroundColor: c.color }}>
                      <div className="absolute inset-0 progress-shimmer" />
                    </div>
                  </div>
                  {nextReward && (
                    <div className="text-[10px] text-slate-400 mt-0.5">
                      Noch {nextReward.cost - c.points} Pkt. für {nextReward.emoji} {nextReward.name}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>}

      {/* Child cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        {children.map(c => {
          const recent = history.filter(e => e.memberId === c.id).slice(0, 3);
          const nextLevel   = c.points >= 400 ? 500 : c.points >= 200 ? 400 : 200;
          const prevLevel   = c.points >= 400 ? 400 : c.points >= 200 ? 200 : 0;
          const levelName   = c.points >= 400 ? 'Gold' : c.points >= 200 ? 'Silber' : 'Bronze';
          const levelIcon   = c.points >= 400 ? '🥇' : c.points >= 200 ? '🥈' : '🥉';
          const canAfford   = rewards.filter(r => r.active && r.cost <= c.points).length;

          return (
            <div key={c.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
              <div className="p-5 relative" style={{ background: `linear-gradient(135deg, ${c.color}12, ${c.color}04)` }}>
                <div className="absolute top-0 right-0 w-24 h-24 rounded-full opacity-10 -translate-y-6 translate-x-6" style={{ backgroundColor: c.color }} />
                <div className="relative flex items-center gap-4">
                  <div className="relative flex-shrink-0">
                    <ProgressRing value={Math.max(0, c.points - prevLevel)} max={nextLevel - prevLevel} color={c.color} />
                    <div className="absolute inset-0 flex items-center justify-center" style={{ width: 72, height: 72 }}>
                      <span className="text-xl font-bold" style={{ color: c.color }}>{c.initials[0]}</span>
                    </div>
                  </div>
                  <div>
                    <h3 className="font-bold text-slate-800 text-base">{c.name}</h3>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      <span>{levelIcon}</span>
                      <span className="text-sm font-semibold" style={{ color: c.color }}>{levelName}</span>
                    </div>
                    <div className="flex items-center gap-1.5 mt-1">
                      <StarIcon size={13} className="text-[#F59E0B]" />
                      <span className="text-xl font-bold text-slate-800">{c.points}</span>
                      <span className="text-xs text-slate-400">Punkte</span>
                    </div>
                    <div className="text-[10px] text-[#22C55E] font-semibold mt-0.5">
                      🎁 {canAfford} Belohnungen verfügbar
                    </div>
                  </div>
                </div>
                <div className="mt-3">
                  <div className="flex justify-between text-xs text-slate-400 mb-1">
                    <span>Nächste Stufe</span>
                    <span style={{ color: c.color }}>{c.points}/{nextLevel}</span>
                  </div>
                  <div className="h-2 bg-slate-100 rounded-full overflow-hidden">
                    <div className="h-full rounded-full relative overflow-hidden" style={{ width: `${(Math.max(0, c.points - prevLevel)/(nextLevel - prevLevel))*100}%`, backgroundColor: c.color }}>
                      <div className="absolute inset-0 progress-shimmer" />
                    </div>
                  </div>
                </div>
              </div>
              {/* recent (Punkte-Historie, Einlösungen als Abzug) */}
              <div className="px-4 pb-4 pt-2">
                <div className="text-xs font-semibold text-slate-500 mb-2">Letzte Aktivitäten</div>
                {recent.length === 0 && <div className="text-xs text-slate-400 py-1">Noch keine Punkte</div>}
                {recent.map(a => (
                  <div key={a.id} className="flex items-center justify-between gap-2 text-xs py-1">
                    <span className="text-slate-600 truncate">{a.reason}</span>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className={`font-bold ${a.amount < 0 ? 'text-[#DC2626]' : 'text-[#22C55E]'}`}>{a.amount > 0 ? '+' : ''}{a.amount}</span>
                      <span className="text-slate-400">{formatAgo(a.createdAt)}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── shop tab ─────────────────────────────────────────────────────────────────

// holders: für wen eingelöst werden darf (Kinder: nur sich selbst; Administratoren: alle Kinder)
function ShopTab({ rewards, redemptions, holders, forOthers, busy, onRedeem, onWithdraw }: {
  rewards: Reward[];
  redemptions: Redemption[];
  holders: PointHolder[];
  forOthers: boolean;
  busy: boolean;
  onRedeem: (reward: Reward, member: PointHolder) => void;
  onWithdraw: (redemption: Redemption) => void;
}) {
  const [viewer, setViewer] = useState(holders[0].id);
  const child      = holders.find(m => m.id === viewer) ?? holders[0];
  const active     = rewards.filter(r => r.active);
  const categories = Array.from(new Set(active.map(r => r.category)));

  const myRedemptions = redemptions.filter(r => r.memberId === child.id);
  const pendingFor  = (reward: Reward) => myRedemptions.find(r => r.rewardId === reward.id && r.status === 'pending');
  const claimedOnce = (reward: Reward) => !reward.repeatable && myRedemptions.some(r => r.rewardId === reward.id && r.status === 'approved');
  const approvedCount = myRedemptions.filter(r => r.status === 'approved').length;

  return (
    <div className="space-y-6">
      {/* Child switcher (Administratoren lösen auch für Kinder ein) */}
      {holders.length > 1 && <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Einlösen für:</div>
        <div className="flex flex-wrap gap-2">
          {holders.map(c => (
            <button
              key={c.id}
              onClick={() => setViewer(c.id)}
              aria-pressed={viewer === c.id}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold transition-all ${viewer === c.id ? 'text-white shadow-sm' : 'bg-slate-50 text-slate-600 hover:bg-slate-100'}`}
              style={viewer === c.id ? { backgroundColor: c.color } : {}}
            >
              <div className="w-6 h-6 rounded-full flex items-center justify-center text-white text-xs font-bold" style={{ backgroundColor: viewer === c.id ? 'rgba(255,255,255,0.3)' : c.color }}>
                {c.initials[0]}
              </div>
              {c.name}
              <span className={`ml-1 text-xs font-bold ${viewer === c.id ? 'text-white/80' : ''}`} style={viewer !== c.id ? { color: c.color } : {}}>
                {c.points} Pkt.
              </span>
            </button>
          ))}
        </div>
      </div>}

      {forOthers && (
        <div className="bg-[#EFF6FF] border border-[#BFDBFE] text-[#1E40AF] text-xs rounded-xl px-3 py-2">
          Wenn du für {child.name} einlöst, ist die Einlösung sofort genehmigt und die Punkte werden abgezogen.
        </div>
      )}

      {/* Balance banner */}
      <div
        className="rounded-2xl p-5 text-white relative overflow-hidden"
        style={{ background: `linear-gradient(135deg, ${child.color}, ${child.color}BB)` }}
      >
        <div className="absolute top-0 right-0 w-32 h-32 rounded-full bg-white/10 -translate-y-8 translate-x-8" />
        <div className="relative flex items-center gap-4">
          <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center text-3xl">⭐</div>
          <div>
            <div className="text-white/70 text-sm">{forOthers ? `Punktestand von ${child.name}` : 'Dein Punktestand'}</div>
            <div className="text-4xl font-bold" data-balance={child.points}>{child.points}</div>
            <div className="text-white/70 text-sm mt-0.5">Punkte verfügbar</div>
          </div>
          <div className="ml-auto text-right hidden sm:block">
            <div className="text-white/70 text-xs">Bereits eingelöst</div>
            <div className="text-2xl font-bold">{approvedCount}×</div>
            <div className="text-white/70 text-xs">Belohnungen</div>
          </div>
        </div>
      </div>

      {active.length === 0 && (
        <div className="bg-white rounded-2xl border border-slate-100 p-10 text-center text-slate-400 text-sm">Noch keine Belohnungen im Shop.</div>
      )}

      {/* Rewards grid by category */}
      {categories.map(cat => {
        const catRewards = active.filter(r => r.category === cat);
        return (
          <div key={cat}>
            <h3 className="font-bold text-slate-700 text-sm mb-3 flex items-center gap-2">
              <span>{CATEGORY_LABELS[cat].icon}</span>{CATEGORY_LABELS[cat].label}
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {catRewards.map(r => {
                const canAfford = child.points >= r.cost;
                const claimed   = claimedOnce(r);
                const pending   = pendingFor(r);
                const pctNeeded = Math.min(Math.max(0, child.points) / r.cost, 1);

                return (
                  <div
                    key={r.id}
                    data-reward={r.name}
                    className={`relative flex flex-col rounded-2xl border transition-all overflow-hidden ${
                      canAfford
                        ? 'border-transparent bg-white shadow-sm hover:shadow-md hover:shadow-slate-100 hover:-translate-y-0.5'
                        : 'border-slate-100 bg-slate-50/50'
                    }`}
                  >
                    <div
                      className={`h-20 flex items-center justify-center text-4xl transition-opacity ${!canAfford ? 'opacity-40 grayscale' : ''}`}
                      style={{ background: canAfford ? `linear-gradient(135deg, ${child.color}20, ${child.color}08)` : '#F8FAFC' }}
                    >
                      {r.emoji}
                    </div>

                    <div className="p-3 flex flex-col flex-1">
                      <div className={`text-sm font-bold leading-tight mb-1 ${!canAfford ? 'text-slate-400' : 'text-slate-800'}`}>{r.name}</div>
                      <div className="text-[10px] text-slate-400 leading-snug mb-2 flex-1">
                        {r.description}{!r.repeatable && <span className="block mt-0.5">Nur einmal einlösbar</span>}
                      </div>

                      <div className="flex items-center justify-between mb-2">
                        <div className={`flex items-center gap-1 text-sm font-bold ${canAfford ? 'text-[#F59E0B]' : 'text-slate-400'}`}>
                          <span>⭐</span>{r.cost}
                        </div>
                        {!canAfford && (
                          <span className="text-[10px] text-slate-400">noch {r.cost - child.points}</span>
                        )}
                      </div>

                      {!canAfford && !pending && !claimed && (
                        <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden mb-2">
                          <div className="h-full rounded-full" style={{ width: `${pctNeeded * 100}%`, backgroundColor: child.color }} />
                        </div>
                      )}

                      {claimed ? (
                        <div className="w-full py-2 rounded-xl bg-[#F0FDF4] text-[#16A34A] text-xs font-bold text-center flex items-center justify-center gap-1">
                          <CheckIcon size={12} strokeWidth={3} /> Eingelöst
                        </div>
                      ) : pending ? (
                        <div className="space-y-1">
                          <div className="w-full py-2 rounded-xl bg-[#FFFBEB] text-[#D97706] text-xs font-bold text-center">
                            ⏳ Wartet auf Eltern
                          </div>
                          <button type="button" disabled={busy} onClick={() => onWithdraw(pending)}
                            className="w-full py-1 rounded-lg text-[11px] font-semibold text-slate-500 hover:bg-slate-100 disabled:opacity-50">
                            Zurückziehen
                          </button>
                        </div>
                      ) : (
                        <button
                          onClick={() => canAfford && onRedeem(r, child)}
                          disabled={!canAfford || busy}
                          className={`w-full py-2 rounded-xl text-xs font-bold transition-all ${
                            canAfford
                              ? 'text-white hover:opacity-90 active:scale-95 disabled:opacity-50'
                              : 'bg-slate-100 text-slate-300 cursor-not-allowed'
                          }`}
                          style={canAfford ? { backgroundColor: child.color } : {}}
                        >
                          {canAfford ? (forOthers ? `Für ${child.name} einlösen` : 'Einlösen') : 'Nicht genug Punkte'}
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      {/* Redemption history */}
      {myRedemptions.length > 0 && (
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
          <h3 className="font-bold text-slate-800 text-base mb-4">📋 {forOthers ? `Einlöseverlauf von ${child.name}` : 'Mein Einlöseverlauf'}</h3>
          <div className="space-y-2">
            {myRedemptions.map(red => (
              <div key={red.id} className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-slate-50">
                <span className="text-xl flex-shrink-0">{red.rewardEmoji}</span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-slate-800">{red.rewardName}</div>
                  <div className="text-xs text-slate-400">{formatDate(red.requestedAt)} · ⭐ {red.cost} Punkte
                    {red.status === 'rejected' && ' · Punkte zurückgebucht'}</div>
                  {red.rejectReason && <div className="text-xs text-[#DC2626] mt-0.5">Grund: {red.rejectReason}</div>}
                </div>
                <StatusBadge redemption={red} />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── achievements tab ─────────────────────────────────────────────────────────

function AchievementsTab({ kids: children, mayManage, refreshKey }: {
  kids: PointHolder[];
  mayManage: boolean;
  refreshKey: unknown;
}) {
  const [progress, setProgress] = useState<MemberAchievements[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listAchievementProgress()
      .then(p => { if (!cancelled) { setProgress(p); setError(null); } })
      .catch(err => { if (!cancelled) setError(errorText(err)); });
    return () => { cancelled = true; };
  }, [refreshKey]);

  return (
    <div className="space-y-6">
      {error && <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{error}</div>}
      {!progress && !error && <div className="text-sm text-slate-400">Erfolge werden geladen…</div>}
      {progress && children.map(c => {
        const items = progress.find(p => p.memberId === c.id)?.items ?? [];
        const earned = items.filter(i => i.earnedAt).length;
        return (
          <div key={c.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" data-achievements={c.name}>
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold" style={{ backgroundColor: c.color }}>{c.initials[0]}</div>
              <div>
                <div className="font-bold text-slate-800">{c.name}</div>
                <div className="text-xs text-slate-400">{earned}/{items.length} Erfolge erreicht</div>
              </div>
              <div className="ml-auto font-bold text-lg" style={{ color: c.color }}>{c.points} Pkt.</div>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
              {items.map(a => {
                const done = !!a.earnedAt;
                return (
                  <div
                    key={a.achievementId}
                    data-achievement={a.name}
                    data-earned={done}
                    className={`flex flex-col items-center p-3 rounded-xl text-center border transition-all ${done ? 'bg-[#FFFBEB] border-[#FDE68A] shadow-sm' : 'bg-slate-50 border-slate-100'}`}
                    title={a.description ?? undefined}
                  >
                    <span className={`text-3xl mb-1.5 ${done ? '' : 'opacity-40 grayscale'}`}>{a.icon}</span>
                    <span className="text-[11px] font-semibold text-slate-700 leading-tight">{a.name}</span>
                    <span className="text-[10px] text-slate-400 mt-0.5 leading-tight">{a.description}</span>
                    {done ? (
                      <span className="text-[10px] text-[#F59E0B] font-bold mt-1.5">
                        ✓ {formatDate(a.earnedAt!)}{a.bonus > 0 && ` · +${a.bonus} Pkt.`}
                      </span>
                    ) : (
                      <div className="w-full mt-2">
                        <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden">
                          <div className="h-full rounded-full" style={{ width: `${(a.current / a.target) * 100}%`, backgroundColor: c.color }} />
                        </div>
                        <div className="text-[10px] text-slate-400 mt-1">{a.current}/{a.target}{a.bonus > 0 && ` · +${a.bonus} Pkt.`}</div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
      <p className="text-xs text-slate-400">Erfolge zählen bestätigte Aufgaben seit Einführung der Erfolge, nicht rückwirkend.</p>
      {mayManage && <AchievementSettings onChanged={() => listAchievementProgress().then(setProgress).catch(() => {})} />}
    </div>
  );
}

// Eltern: Erfolge aktivieren/deaktivieren, Ziel und Bonus anpassen
function AchievementSettings({ onChanged }: { onChanged: () => void }) {
  const { reload: reloadPoints } = useTaskData();
  const [catalog, setCatalog] = useState<Achievement[]>([]);
  const [drafts, setDrafts] = useState<Record<string, { active: boolean; target: number; bonus: number }>>({});
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);

  useEffect(() => {
    listAchievements().then(list => {
      setCatalog(list);
      setDrafts(Object.fromEntries(list.map(a => [a.id, { active: a.active, target: a.target, bonus: a.bonus }])));
    }).catch(err => setMessage({ text: errorText(err), ok: false }));
  }, []);

  const save = async (a: Achievement) => {
    setMessage(null);
    try {
      const saved = await updateAchievement(a.id, drafts[a.id]);
      setCatalog(list => list.map(x => (x.id === saved.id ? saved : x)));
      setMessage({ text: `„${a.name}“ gespeichert.`, ok: true });
      onChanged();
      await reloadPoints();
    } catch (err) {
      setMessage({ text: errorText(err), ok: false });
    }
  };

  const changed = (a: Achievement) => {
    const d = drafts[a.id];
    return d && (d.active !== a.active || d.target !== a.target || d.bonus !== a.bonus);
  };

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="achievement-settings-title">
      <h3 id="achievement-settings-title" className="font-bold text-slate-800 text-base mb-1">⚙️ Erfolge anpassen</h3>
      <p className="text-xs text-slate-400 mb-3">Wer ein gesenktes Ziel schon erreicht, bekommt den Erfolg sofort samt Bonus.</p>
      {message && <p role={message.ok ? 'status' : 'alert'} className={`text-sm mb-2 ${message.ok ? 'text-[#15803D]' : 'text-[#DC2626]'}`}>{message.text}</p>}
      <div className="divide-y divide-slate-50">
        {catalog.map(a => {
          const d = drafts[a.id];
          if (!d) return null;
          const set = (patch: Partial<typeof d>) => setDrafts(all => ({ ...all, [a.id]: { ...d, ...patch } }));
          return (
            <div key={a.id} className={`flex flex-wrap items-center gap-3 py-2.5 ${d.active ? '' : 'opacity-60'}`} data-achievement-setting={a.name}>
              <span className="text-2xl w-8 text-center">{a.icon}</span>
              <div className="flex-1 min-w-[160px]">
                <div className="text-sm font-semibold text-slate-800">{a.name}</div>
                <div className="text-xs text-slate-400">{a.description}</div>
              </div>
              <label className="flex items-center gap-1.5 text-xs text-slate-600">
                <input type="checkbox" className="w-4 h-4 accent-[#22C55E]" checked={d.active} onChange={e => set({ active: e.target.checked })} />
                Aktiv
              </label>
              <label className="flex items-center gap-1.5 text-xs text-slate-600">
                Ziel
                <input type="number" min={1} max={10000} value={d.target} onChange={e => set({ target: Number(e.target.value) })}
                  className="w-20 border border-slate-200 rounded-lg px-2 py-1 text-sm" aria-label={`Ziel für ${a.name}`} />
              </label>
              <label className="flex items-center gap-1.5 text-xs text-slate-600">
                Bonus ⭐
                <input type="number" min={0} max={1000} value={d.bonus} onChange={e => set({ bonus: Number(e.target.value) })}
                  className="w-20 border border-slate-200 rounded-lg px-2 py-1 text-sm" aria-label={`Bonus für ${a.name}`} />
              </label>
              <button type="button" onClick={() => save(a)} disabled={!changed(a)}
                className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#2563EB] text-white hover:bg-[#1D4ED8] disabled:opacity-30">
                Speichern
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

// ─── manage tab (parents) ─────────────────────────────────────────────────────

const EMPTY_FORM: RewardInput = { emoji: '🎁', name: '', description: '', cost: 100, category: 'freizeit', active: true, repeatable: true };

function PendingApprovals({ pending, busy, onApprove, onReject }: {
  pending: Redemption[];
  busy: boolean;
  onApprove: (r: Redemption) => void;
  onReject: (r: Redemption, reason: string | null) => void;
}) {
  const { memberById } = useCalendarData();
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [reason, setReason] = useState('');

  return (
    <div className="bg-white rounded-2xl border border-[#FDE68A] shadow-sm p-5" aria-labelledby="pending-title">
      <h3 id="pending-title" className="font-bold text-slate-800 text-base mb-1">⏳ Wartet auf Genehmigung</h3>
      <p className="text-xs text-slate-400 mb-3">Die Punkte sind schon abgezogen. Beim Ablehnen werden sie zurückgebucht.</p>
      {pending.length === 0 && <p className="text-sm text-slate-400 py-2">Keine offenen Einlösungen.</p>}
      <div className="space-y-2">
        {pending.map(r => {
          const member = memberById(r.memberId);
          return (
            <div key={r.id} className="rounded-xl bg-[#FFFBEB] px-3 py-2.5" data-redemption={r.rewardName}>
              <div className="flex items-center gap-3 flex-wrap">
                <span className="text-xl">{r.rewardEmoji}</span>
                <div className="flex-1 min-w-0">
                  <div className="text-sm font-semibold text-slate-800">
                    <span style={{ color: member?.color }}>{member?.name ?? 'Unbekannt'}</span> möchte „{r.rewardName}“
                  </div>
                  <div className="text-xs text-slate-500">⭐ {r.cost} Punkte · {formatAgo(r.requestedAt)}</div>
                </div>
                {rejecting !== r.id && (
                  <div className="flex gap-1.5">
                    <button type="button" disabled={busy} onClick={() => { setRejecting(r.id); setReason(''); }}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-50">
                      Ablehnen
                    </button>
                    <button type="button" disabled={busy} onClick={() => onApprove(r)}
                      className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#22C55E] text-white hover:bg-[#16A34A] disabled:opacity-50">
                      Genehmigen
                    </button>
                  </div>
                )}
              </div>
              {rejecting === r.id && (
                <div className="mt-2 flex flex-wrap gap-2 items-center">
                  <input aria-label="Grund (optional)" placeholder="Grund (optional), z. B. „Am Wochenende gerne“"
                    maxLength={200} value={reason} onChange={e => setReason(e.target.value)} autoFocus
                    className="flex-1 min-w-[200px] border border-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-[#2563EB]" />
                  <button type="button" onClick={() => setRejecting(null)} className="text-xs text-slate-500 hover:underline">Abbrechen</button>
                  <button type="button" disabled={busy} onClick={() => { onReject(r, reason.trim() || null); setRejecting(null); }}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-[#EF4444] text-white hover:bg-[#DC2626] disabled:opacity-50">
                    Ablehnen und Punkte zurückbuchen
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function ManageTab({ rewards, busy, onSave, onDelete }: {
  rewards: Reward[];
  busy: boolean;
  onSave: (input: RewardInput, id?: string) => Promise<boolean>;
  onDelete: (id: string) => void;
}) {
  const [editing, setEditing] = useState<Reward | null>(null);
  const [form, setForm]       = useState<RewardInput>(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);
  const [emojiPicker, setEmojiPicker] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const openNew = () => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true); };
  const openEdit = (r: Reward) => {
    setForm({ emoji: r.emoji, name: r.name, description: r.description ?? '', cost: r.cost, category: r.category, active: r.active, repeatable: r.repeatable });
    setEditing(r);
    setShowForm(true);
  };

  const handleSave = async () => {
    if (!form.name.trim()) return;
    const ok = await onSave({ ...form, name: form.name.trim(), description: form.description?.trim() || null }, editing?.id);
    if (ok) {
      setShowForm(false);
      setEditing(null);
    }
  };

  const toggle = (r: Reward) => onSave({ ...r, active: !r.active }, r.id);

  const activeCount   = rewards.filter(r => r.active).length;
  const inactiveCount = rewards.length - activeCount;

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-3 flex-1 flex-wrap">
          <div className="bg-white rounded-xl border border-slate-100 px-4 py-2.5 flex items-center gap-2">
            <span className="text-[#22C55E] font-bold text-sm">{activeCount}</span>
            <span className="text-xs text-slate-500">Aktiv</span>
          </div>
          <div className="bg-white rounded-xl border border-slate-100 px-4 py-2.5 flex items-center gap-2">
            <span className="text-slate-400 font-bold text-sm">{inactiveCount}</span>
            <span className="text-xs text-slate-500">Deaktiviert</span>
          </div>
        </div>
        <button
          onClick={openNew}
          className="flex items-center gap-2 bg-[#2563EB] text-white px-4 py-2.5 rounded-xl text-sm font-semibold hover:bg-[#1D4ED8] transition-colors shadow-sm"
        >
          <PlusIcon size={16} /> Belohnung erstellen
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        {rewards.map((r, i) => (
          <div key={r.id} className={`flex items-center gap-4 px-5 py-4 ${i > 0 ? 'border-t border-slate-50' : ''} ${!r.active ? 'opacity-50' : ''} hover:bg-slate-50 transition-colors`}>
            <div className={`w-12 h-12 rounded-2xl flex items-center justify-center text-2xl flex-shrink-0 ${r.active ? 'bg-[#FFFBEB]' : 'bg-slate-100'}`}>
              {r.emoji}
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`font-semibold text-sm ${r.active ? 'text-slate-800' : 'text-slate-400'}`}>{r.name}</span>
                <span className="text-[10px] bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full">{CATEGORY_LABELS[r.category].label}</span>
                <span className="text-[10px] bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full">{r.repeatable ? 'mehrfach' : 'einmalig'}</span>
                {!r.active && <span className="text-[10px] bg-slate-100 text-slate-400 px-2 py-0.5 rounded-full">Deaktiviert</span>}
              </div>
              <div className="text-xs text-slate-400 mt-0.5 truncate">{r.description}</div>
            </div>

            <div className={`flex items-center gap-1 font-bold text-sm flex-shrink-0 ${r.active ? 'text-[#F59E0B]' : 'text-slate-300'}`}>
              ⭐ {r.cost}
            </div>

            <div className="flex items-center gap-1.5 flex-shrink-0">
              <button
                onClick={() => toggle(r)}
                disabled={busy}
                className={`text-xs px-2.5 py-1.5 rounded-lg font-semibold transition-colors disabled:opacity-50 ${r.active ? 'bg-[#F0FDF4] text-[#16A34A] hover:bg-[#DCFCE7]' : 'bg-slate-100 text-slate-400 hover:bg-slate-200'}`}
                title={r.active ? 'Deaktivieren' : 'Aktivieren'}
              >
                {r.active ? '✓ Aktiv' : 'Inaktiv'}
              </button>
              <button
                onClick={() => openEdit(r)}
                className="p-2 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors text-sm"
                title="Bearbeiten"
                aria-label={`${r.name} bearbeiten`}
              >✏️</button>
              {confirmDelete === r.id ? (
                <button onClick={() => { setConfirmDelete(null); onDelete(r.id); }} disabled={busy}
                  className="text-xs px-2.5 py-1.5 rounded-lg font-semibold bg-[#EF4444] text-white hover:bg-[#DC2626] disabled:opacity-50">
                  Wirklich löschen?
                </button>
              ) : (
                <button
                  onClick={() => setConfirmDelete(r.id)}
                  className="p-2 rounded-lg text-slate-300 hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors"
                  title="Löschen"
                  aria-label={`${r.name} löschen`}
                >
                  <XIcon size={14} />
                </button>
              )}
            </div>
          </div>
        ))}

        {rewards.length === 0 && (
          <div className="py-16 text-center text-slate-300">
            <div className="text-4xl mb-3">🎁</div>
            <div className="text-sm font-medium">Noch keine Belohnungen erstellt</div>
          </div>
        )}
      </div>

      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div role="dialog" aria-modal="true" aria-labelledby="reward-form-title" className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div className="p-6">
              <h2 id="reward-form-title" className="font-bold text-slate-800 text-lg mb-5">
                {editing ? 'Belohnung bearbeiten' : 'Neue Belohnung erstellen'}
              </h2>

              <div className="space-y-4">
                <div className="flex gap-3">
                  <div className="relative">
                    <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Emoji</label>
                    <button
                      type="button"
                      onClick={() => setEmojiPicker(!emojiPicker)}
                      className="w-14 h-11 flex items-center justify-center text-2xl border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors"
                    >
                      {form.emoji}
                    </button>
                    {emojiPicker && (
                      <div className="absolute z-10 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl p-2 grid grid-cols-5 gap-1 w-48">
                        {EMOJIS.map(e => (
                          <button key={e} type="button" onClick={() => { setForm(f => ({...f, emoji: e})); setEmojiPicker(false); }}
                            className="text-xl p-1 rounded hover:bg-slate-100 transition-colors">{e}</button>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex-1">
                    <label htmlFor="reward-name" className="text-xs font-semibold text-slate-600 mb-1.5 block">Titel</label>
                    <input
                      id="reward-name"
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                      placeholder="z.B. Kinobesuch"
                      maxLength={60}
                      value={form.name}
                      onChange={e => setForm(f => ({...f, name: e.target.value}))}
                      autoFocus
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="reward-description" className="text-xs font-semibold text-slate-600 mb-1.5 block">Beschreibung</label>
                  <textarea
                    id="reward-description"
                    className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none"
                    rows={2}
                    maxLength={200}
                    placeholder="Was beinhaltet diese Belohnung?"
                    value={form.description ?? ''}
                    onChange={e => setForm(f => ({...f, description: e.target.value}))}
                  />
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label htmlFor="reward-cost" className="text-xs font-semibold text-slate-600 mb-1.5 block">Benötigte Punkte ⭐</label>
                    <input
                      id="reward-cost"
                      type="number"
                      min={1}
                      max={10000}
                      step={10}
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                      value={form.cost}
                      onChange={e => setForm(f => ({...f, cost: Number(e.target.value)}))}
                    />
                  </div>
                  <div>
                    <label htmlFor="reward-category" className="text-xs font-semibold text-slate-600 mb-1.5 block">Kategorie</label>
                    <select
                      id="reward-category"
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB]"
                      value={form.category}
                      onChange={e => setForm(f => ({...f, category: e.target.value as RewardCategory}))}
                    >
                      {(Object.keys(CATEGORY_LABELS) as RewardCategory[]).map(c => <option key={c} value={c}>{CATEGORY_LABELS[c].label}</option>)}
                    </select>
                  </div>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-600 mb-2 block">Schnellauswahl</label>
                  <div className="flex flex-wrap gap-2">
                    {[50, 100, 150, 200, 300, 500].map(v => (
                      <button
                        key={v}
                        type="button"
                        onClick={() => setForm(f => ({...f, cost: v}))}
                        className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${form.cost === v ? 'bg-[#2563EB] text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                      >
                        ⭐ {v}
                      </button>
                    ))}
                  </div>
                </div>

                <label className="flex items-start gap-3 cursor-pointer py-1">
                  <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#22C55E]" checked={form.active}
                    onChange={e => setForm(f => ({...f, active: e.target.checked}))} />
                  <div>
                    <div className="text-sm font-semibold text-slate-800">Belohnung aktiv</div>
                    <div className="text-xs text-slate-400">Kinder können diese Belohnung sehen und einlösen</div>
                  </div>
                </label>

                <label className="flex items-start gap-3 cursor-pointer py-1">
                  <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#2563EB]" checked={form.repeatable}
                    onChange={e => setForm(f => ({...f, repeatable: e.target.checked}))} />
                  <div>
                    <div className="text-sm font-semibold text-slate-800">Mehrfach einlösbar</div>
                    <div className="text-xs text-slate-400">Sonst kann jedes Kind sie nur einmal bekommen</div>
                  </div>
                </label>
              </div>

              <div className="flex gap-3 mt-6">
                <button type="button" onClick={() => { setShowForm(false); setEditing(null); }} className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">Abbrechen</button>
                <button type="button" onClick={handleSave} disabled={busy || !form.name.trim()} className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
                  {editing ? 'Änderungen speichern' : 'Belohnung erstellen'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── main ─────────────────────────────────────────────────────────────────────

export default function Rewards({ onNavigate }: Props) {
  const me = useMe();
  const { can } = useAuth();
  const { tasks, balances } = useTaskData();
  const { rewards, redemptions, error: loadError, saveReward, removeReward, redeem, approve, reject, withdraw } = useRewardData();
  const children = usePointHolders();
  const mayViewPoints = can('punkte', 'ansehen', 'eigen');
  const mayManage = can('punkte', 'verwalten', 'familie');
  const mayDecide = can('punkte', 'freigeben', 'familie');
  const mayRedeemForOthers = can('punkte', 'vorschlagen', 'familie');
  const mayRedeemOwn = can('punkte', 'vorschlagen', 'eigen');
  const [tab, setTab]         = useState<Tab>('overview');
  const [history, setHistory] = useState<PointEntry[]>([]);
  const [busy, setBusy]       = useState(false);
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null);

  // Historie neu laden, sobald sich Punktestände ändern (z. B. nach einer Bestätigung oder Einlösung)
  useEffect(() => {
    if (!mayViewPoints) return;
    let cancelled = false;
    listHistory().then(h => { if (!cancelled) setHistory(h); }).catch(() => { if (!cancelled) setHistory([]); });
    return () => { cancelled = true; };
  }, [mayViewPoints, balances]);

  const run = async (action: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    setMessage(null);
    try {
      await action();
      if (success) setMessage({ text: success, ok: true });
      return true;
    } catch (err) {
      setMessage({ text: errorText(err), ok: false });
      return false;
    } finally {
      setBusy(false);
    }
  };

  // Für wen im Shop eingelöst werden kann: Administratoren für alle Kinder, sonst nur für sich selbst
  const shopHolders = mayRedeemForOthers ? children : children.filter(c => c.id === me.id);
  const pending = redemptions.filter(r => r.status === 'pending');
  const pendingCount = mayDecide ? pending.length : 0;
  const tabs = TABS.filter(t => (t.id !== 'manage' || mayManage) && (t.id !== 'shop' || shopHolders.length > 0));

  if (!mayViewPoints || children.length === 0) {
    return (
      <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-10 text-center text-slate-400">
          <div className="text-4xl mb-3">⭐</div>
          {mayViewPoints ? 'Noch keine Punktestände vorhanden.' : 'Punkte und Belohnungen sind für dich nicht freigegeben.'}
        </div>
      </div>
    );
  }

  return (
    <div className="p-4 lg:p-6 max-w-[1400px] mx-auto">
      {/* Tab bar */}
      <div className="flex flex-wrap gap-1.5 mb-6 bg-white border border-slate-100 rounded-2xl p-1.5 shadow-sm">
        {tabs.map(t => (
          <button
            key={t.id}
            onClick={() => { setTab(t.id); setMessage(null); }}
            aria-pressed={tab === t.id}
            className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all flex-1 justify-center ${
              tab === t.id
                ? 'bg-[#2563EB] text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-50'
            }`}
          >
            <span>{t.emoji}</span>
            <span className="hidden sm:inline">{t.label}</span>
            {t.id === 'manage' && pendingCount > 0 && (
              <span className={`text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0 ${tab === t.id ? 'bg-white/30 text-white' : 'bg-[#EF4444] text-white'}`}>
                {pendingCount}<span className="sr-only"> warten auf Genehmigung</span>
              </span>
            )}
          </button>
        ))}
      </div>

      {loadError && (
        <div role="alert" className="mb-5 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{loadError}</div>
      )}
      {message && (
        <div role={message.ok ? 'status' : 'alert'}
          className={`mb-5 text-sm rounded-xl p-3 border ${message.ok ? 'bg-[#F0FDF4] border-[#BBF7D0] text-[#15803D]' : 'bg-[#FEF2F2] border-[#FECACA] text-[#DC2626]'}`}>
          {message.text}
        </div>
      )}

      {/* Pending approvals banner */}
      {pendingCount > 0 && tab !== 'manage' && mayManage && (
        <div className="mb-5 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl p-3 flex items-center gap-3">
          <span className="text-xl">⏳</span>
          <span className="text-sm font-semibold text-[#92400E] flex-1">
            {pendingCount} Einlösung{pendingCount > 1 ? 'en warten' : ' wartet'} auf Genehmigung
          </span>
          <button onClick={() => setTab('manage')} className="text-xs font-semibold text-[#D97706] hover:underline flex-shrink-0">
            Verwalten →
          </button>
        </div>
      )}

      {tab === 'overview' && <OverviewTab rewards={rewards} kids={children} history={history}
        confirmedTasks={tasks.filter(t => t.status === 'confirmed').length}
        redeemedCount={redemptions.filter(r => r.status === 'approved').length} />}
      {tab === 'shop' && shopHolders.length > 0 && (mayRedeemOwn || mayRedeemForOthers) && (
        <ShopTab rewards={rewards} redemptions={redemptions} holders={shopHolders} forOthers={mayRedeemForOthers}
          busy={busy}
          onRedeem={(reward, member) => run(() => redeem(reward.id, member.id === me.id ? undefined : member.id),
            member.id === me.id
              ? `„${reward.name}“ eingelöst: ${reward.cost} Punkte abgezogen. Deine Eltern müssen noch zustimmen.`
              : `„${reward.name}“ für ${member.name} eingelöst und genehmigt: ${reward.cost} Punkte abgezogen.`)}
          onWithdraw={r => run(() => withdraw(r.id), `Einlösung zurückgezogen, ${r.cost} Punkte sind wieder da.`)} />
      )}
      {tab === 'achievements' && <AchievementsTab kids={children} mayManage={mayManage} refreshKey={balances} />}
      {tab === 'manage' && mayManage && (
        <div className="space-y-5">
          {mayDecide && (
            <PendingApprovals pending={pending} busy={busy}
              onApprove={r => run(() => approve(r.id), `„${r.rewardName}“ genehmigt.`)}
              onReject={(r, reason) => run(() => reject(r.id, reason), `„${r.rewardName}“ abgelehnt, ${r.cost} Punkte zurückgebucht.`)} />
          )}
          <ManageTab rewards={rewards} busy={busy}
            onSave={(input, id) => run(() => saveReward(input, id))}
            onDelete={id => run(() => removeReward(id), 'Belohnung gelöscht.')} />
        </div>
      )}
    </div>
  );
}
