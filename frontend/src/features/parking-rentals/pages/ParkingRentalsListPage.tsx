import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { Page, PageHeader } from '@/components/layout/Page';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  listParkingRentals,
  type ParkingRental,
  type ParkingRentalStatus,
} from '../api/parkingRentalsApi';

const STATUS_LABEL: Record<ParkingRentalStatus, string> = {
  ACTIVE: 'Ativos',
  RENTED: 'Alugados',
  ARCHIVED: 'Arquivados',
};

const brl = (n: number) => n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export function ParkingRentalsListPage() {
  const [status, setStatus] = useState<ParkingRentalStatus>('ACTIVE');
  const [items, setItems] = useState<ParkingRental[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listParkingRentals(status)
      .then((p) => {
        if (active) setItems(p.content);
      })
      .catch(() => {
        if (active) toast.error('Erro ao carregar anúncios de vagas.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [status]);

  return (
    <Page variant="list">
      <PageHeader
        title="Aluguel de Vagas"
        accent="blue"
        actions={
          <Button asChild className="min-h-[44px]">
            <Link to="/vagas/aluguel/novo">Anunciar vaga</Link>
          </Button>
        }
      />
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Filtrar por status">
        {(Object.keys(STATUS_LABEL) as ParkingRentalStatus[]).map((s) => (
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
        <p className="text-muted-foreground">Nenhuma vaga anunciada.</p>
      ) : (
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-2 xl:grid-cols-3">
          {items.map((r) => (
            <li key={r.id}>
              <Link
                to={`/vagas/aluguel/${r.id}`}
                className="block h-full rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <Card className="h-full transition-colors hover:bg-accent">
                  <CardHeader>
                    <CardTitle className="text-base">
                      Torre {r.tower} · Andar {r.floor} · Vaga {r.spotNumber}
                    </CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm font-medium">{brl(r.monthlyPrice)}/mês</p>
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Page>
  );
}
