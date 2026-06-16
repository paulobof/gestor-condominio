import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  approveRegistration,
  getProofBlob,
  listPending,
  rejectRegistration,
  type PendingRegistration,
} from '../api/adminApi';
import { Check, X, FileText } from 'lucide-react';
import { formatDateBR } from '@/lib/date';

export function PendingRegistrationsPage() {
  const [items, setItems] = useState<PendingRegistration[]>([]);
  const [loading, setLoading] = useState(true);
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const reload = async () => {
    setLoading(true);
    try {
      const data = await listPending();
      setItems(data.content);
    } catch {
      toast.error('Erro ao carregar pendentes.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    reload();
  }, []);

  const handleApprove = async (id: string) => {
    try {
      await approveRegistration(id);
      toast.success('Cadastro aprovado.');
      reload();
    } catch {
      toast.error('Erro ao aprovar.');
    }
  };

  const handleReject = async (id: string) => {
    if (!rejectReason.trim()) return;
    try {
      await rejectRegistration(id, rejectReason);
      toast.success('Cadastro rejeitado.');
      setRejecting(null);
      setRejectReason('');
      reload();
    } catch {
      toast.error('Erro ao rejeitar.');
    }
  };

  const handleViewProof = async (id: string) => {
    try {
      const blob = await getProofBlob(id);
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener,noreferrer');
      // libera o object URL depois que a aba teve tempo de carregar
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      toast.error('Erro ao abrir o comprovante.');
    }
  };

  return (
    <main className="container py-8 space-y-6">
      <h1 className="text-2xl font-heading font-semibold">Cadastros pendentes</h1>
      {loading && <p className="text-muted-foreground">Carregando...</p>}
      {!loading && items.length === 0 && (
        <p className="text-muted-foreground">Nenhum cadastro pendente.</p>
      )}
      <div className="grid gap-4">
        {items.map((it) => (
          <Card key={it.userId}>
            <CardHeader>
              <CardTitle className="text-lg">
                {it.fullName} — Unidade {it.unitCode}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="text-sm grid grid-cols-2 gap-2">
                <div>
                  <strong>E-mail:</strong> {it.email}
                </div>
                <div>
                  <strong>Telefone:</strong> {it.phone}
                </div>
                <div>
                  <strong>Nascimento:</strong> {formatDateBR(it.birthDate)}
                </div>
                <div>
                  <strong>Comprovante:</strong> {it.residenceProofFilename}
                </div>
              </div>
              <div className="flex gap-2 flex-wrap">
                <Button variant="outline" onClick={() => handleViewProof(it.userId)}>
                  <FileText className="w-4 h-4 mr-2" />
                  Ver comprovante
                </Button>
                <Button onClick={() => handleApprove(it.userId)}>
                  <Check className="w-4 h-4 mr-2" />
                  Aprovar
                </Button>
                <Button variant="destructive" onClick={() => setRejecting(it.userId)}>
                  <X className="w-4 h-4 mr-2" />
                  Rejeitar
                </Button>
              </div>
              {rejecting === it.userId && (
                <div className="space-y-2">
                  <Label>Motivo da rejeição</Label>
                  <Input
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Ex.: comprovante ilegível"
                  />
                  <div className="flex gap-2">
                    <Button onClick={() => handleReject(it.userId)} disabled={!rejectReason.trim()}>
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
