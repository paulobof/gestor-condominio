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
import { registerOwner } from '@/features/consent/api/consentApi';
import { PasswordInput } from '@/components/ui/password-input';
import { PhoneInput } from '@/components/ui/phone-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { isStrongPassword } from '@/features/auth/passwordPolicy';
import { parsePhone, isValidPhone } from '@/lib/phone';

export function RegisterOwnerPage() {
  const navigate = useNavigate();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [gender, setGender] = useState('NOT_INFORMED');
  const [birthDate, setBirthDate] = useState('');
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [proof, setProof] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const phoneParsed = parsePhone(phone);
  const phoneValid = isValidPhone(phoneParsed.ddi, phoneParsed.national);

  const passwordsMatch = password === confirmPassword;
  const confirmMismatch = confirmPassword.length > 0 && !passwordsMatch;

  // Proprietário NÃO bloqueia por "unidade já tem master" — posse é independente da residência.
  const canSubmit =
    !!fullName &&
    !!greetingName &&
    !!email &&
    phoneValid &&
    !!unitCode &&
    isStrongPassword(password) &&
    passwordsMatch &&
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
      await registerOwner(fd);
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
          <CardTitle>
            <h1 className="text-2xl font-semibold leading-none tracking-tight">
              Cadastro de proprietário
            </h1>
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            Para donos de unidade que <strong>não moram</strong> no condomínio.
          </p>
        </CardHeader>
        <CardContent className="space-y-6">
          <section>
            <h3 className="font-semibold mb-3">1. Unidade que você possui</h3>
            <UnitSelector value={unitCode} onChange={(c) => setUnitCode(c)} />
          </section>
          <section className="space-y-3">
            <h3 className="font-semibold">2. Seus dados</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <Label htmlFor="fullName">Nome completo</Label>
                <Input
                  id="fullName"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="greetingName">Como prefere ser chamado</Label>
                <Input
                  id="greetingName"
                  value={greetingName}
                  onChange={(e) => setGreetingName(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="email">E-mail</Label>
                <Input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
              <div>
                <Label htmlFor="phone">Telefone (WhatsApp)</Label>
                <PhoneInput id="phone" value={phone} onChange={setPhone} />
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
                <Label htmlFor="password">Senha</Label>
                <PasswordInput
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <PasswordChecklist value={password} />
              </div>
              <div className="col-span-2">
                <Label htmlFor="confirmPassword">Confirmar senha</Label>
                <PasswordInput
                  id="confirmPassword"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  aria-invalid={confirmMismatch}
                />
                {confirmMismatch && (
                  <p className="mt-1 text-sm text-destructive">As senhas não conferem.</p>
                )}
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
            <h3 className="font-semibold mb-3">3. Comprovante de propriedade</h3>
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
