import { NavLink } from 'react-router-dom';
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  ShieldCheck,
  BookOpen,
  Info,
  UserCog,
} from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';

type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  brand: Brand;
  end?: boolean;
  requires?: string;
}

const ITEMS: NavItem[] = [
  { to: '/', label: 'Início', icon: Home, brand: 'ink', end: true },
  { to: '/avisos', label: 'Avisos', icon: Megaphone, brand: 'red' },
  { to: '/informacoes', label: 'Informações', icon: Info, brand: 'blue' },
  { to: '/faq', label: 'Perguntas Frequentes', icon: BookOpen, brand: 'blue' },
  { to: '/indicacoes', label: 'Indicações', icon: Lightbulb, brand: 'orange' },
  { to: '/classificados', label: 'Classificados', icon: ShoppingBag, brand: 'green' },
  {
    to: '/admin/registrations',
    label: 'Cadastros pendentes',
    icon: ClipboardCheck,
    brand: 'ink',
    requires: 'REGISTRATION_VIEW',
  },
  {
    to: '/admin/acessos',
    label: 'Gerenciar acessos',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
  },
  { to: '/privacidade', label: 'Privacidade', icon: ShieldCheck, brand: 'ink' },
];

const hsl = (b: Brand, a?: number) =>
  a == null ? `hsl(var(--brand-${b}))` : `hsl(var(--brand-${b}) / ${a})`;

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const items = ITEMS.filter(
    (i) => !i.requires || (user?.authorities.includes(i.requires) ?? false)
  );

  return (
    <nav aria-label="Navegação principal" className="flex flex-col gap-1 p-3">
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            onClick={onNavigate}
            className={({ isActive }) =>
              [
                'flex min-h-[44px] items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
                isActive ? 'font-semibold' : 'text-foreground hover:bg-accent',
              ].join(' ')
            }
            style={({ isActive }) =>
              isActive
                ? { backgroundColor: hsl(item.brand, 0.12), color: hsl(item.brand) }
                : undefined
            }
          >
            {({ isActive }) => (
              <>
                <Icon
                  className="h-5 w-5 shrink-0"
                  aria-hidden="true"
                  style={isActive ? { color: hsl(item.brand) } : { color: hsl(item.brand) }}
                />
                <span>{item.label}</span>
              </>
            )}
          </NavLink>
        );
      })}
    </nav>
  );
}

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

/** Menu lateral: fixo no desktop (lg+), drawer deslizante no mobile. */
export function Sidebar({ open, onClose }: SidebarProps) {
  return (
    <>
      {/* Desktop: fixo, sempre visível */}
      <aside className="sticky top-14 hidden h-[calc(100dvh-3.5rem)] w-64 shrink-0 overflow-y-auto border-r border-border bg-card lg:block">
        <SidebarNav />
      </aside>

      {/* Mobile: drawer lateral */}
      {open && (
        <div
          className="fixed inset-0 z-50 lg:hidden"
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
        >
          <button
            type="button"
            aria-label="Fechar menu"
            className="absolute inset-0 bg-black/50"
            onClick={onClose}
          />
          <aside className="absolute left-0 top-0 flex h-full w-72 max-w-[82%] flex-col overflow-y-auto border-r border-border bg-card shadow-xl">
            <div className="px-4 py-3 font-heading text-sm font-semibold text-muted-foreground">
              Menu
            </div>
            <SidebarNav onNavigate={onClose} />
          </aside>
        </div>
      )}
    </>
  );
}
