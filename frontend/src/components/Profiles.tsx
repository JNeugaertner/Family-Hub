import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { PlusIcon, CheckIcon, SettingsIcon } from './Icons';
import { ApiError } from '../api/client';
import { useAuth, useMe } from '../auth/AuthContext';
import { changePassword } from '../auth/api';
import { useCalendarData } from '../calendar/CalendarDataContext';
import { CATEGORY_OPTIONS } from '../calendar/categories';
import type { EventCategory } from './data';
import {
  createMember, deleteMember, getSettings, listMembers, listRoles, updateMember, updateSettings,
  type ApiMember, type MemberInput, type RoleInfo,
} from '../family/api';
import { FAMILY_COLORS } from '../family/colors';
import { hasPermission, permissionKey, ROLE_NAMES, type Permission, type RoleId } from '../roles';

function ShieldLock({ size = 16, className = '' }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
  );
}

interface Props { onNavigate: (p: any) => void; }

// Rechte, die auf der Profilseite angezeigt und einzeln vergeben oder entzogen werden können.
// Jeder Eintrag entspricht genau einem Recht aus den Standardrollen (Backend: StandardRoles).
const PERMISSION_DISPLAY: (Permission & { label: string; icon: string })[] = [
  { module: 'kalender', action: 'ansehen', scope: 'familie', label: 'Familienkalender ansehen', icon: '📅' },
  { module: 'kalender', action: 'erstellen', scope: 'eigen', label: 'Eigene Termine anlegen', icon: '➕' },
  { module: 'kalender', action: 'vorschlagen', scope: 'familie', label: 'Termine für andere vorschlagen', icon: '💡' },
  { module: 'kalender', action: 'erstellen', scope: 'familie', label: 'Termine für alle anlegen', icon: '🗓️' },
  { module: 'kalender', action: 'freigeben', scope: 'familie', label: 'Vorschläge freigeben', icon: '✅' },
  { module: 'aufgaben', action: 'bearbeiten', scope: 'eigen', label: 'Eigene Aufgaben abhaken', icon: '☑️' },
  { module: 'einkauf', action: 'bearbeiten', scope: 'familie', label: 'Einkaufsliste bearbeiten', icon: '🛒' },
  { module: 'essen', action: 'vorschlagen', scope: 'familie', label: 'Essenswünsche einreichen', icon: '🍽️' },
  { module: 'punkte', action: 'ansehen', scope: 'familie', label: 'Punktestände der Familie sehen', icon: '⭐' },
  { module: 'punkte', action: 'freigeben', scope: 'familie', label: 'Punkte vergeben', icon: '🎁' },
  { module: 'familie', action: 'verwalten', scope: 'familie', label: 'Familienmitglieder verwalten', icon: '👥' },
  { module: 'system', action: 'verwalten', scope: 'familie', label: 'Rollen & Rechte verwalten', icon: '🔒' },
];

const INPUT = 'w-full border rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:ring-2';
const inputClass = (hasError: boolean) =>
  `${INPUT} ${hasError ? 'border-[#EF4444] focus:border-[#EF4444] focus:ring-[#EF4444]/20' : 'border-slate-200 focus:border-[#2563EB] focus:ring-[#2563EB]/20'}`;

function Field({ id, label, error, children }: { id: string; label: string; error?: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="text-xs font-semibold text-slate-600 mb-1.5 block">{label}</label>
      {children}
      {error && <p className="text-xs text-[#DC2626] mt-1">{error}</p>}
    </div>
  );
}

function errorsOf(err: unknown): { form: string | null; fields: Record<string, string> } {
  if (err instanceof ApiError && err.problem.errors) return { form: null, fields: err.problem.errors };
  return { form: err instanceof Error ? err.message : String(err), fields: {} };
}

function ageOf(birthDate: string | null): number | null {
  if (!birthDate) return null;
  const birth = new Date(birthDate);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  if (today.getMonth() < birth.getMonth() || (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())) age--;
  return age;
}

// Geltende Rechte wie im Backend: Rolle + zusätzliche - entzogene; Administratoren haben immer alle.
function effectivePermissions(member: ApiMember, roles: RoleInfo[]): Permission[] {
  const rolePermissions = roles.find(r => r.id === member.effectiveRole)?.permissions ?? [];
  if (member.effectiveRole === 'administrator') return rolePermissions;
  const revoked = new Set((member.revokedPermissions ?? []).map(permissionKey));
  return [...rolePermissions.filter(p => !revoked.has(permissionKey(p))), ...(member.extraPermissions ?? [])];
}

// ─── Karte eines Familienmitglieds ────────────────────────────────────────────

function MemberCard({ member, roles, isSelf, onEdit }: {
  member: ApiMember; roles: RoleInfo[]; isSelf: boolean; onEdit?: () => void;
}) {
  const permissions = effectivePermissions(member, roles);
  const age = ageOf(member.birthDate);
  const autoRole = member.role !== member.effectiveRole;

  return (
    <div className="bg-white rounded-2xl border border-slate-100 shadow-sm overflow-hidden">
      <div className="h-20 relative" style={{ background: `linear-gradient(135deg, ${member.color}, ${member.color}AA)` }}>
        <div className="absolute inset-0 opacity-20 bg-gradient-to-br from-white to-transparent" />
        {member.effectiveRole === 'administrator' && (
          <div className="absolute top-3 right-3 bg-white/30 backdrop-blur-sm text-white text-[10px] font-bold px-2 py-1 rounded-full flex items-center gap-1">
            <ShieldLock size={10} /> Admin
          </div>
        )}
      </div>

      <div className="px-5 pb-5">
        <div className="relative flex items-end justify-between -mt-7 mb-4">
          <div className="w-14 h-14 rounded-2xl flex items-center justify-center text-white text-xl font-bold border-4 border-white shadow-md"
            style={{ backgroundColor: member.color }}>
            {member.name.charAt(0).toUpperCase()}
          </div>
          {onEdit && (
            <button onClick={onEdit} aria-label={`${member.name} bearbeiten`}
              className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-800 bg-slate-50 hover:bg-slate-100 px-3 py-1.5 rounded-xl transition-all font-medium">
              <SettingsIcon size={12} /> Bearbeiten
            </button>
          )}
        </div>

        <div className="mb-4">
          <h3 className="font-bold text-slate-800 text-lg">{member.name}{isSelf && <span className="text-sm font-normal text-slate-400"> (du)</span>}</h3>
          <div className="flex items-center gap-2 mt-1 flex-wrap">
            <span className="text-xs font-semibold px-2.5 py-1 rounded-full text-white" style={{ backgroundColor: member.color }}>
              {ROLE_NAMES[member.effectiveRole]}
            </span>
            {autoRole && <span className="text-[11px] text-slate-400">automatisch ab 13</span>}
            {age !== null && <span className="text-xs text-slate-400">{age} Jahre</span>}
            {member.username && <span className="text-xs text-slate-400">@{member.username}</span>}
          </div>
        </div>

        <div>
          <div className="text-xs font-semibold text-slate-500 uppercase tracking-wide mb-2 flex items-center gap-1.5">
            <ShieldLock size={12} /> Rechte
          </div>
          <div className="grid grid-cols-2 gap-1.5">
            {PERMISSION_DISPLAY.slice(0, 8).map(p => {
              const has = hasPermission(permissions, p.module, p.action, p.scope);
              return (
                <div key={permissionKey(p)}
                  className={`flex items-center gap-1.5 text-[11px] px-2 py-1 rounded-lg ${has ? 'bg-[#F0FDF4] text-[#16A34A]' : 'bg-slate-50 text-slate-300'}`}>
                  <span className={has ? '' : 'grayscale opacity-50'}>{p.icon}</span>
                  <span className={`font-medium truncate ${has ? '' : 'line-through'}`}>{p.label}</span>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}

// ─── Mitglied anlegen / bearbeiten (nur Administratoren) ─────────────────────

function MemberModal({ member, roles, mayManageRights, onClose, onSaved }: {
  member?: ApiMember; roles: RoleInfo[]; mayManageRights: boolean; onClose: () => void; onSaved: () => Promise<void>;
}) {
  const isEdit = !!member;
  const [name, setName] = useState(member?.name ?? '');
  const [color, setColor] = useState(member?.color ?? FAMILY_COLORS[2]);
  const [username, setUsername] = useState(member?.username ?? '');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<RoleId>(member?.role ?? 'kind');
  const [birthDate, setBirthDate] = useState(member?.birthDate ?? '');
  const [roleFixed, setRoleFixed] = useState(member?.roleFixed ?? false);
  const [extra, setExtra] = useState<Permission[]>(member?.extraPermissions ?? []);
  const [revoked, setRevoked] = useState<Permission[]>(member?.revokedPermissions ?? []);

  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const rolePermissions = roles.find(r => r.id === role)?.permissions ?? [];
  const inRole = (p: Permission) => rolePermissions.some(r => permissionKey(r) === permissionKey(p));
  const contains = (list: Permission[], p: Permission) => list.some(x => permissionKey(x) === permissionKey(p));
  const toggled = (list: Permission[], p: Permission) =>
    contains(list, p) ? list.filter(x => permissionKey(x) !== permissionKey(p)) : [...list, p];

  // Standardrecht der Rolle: Haken entfernen = entziehen. Sonst: Haken setzen = zusätzlich vergeben.
  const toggle = (p: Permission) => {
    if (inRole(p)) setRevoked(r => toggled(r, p));
    else setExtra(e => toggled(e, p));
  };

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
      await onSaved();
      onClose();
    } catch (err) {
      const { form, fields } = errorsOf(err);
      setErrors(fields);
      setFormError(form);
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const input: MemberInput = {
      name: name.trim(),
      color,
      username: username.trim().toLowerCase(),
      password: password || null,
      role,
      birthDate: birthDate || null,
      roleFixed,
      // Einzelrechte nur für Nicht-Administratoren, im Rollenwechsel bereinigt
      extraPermissions: role === 'administrator' ? [] : extra.filter(p => !inRole(p)),
      revokedPermissions: role === 'administrator' ? [] : revoked.filter(inRole),
    };
    run(() => (member ? updateMember(member.id, input) : createMember(input)));
  };

  const remove = () => {
    if (!member) return;
    if (!confirmDelete) { setConfirmDelete(true); return; }
    run(() => deleteMember(member.id));
  };

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <form onSubmit={submit} noValidate role="dialog" aria-modal="true" aria-labelledby="member-form-title"
        className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="h-20 rounded-t-2xl relative flex items-end p-5" style={{ background: `linear-gradient(135deg, ${color}, ${color}BB)` }}>
          <h2 id="member-form-title" className="text-white font-bold text-lg">{isEdit ? `${member.name} bearbeiten` : 'Familienmitglied hinzufügen'}</h2>
          <button type="button" onClick={onClose} className="absolute top-3 right-3 text-white/80 hover:text-white text-xl" aria-label="Schließen">✕</button>
        </div>

        <div className="p-6 space-y-4">
          {formError && <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{formError}</div>}

          <div className="grid grid-cols-2 gap-3">
            <Field id="member-name" label="Name" error={errors.name}>
              <input id="member-name" className={inputClass(!!errors.name)} value={name} onChange={e => setName(e.target.value)} autoFocus />
            </Field>
            <Field id="member-username" label="Benutzername" error={errors.username}>
              <input id="member-username" className={inputClass(!!errors.username)} value={username} onChange={e => setUsername(e.target.value)} autoComplete="off" />
            </Field>
          </div>

          <Field id="member-password" label={isEdit ? 'Neues Passwort (leer lassen = unverändert)' : 'Passwort (mind. 8 Zeichen)'} error={errors.password}>
            <input id="member-password" type="password" className={inputClass(!!errors.password)} value={password}
              onChange={e => setPassword(e.target.value)} autoComplete="new-password" />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field id="member-role" label="Rolle" error={errors.role}>
              <select id="member-role" className={inputClass(!!errors.role)} value={role} disabled={!mayManageRights}
                onChange={e => setRole(e.target.value as RoleId)}>
                {roles.filter(r => r.assignable).map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
              </select>
            </Field>
            <Field id="member-birthDate" label="Geburtsdatum (optional)" error={errors.birthDate}>
              <input id="member-birthDate" type="date" className={inputClass(!!errors.birthDate)} value={birthDate}
                onChange={e => setBirthDate(e.target.value)} />
            </Field>
          </div>

          {role === 'kind' && (
            <label className="flex items-start gap-2.5 text-sm text-slate-700">
              <input type="checkbox" className="mt-0.5 w-4 h-4 accent-[#2563EB]" checked={roleFixed} disabled={!mayManageRights}
                onChange={e => setRoleFixed(e.target.checked)} />
              <span>
                Rolle nicht automatisch anpassen
                <span className="block text-xs text-slate-400">Sonst wird ein Kind mit Geburtsdatum ab 13 Jahren automatisch Jugendlicher.</span>
              </span>
            </label>
          )}

          <div>
            <span className="text-xs font-semibold text-slate-600 mb-1.5 block">Farbe</span>
            <div className="flex flex-wrap gap-2">
              {FAMILY_COLORS.map(c => (
                <button key={c} type="button" onClick={() => setColor(c)} aria-label={`Farbe ${c}`} aria-pressed={color === c}
                  className="w-8 h-8 rounded-lg transition-transform hover:scale-110"
                  style={{ backgroundColor: c, outline: color === c ? `3px solid ${c}` : 'none', outlineOffset: '2px' }}>
                  {color === c && <CheckIcon size={14} className="text-white mx-auto" strokeWidth={3} />}
                </button>
              ))}
            </div>
          </div>

          {mayManageRights && role !== 'administrator' && (
            <fieldset>
              <legend className="text-xs font-semibold text-slate-600 mb-2 flex items-center gap-2">
                <ShieldLock size={12} /> Rechte
                <span className="text-[10px] font-normal text-slate-400">„Rolle“ = Standard der Rolle, „einzeln“ = von dir angepasst</span>
              </legend>
              <div className="space-y-1.5">
                {PERMISSION_DISPLAY.map(p => {
                  const fromRole = inRole(p);
                  const has = fromRole ? !contains(revoked, p) : contains(extra, p);
                  const changed = fromRole ? contains(revoked, p) : contains(extra, p);
                  return (
                    <label key={permissionKey(p)}
                      className={`flex items-center gap-3 p-2 rounded-xl cursor-pointer transition-colors ${has ? 'bg-[#F0FDF4]' : 'hover:bg-slate-50'}`}>
                      <input type="checkbox" className="w-4 h-4 accent-[#22C55E]" checked={has} onChange={() => toggle(p)} />
                      <span className="text-sm">{p.icon}</span>
                      <span className={`text-sm flex-1 ${has ? 'font-medium text-slate-800' : 'text-slate-500'}`}>{p.label}</span>
                      <span className={`text-[10px] ${changed ? 'text-[#2563EB] font-semibold' : 'text-slate-400'}`}>
                        {changed ? 'einzeln' : fromRole ? 'Rolle' : ''}
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
          )}
          {role === 'administrator' && (
            <p className="text-xs text-slate-500">Administratoren haben immer alle Rechte, Einzelrechte gelten für sie nicht.</p>
          )}

          <div className="flex flex-wrap gap-3 pt-2">
            {isEdit && (
              <button type="button" onClick={remove} disabled={busy}
                className={`py-2.5 px-4 rounded-xl text-sm font-semibold transition-colors disabled:opacity-50 ${confirmDelete ? 'bg-[#EF4444] text-white hover:bg-[#DC2626]' : 'border border-[#FECACA] text-[#DC2626] hover:bg-[#FEF2F2]'}`}>
                {confirmDelete ? 'Wirklich löschen?' : 'Löschen'}
              </button>
            )}
            <button type="button" onClick={onClose} disabled={busy}
              className="flex-1 py-2.5 rounded-xl border border-slate-200 text-slate-600 text-sm font-semibold hover:bg-slate-50 disabled:opacity-50">
              Abbrechen
            </button>
            <button type="submit" disabled={busy}
              className="flex-1 py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
              {busy ? 'Speichern…' : 'Speichern'}
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}

// ─── Freigaben für Gäste (nur Administratoren) ────────────────────────────────

function GuestSettingsCard() {
  const [categories, setCategories] = useState<EventCategory[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getSettings().then(s => setCategories(s.guestCategories)).catch(err => setError(errorsOf(err).form));
  }, []);

  const toggle = async (category: EventCategory) => {
    if (!categories) return;
    const next = categories.includes(category) ? categories.filter(c => c !== category) : [...categories, category];
    setMessage(null);
    setError(null);
    try {
      setCategories((await updateSettings({ guestCategories: next })).guestCategories);
      setMessage('Gespeichert.');
    } catch (err) {
      setError(errorsOf(err).form);
    }
  };

  return (
    <section className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5" aria-labelledby="guest-settings-title">
      <h3 id="guest-settings-title" className="font-bold text-slate-800 text-base mb-1 flex items-center gap-2">
        <SettingsIcon size={16} className="text-slate-500" /> Freigaben für Gäste
      </h3>
      <p className="text-xs text-slate-500 mb-3">Gäste sehen nur Termine aus diesen Kategorien, und nie private Termine.</p>
      {error && <p role="alert" className="text-sm text-[#DC2626] mb-2">{error}</p>}
      <div className="flex flex-wrap gap-2">
        {CATEGORY_OPTIONS.map(c => {
          const active = categories?.includes(c.value) ?? false;
          return (
            <button key={c.value} type="button" disabled={!categories} onClick={() => toggle(c.value)} aria-pressed={active}
              className={`px-3 py-1.5 rounded-full text-xs font-medium border transition-colors ${active ? 'bg-[#EFF6FF] border-[#2563EB] text-[#2563EB]' : 'bg-white border-slate-200 text-slate-500 hover:bg-slate-50'}`}>
              {active && '✓ '}{c.label}
            </button>
          );
        })}
      </div>
      {message && <p className="text-xs text-[#16A34A] mt-2">{message}</p>}
    </section>
  );
}

// ─── Eigenes Passwort ändern (alle) ───────────────────────────────────────────

function PasswordCard() {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    if (next !== repeat) {
      setErrors({ repeat: 'Die Passwörter stimmen nicht überein' });
      return;
    }
    setBusy(true);
    try {
      await changePassword(current, next);
      setCurrent(''); setNext(''); setRepeat('');
      setErrors({});
      setFormError(null);
      setDone(true);
    } catch (err) {
      const { form, fields } = errorsOf(err);
      setErrors(fields);
      setFormError(form);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} noValidate className="bg-white rounded-2xl border border-slate-100 shadow-sm p-5 space-y-3" aria-labelledby="password-title">
      <h3 id="password-title" className="font-bold text-slate-800 text-base flex items-center gap-2">
        <ShieldLock size={16} className="text-slate-500" /> Mein Passwort ändern
      </h3>
      {formError && <p role="alert" className="text-sm text-[#DC2626]">{formError}</p>}
      <Field id="password-current" label="Aktuelles Passwort" error={errors.currentPassword}>
        <input id="password-current" type="password" className={inputClass(!!errors.currentPassword)} value={current}
          onChange={e => setCurrent(e.target.value)} autoComplete="current-password" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field id="password-new" label="Neues Passwort" error={errors.newPassword}>
          <input id="password-new" type="password" className={inputClass(!!errors.newPassword)} value={next}
            onChange={e => setNext(e.target.value)} autoComplete="new-password" />
        </Field>
        <Field id="password-repeat" label="Wiederholen" error={errors.repeat}>
          <input id="password-repeat" type="password" className={inputClass(!!errors.repeat)} value={repeat}
            onChange={e => setRepeat(e.target.value)} autoComplete="new-password" />
        </Field>
      </div>
      <div className="flex items-center gap-3">
        <button type="submit" disabled={busy || !current || !next}
          className="py-2 px-4 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
          Passwort ändern
        </button>
        {done && <span className="text-xs text-[#16A34A]">Passwort geändert.</span>}
      </div>
    </form>
  );
}

// ─── Seite ────────────────────────────────────────────────────────────────────

export default function Profiles({ onNavigate }: Props) {
  const me = useMe();
  const { can } = useAuth();
  const { reload: reloadCalendar } = useCalendarData();
  const mayManage = can('familie', 'verwalten', 'familie');
  const mayManageRights = can('system', 'verwalten', 'familie');

  const [members, setMembers] = useState<ApiMember[]>([]);
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ member?: ApiMember } | null>(null);

  const load = useCallback(async () => {
    try {
      const [m, r] = await Promise.all([listMembers(), listRoles()]);
      setMembers(m);
      setRoles(r);
      setError(null);
    } catch (err) {
      setError(errorsOf(err).form);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const afterSave = useCallback(async () => {
    await load();
    await reloadCalendar();
  }, [load, reloadCalendar]);

  const groups = useMemo(() => ({
    admins: members.filter(m => m.role === 'administrator'),
    children: members.filter(m => m.role === 'kind' || m.role === 'jugendlicher'),
    guests: members.filter(m => m.role === 'gast'),
  }), [members]);

  const section = (title: ReactNode, list: ApiMember[], limit?: number) => list.length > 0 && (
    <div className="mb-6">
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">{title}</h3>
        {limit && <span className="text-xs text-slate-400">{list.length}/{limit} Plätze belegt</span>}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {list.map(m => (
          <MemberCard key={m.id} member={m} roles={roles} isSelf={m.id === me.id}
            onEdit={mayManage ? () => setEditing({ member: m }) : undefined} />
        ))}
      </div>
    </div>
  );

  return (
    <div className="p-4 lg:p-6 max-w-[1200px] mx-auto">
      <div className="bg-gradient-to-br from-[#1E3A8A] via-[#2563EB] to-[#14B8A6] rounded-2xl p-6 mb-6 text-white relative overflow-hidden">
        <div className="absolute top-0 right-0 w-48 h-48 rounded-full bg-white/10 -translate-y-16 translate-x-16" />
        <div className="relative flex flex-wrap items-center gap-4">
          <div className="text-4xl">👨‍👩‍👧‍👦</div>
          <div className="flex-1 min-w-[180px]">
            <h2 className="text-2xl font-bold">Familie</h2>
            <p className="text-white/70 text-sm">{members.length} Konten · höchstens 2 Administratoren und 5 Kinder</p>
          </div>
          {mayManage && (
            <button onClick={() => setEditing({})}
              className="flex items-center gap-2 bg-white text-[#1E3A8A] px-4 py-2 rounded-xl text-sm font-semibold hover:bg-white/90">
              <PlusIcon size={16} /> Mitglied hinzufügen
            </button>
          )}
        </div>
      </div>

      {error && <div role="alert" className="mb-5 bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{error}</div>}

      {section(<><ShieldLock size={16} className="text-[#2563EB]" /> Eltern <span className="text-sm text-slate-400 font-normal">(Administratoren)</span></>, groups.admins, 2)}
      {section(<><span className="text-lg">👦</span> Kinder und Jugendliche</>, groups.children, 5)}
      {section(<><span className="text-lg">🧓</span> Gäste</>, groups.guests)}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {mayManageRights && <GuestSettingsCard />}
        <PasswordCard />
      </div>

      {editing && (
        <MemberModal member={editing.member} roles={roles} mayManageRights={mayManageRights}
          onClose={() => setEditing(null)} onSaved={afterSave} />
      )}
    </div>
  );
}
