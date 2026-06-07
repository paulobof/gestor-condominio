import { Link, Outlet } from 'react-router-dom';
import { LogOut } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { BottomNav } from './BottomNav';

/**
 * Casca do app autenticado: top bar de marca (logo HELBOR + sair), conteúdo via Outlet e a
 * bottom-nav fixa. Header e nav usam o preto do logo (--brand-ink) para dar identidade de app.
 */
export function Shell() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <header
        className="sticky top-0 z-30 border-b border-white/10 text-white"
        style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
      >
        <div className="container flex items-center gap-3 py-2.5">
          <Link
            to="/"
            className="flex items-center gap-2.5"
            aria-label="Início — HELBOR TRILOGY HOME"
          >
            <img src="/icon-192.png" alt="" className="h-9 w-9 rounded-lg" width={36} height={36} />
            <span className="font-heading text-sm font-semibold leading-tight sm:text-base">
              HELBOR <span className="text-white/70">TRILOGY HOME</span>
            </span>
          </Link>
          <div className="flex-1" />
          {user && (
            <div className="flex items-center gap-2 text-sm">
              <span className="hidden text-white/70 sm:inline">
                {user.greetingName || user.fullName}
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={logout}
                aria-label="Sair"
                className="text-white hover:bg-white/10 hover:text-white"
              >
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
