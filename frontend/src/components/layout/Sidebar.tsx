import { NavLink } from 'react-router-dom';
// nota: 'Privacidade' foi removida do menu a pedido; rota /privacidade segue por URL.
import { useAuth } from '@/features/auth/useAuth';
import { DeveloperCredit } from '@/components/branding/DeveloperCredit';
import { brandColor as hsl, visibleSections, type NavItem } from './navigation';

function ItemLink({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const Icon = item.icon;

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
        </>
      )}
    </NavLink>
  );
}

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { user } = useAuth();
  const sections = visibleSections(user);

  return (
    <div className="flex h-full flex-1 flex-col">
      <nav aria-label="Navegação principal" className="flex flex-col gap-1 p-3">
        {sections.map(({ section, title, items }) => (
          <div
            key={section}
            role={title ? 'group' : undefined}
            aria-label={title ?? undefined}
            className={title ? 'mt-3 flex flex-col gap-1 border-t border-border pt-3' : 'contents'}
          >
            {title && (
              <p className="px-3 pb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {title}
              </p>
            )}
            {items.map((item) => (
              <ItemLink key={item.to} item={item} onNavigate={onNavigate} />
            ))}
          </div>
        ))}
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
