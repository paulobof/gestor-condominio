import { Home, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';

export default function App() {
  const { user, logout } = useAuth();

  return (
    <main className="min-h-dvh bg-background text-foreground">
      <header className="border-b border-border">
        <div className="container flex items-center gap-3 py-6">
          <Home className="text-primary" aria-hidden="true" />
          <h1 className="text-2xl md:text-3xl font-heading font-semibold tracking-tight flex-1">
            HELBOR TRILOGY HOME
          </h1>
          {user && (
            <div className="flex items-center gap-3 text-sm">
              <span className="text-muted-foreground">
                Olá,{' '}
                <strong className="text-foreground">{user.greetingName || user.fullName}</strong>
              </span>
              <Button variant="ghost" size="sm" onClick={logout} aria-label="Sair">
                <LogOut className="w-4 h-4 mr-2" />
                Sair
              </Button>
            </div>
          )}
        </div>
      </header>
      <section className="container py-10 space-y-4">
        <p className="text-muted-foreground max-w-prose">
          Bem-vindo ao portal de gestão do condomínio. Mais funcionalidades em breve.
        </p>
        {user && (
          <div className="text-sm text-muted-foreground">
            <p>Roles: {user.roles.join(', ') || '—'}</p>
            <p>Permissões: {user.authorities.length}</p>
          </div>
        )}
      </section>
    </main>
  );
}
