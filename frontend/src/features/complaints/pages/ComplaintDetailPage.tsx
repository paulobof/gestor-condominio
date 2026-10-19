import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { DetailLayout, Page } from '@/components/layout/Page';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatDateBR, formatInstantBR } from '@/lib/date';
import {
  backendMessage,
  cancelComplaint,
  getComplaint,
  type Complaint,
} from '../api/complaintsApi';
import { StatusBadge } from '../components/StatusBadge';
import { ComplaintPhotos } from '../components/ComplaintPhotos';

export function ComplaintDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [c, setC] = useState<Complaint | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    getComplaint(id)
      .then(setC)
      .catch(() => setNotFound(true));
  }, [id]);

  const cancel = async () => {
    if (!id) return;
    setBusy(true);
    try {
      await cancelComplaint(id);
      toast.success('Reclamação cancelada.');
      navigate('/reclamacoes', { replace: true });
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao cancelar.'));
    } finally {
      setBusy(false);
    }
  };

  if (notFound) return <Page variant="detail">Reclamação não encontrada.</Page>;
  if (!c) return <Page variant="detail">Carregando…</Page>;

  return (
    <Page variant="detail">
      <Button asChild variant="ghost" className="-ml-2 min-h-[44px] px-2">
        <Link to="/reclamacoes">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>
      <DetailLayout
        main={
          <Card>
            <CardHeader className="space-y-2">
              <span className="text-sm text-muted-foreground">
                {c.code != null ? `#${c.code}` : ''}
              </span>
              <CardTitle>
                {c.typeName} › {c.subtypeName}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="whitespace-pre-wrap">{c.description}</p>
              <ComplaintPhotos complaintId={c.id} photos={c.photos} />
              {c.syndicResponse && (
                <section className="space-y-1 rounded-md border-l-4 border-indigo-500 bg-indigo-50 p-3">
                  <h2 className="text-sm font-semibold">Resposta da síndica</h2>
                  <p className="whitespace-pre-wrap">{c.syndicResponse}</p>
                  {c.syndicResponseAt && (
                    <p className="text-xs text-muted-foreground">
                      Atualizada em {formatInstantBR(c.syndicResponseAt)}
                    </p>
                  )}
                </section>
              )}
              {c.rejectionReason && <p className="text-red-800">Motivo: {c.rejectionReason}</p>}
              {c.resolutionNote && <p>Resolução: {c.resolutionNote}</p>}
            </CardContent>
          </Card>
        }
        aside={
          <Card>
            <CardContent className="space-y-3 pt-6">
              <StatusBadge status={c.status} />
              <p className="text-sm text-muted-foreground">
                {c.locationName}
                {c.locationDetail ? ` — ${c.locationDetail}` : ''}
              </p>
              <dl className="space-y-1 text-sm">
                <div>Aberta em {formatInstantBR(c.createdAt)}</div>
                {c.forwardedAt && <div>Encaminhada em {formatInstantBR(c.forwardedAt)}</div>}
                {c.dueDate && <div className="font-medium">Prazo: {formatDateBR(c.dueDate)}</div>}
                {c.resolvedAt && <div>Resolvida em {formatInstantBR(c.resolvedAt)}</div>}
              </dl>

              {c.editable && !confirming && (
                <div className="flex flex-col gap-2">
                  <Button asChild variant="outline" className="min-h-[44px]">
                    <Link to={`/reclamacoes/${c.id}/editar`}>Editar</Link>
                  </Button>
                  <Button
                    variant="destructive"
                    className="min-h-[44px]"
                    onClick={() => setConfirming(true)}
                  >
                    Cancelar reclamação
                  </Button>
                </div>
              )}
              {confirming && (
                <div
                  role="alertdialog"
                  aria-label="Confirmar cancelamento"
                  className="space-y-2 rounded-md border p-3"
                >
                  <p>Cancelar esta reclamação? Essa ação não pode ser desfeita.</p>
                  <div className="flex gap-2">
                    <Button
                      variant="destructive"
                      className="min-h-[44px]"
                      disabled={busy}
                      onClick={cancel}
                    >
                      Sim, cancelar
                    </Button>
                    <Button
                      variant="outline"
                      className="min-h-[44px]"
                      onClick={() => setConfirming(false)}
                    >
                      Voltar
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        }
      />
    </Page>
  );
}
