import { Navigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/useAuth';
import { FullPageSpinner } from './FullPageSpinner';
import type { ReactNode } from 'react';

/**
 * Porta das areas que exigem conta: escrita, area da unidade, admin e dados pessoais.
 *
 * A home e todo o conteudo de leitura ficam fora daqui — o visitante entra direto na home e so
 * encontra o login quando tenta fazer algo que precisa de identidade.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'unauthenticated') return <Navigate to="/login" replace />;
  return <>{children}</>;
}
