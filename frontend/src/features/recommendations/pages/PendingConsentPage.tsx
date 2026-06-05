import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { ArrowLeft, Check, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { listPendingConsent, respondConsent, type Recommendation } from '../api/recommendationsApi';

export function PendingConsentPage() {
  const [items, setItems] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(true);
  const [pendingId, setPendingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listPendingConsent();
      setItems(data);
    } catch {
      toast.error('Erro ao carregar indicações pendentes.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const respond = async (id: string, approved: boolean) => {
    setPendingId(id);
    try {
      await respondConsent(id, approved);
      toast.success(approved ? 'Indicação autorizada.' : 'Indicação recusada.');
      await load();
    } catch {
      toast.error('Erro ao registrar a resposta.');
    } finally {
      setPendingId(null);
    }
  };

  return (
    <main className="mx-auto max-w-3xl p-4 space-y-4">
      <Button asChild variant="ghost" className="min-h-[44px] px-2">
        <Link to="/indicacoes">
          <ArrowLeft aria-hidden="true" /> Voltar
        </Link>
      </Button>

      <h1 className="text-2xl font-heading font-semibold">Indicações pendentes</h1>
      <p className="text-sm text-muted-foreground">
        Indicações em que você foi citado como morador. Autorize para que apareçam publicamente.
      </p>

      {loading ? (
        <p className="text-muted-foreground">Carregando…</p>
      ) : items.length === 0 ? (
        <p className="text-muted-foreground">Nenhuma indicação pendente.</p>
      ) : (
        <ul className="space-y-3">
          {items.map((r) => (
            <li key={r.id}>
              <Card>
                <CardHeader>
                  <CardTitle className="text-base">{r.serviceName}</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  {r.professionalName && (
                    <p className="text-sm text-muted-foreground">{r.professionalName}</p>
                  )}
                  {r.comment && (
                    <p className="whitespace-pre-line text-sm leading-relaxed">{r.comment}</p>
                  )}
                  <div className="flex flex-wrap gap-2">
                    <Button
                      className="min-h-[44px]"
                      onClick={() => respond(r.id, true)}
                      disabled={pendingId === r.id}
                    >
                      <Check aria-hidden="true" /> Autorizar
                    </Button>
                    <Button
                      variant="destructive"
                      className="min-h-[44px]"
                      onClick={() => respond(r.id, false)}
                      disabled={pendingId === r.id}
                    >
                      <X aria-hidden="true" /> Recusar
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
