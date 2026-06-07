import { NavLink } from 'react-router-dom';
import { Home, Megaphone, Lightbulb, ShoppingBag } from 'lucide-react';

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  /** end=true ativa só na rota exata (ex.: "/"). */
  end?: boolean;
}

const ITEMS: NavItem[] = [
  { to: '/', label: 'Início', icon: Home, end: true },
  { to: '/avisos', label: 'Avisos', icon: Megaphone },
  { to: '/indicacoes', label: 'Indicações', icon: Lightbulb },
  { to: '/classificados', label: 'Vendas', icon: ShoppingBag },
];

/**
 * Barra de navegação fixa inferior (estilo app), no preto do logo. Item ativo destacado em branco.
 * Respeita a safe-area do iOS.
 */
export function BottomNav() {
  return (
    <nav
      aria-label="Navegação principal"
      className="fixed inset-x-0 bottom-0 z-40 border-t border-white/10 text-white"
      style={{
        backgroundColor: 'hsl(var(--brand-ink))',
        paddingBottom: 'env(safe-area-inset-bottom)',
      }}
    >
      <ul className="mx-auto flex h-16 max-w-3xl items-stretch justify-around">
        {ITEMS.map((item) => {
          const Icon = item.icon;
          return (
            <li key={item.to} className="flex-1">
              <NavLink
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  [
                    'flex h-full min-h-[44px] flex-col items-center justify-center gap-0.5 text-xs font-medium transition-colors',
                    isActive ? 'text-white' : 'text-white/55 hover:text-white/90',
                  ].join(' ')
                }
              >
                {({ isActive }) => (
                  <>
                    <span
                      className={[
                        'flex h-7 w-7 items-center justify-center rounded-xl transition-colors',
                        isActive ? 'bg-white/15' : '',
                      ].join(' ')}
                    >
                      <Icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <span>{item.label}</span>
                  </>
                )}
              </NavLink>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
