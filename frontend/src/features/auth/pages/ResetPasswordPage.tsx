import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { Home } from 'lucide-react';
import axios from 'axios';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { consumePasswordReset } from '@/features/auth/api/authApi';
import { PasswordInput } from '@/components/ui/password-input';
import { PasswordChecklist } from '@/components/auth/PasswordChecklist';
import { passwordSchema } from '@/features/auth/passwordPolicy';

const schema = z
  .object({
    newPassword: passwordSchema,
    confirmPassword: z.string().min(1, 'Confirme a nova senha'),
  })
  .refine((d) => d.newPassword === d.confirmPassword, {
    path: ['confirmPassword'],
    message: 'As senhas não coincidem',
  });

type FormValues = z.infer<typeof schema>;

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  useEffect(() => {
    if (!token) toast.error('Link inválido. Solicite um novo link.');
  }, [token]);

  const onSubmit = async (data: FormValues) => {
    setSubmitting(true);
    try {
      await consumePasswordReset(token, data.newPassword);
      toast.success('Senha alterada com sucesso. Faça login com a nova senha.');
      navigate('/login', { replace: true });
    } catch (e) {
      const code =
        axios.isAxiosError(e) && (e.response?.data as { code?: string } | undefined)?.code;
      if (code === 'INVALID_OR_EXPIRED_TOKEN') {
        toast.error('Link inválido ou expirado. Solicite um novo.');
      } else if (code === 'PASSWORD_REUSED') {
        setError('newPassword', {
          message: 'Você já usou esta senha recentemente. Escolha outra.',
        });
      } else {
        toast.error('Não foi possível alterar a senha. Tente novamente.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="min-h-dvh flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-md">
        <CardHeader className="space-y-3 text-center">
          <div className="flex items-center justify-center gap-2">
            <Home className="text-primary" aria-hidden="true" />
            <span className="font-heading font-semibold text-lg">HELBOR TRILOGY HOME</span>
          </div>
          <CardTitle>Nova senha</CardTitle>
          <CardDescription>
            Defina sua nova senha. O link expira 30 minutos após o envio.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="newPassword">Nova senha</Label>
              <PasswordInput
                id="newPassword"
                autoComplete="new-password"
                {...register('newPassword')}
                aria-invalid={!!errors.newPassword}
              />
              {errors.newPassword && (
                <p role="alert" className="text-sm text-destructive">
                  {errors.newPassword.message}
                </p>
              )}
              <PasswordChecklist value={watch('newPassword') ?? ''} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirme a nova senha</Label>
              <PasswordInput
                id="confirmPassword"
                autoComplete="new-password"
                {...register('confirmPassword')}
                aria-invalid={!!errors.confirmPassword}
              />
              {errors.confirmPassword && (
                <p role="alert" className="text-sm text-destructive">
                  {errors.confirmPassword.message}
                </p>
              )}
            </div>
            <Button type="submit" disabled={submitting || !token} className="w-full">
              {submitting ? 'Alterando...' : 'Alterar senha'}
            </Button>
            <div className="text-center text-sm">
              <Link to="/login" className="text-muted-foreground hover:text-foreground underline">
                Voltar para o login
              </Link>
            </div>
          </form>
        </CardContent>
      </Card>
    </main>
  );
}
