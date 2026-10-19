import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * Larguras por tipo de tela. As páginas alinham à ESQUERDA, junto ao menu lateral (lê como app,
 * não como documento solto no meio da tela); a largura máxima vem do conteúdo:
 * - list: grades e listas — aproveita a largura (1→2→3 colunas nos grids).
 * - detail: conteúdo + coluna lateral de contato/ações ({@link DetailLayout}).
 * - form: formulário — linha curta, fácil de preencher.
 * - reading: texto corrido — até ~70 caracteres por linha.
 */
export type PageVariant = 'list' | 'detail' | 'form' | 'reading';

const WIDTH: Record<PageVariant, string> = {
  list: 'max-w-6xl',
  detail: 'max-w-6xl',
  form: 'max-w-2xl',
  reading: 'max-w-3xl',
};

/**
 * Contêiner de página dentro da casca do app. Renderiza `div` (não `main`): o `<main>` é da casca,
 * e landmarks `main` aninhados são inválidos.
 */
export function Page({
  variant,
  className,
  children,
}: {
  variant: PageVariant;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div
      data-page-variant={variant}
      className={cn('w-full space-y-6 px-4 py-6 sm:px-6 lg:px-8', WIDTH[variant], className)}
    >
      {children}
    </div>
  );
}

/**
 * Título da página com a ação principal na mesma linha (quebra para baixo no celular).
 * `accent` é a cor de marca da área (mesma do item no menu).
 */
export function PageHeader({
  title,
  description,
  accent,
  actions,
}: {
  title: ReactNode;
  description?: ReactNode;
  accent?: 'red' | 'orange' | 'green' | 'blue';
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0 space-y-1">
        <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold">
          {accent && (
            <span
              aria-hidden="true"
              className="inline-block h-6 w-1.5 shrink-0 rounded-full"
              style={{ backgroundColor: `hsl(var(--brand-${accent}))` }}
            />
          )}
          {title}
        </h1>
        {description && <p className="text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/**
 * Detalhe em duas colunas no desktop: conteúdo à esquerda, coluna lateral (contato, ações,
 * metadados) à direita e fixa ao rolar. No celular, a lateral vem depois do conteúdo.
 *
 * `after`: conteúdo longo e secundário (ex.: avaliações). No desktop continua na coluna da
 * esquerda, abaixo de `main`; no celular vem DEPOIS da lateral, para o contato não ficar enterrado
 * no fim da página.
 */
export function DetailLayout({
  main,
  aside,
  after,
}: {
  main?: ReactNode;
  aside?: ReactNode;
  after?: ReactNode;
}) {
  if (!aside)
    return (
      <div className="max-w-3xl space-y-4">
        {main}
        {after}
      </div>
    );
  return (
    // grid-rows-[auto_1fr]: a 1ª linha abraça `main`; a lateral (2 linhas) não empurra `after`.
    <div
      className={cn(
        'grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] xl:grid-cols-[minmax(0,1fr)_22rem]',
        after && 'lg:grid-rows-[auto_1fr]'
      )}
    >
      {main && <div className="min-w-0 space-y-4 lg:col-start-1 lg:row-start-1">{main}</div>}
      <aside
        className={cn(
          'space-y-4 lg:sticky lg:top-4 lg:col-start-2 lg:row-start-1 lg:self-start',
          after && 'lg:row-span-2'
        )}
      >
        {aside}
      </aside>
      {after && (
        <div
          className={cn(
            'min-w-0 space-y-4 lg:col-start-1',
            main ? 'lg:row-start-2' : 'lg:row-start-1 lg:row-span-2'
          )}
        >
          {after}
        </div>
      )}
    </div>
  );
}
