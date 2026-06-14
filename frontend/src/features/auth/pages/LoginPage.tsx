import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PasswordInput } from '@/components/ui/password-input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';
import { useState } from 'react';

const schema = z.object({
  email: z.string().min(1, 'Informe seu e-mail').email('E-mail inválido'),
  password: z.string().min(1, 'Informe sua senha'),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { status, login } = useAuth();
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  if (status === 'authenticated') return <Navigate to="/" replace />;

  const onSubmit = async (data: FormValues) => {
    setSubmitting(true);
    try {
      await login(data.email, data.password);
      navigate('/', { replace: true });
    } catch {
      toast.error('E-mail ou senha inválidos, ou cadastro não ativo.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="relative flex min-h-dvh flex-col bg-background p-4">
      <div className="absolute right-3 top-3">
        <ThemeToggle />
      </div>
      <div className="flex flex-1 items-center justify-center">
        <Card className="w-full max-w-md">
          <CardHeader className="space-y-3 text-center">
            <div className="flex justify-center">
              <img
                src="/icon-512.png"
                alt="HELBOR TRILOGY HOME"
                className="h-20 w-20 rounded-2xl shadow-sm"
                width={80}
                height={80}
              />
            </div>
            <CardTitle>Entrar no sistema</CardTitle>
            <CardDescription>Use seu e-mail cadastrado.</CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="email">E-mail</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  {...register('email')}
                  aria-invalid={!!errors.email}
                />
                {errors.email && (
                  <p role="alert" className="text-sm text-destructive">
                    {errors.email.message}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Senha</Label>
                <PasswordInput
                  id="password"
                  autoComplete="current-password"
                  {...register('password')}
                  aria-invalid={!!errors.password}
                />
                {errors.password && (
                  <p role="alert" className="text-sm text-destructive">
                    {errors.password.message}
                  </p>
                )}
              </div>
              <Button type="submit" disabled={submitting} className="w-full">
                {submitting ? 'Entrando...' : 'Entrar'}
              </Button>
              <div className="space-y-1 text-center text-sm">
                <Link
                  to="/forgot-password"
                  className="block text-muted-foreground hover:text-foreground underline"
                >
                  Esqueci minha senha
                </Link>
                <Link
                  to="/register-master"
                  className="block text-muted-foreground hover:text-foreground underline"
                >
                  Primeiro acesso? Cadastre-se
                </Link>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
      <footer className="pt-4">
        <DeveloperCredit />
      </footer>
    </main>
  );
}
