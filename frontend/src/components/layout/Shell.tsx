import { Link, Outlet } from 'react-router-dom';
import { Home, LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { BottomNav } from './BottomNav';

/**
 * Casca do app autenticado: top bar (título + usuário/sair), conteúdo das rotas via Outlet e a
 * bottom-nav fixa. Dá a sensação de aplicativo (PWA instalável).
 */
export function Shell() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <header className="sticky top-0 z-30 border-b border-border bg-background/90 backdrop-blur">
        <div className="container flex items-center gap-3 py-3">
          <Link to="/" className="flex items-center gap-2 font-heading font-semibold">
            <Home className="text-primary" aria-hidden="true" />
            <span className="text-base sm:text-lg">HELBOR TRILOGY HOME</span>
          </Link>
          <div className="flex-1" />
          {user && (
            <div className="flex items-center gap-2 text-sm">
              <span className="hidden text-muted-foreground sm:inline">
                {user.greetingName || user.fullName}
              </span>
              <Button variant="ghost" size="sm" onClick={logout} aria-label="Sair">
                <LogOut className="h-4 w-4" />
                <span className="ml-2 hidden sm:inline">Sair</span>
              </Button>
            </div>
          )}
        </div>
      </header>

      {/* pb-24 garante que o conteúdo não fique sob a bottom-nav */}
      <div className="pb-24">
        <Outlet />
      </div>

      <BottomNav />
    </div>
  );
}
