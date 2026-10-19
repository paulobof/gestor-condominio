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
  UserPen,
  Users,
  SquareParking,
  Wrench,
  ListTree,
} from 'lucide-react';

export type Brand = 'red' | 'orange' | 'green' | 'blue' | 'ink';

/** Seções do menu: as áreas do portal e, separada, a configuração da conta/unidade. */
export type NavSection = 'main' | 'config';

export interface NavItem {
  to: string;
  label: string;
  /** Título do card na home, quando difere do rótulo do menu. */
  homeTitle?: string;
  /** Descrição curta exibida no card da home. */
  desc: string;
  icon: typeof Home;
  brand: Brand;
  section: NavSection;
  /** Só no menu lateral (ex.: "Início", que é a própria home). */
  sidebarOnly?: boolean;
  end?: boolean;
  /** Permission exigida para ver o item. */
  requires?: string;
  /** Área só para quem tem conta: visitante (sem login) não vê o item. */
  requiresLogin?: boolean;
  /** Logado, só aparece para quem mora numa unidade (ou tem a permission indicada aqui). */
  requiresUnit?: string;
}

/**
 * Fonte única do que o portal oferece. O menu lateral e a home leem desta lista com a mesma regra
 * de visibilidade ({@link canSee}), para nunca mostrarem coisas diferentes.
 */
export const NAV_ITEMS: NavItem[] = [
  {
    to: '/',
    label: 'Início',
    desc: '',
    icon: Home,
    brand: 'ink',
    section: 'main',
    sidebarOnly: true,
    end: true,
  },
  {
    to: '/informacoes',
    label: 'Informações',
    desc: 'Informações gerais do condomínio.',
    icon: Info,
    brand: 'blue',
    section: 'main',
  },
  {
    to: '/indicacoes',
    label: 'Indicações',
    desc: 'Serviços recomendados por moradores.',
    icon: Lightbulb,
    brand: 'orange',
    section: 'main',
  },
  {
    to: '/classificados',
    label: 'Classificados',
    desc: 'Compra e venda entre moradores.',
    icon: ShoppingBag,
    brand: 'green',
    section: 'main',
  },
  {
    to: '/avisos',
    label: 'Avisos',
    homeTitle: 'Mural de avisos',
    desc: 'Comunicados do condomínio.',
    icon: Megaphone,
    brand: 'red',
    section: 'main',
    requiresLogin: true,
  },
  {
    to: '/faq',
    label: 'Perguntas Frequentes',
    desc: 'Dúvidas comuns do condomínio.',
    icon: BookOpen,
    brand: 'blue',
    section: 'main',
    requiresLogin: true,
  },
  {
    to: '/documentos',
    label: 'Documentos',
    desc: 'Atas, regulamentos e outros arquivos.',
    icon: FileText,
    brand: 'blue',
    section: 'main',
    requiresLogin: true,
  },
  {
    to: '/vagas/aluguel',
    label: 'Vagas',
    desc: 'Anuncie: procura, aluguel ou troca de vaga.',
    icon: SquareParking,
    brand: 'blue',
    section: 'main',
    requiresLogin: true,
  },
  {
    to: '/reclamacoes',
    label: 'Reclamações',
    desc: 'Registre e acompanhe reclamações sobre o condomínio.',
    icon: Wrench,
    brand: 'orange',
    section: 'main',
    requiresLogin: true,
    requiresUnit: 'COMPLAINT_MANAGE',
  },
  {
    to: '/admin/registrations',
    label: 'Cadastros pendentes',
    desc: 'Aprovar ou recusar novos moradores.',
    icon: ClipboardCheck,
    brand: 'ink',
    section: 'main',
    requires: 'REGISTRATION_VIEW',
  },
  {
    to: '/admin/acessos',
    label: 'Gestão de usuários',
    desc: 'Acessos e dados dos usuários.',
    icon: UserCog,
    brand: 'ink',
    section: 'main',
    requires: 'ROLE_ASSIGN',
  },
  {
    to: '/admin/reclamacoes',
    label: 'Gestão de reclamações',
    desc: 'Tratar e encaminhar as reclamações dos moradores.',
    icon: Wrench,
    brand: 'ink',
    section: 'main',
    requires: 'COMPLAINT_MANAGE',
  },
  {
    to: '/admin/reclamacoes/catalogo',
    label: 'Catálogo de reclamações',
    desc: 'Tipos, subtipos e locais das reclamações.',
    icon: ListTree,
    brand: 'ink',
    section: 'main',
    requires: 'COMPLAINT_CATALOG_MANAGE',
  },
  {
    to: '/minha-unidade/moradores',
    label: 'Moradores',
    desc: 'Cadastre e gerencie os moradores da sua unidade.',
    icon: Users,
    brand: 'ink',
    section: 'config',
    requires: 'RESIDENT_MANAGE',
  },
  {
    to: '/meu-cadastro',
    label: 'Alterar cadastro',
    desc: 'Seus dados de contato e sua senha.',
    icon: UserPen,
    brand: 'ink',
    section: 'config',
    requiresLogin: true,
  },
];

export const SECTION_TITLE: Record<NavSection, string | null> = {
  main: null,
  config: 'Configuração',
};

interface NavUser {
  authorities: string[];
  unitId?: string | null;
}

/** Visitante só vê o que pode acessar sem conta; logado, unidade/permission decidem. */
export function canSee(item: NavItem, user: NavUser | null | undefined): boolean {
  const has = (p?: string) => !p || (user?.authorities.includes(p) ?? false);
  if (!user) return !item.requires && !item.requiresLogin;
  if (item.requiresUnit && !user.unitId) return has(item.requiresUnit);
  return has(item.requires);
}

/** Itens visíveis agrupados por seção, na ordem da lista. Seções vazias ficam de fora. */
export function visibleSections(
  user: NavUser | null | undefined,
  filter: (item: NavItem) => boolean = () => true
): { section: NavSection; title: string | null; items: NavItem[] }[] {
  const order: NavSection[] = ['main', 'config'];
  return order
    .map((section) => ({
      section,
      title: SECTION_TITLE[section],
      items: NAV_ITEMS.filter((i) => i.section === section && filter(i) && canSee(i, user)),
    }))
    .filter((s) => s.items.length > 0);
}

// 'ink' é a cor neutra dos itens de sistema. Como ícone/texto ele segue --foreground (escuro no
// tema claro, claro no escuro) para manter contraste no dark; --brand-ink (preto fixo do wordmark)
// fica só para fundos (header/banner).
const brandVar = (b: Brand) => (b === 'ink' ? '--foreground' : `--brand-${b}`);
export const brandColor = (b: Brand, alpha?: number) =>
  alpha == null ? `hsl(var(${brandVar(b)}))` : `hsl(var(${brandVar(b)}) / ${alpha})`;
