import { AlertTriangle } from 'lucide-react';
import { cn } from '@/lib/utils';

/**
 * Aviso de transparência (LGPD): deixa claro que este é um aplicativo
 * INDEPENDENTE da WIZOR TECH, **sem vínculo com a atual gestão do condomínio**,
 * e que a controladora dos dados é a WIZOR TECNOLOGIA LTDA. Exibido em todas as
 * telas (públicas via PublicShell, autenticadas via Shell).
 */
export function IndependentNotice({ className }: { className?: string }) {
  return (
    <div
      role="note"
      className={cn(
        'flex items-center justify-center gap-1.5 border-b border-amber-300/60 ' +
          'bg-amber-100 px-3 py-1.5 text-center text-xs leading-snug text-amber-900 ' +
          'dark:border-amber-900/60 dark:bg-amber-950/70 dark:text-amber-200',
        className
      )}
    >
      <AlertTriangle className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
      <span>
        Aplicativo <strong className="font-semibold">independente</strong> da WIZOR TECH — sem
        vínculo com a atual gestão do condomínio. Dados tratados pela WIZOR TECNOLOGIA LTDA.
      </span>
    </div>
  );
}
