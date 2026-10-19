import { useEffect, useState } from 'react';
import { Page, PageHeader } from '@/components/layout/Page';
import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PasswordInput } from '@/components/ui/password-input';
import { PhoneInput } from '@/components/ui/phone-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { passwordSchema } from '@/features/auth/passwordPolicy';
import { useAuth } from '@/features/auth/useAuth';
import { changeOwnPassword, getOwnProfile, updateOwnProfile } from '../api/profileApi';

const profileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Informe seu nome completo')
    .max(180, 'Máximo de 180 caracteres'),
  greetingName: z.string().trim().max(60, 'Máximo de 60 caracteres'),
  phone: z.string().max(20, 'Telefone inválido'),
});

type ProfileValues = z.infer<typeof profileSchema>;

const passwordFormSchema = z
  .object({
    currentPassword: z.string().min(1, 'Informe sua senha atual'),
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Confirme a nova senha'),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As senhas não coincidem',
  });

type PasswordValues = z.infer<typeof passwordFormSchema>;

interface ApiErrorBody {
  code?: string;
  message?: string;
}

function apiError(err: unknown): ApiErrorBody {
  return (err as { response?: { data?: ApiErrorBody } } | null)?.response?.data ?? {};
}

const emptyToNull = (v: string) => (v.trim() === '' ? null : v.trim());

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p role="alert" className="text-sm text-destructive">
      {message}
    </p>
  );
}

/** "Meu cadastro": o usuário logado edita os próprios dados e troca a própria senha. */
export function MyProfilePage() {
  const { refreshUser } = useAuth();
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState(false);

  const profileForm = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: '', greetingName: '', phone: '' },
  });
  const passwordForm = useForm<PasswordValues>({
    resolver: zodResolver(passwordFormSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const { reset: resetProfile } = profileForm;

  useEffect(() => {
    let cancelled = false;
    getOwnProfile()
      .then((p) => {
        if (cancelled) return;
        setEmail(p.email ?? '');
        resetProfile({
          fullName: p.fullName,
          greetingName: p.greetingName ?? '',
          phone: p.phone ?? '',
        });
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [resetProfile]);

  const onSaveProfile = async (data: ProfileValues) => {
    try {
      const saved = await updateOwnProfile({
        fullName: data.fullName.trim(),
        greetingName: emptyToNull(data.greetingName),
        phone: emptyToNull(data.phone),
      });
      resetProfile({
        fullName: saved.fullName,
        greetingName: saved.greetingName ?? '',
        phone: saved.phone ?? '',
      });
      toast.success('Dados atualizados.');
      await refreshUser?.().catch(() => undefined);
    } catch (err) {
      toast.error(apiError(err).message ?? 'Não foi possível salvar seus dados.');
    }
  };

  const onChangePassword = async (data: PasswordValues) => {
    try {
      await changeOwnPassword(data.currentPassword, data.newPassword);
      passwordForm.reset();
      toast.success('Senha alterada.');
    } catch (err) {
      const { code, message } = apiError(err);
      if (code === 'INVALID_PASSWORD') {
        passwordForm.setError('currentPassword', { message: 'Senha atual incorreta.' });
      } else if (code === 'PASSWORD_REUSED') {
        passwordForm.setError('newPassword', {
          message: message ?? 'Você já usou essa senha recentemente.',
        });
      } else {
        toast.error(message ?? 'Não foi possível trocar a senha.');
      }
    }
  };

  if (loading) {
    return (
      <Page variant="form" className="flex min-h-[50dvh] items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" aria-label="Carregando" />
      </Page>
    );
  }

  if (loadFailed) {
    return (
      <Page variant="form">
        <p role="alert" className="text-sm text-destructive">
          Não foi possível carregar seu cadastro.
        </p>
      </Page>
    );
  }

  const pErrors = profileForm.formState.errors;
  const pwErrors = passwordForm.formState.errors;

  return (
    <Page variant="form">
      <PageHeader title="Meu cadastro" description="Seus dados de acesso ao aplicativo." />

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
          <CardDescription>Nome e contato da sua conta.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={profileForm.handleSubmit(onSaveProfile)} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="fullName">Nome completo</Label>
              <Input
                id="fullName"
                autoComplete="name"
                maxLength={180}
                className="min-h-11"
                {...profileForm.register('fullName')}
                aria-invalid={!!pErrors.fullName}
              />
              <FieldError message={pErrors.fullName?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="greetingName">Como quer ser chamado</Label>
              <Input
                id="greetingName"
                autoComplete="nickname"
                maxLength={60}
                className="min-h-11"
                {...profileForm.register('greetingName')}
                aria-invalid={!!pErrors.greetingName}
                aria-describedby="greetingName-hint"
              />
              <p id="greetingName-hint" className="text-xs text-muted-foreground">
                Opcional. Usado na saudação da tela inicial.
              </p>
              <FieldError message={pErrors.greetingName?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="phone">Telefone</Label>
              <Controller
                control={profileForm.control}
                name="phone"
                render={({ field }) => (
                  <PhoneInput
                    id="phone"
                    value={field.value}
                    onChange={field.onChange}
                    invalid={!!pErrors.phone}
                  />
                )}
              />
              <p className="text-xs text-muted-foreground">
                Opcional e privado: usado só para WhatsApp e recuperação de senha. Não aparece para
                outros moradores.
              </p>
              <FieldError message={pErrors.phone?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="email">E-mail</Label>
              <Input
                id="email"
                type="email"
                value={email}
                readOnly
                className="min-h-11 bg-muted"
                aria-describedby="email-hint"
              />
              <p id="email-hint" className="text-xs text-muted-foreground">
                Para trocar o e-mail, fale com o administrador.
              </p>
            </div>

            <Button
              type="submit"
              className="min-h-11 w-full sm:w-auto"
              disabled={profileForm.formState.isSubmitting}
            >
              {profileForm.formState.isSubmitting ? 'Salvando...' : 'Salvar dados'}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Trocar senha</CardTitle>
          <CardDescription>Para sua segurança, confirme a senha atual.</CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={passwordForm.handleSubmit(onChangePassword)}
            className="space-y-4"
            noValidate
          >
            <div className="space-y-2">
              <Label htmlFor="currentPassword">Senha atual</Label>
              <PasswordInput
                id="currentPassword"
                autoComplete="current-password"
                {...passwordForm.register('currentPassword')}
                aria-invalid={!!pwErrors.currentPassword}
              />
              <FieldError message={pwErrors.currentPassword?.message} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="newPassword">Nova senha</Label>
              <PasswordInput
                id="newPassword"
                autoComplete="new-password"
                {...passwordForm.register('newPassword')}
                aria-invalid={!!pwErrors.newPassword}
              />
              <FieldError message={pwErrors.newPassword?.message} />
              <PasswordChecklist value={passwordForm.watch('newPassword') ?? ''} />
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirme a nova senha</Label>
              <PasswordInput
                id="confirmPassword"
                autoComplete="new-password"
                {...passwordForm.register('confirmPassword')}
                aria-invalid={!!pwErrors.confirmPassword}
              />
              <FieldError message={pwErrors.confirmPassword?.message} />
            </div>

            <Button
              type="submit"
              className="min-h-11 w-full sm:w-auto"
              disabled={passwordForm.formState.isSubmitting}
            >
              {passwordForm.formState.isSubmitting ? 'Trocando...' : 'Trocar senha'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </Page>
  );
}
