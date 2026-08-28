import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { listClassifieds, type Classified, type ClassifiedStatus } from '../api/classifiedsApi';

const STATUS_LABEL: Record<ClassifiedStatus, string> = {
  ACTIVE: 'Ativos',
  SOLD: 'Vendidos',
  ARCHIVED: 'Arquivados',
};

export function ClassifiedsListPage() {
  const [status, setStatus] = useState<ClassifiedStatus>('ACTIVE');
  const [items, setItems] = useState<Classified[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listClassifieds(status)
      .then((p) => {
        if (active) setItems(p.content);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar anúncios.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [status]);

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="flex items-center gap-2 text-2xl font-heading font-semibold">
          <span
            aria-hidden="true"
            className="inline-block h-6 w-1.5 rounded-full"
            style={{ backgroundColor: 'hsl(var(--brand-green))' }}
          />
          Classificados
        </h1>
        <Button asChild className="min-h-[44px]">
          <Link to="/classificados/novo">Novo anúncio</Link>
        </Button>
      </div>
      <div className="mb-4 flex flex-wrap gap-2" role="tablist" aria-label="Filtrar por status">
        {(Object.keys(STATUS_LABEL) as ClassifiedStatus[]).map((s) => (
          <Button
            key={s}
            type="button"
            role="tab"
            aria-selected={s === status}
            variant={s === status ? 'default' : 'secondary'}
            className="min-h-[44px]"
            onClick={() => setStatus(s)}
          >
            {STATUS_LABEL[s]}
          </Button>
        ))}
      </div>
      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhum anúncio.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {items.map((c) => (
            <li key={c.id}>
              <Link
                to={`/classificados/${c.id}`}
                className="block rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card className="h-full transition-colors hover:bg-accent">
                  <CardHeader>
                    <CardTitle className="text-base">{c.title}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    {c.price != null && (
                      <p className="text-sm font-medium">
                        {c.price.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                      </p>
                    )}
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
