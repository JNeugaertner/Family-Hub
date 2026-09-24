import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { FAMILY_COLORS } from '../family/colors';
import { CheckIcon } from '../components/Icons';
import { useAuth } from './AuthContext';

const INPUT = 'w-full border border-slate-200 rounded-xl px-3 py-2.5 text-sm focus:outline-none focus:border-[#2563EB] focus:ring-2 focus:ring-[#2563EB]/20';

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

function LoginForm() {
  const { login, notice } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username.trim().toLowerCase(), password);
    } catch (err) {
      setError(errorsOf(err).form);
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" aria-label="Anmelden">
      <h2 className="text-lg font-bold text-slate-800">Anmelden</h2>
      {(error || notice) && (
        <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{error ?? notice}</div>
      )}
      <Field id="login-username" label="Benutzername">
        <input id="login-username" className={INPUT} value={username} onChange={e => setUsername(e.target.value)}
          autoComplete="username" autoFocus />
      </Field>
      <Field id="login-password" label="Passwort">
        <input id="login-password" type="password" className={INPUT} value={password}
          onChange={e => setPassword(e.target.value)} autoComplete="current-password" />
      </Field>
      <button type="submit" disabled={busy || !username || !password}
        className="w-full py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
        {busy ? 'Anmelden…' : 'Anmelden'}
      </button>
      {import.meta.env.DEV && (
        <p className="text-[11px] text-slate-400 text-center">
          Beispielkonten: sarah, mike (Admin) · emma (Jugendliche) · lucas, lily (Kind) · oma (Gast) – Passwort <code>familyhub</code>
        </p>
      )}
    </form>
  );
}

function SetupForm() {
  const { setup } = useAuth();
  const [name, setName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [color, setColor] = useState(FAMILY_COLORS[0]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== repeat) {
      setErrors({ repeat: 'Die Passwörter stimmen nicht überein' });
      return;
    }
    setBusy(true);
    try {
      await setup({ name: name.trim(), color, username: username.trim().toLowerCase(), password });
    } catch (err) {
      const { form, fields } = errorsOf(err);
      setErrors(fields);
      setFormError(form);
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="space-y-4" aria-label="Familie einrichten" noValidate>
      <div>
        <h2 className="text-lg font-bold text-slate-800">Familie einrichten</h2>
        <p className="text-sm text-slate-500 mt-1">Lege dich als ersten Administrator an. Weitere Familienmitglieder fügst du danach unter „Profiles“ hinzu.</p>
      </div>
      {formError && <div role="alert" className="bg-[#FEF2F2] border border-[#FECACA] text-[#DC2626] text-sm rounded-xl p-3">{formError}</div>}
      <Field id="setup-name" label="Dein Name" error={errors.name}>
        <input id="setup-name" className={INPUT} value={name} onChange={e => setName(e.target.value)} autoFocus />
      </Field>
      <Field id="setup-username" label="Benutzername" error={errors.username}>
        <input id="setup-username" className={INPUT} value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" />
      </Field>
      <div className="grid grid-cols-2 gap-3">
        <Field id="setup-password" label="Passwort (mind. 8 Zeichen)" error={errors.password}>
          <input id="setup-password" type="password" className={INPUT} value={password} onChange={e => setPassword(e.target.value)} autoComplete="new-password" />
        </Field>
        <Field id="setup-repeat" label="Passwort wiederholen" error={errors.repeat}>
          <input id="setup-repeat" type="password" className={INPUT} value={repeat} onChange={e => setRepeat(e.target.value)} autoComplete="new-password" />
        </Field>
      </div>
      <div>
        <span className="text-xs font-semibold text-slate-600 mb-1.5 block">Deine Farbe</span>
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
      <button type="submit" disabled={busy}
        className="w-full py-2.5 rounded-xl bg-[#2563EB] text-white text-sm font-semibold hover:bg-[#1D4ED8] disabled:opacity-50">
        {busy ? 'Wird eingerichtet…' : 'Familie einrichten'}
      </button>
    </form>
  );
}

export default function LoginPage() {
  const { status } = useAuth();

  return (
    <div className="min-h-screen bg-[#F8FAFC] flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="flex items-center gap-3 justify-center mb-6">
          <div className="w-11 h-11 rounded-xl bg-gradient-to-br from-[#2563EB] to-[#14B8A6] flex items-center justify-center">
            <span className="text-white text-xl">🏠</span>
          </div>
          <div>
            <div className="font-bold text-[#0F172A] text-xl leading-tight">FamilyHub</div>
            <div className="text-[10px] text-slate-400 font-medium tracking-wide uppercase">AI Organizer</div>
          </div>
        </div>
        <div className="bg-white rounded-2xl border border-slate-100 shadow-sm p-6">
          {status === 'setup' ? <SetupForm /> : <LoginForm />}
        </div>
      </div>
    </div>
  );
}
