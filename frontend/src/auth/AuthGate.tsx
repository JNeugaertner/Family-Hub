import type { ReactNode } from 'react';
import { useAuth } from './AuthContext';
import LoginPage from './LoginPage';

// Zeigt Login bzw. Ersteinrichtung, bis jemand angemeldet ist. Die Kinder werden bei jeder Anmeldung neu
// aufgebaut, damit keine Daten der vorherigen Person im Speicher bleiben.
export default function AuthGate({ children }: { children: ReactNode }) {
  const { status, me } = useAuth();

  if (status === 'loading') {
    return <div className="min-h-screen flex items-center justify-center text-sm text-slate-400">FamilyHub wird geladen…</div>;
  }
  if (status !== 'authenticated' || !me) {
    return <LoginPage />;
  }
  return <div key={me.id} className="contents">{children}</div>;
}
