import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { UnitSelector } from '@/components/UnitSelector';
import { ProofUploader } from '@/components/ProofUploader';
import { ConsentBox } from '@/features/consent/ConsentBox';
import { registerMaster } from '@/features/consent/api/consentApi';

export function RegisterMasterPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('NOT_INFORMED');
  const [birthDate, setBirthDate] = useState('');
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [hasMaster, setHasMaster] = useState<boolean | null>(null);
  const [password, setPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [proof, setProof] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const canSubmit =
    !!fullName &&
    !!greetingName &&
    !!email &&
    !!phone &&
    !!unitCode &&
    hasMaster === false &&
    password.length >= 8 &&
    !!consentVersion &&
    !!proof;

  const submit = async () => {
    if (!canSubmit || !proof) return;
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('fullName', fullName);
      fd.append('greetingName', greetingName);
      fd.append('email', email);
      fd.append('phone', phone);
      fd.append('gender', gender);
      if (birthDate) fd.append('birthDate', birthDate);
      fd.append('unitCode', unitCode!);
      fd.append('password', password);
      fd.append('consentVersion', consentVersion!);
      fd.append('whatsappOptIn', whatsappOptIn ? 'true' : 'false');
      fd.append('proof', proof);
      await registerMaster(fd);
      toast.success('Cadastro enviado! Aguarde aprovação do síndico.');
      navigate('/pending-approval', { replace: true });
    } catch (e) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erro ao cadastrar. Tente novamente.';
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl my-8">
        <CardHeader>
          <CardTitle>Cadastro de morador (master)</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <section>
            <h3 className="font-semibold mb-3">1. Identificação da unidade</h3>
            <UnitSelector
              value={unitCode}
              onChange={(c, h) => {
                setUnitCode(c);
                setHasMaster(h);
              }}
            />
          </section>
          <section className="space-y-3">
            <h3 className="font-semibold">2. Seus dados</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label>Nome completo</Label>
                <Input value={fullName} onChange={(e) => setFullName(e.target.value)} />
              </div>
              <div>
                <Label>Como prefere ser chamado</Label>
                <Input value={greetingName} onChange={(e) => setGreetingName(e.target.value)} />
              </div>
              <div>
                <Label>E-mail</Label>
                <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
              <div>
                <Label>Telefone (WhatsApp)</Label>
                <Input
                  type="tel"
                  placeholder="+5511..."
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
              <div>
                <Label>Data de nascimento</Label>
                <Input
                  type="date"
                  value={birthDate}
                  onChange={(e) => setBirthDate(e.target.value)}
                />
              </div>
              <div>
                <Label>Gênero (opcional)</Label>
                <select
                  className="w-full rounded-md border border-input bg-background px-3 py-2"
                  value={gender}
                  onChange={(e) => setGender(e.target.value)}
                >
                  <option value="NOT_INFORMED">Prefiro não informar</option>
                  <option value="MALE">Masculino</option>
                  <option value="FEMALE">Feminino</option>
                  <option value="OTHER">Outro</option>
                </select>
              </div>
              <div className="col-span-2">
                <Label>Senha (mínimo 8 caracteres)</Label>
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={whatsappOptIn}
                onChange={(e) => setWhatsappOptIn(e.target.checked)}
              />
              Aceito receber comunicações operacionais via WhatsApp neste número.
            </label>
          </section>
          <section>
            <h3 className="font-semibold mb-3">3. Comprovante de residência</h3>
            <ProofUploader value={proof} onChange={setProof} />
          </section>
          <section>
            <h3 className="font-semibold mb-3">4. Termo de privacidade</h3>
            <ConsentBox
              accepted={!!consentVersion}
              onChange={(a, v) => setConsentVersion(a ? v : null)}
            />
          </section>
          <Button onClick={submit} disabled={!canSubmit || submitting} className="w-full">
            {submitting ? 'Enviando...' : 'Enviar cadastro'}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
