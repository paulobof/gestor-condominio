import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { Home, MessageCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { requestPasswordReset } from '@/features/auth/api/authApi';

const schema = z.object({
  email: z.string().min(1, 'Informe seu e-mail').email('E-mail inválido'),
});

type FormValues = z.infer<typeof schema>;

export function ForgotPasswordPage() {
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (data: FormValues) => {
    setSubmitting(true);
    try {
      await requestPasswordReset(data.email);
    } catch {
      // Por design: silenciamos erros. O backend já garante 202 mesmo para email inexistente,
      // mas se a chamada falhar (rede, 5xx), mostramos a mesma confirmação para não vazar nada.
    } finally {
      setSubmitting(false);
      setSent(true);
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
          <CardTitle>Esqueci minha senha</CardTitle>
          <CardDescription>
            Enviaremos um link via WhatsApp para o telefone cadastrado.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {sent ? (
            <div className="space-y-4 text-center" role="status">
              <MessageCircle className="mx-auto h-10 w-10 text-primary" aria-hidden="true" />
              <p className="text-sm">
                Se a conta existir e tiver telefone verificado, você receberá um link via WhatsApp
                em alguns instantes. O link expira em 30 minutos.
              </p>
              <Button asChild variant="outline" className="w-full">
                <Link to="/login">Voltar para o login</Link>
              </Button>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="email">E-mail cadastrado</Label>
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
              <Button type="submit" disabled={submitting} className="w-full">
                {submitting ? 'Enviando...' : 'Enviar link via WhatsApp'}
              </Button>
              <div className="text-center text-sm">
                <Link to="/login" className="text-muted-foreground hover:text-foreground underline">
                  Voltar para o login
                </Link>
              </div>
            </form>
          )}
        </CardContent>
      </Card>
    </main>
  );
}
