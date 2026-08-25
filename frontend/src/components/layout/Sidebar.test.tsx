import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/featureflags/useFeatures', () => ({ useFeatures: vi.fn() }));

import { Sidebar } from './Sidebar';
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';

const useAuthMock = vi.mocked(useAuth);
const useFeaturesMock = vi.mocked(useFeatures);

/** Por padrão tudo ligado; os testes de flag passam a lista do que está ligado. */
let enabledFeatures: string[] | null = null;

function renderSidebar(authorities: string[] = [], path = '/') {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities } } as never);
  useFeaturesMock.mockReturnValue({
    enabled: (name: string) => enabledFeatures === null || enabledFeatures.includes(name),
    loading: false,
  });
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Sidebar open={true} onClose={() => {}} />
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  enabledFeatures = null;
});

describe('Sidebar', () => {
  it('visitante vê Avisos e FAQ com cadeado; áreas abertas ficam sem', () => {
    useAuthMock.mockReturnValue({ user: null } as never);
    useFeaturesMock.mockReturnValue({ enabled: () => true, loading: false });
    render(
      <MemoryRouter initialEntries={['/']}>
        <Sidebar open={true} onClose={() => {}} />
      </MemoryRouter>
    );

    // Area com cadeado nao e link: e botao, porque abre o popup de entrada sem tirar
    // o visitante da pagina em que ele estava.
    for (const label of [/avisos/i, /perguntas frequentes/i, /documentos/i]) {
      const locked = screen.getAllByRole('button', { name: label })[0];
      expect(locked.querySelector('[aria-label="Requer login"]')).toBeTruthy();
    }

    for (const label of [/indicações/i, /informações/i, /classificados/i]) {
      const open = screen.getAllByRole('link', { name: label })[0];
      expect(open.querySelector('[aria-label="Requer login"]')).toBeNull();
    }
  });

  it('morador logado não vê cadeado em lugar nenhum', () => {
    renderSidebar([]);
    expect(screen.queryByLabelText('Requer login')).not.toBeInTheDocument();
  });

  it('mostra os atalhos principais com seus destinos', () => {
    renderSidebar();
    // o drawer mobile e a versão desktop renderizam ambos -> usa getAllByRole
    const avisos = screen.getAllByRole('link', { name: /avisos/i });
    expect(avisos[0]).toHaveAttribute('href', '/avisos');
    expect(screen.getAllByRole('link', { name: /classificados/i })[0]).toHaveAttribute(
      'href',
      '/classificados'
    );
  });

  it('esconde "Cadastros pendentes" sem REGISTRATION_VIEW', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /cadastros pendentes/i })).not.toBeInTheDocument();
  });

  it('mostra "Cadastros pendentes" com REGISTRATION_VIEW', () => {
    renderSidebar(['REGISTRATION_VIEW']);
    expect(screen.getAllByRole('link', { name: /cadastros pendentes/i })[0]).toHaveAttribute(
      'href',
      '/admin/registrations'
    );
  });

  it('marca a rota ativa com aria-current', () => {
    renderSidebar([], '/avisos');
    expect(screen.getAllByRole('link', { name: /avisos/i })[0]).toHaveAttribute(
      'aria-current',
      'page'
    );
  });

  it('usa cor de texto adaptável (não brand-ink fixo) nos ícones neutros, para contraste no dark', () => {
    renderSidebar();
    // "Início" é item neutro (brand ink) e sempre visível
    const link = screen.getAllByRole('link', { name: /início/i })[0];
    const icon = link.querySelector('svg');
    expect(icon).toBeTruthy();
    const style = icon!.getAttribute('style') ?? '';
    expect(style).toContain('--foreground');
    expect(style).not.toContain('--brand-ink');
  });

  it('não mostra "Privacidade" no menu', () => {
    renderSidebar(['REGISTRATION_VIEW', 'ROLE_ASSIGN']);
    expect(screen.queryByRole('link', { name: /privacidade/i })).not.toBeInTheDocument();
  });

  it('esconde "Gestão de usuários" sem ROLE_ASSIGN', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /gestão de usuários/i })).not.toBeInTheDocument();
  });

  it('mostra "Gestão de usuários" com ROLE_ASSIGN', () => {
    renderSidebar(['ROLE_ASSIGN']);
    expect(screen.getAllByRole('link', { name: /gestão de usuários/i })[0]).toHaveAttribute(
      'href',
      '/admin/acessos'
    );
  });

  it('esconde "Moradores" sem RESIDENT_MANAGE', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /^moradores$/i })).not.toBeInTheDocument();
  });

  it('mostra "Moradores" com RESIDENT_MANAGE', () => {
    renderSidebar(['RESIDENT_MANAGE']);
    expect(screen.getAllByRole('link', { name: /^moradores$/i })[0]).toHaveAttribute(
      'href',
      '/minha-unidade/moradores'
    );
  });

  it('usa cor de brand do item no estado ativo (não amarelo fixo)', () => {
    // 'Avisos' tem brand red → style ativo deve referenciar --brand-red, nunca --accent
    renderSidebar([], '/avisos');
    const link = screen.getAllByRole('link', { name: /avisos/i })[0];
    const style = link.getAttribute('style') ?? '';
    expect(style).toContain('--brand-red');
    expect(style).not.toContain('--accent');
  });

  it('item ativo tem hover:bg-transparent para não vazar amarelo do accent ao navegar', () => {
    renderSidebar([], '/avisos');
    const link = screen.getAllByRole('link', { name: /avisos/i })[0];
    expect(link.className).toContain('hover:bg-transparent');
    expect(link.className).not.toContain('hover:bg-accent');
  });

  it('"Aluguel de Vagas" é item direto do menu (sem grupo "Vagas")', () => {
    renderSidebar();
    expect(screen.queryByRole('button', { name: 'Vagas' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Aluguel de Vagas' })[0]).toHaveAttribute(
      'href',
      '/vagas/aluguel'
    );
  });

  it('não oferece mais "Escolha de Vaga" (rota inexistente)', () => {
    renderSidebar();
    expect(screen.queryByText(/escolha de vaga/i)).not.toBeInTheDocument();
    expect(screen.queryByText('Em breve')).not.toBeInTheDocument();
  });

  it('esconde o item quando a feature está desligada no ambiente', () => {
    enabledFeatures = ['announcements'];
    renderSidebar(['RESIDENT_MANAGE']);

    expect(screen.getAllByRole('link', { name: /avisos/i })[0]).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /classificados/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Aluguel de Vagas' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /documentos/i })).not.toBeInTheDocument();
    // itens sem flag continuam: Início e Moradores (permission-gated)
    expect(screen.getAllByRole('link', { name: /início/i })[0]).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /^moradores$/i })[0]).toBeInTheDocument();
  });

  it('esconde "Registrar unidade" e "Pedidos de unidade" com unitownership desligada', () => {
    enabledFeatures = [];
    renderSidebar(['REGISTRATION_VIEW']);
    expect(screen.queryByRole('link', { name: /registrar unidade/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /pedidos de unidade/i })).not.toBeInTheDocument();
    // a fila de exceção do admin não tem flag: continua disponível
    expect(screen.getAllByRole('link', { name: /cadastros pendentes/i })[0]).toBeInTheDocument();
  });

  it('proprietário (só leitura) não vê itens de escrita/admin', () => {
    renderSidebar(['GENERAL_AREAS_VIEW']);
    expect(screen.queryByRole('link', { name: /^moradores$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /gestão de usuários/i })).not.toBeInTheDocument();
    // itens de leitura devem aparecer normalmente
    expect(screen.getAllByRole('link', { name: /avisos/i })[0]).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /classificados/i })[0]).toBeInTheDocument();
  });
});
