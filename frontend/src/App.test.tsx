import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import App from './App';
import { AuthContext } from './features/auth/AuthProvider';

function authValue(authorities: string[]) {
  return {
    status: 'authenticated' as const,
    user: {
      id: 'u1',
      fullName: 'Paulo Teste',
      greetingName: 'Paulo',
      email: 'paulo@test.com',
      unitId: null,
      isUnitMaster: false,
      roles: ['MANAGER'],
      authorities,
      mustChangePassword: false,
    },
    login: vi.fn(),
    logout: vi.fn(),
  };
}

function renderApp(authorities: string[] = ['USER_VIEW']) {
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={authValue(authorities)}>
        <App />
      </AuthContext.Provider>
    </MemoryRouter>
  );
}

describe('App', () => {
  it('mostra a saudação do usuário logado', () => {
    renderApp();
    expect(screen.getByText(/olá, paulo/i)).toBeInTheDocument();
  });

  it('mostra os atalhos de navegação (mural, indicações, classificados)', () => {
    renderApp();
    expect(screen.getByRole('link', { name: /^mural de avisos/i })).toHaveAttribute(
      'href',
      '/avisos'
    );
    expect(screen.getByRole('link', { name: /^indicações/i })).toHaveAttribute(
      'href',
      '/indicacoes'
    );
    expect(screen.getByRole('link', { name: /^classificados/i })).toHaveAttribute(
      'href',
      '/classificados'
    );
  });

  it('esconde "Cadastros pendentes" sem REGISTRATION_VIEW', () => {
    renderApp(['USER_VIEW']);
    expect(screen.queryByRole('link', { name: /cadastros pendentes/i })).not.toBeInTheDocument();
  });

  it('mostra "Cadastros pendentes" com REGISTRATION_VIEW', () => {
    renderApp(['REGISTRATION_VIEW']);
    expect(screen.getByRole('link', { name: /cadastros pendentes/i })).toHaveAttribute(
      'href',
      '/admin/registrations'
    );
  });

  it('mostra "Informações" para qualquer autenticado', () => {
    renderApp(['USER_VIEW']);
    expect(screen.getByRole('link', { name: /^informações/i })).toHaveAttribute(
      'href',
      '/informacoes'
    );
  });

  it('esconde "Gestão de usuários" sem ROLE_ASSIGN', () => {
    renderApp(['USER_VIEW']);
    expect(screen.queryByRole('link', { name: /gestão de usuários/i })).not.toBeInTheDocument();
  });

  it('mostra "Gestão de usuários" com ROLE_ASSIGN', () => {
    renderApp(['ROLE_ASSIGN']);
    expect(screen.getByRole('link', { name: /gestão de usuários/i })).toHaveAttribute(
      'href',
      '/admin/acessos'
    );
  });

  it('usa cor adaptável (não brand-ink fixo) no card neutro, para contraste no dark', () => {
    renderApp(['REGISTRATION_VIEW']);
    const link = screen.getByRole('link', { name: /cadastros pendentes/i });
    const iconWrap = link.querySelector('svg')?.parentElement;
    expect(iconWrap).toBeTruthy();
    const style = iconWrap!.getAttribute('style') ?? '';
    expect(style).toContain('--foreground');
    expect(style).not.toContain('--brand-ink');
  });
});
