import { createContext, useContext, useMemo, useState, ReactNode } from 'react';
import { FAMILY_MEMBERS, FamilyMember } from '../components/data';
import { Module, Action, Scope, isAllowed, STANDARD_ROLES } from './index';

interface ActorContextValue {
  currentActor: FamilyMember;
  setCurrentActorId: (id: number) => void;
  can: (module: Module, action: Action, scope: Scope) => boolean;
  roleName: string;
}

const ActorContext = createContext<ActorContextValue | null>(null);

export function ActorProvider({ children }: { children: ReactNode }) {
  const [currentActorId, setCurrentActorId] = useState<number>(FAMILY_MEMBERS[0].id);
  const currentActor = FAMILY_MEMBERS.find(m => m.id === currentActorId) ?? FAMILY_MEMBERS[0];

  const value = useMemo<ActorContextValue>(() => ({
    currentActor,
    setCurrentActorId,
    can: (module, action, scope) =>
      isAllowed({ name: currentActor.name, roleId: currentActor.roleId }, module, action, scope),
    roleName: STANDARD_ROLES[currentActor.roleId].name,
  }), [currentActor]);

  return <ActorContext.Provider value={value}>{children}</ActorContext.Provider>;
}

export function useActor(): ActorContextValue {
  const ctx = useContext(ActorContext);
  if (!ctx) {
    throw new Error('useActor muss innerhalb von <ActorProvider> verwendet werden.');
  }
  return ctx;
}
