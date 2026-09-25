import { useEffect, useState } from 'react';
import { ACHIEVEMENTS } from './data';
import { StarIcon, TrophyIcon, PlusIcon, CheckIcon, XIcon } from './Icons';
import { useAuth } from '../auth/AuthContext';
import { listHistory, type PointEntry } from '../points/api';
import { formatAgo, usePointHolders, type PointHolder } from '../points/usePointHolders';
import { useTaskData } from '../tasks/TaskDataContext';

interface Props { onNavigate: (p: any) => void; }

// ─── types ────────────────────────────────────────────────────────────────────

interface Reward {
  id: number;
  emoji: string;
  name: string;
  description: string;
  cost: number;
  active: boolean;
  category: string;
}

// Belohnungen und Einlösungen sind noch Beispieldaten (Belohnungsshop folgt später); zugeordnet über den Namen.
interface Redemption {
  id: number;
  rewardId: number;
  childName: string;
  redeemedAt: string;
  approved: boolean;
}

// ─── initial data ─────────────────────────────────────────────────────────────

const INITIAL_REWARDS: Reward[] = [
  { id: 1, emoji: '🍽️', name: 'Lieblingsessen',        description: 'Du bestimmst, was heute Abend gekocht wird.',          cost: 50,  active: true,  category: 'Essen'      },
  { id: 2, emoji: '📱', name: '1 Std. Extra-Bildschirmzeit', description: 'Eine Stunde zusätzliche Bildschirmzeit an einem Tag.', cost: 100, active: true,  category: 'Freizeit'   },
  { id: 3, emoji: '🎬', name: 'Film-Abend Wahl',        description: 'Du wählst den Familienfilm am Freitagabend.',           cost: 150, active: true,  category: 'Freizeit'   },
  { id: 4, emoji: '🌙', name: '30 Min. länger wach',    description: 'Einmal darf du 30 Minuten später ins Bett.',            cost: 150, active: true,  category: 'Freizeit'   },
  { id: 5, emoji: '🎮', name: '2 Std. Gaming',          description: 'Zwei Stunden ununterbrochenes Spielen am Wochenende.',  cost: 200, active: true,  category: 'Freizeit'   },
  { id: 6, emoji: '🍕', name: 'Pizza bestellen',        description: 'Wir bestellen Pizza – du wählst den Belag!',            cost: 200, active: true,  category: 'Essen'      },
  { id: 7, emoji: '🎪', name: 'Freizeitpark-Besuch',    description: 'Ein Ausflug in einen Freizeitpark deiner Wahl.',        cost: 300, active: true,  category: 'Ausflug'    },
  { id: 8, emoji: '🏊', name: 'Schwimmbad-Tag',         description: 'Ein ganzer Tag im Schwimmbad mit der Familie.',         cost: 250, active: true,  category: 'Ausflug'    },
  { id: 9, emoji: '🎁', name: 'Neues Buch oder Spiel',  description: 'Ein neues Buch oder kleines Spiel deiner Wahl.',        cost: 400, active: true,  category: 'Geschenk'   },
  { id: 10,emoji: '🛍️', name: 'Shopping-Gutschein 10€', description: '10€ für dein Lieblings-Onlineshop.',                   cost: 500, active: false, category: 'Geschenk'   },
];

const INITIAL_REDEMPTIONS: Redemption[] = [
  { id: 1, rewardId: 1, childName: 'Emma',  redeemedAt: '2026-09-15', approved: true  },
  { id: 2, rewardId: 3, childName: 'Lucas', redeemedAt: '2026-09-10', approved: true  },
  { id: 3, rewardId: 2, childName: 'Lily',  redeemedAt: '2026-09-18', approved: true  },
  { id: 4, rewardId: 6, childName: 'Emma',  redeemedAt: '2026-09-20', approved: false },
];

const CHILD_ACHIEVEMENTS: Record<string, number[]> = {
  Emma: [1, 2, 5],
  Lucas: [1, 5],
  Lily: [2],
};

const CATEGORY_ICONS: Record<string, string> = {
  Essen: '🍴', Freizeit: '🎉', Ausflug: '🗺️', Geschenk: '🎁',
};

const EMOJIS = ['🍽️','📱','🎬','🌙','🎮','🍕','🎪','🏊','🎁','🛍️','⚽','🎨','📚','🍦','🎠','🏖️','🎡','🎯','🏆','✈️'];

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

// ─── tabs ─────────────────────────────────────────────────────────────────────

type Tab = 'overview' | 'shop' | 'achievements' | 'manage';

const TABS: { id: Tab; label: string; emoji: string }[] = [
  { id: 'overview',     label: 'Übersicht',           emoji: '📊' },
  { id: 'shop',         label: 'Belohnungsshop',       emoji: '🛍️' },
  { id: 'achievements', label: 'Erfolge',              emoji: '🏆' },
  { id: 'manage',       label: 'Verwalten',            emoji: '⚙️' },
];

// ─── overview tab ─────────────────────────────────────────────────────────────

function OverviewTab({ rewards, kids: children, history, confirmedTasks }: {
  rewards: Reward[];
  kids: PointHolder[];
  history: PointEntry[];
  confirmedTasks: number;
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
          { label: 'Eingelöst',    value: INITIAL_REDEMPTIONS.filter(r => r.approved).length, icon: '🎁', color: '#8B5CF6', bg: '#F5F3FF' },
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
                    <div className="h-full rounded-full relative overflow-hidden transition-all duration-1000" style={{ width: `${(c.points / maxPoints) * 100}%`, backgroundColor: c.color }}>
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
                    <ProgressRing value={c.points - prevLevel} max={nextLevel - prevLevel} color={c.color} />
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
                    <div className="h-full rounded-full relative overflow-hidden" style={{ width: `${((c.points - prevLevel)/(nextLevel - prevLevel))*100}%`, backgroundColor: c.color }}>
                      <div className="absolute inset-0 progress-shimmer" />
                    </div>
                  </div>
                </div>
              </div>
              {/* recent (Punkte-Historie) */}
              <div className="px-4 pb-4 pt-2">
                <div className="text-xs font-semibold text-slate-500 mb-2">Letzte Aktivitäten</div>
                {recent.length === 0 && <div className="text-xs text-slate-400 py-1">Noch keine Punkte</div>}
                {recent.map(a => (
                  <div key={a.id} className="flex items-center justify-between gap-2 text-xs py-1">
                    <span className="text-slate-600 truncate">{a.reason}</span>
                    <div className="flex items-center gap-2 flex-shrink-0">
                      <span className="text-[#22C55E] font-bold">{a.amount > 0 ? '+' : ''}{a.amount}</span>
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

function ShopTab({
  rewards,
  redemptions,
  onRedeem,
  kids: children,
}: {
  rewards: Reward[];
  redemptions: Redemption[];
  onRedeem: (rewardId: number, childName: string) => void;
  kids: PointHolder[];
}) {
  const [viewer, setViewer] = useState(children[0].id);
  const child      = children.find(m => m.id === viewer) ?? children[0];
  const categories = Array.from(new Set(rewards.filter(r => r.active).map(r => r.category)));

  const myRedemptions = redemptions.filter(r => r.childName === child.name);
  const alreadyClaimed = new Set(myRedemptions.filter(r => r.approved).map(r => r.rewardId));
  const pendingSet     = new Set(myRedemptions.filter(r => !r.approved).map(r => r.rewardId));

  return (
    <div className="space-y-6">
      <div className="bg-[#EFF6FF] border border-[#BFDBFE] text-[#1E40AF] text-xs rounded-xl px-3 py-2">
        Vorschau: Die Punktestände sind echt, das Einlösen wird aber noch nicht gespeichert und zieht keine Punkte ab.
      </div>

      {/* Child switcher (nur wenn mehrere Punktestände sichtbar sind) */}
      {children.length > 1 && <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-4">
        <div className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-3">Ansicht für:</div>
        <div className="flex flex-wrap gap-2">
          {children.map(c => (
            <button
              key={c.id}
              onClick={() => setViewer(c.id)}
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

      {/* Balance banner */}
      <div
        className="rounded-2xl p-5 text-white relative overflow-hidden"
        style={{ background: `linear-gradient(135deg, ${child.color}, ${child.color}BB)` }}
      >
        <div className="absolute top-0 right-0 w-32 h-32 rounded-full bg-white/10 -translate-y-8 translate-x-8" />
        <div className="relative flex items-center gap-4">
          <div className="w-14 h-14 rounded-2xl bg-white/20 flex items-center justify-center text-3xl">⭐</div>
          <div>
            <div className="text-white/70 text-sm">Dein Punktestand</div>
            <div className="text-4xl font-bold">{child.points}</div>
            <div className="text-white/70 text-sm mt-0.5">Punkte verfügbar</div>
          </div>
          <div className="ml-auto text-right hidden sm:block">
            <div className="text-white/70 text-xs">Bereits eingelöst</div>
            <div className="text-2xl font-bold">{alreadyClaimed.size}×</div>
            <div className="text-white/70 text-xs">Belohnungen</div>
          </div>
        </div>
      </div>

      {/* Rewards grid by category */}
      {categories.map(cat => {
        const catRewards = rewards.filter(r => r.active && r.category === cat);
        return (
          <div key={cat}>
            <h3 className="font-bold text-slate-700 text-sm mb-3 flex items-center gap-2">
              <span>{CATEGORY_ICONS[cat] || '🎁'}</span>{cat}
            </h3>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-3">
              {catRewards.map(r => {
                const canAfford = child.points >= r.cost;
                const claimed   = alreadyClaimed.has(r.id);
                const pending   = pendingSet.has(r.id);
                const pctNeeded = Math.min(child.points / r.cost, 1);

                return (
                  <div
                    key={r.id}
                    className={`relative flex flex-col rounded-2xl border transition-all overflow-hidden ${
                      canAfford
                        ? 'border-transparent bg-white shadow-sm hover:shadow-md hover:shadow-slate-100 hover:-translate-y-0.5'
                        : 'border-slate-100 bg-slate-50/50'
                    }`}
                  >
                    {/* Top color band */}
                    <div
                      className={`h-20 flex items-center justify-center text-4xl transition-opacity ${!canAfford ? 'opacity-40 grayscale' : ''}`}
                      style={{ background: canAfford ? `linear-gradient(135deg, ${child.color}20, ${child.color}08)` : '#F8FAFC' }}
                    >
                      {r.emoji}
                    </div>

                    <div className="p-3 flex flex-col flex-1">
                      <div className={`text-sm font-bold leading-tight mb-1 ${!canAfford ? 'text-slate-400' : 'text-slate-800'}`}>{r.name}</div>
                      <div className="text-[10px] text-slate-400 leading-snug mb-2 flex-1">{r.description}</div>

                      {/* Cost + progress */}
                      <div className="flex items-center justify-between mb-2">
                        <div className={`flex items-center gap-1 text-sm font-bold ${canAfford ? 'text-[#F59E0B]' : 'text-slate-400'}`}>
                          <span>⭐</span>{r.cost}
                        </div>
                        {!canAfford && (
                          <span className="text-[10px] text-slate-400">noch {r.cost - child.points}</span>
                        )}
                      </div>

                      {/* Progress bar when can't afford */}
                      {!canAfford && (
                        <div className="h-1.5 bg-slate-200 rounded-full overflow-hidden mb-2">
                          <div className="h-full rounded-full" style={{ width: `${pctNeeded * 100}%`, backgroundColor: child.color }} />
                        </div>
                      )}

                      {/* Action button */}
                      {claimed ? (
                        <div className="w-full py-2 rounded-xl bg-[#F0FDF4] text-[#16A34A] text-xs font-bold text-center flex items-center justify-center gap-1">
                          <CheckIcon size={12} strokeWidth={3} /> Eingelöst
                        </div>
                      ) : pending ? (
                        <div className="w-full py-2 rounded-xl bg-[#FFFBEB] text-[#D97706] text-xs font-bold text-center">
                          ⏳ Ausstehend
                        </div>
                      ) : (
                        <button
                          onClick={() => canAfford && onRedeem(r.id, child.name)}
                          disabled={!canAfford}
                          className={`w-full py-2 rounded-xl text-xs font-bold transition-all ${
                            canAfford
                              ? 'text-white hover:opacity-90 active:scale-95'
                              : 'bg-slate-100 text-slate-300 cursor-not-allowed'
                          }`}
                          style={canAfford ? { backgroundColor: child.color } : {}}
                        >
                          {canAfford ? 'Einlösen' : 'Nicht genug Punkte'}
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
          <h3 className="font-bold text-slate-800 text-base mb-4">📋 Mein Einlöseverlauf</h3>
          <div className="space-y-2">
            {myRedemptions.map(red => {
              const rew = rewards.find(r => r.id === red.rewardId);
              if (!rew) return null;
              return (
                <div key={red.id} className="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-slate-50">
                  <span className="text-xl flex-shrink-0">{rew.emoji}</span>
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-semibold text-slate-800">{rew.name}</div>
                    <div className="text-xs text-slate-400">{red.redeemedAt} · ⭐ {rew.cost} Punkte</div>
                  </div>
                  <span className={`text-xs font-bold px-2.5 py-1 rounded-full flex-shrink-0 ${red.approved ? 'bg-[#F0FDF4] text-[#16A34A]' : 'bg-[#FFFBEB] text-[#D97706]'}`}>
                    {red.approved ? '✅ Genehmigt' : '⏳ Ausstehend'}
                  </span>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

// ─── achievements tab ─────────────────────────────────────────────────────────

function AchievementsTab({ kids: children }: { kids: PointHolder[] }) {
  return (
    <div className="space-y-6">
      {children.map(c => {
        const earned = CHILD_ACHIEVEMENTS[c.name] || [];
        return (
          <div key={c.id} className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
            <div className="flex items-center gap-3 mb-4">
              <div className="w-10 h-10 rounded-full flex items-center justify-center text-white font-bold" style={{ backgroundColor: c.color }}>{c.initials[0]}</div>
              <div>
                <div className="font-bold text-slate-800">{c.name}</div>
                <div className="text-xs text-slate-400">{earned.length}/{ACHIEVEMENTS.length} Erfolge freigeschaltet</div>
              </div>
              <div className="ml-auto font-bold text-lg" style={{ color: c.color }}>{c.points} Pkt.</div>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
              {ACHIEVEMENTS.map(a => {
                const isEarned = earned.includes(a.id);
                return (
                  <div
                    key={a.id}
                    className={`flex flex-col items-center p-3 rounded-xl text-center border transition-all ${isEarned ? 'bg-[#FFFBEB] border-[#FDE68A] shadow-sm' : 'bg-slate-50 border-slate-100 opacity-40 grayscale'}`}
                    title={a.description}
                  >
                    <span className="text-3xl mb-1.5">{a.icon}</span>
                    <span className="text-[11px] font-semibold text-slate-700 leading-tight">{a.name}</span>
                    <span className="text-[10px] text-slate-400 mt-0.5 leading-tight">{a.description}</span>
                    {isEarned && <span className="text-[10px] text-[#F59E0B] font-bold mt-1.5">+{a.points} Pkt.</span>}
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}
    </div>
  );
}

// ─── manage tab (parents) ─────────────────────────────────────────────────────

interface RewardFormState {
  emoji: string;
  name: string;
  description: string;
  cost: number;
  category: string;
  active: boolean;
}

const EMPTY_FORM: RewardFormState = { emoji: '🎁', name: '', description: '', cost: 100, category: 'Freizeit', active: true };
const CATEGORIES = ['Essen','Freizeit','Ausflug','Geschenk'];

function ManageTab({ rewards, onSave, onDelete, onToggle }: {
  rewards: Reward[];
  onSave: (r: Omit<Reward, 'id'> & { id?: number }) => void;
  onDelete: (id: number) => void;
  onToggle: (id: number) => void;
}) {
  const [editing, setEditing] = useState<Reward | null>(null);
  const [form, setForm]       = useState<RewardFormState>(EMPTY_FORM);
  const [showForm, setShowForm] = useState(false);
  const [emojiPicker, setEmojiPicker] = useState(false);

  const openNew = () => { setForm(EMPTY_FORM); setEditing(null); setShowForm(true); };
  const openEdit = (r: Reward) => { setForm({ emoji: r.emoji, name: r.name, description: r.description, cost: r.cost, category: r.category, active: r.active }); setEditing(r); setShowForm(true); };

  const handleSave = () => {
    if (!form.name.trim()) return;
    onSave({ ...form, id: editing?.id });
    setShowForm(false);
    setEditing(null);
  };

  const activeCount   = rewards.filter(r => r.active).length;
  const inactiveCount = rewards.length - activeCount;

  return (
    <div className="space-y-5">
      {/* Header */}
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

      {/* Rewards list */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
        {rewards.map((r, i) => (
          <div key={r.id} className={`flex items-center gap-4 px-5 py-4 ${i > 0 ? 'border-t border-slate-50' : ''} ${!r.active ? 'opacity-50' : ''} hover:bg-slate-50 transition-colors`}>
            {/* Emoji */}
            <div className={`w-12 h-12 rounded-2xl flex items-center justify-center text-2xl flex-shrink-0 ${r.active ? 'bg-[#FFFBEB]' : 'bg-slate-100'}`}>
              {r.emoji}
            </div>

            {/* Info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`font-semibold text-sm ${r.active ? 'text-slate-800' : 'text-slate-400'}`}>{r.name}</span>
                <span className="text-[10px] bg-slate-100 text-slate-500 px-2 py-0.5 rounded-full">{r.category}</span>
                {!r.active && <span className="text-[10px] bg-slate-100 text-slate-400 px-2 py-0.5 rounded-full">Deaktiviert</span>}
              </div>
              <div className="text-xs text-slate-400 mt-0.5 truncate">{r.description}</div>
            </div>

            {/* Cost */}
            <div className={`flex items-center gap-1 font-bold text-sm flex-shrink-0 ${r.active ? 'text-[#F59E0B]' : 'text-slate-300'}`}>
              ⭐ {r.cost}
            </div>

            {/* Actions */}
            <div className="flex items-center gap-1.5 flex-shrink-0">
              {/* Toggle active */}
              <button
                onClick={() => onToggle(r.id)}
                className={`text-xs px-2.5 py-1.5 rounded-lg font-semibold transition-colors ${r.active ? 'bg-[#F0FDF4] text-[#16A34A] hover:bg-[#DCFCE7]' : 'bg-slate-100 text-slate-400 hover:bg-slate-200'}`}
                title={r.active ? 'Deaktivieren' : 'Aktivieren'}
              >
                {r.active ? '✓ Aktiv' : 'Inaktiv'}
              </button>
              <button
                onClick={() => openEdit(r)}
                className="p-2 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition-colors text-sm"
                title="Bearbeiten"
              >✏️</button>
              <button
                onClick={() => onDelete(r.id)}
                className="p-2 rounded-lg text-slate-300 hover:text-[#EF4444] hover:bg-[#FEF2F2] transition-colors"
                title="Löschen"
              >
                <XIcon size={14} />
              </button>
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

      {/* Create / Edit form */}
      {showForm && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
            <div className="p-6">
              <h2 className="font-bold text-slate-800 text-lg mb-5">
                {editing ? 'Belohnung bearbeiten' : 'Neue Belohnung erstellen'}
              </h2>

              <div className="space-y-4">
                {/* Emoji + Name row */}
                <div className="flex gap-3">
                  <div>
                    <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Emoji</label>
                    <button
                      onClick={() => setEmojiPicker(!emojiPicker)}
                      className="w-14 h-11 flex items-center justify-center text-2xl border border-slate-200 rounded-xl hover:bg-slate-50 transition-colors relative"
                    >
                      {form.emoji}
                    </button>
                    {emojiPicker && (
                      <div className="absolute z-10 mt-1 bg-white border border-slate-200 rounded-xl shadow-xl p-2 grid grid-cols-5 gap-1 w-48">
                        {EMOJIS.map(e => (
                          <button key={e} onClick={() => { setForm(f => ({...f, emoji: e})); setEmojiPicker(false); }}
                            className="text-xl p-1 rounded hover:bg-slate-100 transition-colors">{e}</button>
                        ))}
                      </div>
                    )}
                  </div>
                  <div className="flex-1">
                    <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Titel</label>
                    <input
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                      placeholder="z.B. Kinobesuch"
                      value={form.name}
                      onChange={e => setForm(f => ({...f, name: e.target.value}))}
                      autoFocus
                    />
                  </div>
                </div>

                {/* Description */}
                <div>
                  <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Beschreibung</label>
                  <textarea
                    className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20 resize-none"
                    rows={2}
                    placeholder="Was beinhaltet diese Belohnung?"
                    value={form.description}
                    onChange={e => setForm(f => ({...f, description: e.target.value}))}
                  />
                </div>

                {/* Points + Category */}
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Benötigte Punkte ⭐</label>
                    <input
                      type="number"
                      min={10}
                      step={10}
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                      value={form.cost}
                      onChange={e => setForm(f => ({...f, cost: Number(e.target.value)}))}
                    />
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Kategorie</label>
                    <select
                      className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB]"
                      value={form.category}
                      onChange={e => setForm(f => ({...f, category: e.target.value}))}
                    >
                      {CATEGORIES.map(c => <option key={c}>{c}</option>)}
                    </select>
                  </div>
                </div>

                {/* Quick cost presets */}
                <div>
                  <label className="text-xs font-semibold text-slate-600 mb-2 block">Schnellauswahl</label>
                  <div className="flex flex-wrap gap-2">
                    {[50, 100, 150, 200, 300, 500].map(v => (
                      <button
                        key={v}
                        onClick={() => setForm(f => ({...f, cost: v}))}
                        className={`px-3 py-1.5 rounded-full text-xs font-semibold transition-colors ${form.cost === v ? 'bg-[#2563EB] text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
                      >
                        ⭐ {v}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Active toggle */}
                <label className="flex items-center gap-3 cursor-pointer py-1">
                  <div
                    className={`w-11 h-6 rounded-full transition-colors flex-shrink-0 relative ${form.active ? 'bg-[#22C55E]' : 'bg-slate-200'}`}
                    onClick={() => setForm(f => ({...f, active: !f.active}))}
                  >
                    <div className={`absolute top-1 w-4 h-4 rounded-full bg-white shadow transition-all ${form.active ? 'left-6' : 'left-1'}`} />
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-slate-800">Belohnung aktiv</div>
                    <div className="text-xs text-slate-400">Kinder können diese Belohnung sehen und einlösen</div>
                  </div>
                </label>
              </div>

              <div className="flex gap-3 mt-6">
                <button onClick={() => { setShowForm(false); setEditing(null); }} className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">Abbrechen</button>
                <button onClick={handleSave} className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8]">
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
  const { can } = useAuth();
  const { tasks, balances } = useTaskData();
  const children = usePointHolders();
  const mayViewPoints = can('punkte', 'ansehen', 'eigen');
  const mayManage = can('punkte', 'verwalten', 'familie');
  const [tab, setTab]               = useState<Tab>('overview');
  const [rewards, setRewards]       = useState<Reward[]>(INITIAL_REWARDS);
  const [redemptions, setRedemptions] = useState<Redemption[]>(INITIAL_REDEMPTIONS);
  const [history, setHistory]       = useState<PointEntry[]>([]);

  // Historie neu laden, sobald sich Punktestände ändern (z. B. nach einer Bestätigung)
  useEffect(() => {
    if (!mayViewPoints) return;
    let cancelled = false;
    listHistory().then(h => { if (!cancelled) setHistory(h); }).catch(() => { if (!cancelled) setHistory([]); });
    return () => { cancelled = true; };
  }, [mayViewPoints, balances]);

  const handleRedeem = (rewardId: number, childName: string) => {
    const rew = rewards.find(r => r.id === rewardId);
    if (!rew) return;
    // Vorschau: Einlösen wird noch nicht gespeichert (Belohnungsshop folgt später)
    setRedemptions(rs => [...rs, {
      id: Date.now(),
      rewardId,
      childName,
      redeemedAt: new Date().toISOString().slice(0, 10),
      approved: false,
    }]);
  };

  const handleSave = (r: Omit<Reward,'id'> & { id?: number }) => {
    if (r.id) {
      setRewards(rs => rs.map(x => x.id === r.id ? { ...x, ...r, id: r.id! } : x));
    } else {
      setRewards(rs => [...rs, { ...r, id: Date.now() }]);
    }
  };

  const handleDelete = (id: number) => setRewards(rs => rs.filter(r => r.id !== id));
  const handleToggle = (id: number) => setRewards(rs => rs.map(r => r.id === id ? { ...r, active: !r.active } : r));

  // pending redemptions needing parent approval (Beispieldaten, nur für Administratoren)
  const pendingCount = mayManage ? redemptions.filter(r => !r.approved).length : 0;

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
        {TABS.filter(t => t.id !== 'manage' || mayManage).map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
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
                {pendingCount}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Pending approvals banner */}
      {pendingCount > 0 && tab !== 'manage' && (
        <div className="mb-5 bg-[#FFFBEB] border border-[#FDE68A] rounded-xl p-3 flex items-center gap-3">
          <span className="text-xl">⏳</span>
          <span className="text-sm font-semibold text-[#92400E] flex-1">
            {pendingCount} Einlösung{pendingCount > 1 ? 'en' : ''} wartet auf Genehmigung
          </span>
          <button onClick={() => setTab('manage')} className="text-xs font-semibold text-[#D97706] hover:underline flex-shrink-0">
            Verwalten →
          </button>
        </div>
      )}

      {/* Tab content */}
      {tab === 'overview'     && <OverviewTab rewards={rewards} kids={children} history={history}
        confirmedTasks={tasks.filter(t => t.status === 'confirmed').length} />}
      {tab === 'shop'         && <ShopTab rewards={rewards} redemptions={redemptions} onRedeem={handleRedeem} kids={children} />}
      {tab === 'achievements' && <AchievementsTab kids={children} />}
      {tab === 'manage' && mayManage && <ManageTab rewards={rewards} onSave={handleSave} onDelete={handleDelete} onToggle={handleToggle} />}
    </div>
  );
}
