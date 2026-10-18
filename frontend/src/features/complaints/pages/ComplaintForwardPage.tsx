import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Copy } from 'lucide-react';
import { Button } from '@/components/ui/button';
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

export function ComplaintForwardPage() {
  const [ready, setReady] = useState<Complaint[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [text, setText] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [batches, setBatches] = useState<ComplaintBatch[]>([]);

  const load = useCallback(() => {
    adminQueue(['READY'], 0)
      .then((p) => {
        const list = p.content.map((r) => r.complaint);
        setReady(list);
        setSelected(new Set(list.map((c) => c.id)));
      })
      .catch(() => toast.error('Erro ao carregar os chamados prontos.'));
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
      const batch = await createBatch(ids);
      toast.success(`${batch.complaintCount} chamado(s) encaminhado(s).`);
    } catch (err) {
      toast.error(backendMessage(err, 'Erro ao confirmar o envio.'));
    } finally {
      setBusy(false);
      load();
    }
  };

  return (
    <main className="mx-auto max-w-3xl space-y-4 p-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/admin/chamados">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>
      <h1 className="text-2xl font-semibold">Encaminhar à síndica</h1>

      <Card>
        <CardHeader>
          <CardTitle className="text-lg">Prontos para encaminhar ({ready.length})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {ready.length === 0 && <p className="text-muted-foreground">Nenhum chamado pronto.</p>}
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
                  Você já enviou este texto à síndica? Os {ids.length} chamado(s) vão aparecer no
                  mural como encaminhados.
                </p>
                <div className="flex gap-2">
                  <Button className="min-h-[44px]" disabled={busy} onClick={confirm}>
                    Sim, marcar como encaminhados
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
                    {formatInstantBR(b.sentAt)} — {b.complaintCount} chamado(s) —{' '}
                    {b.sentByName ?? '—'}
                  </summary>
                  <pre className="whitespace-pre-wrap text-sm">{b.renderedText}</pre>
                </details>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </main>
  );
}
