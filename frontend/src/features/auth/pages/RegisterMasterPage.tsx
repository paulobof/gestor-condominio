import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { UnitSelector } from '@/components/UnitSelector';
import { ConsentBox } from '@/features/consent/ConsentBox';
import { registerMaster } from '@/features/consent/api/consentApi';
import { PasswordInput } from '@/components/ui/password-input';
import { PhoneInput } from '@/components/ui/phone-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { isStrongPassword } from '@/features/auth/passwordPolicy';
import { useAuth } from '@/features/auth/useAuth';
import { parsePhone, isValidPhone } from '@/lib/phone';

/**
 * Cadastro do morador. Pede só o essencial — sem comprovante de residência.
 *
 * Quem chega primeiro numa unidade sem responsável entra na hora e passa a responder por ela.
 * Quem chega depois vira um pedido que o responsável aprova (ver `RegistrationService`).
 */
export function RegisterMasterPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [fullName, setFullName] = useState('');
  const [greetingName, setGreetingName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [unitCode, setUnitCode] = useState<string | null>(null);
  const [hasMaster, setHasMaster] = useState<boolean | null>(null);
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [consentVersion, setConsentVersion] = useState<string | null>(null);
  const [whatsappOptIn, setWhatsappOptIn] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const phoneParsed = parsePhone(phone);
  const phoneValid = isValidPhone(phoneParsed.ddi, phoneParsed.national);

  const passwordsMatch = password === confirmPassword;
  const confirmMismatch = confirmPassword.length > 0 && !passwordsMatch;

  const canSubmit =
    !!fullName &&
    !!greetingName &&
    !!email &&
    phoneValid &&
    !!unitCode &&
    isStrongPassword(password) &&
    passwordsMatch &&
    !!consentVersion;

  const submit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    try {
      const resp = await registerMaster({
        fullName,
        greetingName,
        email,
        phone,
        unitCode: unitCode!,
        password,
        consentVersion: consentVersion!,
        whatsappOptIn,
      });

      if (resp?.status === 'ACTIVE') {
        // Unidade sem responsável: entra direto, sem passar pela tela de login.
        await login(email, password);
        toast.success('Conta criada! Bem-vindo.');
        navigate('/', { replace: true });
        return;
      }

      toast.success('Pedido enviado! O responsável pela sua unidade vai aprovar.');
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
          <CardTitle>Criar minha conta</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <section>
            <h3 className="font-semibold mb-3">1. Sua unidade</h3>
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
            <h3 className="font-semibold mb-3">3. Termo de privacidade</h3>
            <ConsentBox
              accepted={!!consentVersion}
              onChange={(a, v) => setConsentVersion(a ? v : null)}
            />
          </section>
          {unitCode && hasMaster && (
            <p className="rounded-md bg-muted p-3 text-sm" role="status">
              A unidade <strong>{unitCode}</strong> já tem um responsável cadastrado. Seu acesso
              precisa da aprovação dele — ele será avisado por WhatsApp assim que você enviar.
            </p>
          )}
          <Button onClick={submit} disabled={!canSubmit || submitting} className="w-full">
            {submitting ? 'Enviando...' : 'Criar minha conta'}
          </Button>
        </CardContent>
      </Card>
    </main>
  );
}
