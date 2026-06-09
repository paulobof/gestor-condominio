import { render, screen } from '@testing-library/react';
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

  it('esconde "Gerenciar acessos" sem ROLE_ASSIGN', () => {
    renderSidebar([]);
    expect(screen.queryByRole('link', { name: /gerenciar acessos/i })).not.toBeInTheDocument();
  });

  it('mostra "Gerenciar acessos" com ROLE_ASSIGN', () => {
    renderSidebar(['ROLE_ASSIGN']);
    expect(screen.getAllByRole('link', { name: /gerenciar acessos/i })[0]).toHaveAttribute(
      'href',
      '/admin/acessos'
    );
  });
});
