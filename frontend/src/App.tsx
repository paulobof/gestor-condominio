import { Link } from 'react-router-dom';
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  Info,
  UserCog,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';

type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

interface NavItem {
  to: string;
  title: string;
  desc: string;
  icon: typeof Home;
  brand: Brand;
  /** Permissão exigida para ver o item; ausente = qualquer autenticado. */
  requires?: string;
}

const NAV: NavItem[] = [
  {
    to: '/avisos',
    title: 'Mural de avisos',
    desc: 'Comunicados do condomínio.',
    icon: Megaphone,
    brand: 'red',
  },
  {
    to: '/informacoes',
    title: 'Informações',
    desc: 'Informações gerais do condomínio.',
    icon: Info,
    brand: 'blue',
  },
  {
    to: '/indicacoes',
    title: 'Indicações',
    desc: 'Serviços recomendados por moradores.',
    icon: Lightbulb,
    brand: 'orange',
  },
  {
    to: '/classificados',
    title: 'Classificados',
    desc: 'Compra e venda entre moradores.',
    icon: ShoppingBag,
    brand: 'green',
  },
  {
    to: '/admin/registrations',
    title: 'Cadastros pendentes',
    desc: 'Aprovar ou recusar novos moradores.',
    icon: ClipboardCheck,
    brand: 'ink',
    requires: 'REGISTRATION_VIEW',
  },
  {
    to: '/admin/acessos',
    title: 'Gestão de usuários',
    desc: 'Acessos e dados dos usuários.',
    icon: UserCog,
    brand: 'ink',
    requires: 'ROLE_ASSIGN',
  },
];

// 'ink' (itens de sistema) segue --foreground como cor/borda para manter
// contraste no dark; --brand-ink (preto fixo do wordmark) fica só para fundos.
const brandVar = (b: Brand) => (b === 'ink' ? '--foreground' : `--brand-${b}`);
const hsl = (b: Brand, alpha?: number) =>
  alpha == null ? `hsl(var(${brandVar(b)}))` : `hsl(var(${brandVar(b)}) / ${alpha})`;

export default function App() {
  const { user } = useAuth();
  const can = (item: NavItem) =>
    !item.requires || (user?.authorities.includes(item.requires) ?? false);
  const items = NAV.filter(can);

  return (
    <section className="container space-y-6 py-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold">
          Olá, {user?.greetingName || user?.fullName || 'morador'} 👋
        </h1>
        <p className="text-muted-foreground">Escolha uma área do portal.</p>
      </div>

      <nav aria-label="Áreas do portal">
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => {
            const Icon = item.icon;
            return (
              <li key={item.to}>
                <Link
                  to={item.to}
                  className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <Card
                    className="h-full border-l-4 transition-colors hover:bg-accent/40"
                    style={{ borderLeftColor: hsl(item.brand) }}
                  >
                    <CardHeader className="flex flex-row items-center gap-3 space-y-0">
                      <span
                        className="flex h-10 w-10 items-center justify-center rounded-xl"
                        style={{ backgroundColor: hsl(item.brand, 0.12), color: hsl(item.brand) }}
                      >
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
  );
}
