import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/useAuth';
import { FullPageSpinner } from './FullPageSpinner';
import type { ReactNode } from 'react';

/**
 * Porta das rotas internas.
 *
 * Quem chega na raiz sem sessão vê a apresentação (`/sobre`), não um formulário de senha — pedir
 * credencial antes de explicar o que é o app é o que mais afasta morador novo. Já quem tentou abrir
 * uma tela interna direto (link salvo, notificação) vai para o login, que é o caminho curto de
 * volta ao lugar onde ele queria chegar.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const { pathname } = useLocation();

  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'unauthenticated') {
    return <Navigate to={pathname === '/' ? '/sobre' : '/login'} replace />;
  }
  return <>{children}</>;
}
