import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { formatDateBR, formatInstantBR } from '@/lib/date';
import {
  STATUS_LABEL,
  adminAction,
  adminDetail,
  adminEdit,
  adminReject,
  adminResolve,
  adminSetSyndicResponse,
  adminSetDueDate,
  backendMessage,
  getCatalog,
  type AdminComplaintDetail,
  type Catalog,
  type ComplaintEvent,
  type ComplaintStatus,
} from '../api/complaintsApi';
import { CatalogSelects, type CatalogSelection } from '../components/CatalogSelects';
import { ComplaintPhotos } from '../components/ComplaintPhotos';
import { StatusBadge } from '../components/StatusBadge';

function describeEvent(e: ComplaintEvent): string {
  const who = e.actorName ?? 'alguém';
  switch (e.kind) {
    case 'CREATED':
      return `Aberta por ${who}`;
    case 'EDITED':
      return `Editada por ${who}`;
    case 'STATUS_CHANGED':
      return `${who}: ${STATUS_LABEL[e.fromValue as ComplaintStatus] ?? e.fromValue} → ${
        STATUS_LABEL[e.toValue as ComplaintStatus] ?? e.toValue
      }`;
    case 'DUE_DATE_SET':
      return `${who} definiu o prazo para ${formatDateBR(e.toValue)}`;
    case 'PHOTO_ADDED':
      return `${who} adicionou uma foto`;
    case 'PHOTO_REMOVED':
      return `${who} removeu uma foto`;
    case 'RESPONSE_SET':
      return `${who} atualizou a resposta da síndica`;
    case 'CANCELLED':
      return `Cancelada por ${who}`;
  }
}

export function ComplaintAdminDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [d, setD] = useState<AdminComplaintDetail | null>(null);
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const [selection, setSelection] = useState<CatalogSelection>({
    typeId: '',
    subtypeId: '',
    locationId: '',
  });
  const [detailText, setDetailText] = useState('');
  const [description, setDescription] = useState('');
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [note, setNote] = useState('');
  const [response, setResponse] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    adminDetail(id)
      .then(setD)
      .catch(() => toast.error('Erro ao carregar a reclamação.'));
    getCatalog()
      .then(setCatalog)
      .catch(() => {});
  }, [id]);

  const run = async (fn: () => Promise<AdminComplaintDetail>, ok: string) => {
    setBusy(true);
    try {
      setD(await fn());
      toast.success(ok);
      return true;
    } catch (err) {
      toast.error(backendMessage(err, 'Não foi possível concluir a ação.'));
      return false;
    } finally {
      setBusy(false);
    }
  };

  if (!d || !id) return <main className="mx-auto max-w-3xl p-4">Carregando…</main>;
  const c = d.complaint;
  const canEdit = c.status === 'IN_REVIEW' || c.status === 'READY';
  const canReject = c.status === 'OPEN' || c.status === 'IN_REVIEW' || c.status === 'READY';

  const startEdit = () => {
    setSelection({ typeId: c.typeId, subtypeId: c.subtypeId, locationId: c.locationId });
    setDetailText(c.locationDetail ?? '');
    setDescription(c.description);
    setEditing(true);
  };

  const saveEdit = async () => {
    const ok = await run(
      () =>
        adminEdit(id, {
          ...selection,
          locationDetail: detailText.trim() || null,
          description: description.trim(),
        }),
      'Reclamação atualizada.'
    );
    if (ok) setEditing(false);
  };

  const confirmReject = async () => {
    if (!reason.trim()) {
      toast.error('Informe o motivo da recusa.');
      return;
    }
    const ok = await run(() => adminReject(id, reason.trim()), 'Reclamação recusada.');
    if (ok) setRejecting(false);
  };

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/admin/reclamacoes">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <Card>
        <CardHeader className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-sm text-muted-foreground">#{c.code}</span>
            <StatusBadge status={c.status} />
          </div>
          <CardTitle>
            {c.typeName} › {c.subtypeName} — {c.locationName}
            {c.locationDetail ? ` (${c.locationDetail})` : ''}
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            {d.authorName ?? '—'} · {d.unitCode ?? '—'}
          </p>
        </CardHeader>
        <CardContent className="space-y-4">
          {!editing && <p className="whitespace-pre-wrap">{c.description}</p>}
          {editing && catalog && (
            <div className="space-y-3">
              <CatalogSelects
                catalog={catalog}
                value={selection}
                onChange={setSelection}
                fallback={{
                  typeId: c.typeId,
                  typeName: c.typeName,
                  subtypeId: c.subtypeId,
                  subtypeName: c.subtypeName,
                  locationId: c.locationId,
                  locationName: c.locationName,
                }}
              />
              <Label htmlFor="adm-detail">Complemento do local</Label>
              <Input
                id="adm-detail"
                maxLength={200}
                value={detailText}
                onChange={(e) => setDetailText(e.target.value)}
              />
              <Label htmlFor="adm-desc">Descrição</Label>
              <textarea
                id="adm-desc"
                rows={5}
                maxLength={2000}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              />
              <div className="flex gap-2">
                <Button className="min-h-[44px]" disabled={busy} onClick={saveEdit}>
                  Salvar edição
                </Button>
                <Button
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setEditing(false)}
                >
                  Descartar
                </Button>
              </div>
            </div>
          )}

          <ComplaintPhotos complaintId={c.id} photos={c.photos} />

          <div className="flex flex-wrap gap-2">
            {c.status === 'OPEN' && (
              <Button
                className="min-h-[44px]"
                disabled={busy}
                onClick={() => run(() => adminAction(id, 'start-review'), 'Análise iniciada.')}
              >
                Iniciar análise
              </Button>
            )}
            {c.status === 'IN_REVIEW' && (
              <Button
                className="min-h-[44px]"
                disabled={busy}
                onClick={() => run(() => adminAction(id, 'ready'), 'Pronta para encaminhar.')}
              >
                Marcar como pronta
              </Button>
            )}
            {c.status === 'READY' && (
              <Button
                variant="outline"
                className="min-h-[44px]"
                disabled={busy}
                onClick={() => run(() => adminAction(id, 'reopen-review'), 'Voltou para análise.')}
              >
                Voltar para análise
              </Button>
            )}
            {canEdit && !editing && (
              <Button variant="outline" className="min-h-[44px]" onClick={startEdit}>
                Editar
              </Button>
            )}
            {canReject && !rejecting && (
              <Button
                variant="destructive"
                className="min-h-[44px]"
                onClick={() => setRejecting(true)}
              >
                Recusar
              </Button>
            )}
          </div>

          {rejecting && (
            <div className="space-y-2 rounded-md border p-3">
              <Label htmlFor="adm-reason">Motivo da recusa</Label>
              <Input
                id="adm-reason"
                maxLength={500}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
              <div className="flex gap-2">
                <Button
                  variant="destructive"
                  className="min-h-[44px]"
                  disabled={busy}
                  onClick={confirmReject}
                >
                  Confirmar recusa
                </Button>
                <Button
                  variant="outline"
                  className="min-h-[44px]"
                  onClick={() => setRejecting(false)}
                >
                  Voltar
                </Button>
              </div>
            </div>
          )}

          {c.status === 'FORWARDED' && (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="adm-due">Prazo informado pela síndica</Label>
                <Input
                  id="adm-due"
                  type="date"
                  value={dueDate || c.dueDate || ''}
                  onChange={(e) => setDueDate(e.target.value)}
                />
                <Button
                  className="min-h-[44px]"
                  disabled={busy || !dueDate}
                  onClick={() => run(() => adminSetDueDate(id, dueDate), 'Prazo salvo.')}
                >
                  Salvar prazo
                </Button>
              </div>
              <div className="space-y-2">
                <Label htmlFor="adm-note">Observação da resolução (opcional)</Label>
                <Input
                  id="adm-note"
                  maxLength={500}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                />
                <Button
                  variant="outline"
                  className="min-h-[44px]"
                  disabled={busy}
                  onClick={() => run(() => adminResolve(id, note.trim()), 'Reclamação resolvida.')}
                >
                  Marcar como resolvida
                </Button>
              </div>
            </div>
          )}

          {(c.status === 'FORWARDED' || c.status === 'RESOLVED') && (
            <div className="space-y-2">
              <Label htmlFor="adm-response">Resposta da síndica (visível aos moradores)</Label>
              <textarea
                id="adm-response"
                rows={4}
                maxLength={2000}
                value={response ?? c.syndicResponse ?? ''}
                onChange={(e) => setResponse(e.target.value)}
                className="flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              />
              <Button
                className="min-h-[44px]"
                disabled={busy || response === null}
                onClick={async () => {
                  const ok = await run(
                    () => adminSetSyndicResponse(id, (response ?? '').trim()),
                    'Resposta salva.'
                  );
                  if (ok) setResponse(null);
                }}
              >
                Salvar resposta
              </Button>
            </div>
          )}

          {c.dueDate && <p className="font-medium">Prazo atual: {formatDateBR(c.dueDate)}</p>}
          {c.rejectionReason && <p className="text-red-800">Motivo: {c.rejectionReason}</p>}
          {c.resolutionNote && <p>Resolução: {c.resolutionNote}</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Histórico</CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="space-y-1 text-sm">
            {d.events.map((e, i) => (
              <li key={i}>
                <span className="text-muted-foreground">{formatInstantBR(e.createdAt)} — </span>
                {describeEvent(e)}
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
    </main>
  );
}
