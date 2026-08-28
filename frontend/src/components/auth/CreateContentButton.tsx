import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { useLoginPrompt } from '@/features/auth/useLoginPrompt';

/**
 * Botão de publicar conteúdo (indicação, classificado). Três situações, uma regra:
 *
 * <ul>
 *   <li>Visitante: vê o botão e o clique pede a conta no popup, sem tirá-lo da listagem.</li>
 *   <li>Quem tem {@code CONTENT_CREATE}: link normal para o formulário.</li>
 *   <li>Convidado e proprietário (sem a permission): o botão some — o portal é leitura para eles.</li>
 * </ul>
 */
export function CreateContentButton({
  to,
  reason,
  children,
}: {
  to: string;
  reason: string;
  children: ReactNode;
}) {
  const { user } = useAuth();
  const { promptLogin } = useLoginPrompt();

  if (!user) {
    return (
      <Button className="min-h-[44px]" onClick={() => promptLogin({ reason, destination: to })}>
        {children}
      </Button>
    );
  }

  if (!user.authorities.includes('CONTENT_CREATE')) return null;

  return (
    <Button asChild className="min-h-[44px]">
      <Link to={to}>{children}</Link>
    </Button>
  );
}
