import { useState } from 'react';
import { FAMILY_MEMBERS, FamilyMember } from './data';
import { PlusIcon, CheckIcon, SettingsIcon } from './Icons';
import { Module, Action, Scope, STANDARD_ROLES, roleHasPermission } from '../roles';

// Simple shield icon not in Icons.tsx
function ShieldLock({ size = 16, className = '' }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
  );
}

interface Props { onNavigate: (p: any) => void; }

// Anzeigeliste der Berechtigungen aus unserem Rollenmodell (src/roles),
// nicht mehr eine freie Liste pro Rolle wie im urspruenglichen Figma-Export.
const PERMISSION_DISPLAY: { module: Module; action: Action; scope: Scope; label: string; icon: string }[] = [
  { module: 'kalender', action: 'ansehen', scope: 'eigen', label: 'Eigene Termine ansehen', icon: '📅' },
  { module: 'kalender', action: 'bearbeiten', scope: 'familie', label: 'Familientermine bearbeiten', icon: '✏️' },
  { module: 'aufgaben', action: 'bearbeiten', scope: 'eigen', label: 'Eigene Aufgaben verwalten', icon: '✅' },
  { module: 'einkauf', action: 'bearbeiten', scope: 'familie', label: 'Einkaufsliste bearbeiten', icon: '🛒' },
  { module: 'essen', action: 'vorschlagen', scope: 'eigen', label: 'Essenswünsche einreichen', icon: '🍽️' },
  { module: 'essen', action: 'freigeben', scope: 'familie', label: 'Essensvorschläge freigeben', icon: '👨‍🍳' },
  { module: 'punkte', action: 'ansehen', scope: 'eigen', label: 'Eigene Punkte einsehen', icon: '⭐' },
  { module: 'punkte', action: 'freigeben', scope: 'familie', label: 'Punkte vergeben/korrigieren', icon: '🎁' },
  { module: 'familie', action: 'verwalten', scope: 'system', label: 'Familienmitglieder verwalten', icon: '👥' },
  { module: 'system', action: 'verwalten', scope: 'system', label: 'Rollen & Berechtigungen verwalten', icon: '🔒' },
];

const FAMILY_COLORS = [
  '#2563EB', '#14B8A6', '#8B5CF6', '#F97316', '#EC4899',
  '#22C55E', '#F59E0B', '#EF4444', '#06B6D4', '#A855F7',
];

function MemberCard({ member, onEdit }: { member: FamilyMember; onEdit: (m: FamilyMember) => void }) {
  const role = STANDARD_ROLES[member.roleId];
  const isParent = member.roleId === 'administrator';
  const grantedCount = PERMISSION_DISPLAY.filter(p => roleHasPermission(role, p.module, p.action, p.scope)).length;

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      {/* Header with color */}
      <div
        className="h-20 relative"
        style={{ background: `linear-gradient(135deg, ${member.color}, ${member.color}AA)` }}
      >
        <div className="absolute inset-0 opacity-20 bg-gradient-to-br from-white to-transparent" />
        {isParent && (
          <div className="absolute top-3 right-3 bg-white/30 backdrop-blur-sm text-white text-[10px] font-bold px-2 py-1 rounded-full flex items-center gap-1">
            <ShieldLock size={10} />
            Admin
          </div>
        )}
      </div>

      <div className="px-5 pb-5">
        {/* Avatar */}
        <div className="flex items-end justify-between -mt-7 mb-4">
          <div
            className="w-14 h-14 rounded-2xl flex items-center justify-center text-white text-xl font-bold border-4 border-white shadow-md"
            style={{ backgroundColor: member.color }}
          >
            {member.initials[0]}
          </div>
          <button
            onClick={() => onEdit(member)}
            className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-800 bg-slate-50 hover:bg-slate-100 px-3 py-1.5 rounded-xl transition-all font-medium"
          >
            <SettingsIcon size={12} />
            Edit
          </button>
        </div>

        <div className="mb-4">
          <h3 className="font-bold text-slate-800 text-lg">{member.name} Johnson</h3>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <span
              className="text-xs font-semibold px-2.5 py-1 rounded-full text-white"
              style={{ backgroundColor: member.color }}
            >
              {role.name}
            </span>
            <span className="text-xs text-slate-400">Age {member.age}</span>
            {!isParent && (
              <span className="text-xs text-[#F59E0B] font-semibold">⭐ {member.points} pts</span>
            )}
          </div>
        </div>

        {/* Permissions */}
        <div>
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <ShieldLock size={12} />
            Permissions ({grantedCount}/{PERMISSION_DISPLAY.length})
          </div>
          <div className="grid grid-cols-2 gap-1.5">
            {PERMISSION_DISPLAY.slice(0, 6).map(p => {
              const has = roleHasPermission(role, p.module, p.action, p.scope);
              return (
                <div
                  key={`${p.module}-${p.action}-${p.scope}`}
                  className={`flex items-center gap-1.5 text-[11px] px-2 py-1 rounded-lg ${has ? 'bg-[#F0FDF4] text-[#16A34A]' : 'bg-slate-50 text-slate-300'}`}
                >
                  <span className={has ? '' : 'grayscale opacity-50'}>{p.icon}</span>
                  <span className={`font-medium truncate ${has ? '' : 'line-through'}`}>{p.label}</span>
                  {has && <CheckIcon size={10} className="ml-auto flex-shrink-0 text-[#22C55E]" strokeWidth={3} />}
                </div>
              );
            })}
          </div>
          {isParent && (
            <div className="mt-2 text-[10px] text-slate-400 text-center">+ Admin permissions</div>
          )}
        </div>
      </div>
    </div>
  );
}

function EditMemberModal({ member, onClose }: { member: FamilyMember; onClose: () => void }) {
  const role = STANDARD_ROLES[member.roleId];
  const isAdmin = member.roleId === 'administrator';
  // Session-lokale Overrides: zusaetzliche Rechte, die ein Administrator
  // dieser Person einzeln gewaehren kann (README Abschnitt 3, "Einzel-
  // Overrides"). Wie im urspruenglichen Mockup wird "Save Changes" hier
  // nicht dauerhaft gespeichert, nur der Zustand waehrend des Modals gezeigt.
  const [overrideKeys, setOverrideKeys] = useState<string[]>([]);
  const [color, setColor] = useState(member.color);
  const [name, setName] = useState(member.name);

  const keyOf = (p: { module: Module; action: Action; scope: Scope }) => `${p.module}-${p.action}-${p.scope}`;

  const toggleOverride = (key: string) => {
    setOverrideKeys(ks => ks.includes(key) ? ks.filter(k => k !== key) : [...ks, key]);
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div
          className="h-24 rounded-t-2xl relative flex items-end p-5"
          style={{ background: `linear-gradient(135deg, ${color}, ${color}BB)` }}
        >
          <div
            className="w-14 h-14 rounded-2xl flex items-center justify-center text-white text-xl font-bold border-4 border-white"
            style={{ backgroundColor: color }}
          >
            {member.initials[0]}
          </div>
          <button
            onClick={onClose}
            className="absolute top-3 right-3 text-white/80 hover:text-white text-xl"
          >
            ✕
          </button>
        </div>

        <div className="p-6">
          <div className="space-y-5">
            {/* Name */}
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Display Name</label>
              <input
                className="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20"
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </div>

            {/* Color picker */}
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-1.5 block">Family Color</label>
              <div className="flex flex-wrap gap-2">
                {FAMILY_COLORS.map(c => (
                  <button
                    key={c}
                    onClick={() => setColor(c)}
                    className="w-9 h-9 rounded-xl transition-transform hover:scale-110"
                    style={{
                      backgroundColor: c,
                      outline: color === c ? `3px solid ${c}` : 'none',
                      outlineOffset: '2px',
                    }}
                    aria-label={`Color ${c}`}
                  >
                    {color === c && <CheckIcon size={16} className="text-white mx-auto" strokeWidth={3} />}
                  </button>
                ))}
              </div>
            </div>

            {/* Permissions */}
            <div>
              <label className="text-xs font-semibold text-slate-600 mb-3 block flex items-center gap-2">
                <ShieldLock size={12} />
                Permissions
                <span className="text-[10px] font-normal text-slate-400 ml-1">
                  Rolle: {role.name}{!isAdmin && ' – Haken zusätzlich = Einzel-Override durch Administrator'}
                </span>
              </label>
              <div className="space-y-2">
                {PERMISSION_DISPLAY.map(p => {
                  const key = keyOf(p);
                  const fromRole = roleHasPermission(role, p.module, p.action, p.scope);
                  const has = fromRole || overrideKeys.includes(key);
                  const locked = isAdmin; // Administrator hat ohnehin Vollzugriff, nichts zu togglen
                  return (
                    <div
                      key={key}
                      className={`flex items-center gap-3 p-2.5 rounded-xl transition-colors ${locked ? 'bg-slate-50 opacity-60' : has ? 'bg-[#F0FDF4]' : 'hover:bg-slate-50 cursor-pointer'}`}
                      onClick={() => !locked && !fromRole && toggleOverride(key)}
                    >
                      <div
                        className={`w-5 h-5 rounded-md border-2 flex items-center justify-center flex-shrink-0 transition-all ${
                          has ? 'bg-[#22C55E] border-[#22C55E]' : 'border-slate-300'
                        }`}
                      >
                        {has && <CheckIcon size={12} className="text-white" strokeWidth={3} />}
                      </div>
                      <span className="text-sm">{p.icon}</span>
                      <span className={`text-sm flex-1 ${has ? 'font-medium text-slate-800' : 'text-slate-500'}`}>{p.label}</span>
                      {locked && <span className="text-[10px] text-slate-400">Admin</span>}
                      {!locked && fromRole && <span className="text-[10px] text-slate-400">Rolle</span>}
                      {!locked && !fromRole && overrideKeys.includes(key) && <span className="text-[10px] text-[#2563EB] font-medium">Override</span>}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>

          <div className="flex gap-3 mt-6">
            <button onClick={onClose} className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50">
              Cancel
            </button>
            <button onClick={onClose} className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8]">
              Save Changes
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function Profiles({ onNavigate }: Props) {
  const [editing, setEditing] = useState<FamilyMember | null>(null);
  const parents = FAMILY_MEMBERS.filter(m => m.roleId === 'administrator');
  const children = FAMILY_MEMBERS.filter(m => m.roleId !== 'administrator');

  return (
    <div className="p-4 lg:p-6 max-w-[1200px] mx-auto">
      {/* Family overview */}
      <div className="bg-gradient-to-br from-[#1E3A8A] via-[#2563EB] to-[#14B8A6] rounded-2xl p-6 mb-6 text-white relative overflow-hidden">
        <div className="absolute top-0 right-0 w-48 h-48 rounded-full bg-white/10 -translate-y-16 translate-x-16" />
        <div className="absolute bottom-0 left-0 w-32 h-32 rounded-full bg-white/5 translate-y-12 -translate-x-8" />
        <div className="relative">
          <div className="flex items-center gap-3 mb-4">
            <div className="text-4xl">👨‍👩‍👧‍👦</div>
            <div>
              <h2 className="text-2xl font-bold">Johnson Family</h2>
              <p className="text-white/70 text-sm">Est. 2010 · 5 members</p>
            </div>
          </div>
          <div className="flex flex-wrap gap-4">
            <div className="bg-white/20 rounded-xl px-4 py-2.5">
              <div className="text-white/70 text-xs mb-0.5">Parents</div>
              <div className="font-bold text-lg">2</div>
            </div>
            <div className="bg-white/20 rounded-xl px-4 py-2.5">
              <div className="text-white/70 text-xs mb-0.5">Children</div>
              <div className="font-bold text-lg">3</div>
            </div>
            <div className="bg-white/20 rounded-xl px-4 py-2.5">
              <div className="text-white/70 text-xs mb-0.5">Total Points</div>
              <div className="font-bold text-lg">895</div>
            </div>
            <div className="bg-white/20 rounded-xl px-4 py-2.5">
              <div className="text-white/70 text-xs mb-0.5">Tasks Done</div>
              <div className="font-bold text-lg">24</div>
            </div>
          </div>
        </div>
      </div>

      {/* Parents section */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
            <ShieldLock size={16} className="text-[#2563EB]" />
            Parents <span className="text-sm text-slate-400 font-normal">(Admin)</span>
          </h3>
          <span className="text-xs text-slate-400">{parents.length}/2 slots used</span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {parents.map(m => <MemberCard key={m.id} member={m} onEdit={setEditing} />)}
        </div>
      </div>

      {/* Children section */}
      <div className="mb-6">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
            <span className="text-lg">👦</span>
            Children
          </h3>
          <span className="text-xs text-slate-400">{children.length}/5 slots used</span>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {children.map(m => <MemberCard key={m.id} member={m} onEdit={setEditing} />)}

          {/* Add child slot */}
          {children.length < 5 && (
            <button className="bg-white rounded-2xl border-2 border-dashed border-slate-200 flex flex-col items-center justify-center p-10 hover:border-[#2563EB] hover:bg-[#EFF6FF]/50 transition-all group min-h-[200px]">
              <div className="w-12 h-12 rounded-2xl bg-slate-100 group-hover:bg-[#DBEAFE] flex items-center justify-center mb-3 transition-colors">
                <PlusIcon size={24} className="text-slate-400 group-hover:text-[#2563EB]" />
              </div>
              <span className="text-sm font-semibold text-slate-400 group-hover:text-[#2563EB] transition-colors">Add Child</span>
              <span className="text-xs text-slate-300 mt-1">{5 - children.length} slot{5 - children.length !== 1 ? 's' : ''} remaining</span>
            </button>
          )}
        </div>
      </div>

      {/* Family settings */}
      <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5">
        <h3 className="font-bold text-slate-800 text-base mb-4 flex items-center gap-2">
          <SettingsIcon size={16} className="text-slate-500" />
          Family Settings
        </h3>
        <div className="space-y-3">
          {[
            { label: 'Family name', value: 'Johnson', editable: true },
            { label: 'Time zone', value: 'Europe/London (GMT+1)', editable: true },
            { label: 'Language', value: 'English (UK)', editable: true },
            { label: 'Notifications', value: 'Enabled for all members', editable: true },
            { label: 'Privacy', value: 'Family only – data not shared', editable: false },
            { label: 'Plan', value: 'FamilyHub Premium 👑', editable: false },
          ].map(s => (
            <div key={s.label} className="flex items-center justify-between py-2.5 px-3 rounded-xl hover:bg-slate-50 transition-colors">
              <span className="text-sm text-slate-600 font-medium">{s.label}</span>
              <div className="flex items-center gap-2">
                <span className="text-sm text-slate-800 font-semibold">{s.value}</span>
                {s.editable && (
                  <button className="text-xs text-[#2563EB] font-medium hover:underline">Edit</button>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>

      {editing && <EditMemberModal member={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}
