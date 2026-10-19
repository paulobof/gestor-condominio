import { useEffect, useRef, useState } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { LogIn, LogOut, Menu } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/features/auth/useAuth';
import { ThemeToggle } from '@/components/theme/ThemeToggle';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';
import { IndependentNotice } from '@/components/branding/IndependentNotice';
import { Sidebar } from './Sidebar';
import { useLoginPrompt } from '@/features/auth/useLoginPrompt';

/**
 * Casca do app autenticado: top bar de marca (logo HELBOR + tema + sair) e menu LATERAL — fixo no
 * desktop, drawer no mobile (hambúrguer). Header segue o tema claro/escuro (fundo do card).
 */
export function Shell() {
  const { user, logout } = useAuth();
  const { promptLogin } = useLoginPrompt();
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();
  const mainRef = useRef<HTMLElement>(null);

  // Ao trocar de rota: fecha o drawer e volta a área central ao topo (ela rola sozinha, não a janela).
  useEffect(() => {
    setNavOpen(false);
    mainRef.current?.scrollTo?.(0, 0);
  }, [location.pathname]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setNavOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  return (
    <>
      {/* Altura da tela, sem rolar a janela: topo fixo e, abaixo, menu e conteúdo com rolagens
          independentes. */}
      <div className="flex h-dvh flex-col overflow-hidden bg-background text-foreground">
        <div className="z-30 shrink-0 bg-background">
          <IndependentNotice />
          <header className="border-b border-border bg-card text-foreground">
            <div className="flex h-14 items-center gap-2 px-3 sm:px-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setNavOpen(true)}
                aria-label="Abrir menu"
                className="text-foreground hover:bg-accent hover:text-foreground lg:hidden"
              >
                <Menu className="h-5 w-5" />
              </Button>

              <Link
                to="/"
                className="flex items-center gap-2.5"
                aria-label="Início — HELBOR TRILOGY HOME"
              >
                <img
                  src="/icon-192.png"
                  alt=""
                  className="h-9 w-9 rounded-lg"
                  width={36}
                  height={36}
                />
                <span className="font-heading text-sm font-semibold leading-tight sm:text-base">
                  HELBOR <span className="text-muted-foreground">TRILOGY HOME</span>
                </span>
              </Link>

              <div className="flex-1" />

              <div className="flex items-center gap-1 text-sm">
                <ThemeToggle className="text-foreground hover:bg-accent hover:text-foreground" />
                {user ? (
                  <>
                    <span className="hidden text-muted-foreground sm:inline">
                      {user.greetingName || user.fullName}
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={logout}
                      aria-label="Sair"
                      className="text-foreground hover:bg-accent hover:text-foreground"
                    >
                      <LogOut className="h-4 w-4" />
                      <span className="ml-2 hidden sm:inline">Sair</span>
                    </Button>
                  </>
                ) : (
                  // Visitante: le o conteudo sem conta; a porta de entrada fica sempre a um clique —
                  // e sempre em popup, sem tirar a pessoa da pagina em que ela estava.
                  <Button
                    variant="ghost"
                    size="sm"
                    aria-label="Entrar"
                    onClick={() => promptLogin()}
                    className="text-foreground hover:bg-accent hover:text-foreground"
                  >
                    <LogIn className="h-4 w-4" />
                    <span className="ml-2">Entrar</span>
                  </Button>
                )}
              </div>
            </div>
          </header>
        </div>

        <div className="flex min-h-0 flex-1">
          <Sidebar open={navOpen} onClose={() => setNavOpen(false)} />
          <main
            ref={mainRef}
            className="flex min-w-0 flex-1 flex-col overflow-y-auto overscroll-contain"
          >
            <div className="flex-1">
              <Outlet />
            </div>
            {/* No desktop o crédito já fica no rodapé do menu lateral. */}
            <footer className="border-t border-border px-4 py-4 lg:hidden">
              <DeveloperCredit />
            </footer>
          </main>
        </div>
      </div>
    </>
  );
}
