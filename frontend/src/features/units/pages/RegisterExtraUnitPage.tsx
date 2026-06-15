import { useState } from 'react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { UnitSelector } from '@/components/UnitSelector';
import { ProofUploader } from '@/components/ProofUploader';
import { createUnitClaim } from '../api/unitClaimsApi';

/** Usuário logado pede a posse de outra unidade (vai para aprovação do síndico). */
export function RegisterExtraUnitPage() {
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [hasMaster, setHasMaster] = useState<boolean | null>(null);
  const [proof, setProof] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const canSubmit = !!unitCode && hasMaster === false && !!proof && !submitting;

  const submit = async () => {
    if (!unitCode || !proof) return;
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('unitCode', unitCode);
      fd.append('proof', proof);
      await createUnitClaim(fd);
      setDone(true);
      toast.success('Pedido enviado! Aguarde a aprovação do síndico.');
    } catch {
      toast.error('Não foi possível enviar o pedido.');
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <main className="mx-auto max-w-xl p-4">
        <Card>
          <CardHeader>
            <CardTitle>Pedido enviado</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm text-muted-foreground">
              Seu pedido de unidade foi enviado e está pendente de aprovação. Você será avisado
              quando for aprovado.
            </p>
          </CardContent>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-xl p-4 space-y-4">
      <h1 className="text-2xl font-heading font-semibold">Registrar outra unidade</h1>
      <Card>
        <CardHeader>
          <CardTitle>Nova unidade</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <UnitSelector
            value={unitCode}
            onChange={(code, hm) => {
              setUnitCode(code);
              setHasMaster(hm);
            }}
          />
          {hasMaster === true && (
            <p className="text-sm text-destructive">Esta unidade já possui um responsável ativo.</p>
          )}
          <div className="space-y-1">
            <Label>Comprovante de residência</Label>
            <ProofUploader value={proof} onChange={setProof} />
          </div>
          <Button onClick={submit} disabled={!canSubmit} className="min-h-[44px] w-full">
            Enviar pedido
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
