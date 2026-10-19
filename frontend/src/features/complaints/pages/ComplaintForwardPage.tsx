import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Copy } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Page, PageHeader } from '@/components/layout/Page';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { formatInstantBR } from '@/lib/date';
import {
  adminQueue,
  backendMessage,
  createBatch,
  listBatches,
  previewBatch,
  type Complaint,
  type ComplaintBatch,
} from '../api/complaintsApi';

/** Carrega TODAS as páginas de chamados READY: o lote não pode deixar nenhum de fora. */
async function loadAllReady(): Promise<Complaint[]> {
  const all: Complaint[] = [];
  let pageNo = 0;
  for (;;) {
    const p = await adminQueue(['READY'], pageNo);
    all.push(...p.content.map((r) => r.complaint));
    if (p.number + 1 >= p.totalPages) return all;
    pageNo = p.number + 1;
  }
}

export function ComplaintForwardPage() {
  const [ready, setReady] = useState<Complaint[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [text, setText] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [total, setTotal] = useState(0);
  const [batches, setBatches] = useState<ComplaintBatch[]>([]);

  const load = useCallback(() => {
    loadAllReady()
      .then((list) => {
        setReady(list);
        setTotal(list.length);
        setSelected(new Set(list.map((c) => c.id)));
      })
      .catch(() => toast.error('Erro ao carregar as reclamações prontas.'));
    listBatches()
      .then(setBatches)
      .catch(() => {});
    setText('');
    setConfirming(false);
  }, []);

  useEffect(load, [load]);

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
    setText(''); // seleção mudou: texto antigo não vale mais
    setConfirming(false);
  };

  const ids = ready.filter((c) => selected.has(c.id)).map((c) => c.id);

  const generate = async () => {
    setBusy(true);
    try {
      setText((await previewBatch(ids)).text);
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao gerar o texto.'));
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success('Texto copiado.');
    } catch {
      toast.error('Não foi possível copiar. Selecione o texto e copie manualmente.');
    }
  };

  const confirm = async () => {
    setBusy(true);
    try {
      const batch = await createBatch(ids, text);
      toast.success(`${batch.complaintCount} reclamação(ões) encaminhada(s).`);
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao confirmar o envio.'));
    } finally {
      setBusy(false);
      load();
    }
  };

  return (
    <Page variant="list">
      <Button asChild variant="ghost" className="-ml-2 min-h-[44px] px-2">
        <Link to="/admin/reclamacoes">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>
      <PageHeader title="Encaminhar à síndica" />

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Prontas para encaminhar ({total})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {ready.length === 0 && (
            <p className="text-muted-foreground">Nenhuma reclamação pronta.</p>
          )}
          <ul className="space-y-1">
            {ready.map((c) => (
              <li key={c.id}>
                <label className="flex min-h-[44px] cursor-pointer items-center gap-3 rounded-md px-2 hover:bg-muted/50">
                  <input
                    type="checkbox"
                    className="h-5 w-5"
                    checked={selected.has(c.id)}
                    onChange={() => toggle(c.id)}
                  />
                  <span>
                    #{c.code} {c.typeName} › {c.subtypeName} — {c.locationName}
                  </span>
                </label>
              </li>
            ))}
          </ul>
          <Button className="min-h-[44px]" disabled={busy || ids.length === 0} onClick={generate}>
            Gerar texto
          </Button>
        </CardContent>
      </Card>

      {text && (
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Texto para a síndica</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <textarea
              readOnly
              aria-label="Texto do lote"
              value={text}
              rows={Math.min(20, text.split('\n').length + 1)}
              className="w-full rounded-md border bg-muted/30 p-3 font-mono text-sm"
            />
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" className="min-h-[44px]" onClick={copy}>
                <Copy aria-hidden="true" /> Copiar
              </Button>
              {!confirming && (
                <Button className="min-h-[44px]" onClick={() => setConfirming(true)}>
                  Confirmar envio
                </Button>
              )}
            </div>
            {confirming && (
              <div
                role="alertdialog"
                aria-label="Confirmar envio"
                className="space-y-2 rounded-md border p-3"
              >
                <p>
                  Você já enviou este texto à síndica? As {ids.length} reclamação(ões) vão aparecer
                  no mural como encaminhadas.
                </p>
                <div className="flex gap-2">
                  <Button className="min-h-[44px]" disabled={busy} onClick={confirm}>
                    Sim, marcar como encaminhadas
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
      )}

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Lotes enviados</CardTitle>
        </CardHeader>
        <CardContent>
          {batches.length === 0 && <p className="text-muted-foreground">Nenhum lote ainda.</p>}
          <ul className="space-y-2">
            {batches.map((b) => (
              <li key={b.id}>
                <details className="rounded-md border p-2">
                  <summary className="min-h-[44px] cursor-pointer py-2">
                    {formatInstantBR(b.sentAt)} — {b.complaintCount} reclamação(ões) —{' '}
                    {b.sentByName ?? '—'}
                  </summary>
                  <pre className="whitespace-pre-wrap text-sm">{b.renderedText}</pre>
                </details>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </Page>
  );
}
