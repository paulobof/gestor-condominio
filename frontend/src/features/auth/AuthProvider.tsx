import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { setAccessToken, setUnauthorizedHandler } from '@/lib/api';
import * as authApi from './api/authApi';
import type { AuthenticatedUserView } from './api/authApi';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthState {
  status: AuthStatus;
  user: AuthenticatedUserView | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /**
   * Recarrega o usuário logado (/auth/me) — ex.: após editar "Meu cadastro", para a saudação
   * refletir o novo nome. Opcional no tipo só para não quebrar contextos montados à mão em testes.
   */
  refreshUser?: () => Promise<void>;
}

export const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthenticatedUserView | null>(null);

  const handleUnauthorized = useCallback(() => {
    setAccessToken(null);
    setUser(null);
    setStatus('unauthenticated');
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(handleUnauthorized);
  }, [handleUnauthorized]);

  // Try to refresh on mount
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const r = await authApi.refresh();
        if (cancelled) return;
        setAccessToken(r.accessToken);
        setUser(r.user);
        setStatus('authenticated');
      } catch {
        if (!cancelled) {
          setAccessToken(null);
          setUser(null);
          setStatus('unauthenticated');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const r = await authApi.login(email, password);
    setAccessToken(r.accessToken);
    setUser(r.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      setAccessToken(null);
      setUser(null);
      setStatus('unauthenticated');
    }
  }, []);

  const refreshUser = useCallback(async () => {
    const me = await authApi.me();
    setUser(me);
  }, []);

  const value = useMemo<AuthState>(
    () => ({ status, user, login, logout, refreshUser }),
    [status, user, login, logout, refreshUser]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
