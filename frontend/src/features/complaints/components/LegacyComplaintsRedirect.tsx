import { Navigate, useLocation } from 'react-router-dom';

/**
 * A feature foi renomeada de "Chamados" para "Reclamações": mantém links e favoritos antigos
 * (/chamados/... e /admin/chamados/...) funcionando, preservando o resto do caminho.
 */
export function LegacyComplaintsRedirect() {
  const { pathname, search, hash } = useLocation();
  const target = pathname
    .replace(/^(\/admin)?\/chamados(?=\/|$)/, '$1/reclamacoes')
    .replace(/^\/reclamacoes\/novo\/?$/, '/reclamacoes/nova');
  return <Navigate to={target + search + hash} replace />;
}
