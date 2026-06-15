import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Pencil, Trash2, MessageCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import {
  deleteParkingRental,
  getParkingRental,
  updateParkingRental,
  type ParkingRental,
  type ParkingRentalStatus,
} from '../api/parkingRentalsApi';

const STATUS_LABEL: Record<ParkingRentalStatus, string> = {
  ACTIVE: 'Ativa',
  RENTED: 'Alugada',
  ARCHIVED: 'Arquivada',
};

const brl = (n: number) => n.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

export function ParkingRentalDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [r, setR] = useState<ParkingRental | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    try {
      setR(await getParkingRental(id));
    } catch {
      toast.error('Erro ao carregar o anúncio.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const isOwnerOrMod =
    !!user &&
    !!r &&
    (user.id === r.authorUserId || user.authorities.includes('PARKING_RENTAL_MODERATE'));

  const changeStatus = async (status: ParkingRentalStatus) => {
    if (!id || !r) return;
    setBusy(true);
    try {
      const updated = await updateParkingRental(id, {
        tower: r.tower,
        floor: r.floor,
        spotNumber: r.spotNumber,
        monthlyPrice: r.monthlyPrice,
        status,
      });
      setR(updated);
      toast.success('Status atualizado.');
    } catch {
      toast.error('Erro ao atualizar o status.');
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm('Tem certeza que deseja excluir este anúncio?')) return;
    setBusy(true);
    try {
      await deleteParkingRental(id);
      toast.success('Anúncio excluído.');
      navigate('/vagas/aluguel', { replace: true });
    } catch {
      toast.error('Erro ao excluir o anúncio.');
      setBusy(false);
    }
  };

  if (loading) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  if (!r)
    return (
      <main className="mx-auto max-w-3xl p-4">
        <p className="text-muted-foreground">Anúncio não encontrado.</p>
        <Button asChild variant="link" className="mt-2 px-0">
          <Link to="/vagas/aluguel">Voltar para Aluguel de Vagas</Link>
        </Button>
      </main>
    );

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/vagas/aluguel">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="space-y-1">
          <h1 className="text-2xl font-heading font-semibold">
            Torre {r.tower} · Andar {r.floor} · Vaga {r.spotNumber}
          </h1>
          <span className="inline-block rounded-full bg-muted px-3 py-1 text-xs font-medium">
            {STATUS_LABEL[r.status]}
          </span>
        </div>
        <p className="text-xl font-semibold">{brl(r.monthlyPrice)}/mês</p>
      </div>

      {(r.authorName || r.authorWhatsapp) && (
        <div className="rounded-lg border bg-muted/40 p-4 space-y-2">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Anunciante
          </p>
          {r.authorName && <p className="text-sm font-medium">{r.authorName}</p>}
          {r.authorWhatsapp && (
            <Button asChild className="min-h-[44px]">
              <a
                href={`https://wa.me/${r.authorWhatsapp}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                <MessageCircle aria-hidden="true" /> Falar no WhatsApp
              </a>
            </Button>
          )}
        </div>
      )}

      {isOwnerOrMod && (
        <div className="flex flex-wrap gap-2 pt-2">
          <Button asChild className="min-h-[44px]">
            <Link to={`/vagas/aluguel/${r.id}/editar`}>
              <Pencil aria-hidden="true" /> Editar
            </Link>
          </Button>
          {r.status === 'ACTIVE' && (
            <Button
              variant="secondary"
              className="min-h-[44px]"
              onClick={() => changeStatus('RENTED')}
              disabled={busy}
            >
              Marcar como alugada
            </Button>
          )}
          {r.status !== 'ARCHIVED' ? (
            <Button
              variant="secondary"
              className="min-h-[44px]"
              onClick={() => changeStatus('ARCHIVED')}
              disabled={busy}
            >
              Arquivar
            </Button>
          ) : (
            <Button
              variant="secondary"
              className="min-h-[44px]"
              onClick={() => changeStatus('ACTIVE')}
              disabled={busy}
            >
              Reativar
            </Button>
          )}
          <Button
            variant="destructive"
            className="min-h-[44px]"
            onClick={handleDelete}
            disabled={busy}
          >
            <Trash2 aria-hidden="true" /> Excluir
          </Button>
        </div>
      )}
    </main>
  );
}
