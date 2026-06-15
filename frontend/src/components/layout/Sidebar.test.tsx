import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { Sidebar } from './Sidebar';
import { useAuth } from '@/features/auth/useAuth';

const useAuthMock = vi.mocked(useAuth);

function renderSidebar(authorities: string[] = [], path = '/') {
  useAuthMock.mockReturnValue({ user: { id: 'u1', authorities } } as never);
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Sidebar open={true} onClose={() => {}} />
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('Sidebar', () => {
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

  it('grupo "Vagas" começa recolhido e expande mostrando os sub-itens', async () => {
    renderSidebar();
    // recolhido: o sub-item ainda não está no DOM
    expect(screen.queryByRole('link', { name: 'Aluguel de Vagas' })).not.toBeInTheDocument();

    await userEvent.click(screen.getAllByRole('button', { name: 'Vagas' })[0]);

    expect(screen.getAllByRole('link', { name: 'Aluguel de Vagas' })[0]).toHaveAttribute(
      'href',
      '/vagas/aluguel'
    );
  });

  it('"Escolha de Vaga" aparece desabilitada com selo "Em breve" (não é link)', async () => {
    renderSidebar();
    await userEvent.click(screen.getAllByRole('button', { name: 'Vagas' })[0]);

    expect(screen.queryByRole('link', { name: /escolha de vaga/i })).not.toBeInTheDocument();
    expect(screen.getAllByText('Escolha de Vaga')[0]).toBeInTheDocument();
    expect(screen.getAllByText('Em breve')[0]).toBeInTheDocument();
  });

  it('grupo "Vagas" abre automaticamente quando a rota ativa pertence a ele', () => {
    renderSidebar([], '/vagas/aluguel');
    expect(screen.getAllByRole('link', { name: 'Aluguel de Vagas' })[0]).toHaveAttribute(
      'href',
      '/vagas/aluguel'
    );
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
