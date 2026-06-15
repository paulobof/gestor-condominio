import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  approveClaim,
  getClaimProofBlob,
  listClaims,
  rejectClaim,
  type OwnershipClaim,
} from '../api/ownershipClaimsApi';
import { Check, X, FileText } from 'lucide-react';

/** Painel do síndico: pedidos de posse de unidade (proprietário) pendentes. */
export function OwnershipClaimsPage() {
  const [items, setItems] = useState<OwnershipClaim[]>([]);
  const [loading, setLoading] = useState(true);
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const reload = async () => {
    setLoading(true);
    try {
      const data = await listClaims();
      setItems(data.content);
    } catch {
      toast.error('Erro ao carregar pedidos de unidade.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const handleApprove = async (id: string) => {
    try {
      await approveClaim(id);
      toast.success('Pedido aprovado.');
      reload();
    } catch {
      toast.error('Erro ao aprovar.');
    }
  };

  const handleReject = async (id: string) => {
    if (!rejectReason.trim()) return;
    try {
      await rejectClaim(id, rejectReason);
      toast.success('Pedido rejeitado.');
      setRejecting(null);
      setRejectReason('');
      reload();
    } catch {
      toast.error('Erro ao rejeitar.');
    }
  };

  const handleViewProof = async (id: string) => {
    try {
      const blob = await getClaimProofBlob(id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      toast.error('Erro ao abrir o comprovante.');
    }
  };

  return (
    <main className="container py-8 space-y-6">
      <h1 className="text-2xl font-heading font-semibold">Pedidos de unidade</h1>
      {loading && <p className="text-muted-foreground">Carregando...</p>}
      {!loading && items.length === 0 && (
        <p className="text-muted-foreground">Nenhum pedido de unidade pendente.</p>
      )}
      <div className="grid gap-4">
        {items.map((it) => (
          <Card key={it.id}>
            <CardHeader>
              <CardTitle className="text-lg">
                {it.userName ?? 'Solicitante'} — Unidade {it.unitCode ?? '—'}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="text-sm">
                <strong>Comprovante:</strong> {it.proofFilename ?? '—'}
              </div>
              <div className="flex gap-2 flex-wrap">
                <Button variant="outline" onClick={() => handleViewProof(it.id)}>
                  <FileText className="w-4 h-4 mr-2" />
                  Ver comprovante
                </Button>
                <Button onClick={() => handleApprove(it.id)}>
                  <Check className="w-4 h-4 mr-2" />
                  Aprovar
                </Button>
                <Button variant="destructive" onClick={() => setRejecting(it.id)}>
                  <X className="w-4 h-4 mr-2" />
                  Rejeitar
                </Button>
              </div>
              {rejecting === it.id && (
                <div className="space-y-2">
                  <Label htmlFor={`reject-reason-${it.id}`}>Motivo da rejeição</Label>
                  <Input
                    id={`reject-reason-${it.id}`}
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Ex.: comprovante ilegível"
                  />
                  <div className="flex gap-2">
                    <Button onClick={() => handleReject(it.id)} disabled={!rejectReason.trim()}>
                      Confirmar rejeição
                    </Button>
                    <Button
                      variant="outline"
                      onClick={() => {
                        setRejecting(null);
                        setRejectReason('');
                      }}
                    >
                      Cancelar
                    </Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </main>
  );
}
