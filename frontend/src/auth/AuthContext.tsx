import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setUnauthorizedHandler } from '../api/client';
import { hasPermission, ROLE_NAMES, type Action, type Module, type Scope } from '../roles';
import * as api from './api';

type Status = 'loading' | 'setup' | 'anonymous' | 'authenticated';

interface AuthValue {
  status: Status;
  me: api.Me | null;
  // Hinweis auf der Login-Seite, z. B. nach abgelaufener Sitzung
  notice: string | null;
  roleName: string;
  can: (module: Module, action: Action, scope: Scope) => boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  setup: (input: api.SetupInput) => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [me, setMe] = useState<api.Me | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const becomeAnonymous = useCallback((message: string | null) => {
    setMe(null);
    setNotice(message);
    setStatus('anonymous');
  }, []);

  // Der erste Aufruf liefert auch das CSRF-Cookie, das alle späteren Änderungen brauchen.
  const refresh = useCallback(async () => {
    const { setupRequired } = await api.getStatus();
    if (setupRequired) {
      setStatus('setup');
      return;
    }
    try {
      setMe(await api.getMe());
      setStatus('authenticated');
    } catch {
      becomeAnonymous(null);
    }
  }, [becomeAnonymous]);

  useEffect(() => {
    setUnauthorizedHandler(() => becomeAnonymous('Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.'));
    refresh().catch(err => becomeAnonymous(err instanceof Error ? err.message : String(err)));
  }, [refresh, becomeAnonymous]);

  const login = useCallback(async (username: string, password: string) => {
    await api.login(username, password);
    // Nach dem Login gibt Spring ein neues CSRF-Token aus; die GET-Anfrage holt es ab.
    setMe(await api.getMe());
    setNotice(null);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    await api.logout().catch(() => undefined);
    becomeAnonymous(null);
    await api.getStatus();
  }, [becomeAnonymous]);

  const setup = useCallback(async (input: api.SetupInput) => {
    await api.setup(input);
    await login(input.username, input.password);
  }, [login]);

  const value = useMemo<AuthValue>(() => ({
    status, me, notice,
    roleName: me ? ROLE_NAMES[me.effectiveRole] : '',
    can: (module, action, scope) => !!me && hasPermission(me.permissions, module, action, scope),
    login, logout, setup, refresh,
  }), [status, me, notice, login, logout, setup, refresh]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth muss innerhalb von AuthProvider verwendet werden');
  return ctx;
}

// Nur für Seiten, die ausschließlich angemeldet erreichbar sind.
export function useMe(): api.Me {
  const { me } = useAuth();
  if (!me) throw new Error('useMe ist nur im angemeldeten Bereich verfügbar');
  return me;
}
