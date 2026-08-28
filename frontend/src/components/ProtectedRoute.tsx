import { useEffect, useRef } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth/useAuth';
import { useLoginPrompt } from '@/features/auth/useLoginPrompt';
import { FullPageSpinner } from './FullPageSpinner';
import type { ReactNode } from 'react';

/**
 * Porta das areas que exigem conta: escrita, area da unidade, admin e dados pessoais.
 *
 * A home e todo o conteudo de leitura ficam fora daqui — o visitante entra direto na home e so
 * encontra o login quando tenta fazer algo que precisa de identidade. E esse pedido e sempre um
 * popup: quem chega sem conta numa area protegida (link direto, favorito) volta para a home com o
 * popup aberto por cima, em vez de cair numa tela de senha. Ao entrar, o popup leva ao destino.
 */
export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const { promptLogin } = useLoginPrompt();
  const location = useLocation();
  const jaPediu = useRef(false);

  useEffect(() => {
    if (status !== 'unauthenticated' || jaPediu.current) return;
    jaPediu.current = true;
    promptLogin({ destination: `${location.pathname}${location.search}` });
  }, [status, promptLogin, location.pathname, location.search]);

  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'unauthenticated') return <Navigate to="/" replace />;
  return <>{children}</>;
}
