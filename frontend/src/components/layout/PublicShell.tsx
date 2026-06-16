import { Outlet } from 'react-router-dom';
import { IndependentNotice } from '@/components/branding/IndependentNotice';

/**
 * Casca das telas públicas (login, cadastro, recuperação de senha). Garante que
 * o aviso de app independente (LGPD) apareça fixo no topo dessas páginas, que
 * não passam pelo Shell autenticado.
 */
export function PublicShell() {
  return (
    <>
      <IndependentNotice className="sticky top-0 z-50" />
      <Outlet />
    </>
  );
}
