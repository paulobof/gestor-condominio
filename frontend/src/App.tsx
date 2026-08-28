import { type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import {
  Home,
  Megaphone,
  Lightbulb,
  ShoppingBag,
  ClipboardCheck,
  Info,
  BookOpen,
  UserCog,
  Users,
  SquareParking,
  Lock,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';
import { useLoginPrompt } from '@/features/auth/useLoginPrompt';

type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

interface NavItem {
  to: string;
  title: string;
  desc: string;
  icon: typeof Home;
  brand: Brand;
  /** Permissão exigida para ver o item; ausente = qualquer visitante. */
  requires?: string;
  /** Feature flag do módulo; item some quando está desligado no ambiente. */
  feature?: string;
  /** Área só para quem tem conta: visitante vê o card com cadeado e cai no login ao clicar. */
  requiresLogin?: boolean;
}

const NAV: NavItem[] = [
  {
    to: '/informacoes',
    feature: 'generalinfo',
    title: 'Informações',
    desc: 'Informações gerais do condomínio.',
    icon: Info,
    brand: 'blue',
  },
  {
    to: '/indicacoes',
    feature: 'recommendations',
    title: 'Indicações',
    desc: 'Serviços recomendados por moradores.',
    icon: Lightbulb,
    brand: 'orange',
  },
  {
    to: '/classificados',
    feature: 'classifieds',
    title: 'Classificados',
    desc: 'Compra e venda entre moradores.',
    icon: ShoppingBag,
    brand: 'green',
  },
  {
    to: '/avisos',
    feature: 'announcements',
    requiresLogin: true,
    title: 'Mural de avisos',
    desc: 'Comunicados do condomínio.',
    icon: Megaphone,
    brand: 'red',
  },
  {
    to: '/faq',
    feature: 'faq',
    requiresLogin: true,
    title: 'Perguntas Frequentes',
    desc: 'Dúvidas comuns do condomínio.',
    icon: BookOpen,
    brand: 'blue',
  },
  {
    to: '/vagas/aluguel',
    feature: 'parkingrental',
    requiresLogin: true,
    title: 'Vagas',
    desc: 'Anuncie: procura, aluguel ou troca de vaga.',
    icon: SquareParking,
    brand: 'blue',
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
    feature: 'accessmanagement',
  },
  {
    to: '/minha-unidade/moradores',
    title: 'Moradores',
    desc: 'Cadastre e gerencie os moradores da sua unidade.',
    icon: Users,
    brand: 'ink',
    requires: 'RESIDENT_MANAGE',
  },
];

// 'ink' (itens de sistema) segue --foreground como cor/borda para manter
// contraste no dark; --brand-ink (preto fixo do wordmark) fica só para fundos.
const brandVar = (b: Brand) => (b === 'ink' ? '--foreground' : `--brand-${b}`);
const hsl = (b: Brand, alpha?: number) =>
  alpha == null ? `hsl(var(${brandVar(b)}))` : `hsl(var(${brandVar(b)}) / ${alpha})`;

export default function App() {
  const { user } = useAuth();
  const { enabled } = useFeatures();
  const { promptLogin } = useLoginPrompt();
  // Visitante nao e barrado por permission em card com cadeado: ele ve o card e o clique abre o
  // popup de entrada. Sem cadeado (area de admin), a permission continua escondendo o card.
  const can = (item: NavItem) =>
    user
      ? !item.requires || user.authorities.includes(item.requires)
      : !item.requires || !!item.requiresLogin;
  const on = (item: NavItem) => !item.feature || enabled(item.feature);
  const items = NAV.filter((i) => can(i) && on(i));

  return (
    <section className="container space-y-6 py-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold">
          {user ? `Olá, ${user.greetingName || user.fullName} 👋` : 'HELBOR TRILOGY HOME'}
        </h1>
        <p className="text-muted-foreground">
          {user ? 'Escolha uma área do portal.' : 'Veja o que está acontecendo no condomínio.'}
        </p>
      </div>

      <nav aria-label="Áreas do portal">
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {items.map((item) => {
            const Icon = item.icon;
            const locked = !user && item.requiresLogin;
            const Wrapper = ({ children }: { children: React.ReactNode }) =>
              locked ? (
                <button
                  type="button"
                  onClick={() =>
                    promptLogin({
                      reason: `${item.title} é para quem tem conta.`,
                      destination: item.to,
                    })
                  }
                  className="block w-full rounded-xl text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {children}
                </button>
              ) : (
                <Link
                  to={item.to}
                  className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {children}
                </Link>
              );
            return (
              <li key={item.to}>
                <Wrapper>
                  <Card
                    className="h-full border-l-4 transition-colors hover:bg-[var(--card-hover)] active:bg-[var(--card-hover)]"
                    style={
                      {
                        borderLeftColor: hsl(item.brand),
                        '--card-hover': hsl(item.brand, 0.1),
                      } as CSSProperties
                    }
                  >
                    <CardHeader className="flex flex-row items-center gap-3 space-y-0">
                      <span
                        className="flex h-10 w-10 items-center justify-center rounded-xl"
                        style={{ backgroundColor: hsl(item.brand, 0.12), color: hsl(item.brand) }}
                      >
                        <Icon className="h-5 w-5" aria-hidden="true" />
                      </span>
                      <CardTitle className="flex flex-1 items-center gap-2 text-base">
                        {item.title}
                        {locked && (
                          <Lock
                            className="h-3.5 w-3.5 shrink-0 text-muted-foreground"
                            aria-label="Requer login"
                          />
                        )}
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      <p className="text-sm text-muted-foreground">
                        {locked ? 'Entre na sua conta para ver.' : item.desc}
                      </p>
                    </CardContent>
                  </Card>
                </Wrapper>
              </li>
            );
          })}
        </ul>
      </nav>
    </section>
  );
}
