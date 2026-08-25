import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { PasswordInput } from '@/components/ui/password-input';
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';

const schema = z.object({
  email: z.string().min(1, 'Informe seu e-mail').email('E-mail inválido'),
  password: z.string().min(1, 'Informe sua senha'),
});

type FormValues = z.infer<typeof schema>;

/**
 * Formulário de entrada, sem casca. Usado pela página `/login` (link direto, sessão expirada) e
 * pelo popup que aparece quando o visitante esbarra em algo que exige conta — mesma lógica nos
 * dois lugares, para não haver duas verdades sobre como se entra no app.
 */
export function LoginForm({ onSuccess }: { onSuccess: () => void }) {
  const { login } = useAuth();
  const { enabled } = useFeatures();
  const ownershipEnabled = enabled('unitownership');
  const [submitting, setSubmitting] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormValues) => {
    setSubmitting(true);
    try {
      await login(data.email, data.password);
      onSuccess();
    } catch {
      toast.error('E-mail ou senha inválidos, ou cadastro não ativo.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
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
        {ownershipEnabled && (
          <Link
            to="/register-owner"
            className="block text-muted-foreground hover:text-foreground underline"
          >
            Sou proprietário (não moro no condomínio)
          </Link>
        )}
        <Link to="/sobre" className="block text-muted-foreground hover:text-foreground underline">
          O que é este aplicativo?
        </Link>
      </div>
    </form>
  );
}
