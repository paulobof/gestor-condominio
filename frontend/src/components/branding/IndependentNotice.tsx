import { cn } from '@/lib/utils';

/**
 * Aviso de transparência (LGPD): diz que este é um aplicativo independente, sem vínculo oficial com
 * a administração, o síndico ou o conselho. Exibido em todas as telas (públicas via PublicShell,
 * autenticadas via Shell).
 *
 * Tom deliberadamente discreto — nota de rodapé, não alerta. A identificação da controladora dos
 * dados vive na política de privacidade, que é onde a LGPD a exige; repeti-la em toda tela só
 * tornava o aviso ruidoso.
 */
export function IndependentNotice({ className }: { className?: string }) {
  return (
    <div
      role="note"
      className={cn(
        'border-b border-border bg-muted/40 px-3 py-1 text-center text-[11px] ' +
          'leading-snug text-muted-foreground',
        className
      )}
    >
      Aplicativo <span className="font-medium text-foreground/80">independente</span>, sem vínculo
      oficial com a administração, o síndico ou o conselho.
    </div>
  );
}
