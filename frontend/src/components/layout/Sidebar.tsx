import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  BookOpen,
  Info,
  FileText,
  UserCog,
  Users,
  Building2,
  SquareParking,
  ChevronDown,
  ChevronRight,
  Lock,
} from 'lucide-react';
// nota: 'Privacidade' foi removida do menu a pedido; rota /privacidade segue por URL.
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';
import { useLoginPrompt } from '@/features/auth/useLoginPrompt';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';

type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  brand: Brand;
  end?: boolean;
  requires?: string;
  /** Nome da feature flag; item some do menu quando ela está desligada no ambiente. */
  requiresFeature?: string;
  /** Área só para quem tem conta: visitante vê o item com cadeado e cai no login ao clicar. */
  requiresLogin?: boolean;
}

interface NavChild {
  to: string;
  label: string;
  requires?: string;
  /** Sub-item visível porém inativo (feature ainda não entregue). */
  disabled?: boolean;
  badge?: string;
}

interface NavGroup {
  label: string;
  icon: typeof Home;
  brand: Brand;
  children: NavChild[];
}

type NavEntry = ({ kind: 'item' } & NavItem) | ({ kind: 'group' } & NavGroup);

const ENTRIES: NavEntry[] = [
  { kind: 'item', to: '/', label: 'Início', icon: Home, brand: 'ink', end: true },
  {
    kind: 'item',
    to: '/informacoes',
    label: 'Informações',
    icon: Info,
    brand: 'blue',
    requiresFeature: 'generalinfo',
  },
  {
    kind: 'item',
    to: '/indicacoes',
    label: 'Indicações',
    icon: Lightbulb,
    brand: 'orange',
    requiresFeature: 'recommendations',
  },
  {
    kind: 'item',
    to: '/classificados',
    label: 'Classificados',
    icon: ShoppingBag,
    brand: 'green',
    requiresFeature: 'classifieds',
  },
  {
    kind: 'item',
    to: '/avisos',
    label: 'Avisos',
    icon: Megaphone,
    brand: 'red',
    requiresFeature: 'announcements',
    requiresLogin: true,
  },
  {
    kind: 'item',
    to: '/faq',
    label: 'Perguntas Frequentes',
    icon: BookOpen,
    brand: 'blue',
    requiresFeature: 'faq',
    requiresLogin: true,
  },
  {
    kind: 'item',
    to: '/documentos',
    label: 'Documentos',
    icon: FileText,
    brand: 'blue',
    requiresFeature: 'documents',
    requiresLogin: true,
  },
  {
    kind: 'item',
    to: '/vagas/aluguel',
    label: 'Vagas',
    icon: SquareParking,
    brand: 'blue',
    requiresFeature: 'parkingrental',
    requiresLogin: true,
  },
  {
    kind: 'item',
    to: '/admin/registrations',
    label: 'Cadastros pendentes',
    icon: ClipboardCheck,
    brand: 'ink',
    requires: 'REGISTRATION_VIEW',
  },
  {
    kind: 'item',
    to: '/admin/ownership-claims',
    label: 'Pedidos de unidade',
    icon: Building2,
    brand: 'ink',
    requires: 'REGISTRATION_VIEW',
    requiresFeature: 'unitownership',
  },
  {
    kind: 'item',
    to: '/admin/acessos',
    label: 'Gestão de usuários',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
    requiresFeature: 'accessmanagement',
  },
  {
    kind: 'item',
    to: '/minha-unidade/moradores',
    label: 'Moradores',
    icon: Users,
    brand: 'ink',
    requires: 'RESIDENT_MANAGE',
  },
  {
    kind: 'item',
    to: '/minha-unidade/registrar',
    label: 'Registrar unidade',
    icon: Building2,
    brand: 'ink',
    requiresFeature: 'unitownership',
    requiresLogin: true,
  },
];

// 'ink' é a cor neutra dos itens de sistema. Como ícone/texto ele segue
// --foreground (escuro no tema claro, claro no escuro) para manter contraste no
// dark; --brand-ink (preto fixo do wordmark) fica só para fundos (header/banner).
const brandVar = (b: Brand) => (b === 'ink' ? '--foreground' : `--brand-${b}`);
const hsl = (b: Brand, a?: number) =>
  a == null ? `hsl(var(${brandVar(b)}))` : `hsl(var(${brandVar(b)}) / ${a})`;

function ItemLink({
  item,
  locked,
  onNavigate,
}: {
  item: NavItem;
  locked?: boolean;
  onNavigate?: () => void;
}) {
  const Icon = item.icon;
  const { promptLogin } = useLoginPrompt();

  if (locked) {
    return (
      <button
        type="button"
        onClick={() => {
          onNavigate?.();
          promptLogin({
            reason: `${item.label} é para quem tem conta.`,
            destination: item.to,
          });
        }}
        className="flex min-h-[44px] w-full items-center gap-3 rounded-lg px-3 text-sm font-medium text-foreground transition-colors hover:bg-accent"
      >
        <Icon className="h-5 w-5 shrink-0" aria-hidden="true" style={{ color: hsl(item.brand) }} />
        <span className="flex-1 text-left">{item.label}</span>
        <Lock className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-label="Requer login" />
      </button>
    );
  }

  return (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={onNavigate}
      className={({ isActive }) =>
        [
          'flex min-h-[44px] items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors',
          isActive ? 'font-semibold hover:bg-transparent' : 'text-foreground hover:bg-accent',
        ].join(' ')
      }
      style={({ isActive }) =>
        isActive ? { backgroundColor: hsl(item.brand, 0.12), color: hsl(item.brand) } : undefined
      }
    >
      {() => (
        <>
          <Icon
            className="h-5 w-5 shrink-0"
            aria-hidden="true"
            style={{ color: hsl(item.brand) }}
          />
          <span className="flex-1">{item.label}</span>
          {locked && (
            <Lock
              className="h-3.5 w-3.5 shrink-0 text-muted-foreground"
              aria-label="Requer login"
            />
          )}
        </>
      )}
    </NavLink>
  );
}

function GroupNav({ group, onNavigate }: { group: NavGroup; onNavigate?: () => void }) {
  const { pathname } = useLocation();
  const active = group.children.some((c) => pathname.startsWith(c.to));
  const [open, setOpen] = useState(active);
  const Icon = group.icon;
  const Chevron = open ? ChevronDown : ChevronRight;

  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
        className="flex min-h-[44px] w-full items-center gap-3 rounded-lg px-3 text-sm font-medium text-foreground transition-colors hover:bg-accent"
      >
        <Icon className="h-5 w-5 shrink-0" aria-hidden="true" style={{ color: hsl(group.brand) }} />
        <span className="flex-1 text-left">{group.label}</span>
        <Chevron className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      </button>
      {open && (
        <div className="ml-4 flex flex-col gap-1 border-l border-border pl-2">
          {group.children.map((c) =>
            c.disabled ? (
              <span
                key={c.to}
                aria-disabled="true"
                className="flex min-h-[44px] items-center gap-2 rounded-lg px-3 text-sm text-muted-foreground"
              >
                {c.label}
                {c.badge && (
                  <span className="rounded-full bg-muted px-2 py-0.5 text-xs">{c.badge}</span>
                )}
              </span>
            ) : (
              <NavLink
                key={c.to}
                to={c.to}
                onClick={onNavigate}
                className={({ isActive }) =>
                  [
                    'flex min-h-[44px] items-center rounded-lg px-3 text-sm font-medium transition-colors',
                    isActive
                      ? 'font-semibold hover:bg-transparent'
                      : 'text-foreground hover:bg-accent',
                  ].join(' ')
                }
                style={({ isActive }) =>
                  isActive
                    ? { backgroundColor: hsl(group.brand, 0.12), color: hsl(group.brand) }
                    : undefined
                }
              >
                {c.label}
              </NavLink>
            )
          )}
        </div>
      )}
    </div>
  );
}

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const { enabled } = useFeatures();
  const can = (requires?: string) => !requires || (user?.authorities.includes(requires) ?? false);
  const on = (feature?: string) => !feature || enabled(feature);

  return (
    <div className="flex h-full flex-1 flex-col">
      <nav aria-label="Navegação principal" className="flex flex-col gap-1 p-3">
        {ENTRIES.map((entry) => {
          if (entry.kind === 'item') {
            if (!can(entry.requires) || !on(entry.requiresFeature)) return null;
            return (
              <ItemLink
                key={entry.to}
                item={entry}
                locked={!user && entry.requiresLogin}
                onNavigate={onNavigate}
              />
            );
          }
          const children = entry.children.filter((c) => can(c.requires));
          if (children.length === 0) return null;
          return (
            <GroupNav key={entry.label} group={{ ...entry, children }} onNavigate={onNavigate} />
          );
        })}
      </nav>
      <div className="mt-auto border-t border-border p-3">
        <DeveloperCredit />
      </div>
    </div>
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
