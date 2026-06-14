import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { LogOut, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';
import { Sidebar } from './Sidebar';

/**
 * Casca do app autenticado: top bar de marca (logo HELBOR + tema + sair) e menu LATERAL — fixo no
 * desktop, drawer no mobile (hambúrguer). Header no preto do logo (--brand-ink) para identidade.
 */
export function Shell() {
  const { user, logout } = useAuth();
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();

  // Fecha o drawer ao trocar de rota e com Esc.
  useEffect(() => setNavOpen(false), [location.pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setNavOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <header
        className="sticky top-0 z-30 border-b border-white/10 text-white"
        style={{ backgroundColor: 'hsl(var(--brand-ink))' }}
      >
        <div className="flex h-14 items-center gap-2 px-3 sm:px-4">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setNavOpen(true)}
            aria-label="Abrir menu"
            className="text-white hover:bg-white/10 hover:text-white lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </Button>

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

          <div className="flex items-center gap-1 text-sm">
            <ThemeToggle className="text-white hover:bg-white/10 hover:text-white" />
            {user && (
              <>
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
              </>
            )}
          </div>
        </div>
      </header>

      <div className="flex">
        <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
        <main className="min-w-0 flex-1">
          <Outlet />
          <footer className="border-t border-border px-4 py-4">
            <DeveloperCredit />
          </footer>
        </main>
      </div>
    </div>
  );
}
