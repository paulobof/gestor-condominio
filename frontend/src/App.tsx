import { Link } from 'react-router-dom';
import {
  Home,
  LogOut,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  BellRing,
  ClipboardCheck,
  ShieldCheck,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';

interface NavItem {
  to: string;
  title: string;
  desc: string;
  icon: typeof Home;
  /** Permissão exigida para ver o item; ausente = qualquer autenticado. */
  requires?: string;
}

const NAV: NavItem[] = [
  {
    to: '/avisos',
    title: 'Mural de avisos',
    desc: 'Comunicados do condomínio.',
    icon: Megaphone,
  },
  {
    to: '/indicacoes',
    title: 'Indicações',
    desc: 'Serviços recomendados por moradores.',
    icon: Lightbulb,
  },
  {
    to: '/classificados',
    title: 'Classificados',
    desc: 'Compra e venda entre moradores.',
    icon: ShoppingBag,
  },
  {
    to: '/indicacoes/pendentes',
    title: 'Consentimentos pendentes',
    desc: 'Indicações em que você foi citado.',
    icon: BellRing,
  },
  {
    to: '/admin/registrations',
    title: 'Cadastros pendentes',
    desc: 'Aprovar ou recusar novos moradores.',
    icon: ClipboardCheck,
    requires: 'REGISTRATION_VIEW',
  },
  {
    to: '/privacidade',
    title: 'Privacidade (LGPD)',
    desc: 'Seus dados, exportação e anonimização.',
    icon: ShieldCheck,
  },
];

export default function App() {
  const { user, logout } = useAuth();
  const can = (item: NavItem) =>
    !item.requires || (user?.authorities.includes(item.requires) ?? false);
  const items = NAV.filter(can);

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

      <section className="container py-8 space-y-6">
        <p className="text-muted-foreground max-w-prose">
          Bem-vindo ao portal de gestão do condomínio. Escolha uma área abaixo.
        </p>

        <nav aria-label="Áreas do portal">
          <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {items.map((item) => {
              const Icon = item.icon;
              return (
                <li key={item.to}>
                  <Link
                    to={item.to}
                    className="block rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <Card className="h-full min-h-[44px] transition-colors hover:bg-accent">
                      <CardHeader className="flex flex-row items-center gap-3 space-y-0">
                        <span className="rounded-md bg-primary/10 p-2 text-primary">
                          <Icon className="h-5 w-5" aria-hidden="true" />
                        </span>
                        <CardTitle className="text-base">{item.title}</CardTitle>
                      </CardHeader>
                      <CardContent>
                        <p className="text-sm text-muted-foreground">{item.desc}</p>
                      </CardContent>
                    </Card>
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
      </section>
    </main>
  );
}
