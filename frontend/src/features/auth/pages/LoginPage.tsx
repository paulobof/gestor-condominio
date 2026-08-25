import { Navigate, useNavigate } from 'react-router-dom';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import { LoginForm } from '@/features/auth/LoginForm';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';

export function LoginPage() {
  const { status } = useAuth();
  const navigate = useNavigate();

  if (status === 'authenticated') return <Navigate to="/" replace />;

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
            <LoginForm onSuccess={() => navigate('/', { replace: true })} />
          </CardContent>
        </Card>
      </div>
      <footer className="pt-4">
        <DeveloperCredit />
      </footer>
    </main>
  );
}
