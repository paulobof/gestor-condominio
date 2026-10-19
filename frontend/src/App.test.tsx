import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import App from './App';
import { NAV_ITEMS, canSee } from './components/layout/navigation';
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

  it('mostra "Perguntas Frequentes" para qualquer autenticado', () => {
    renderApp(['USER_VIEW']);
    expect(screen.getByRole('link', { name: /^perguntas frequentes/i })).toHaveAttribute(
      'href',
      '/faq'
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

  it('esconde "Moradores" sem RESIDENT_MANAGE', () => {
    renderApp(['USER_VIEW']);
    expect(screen.queryByRole('link', { name: /^moradores/i })).not.toBeInTheDocument();
  });

  it('mostra "Moradores" com RESIDENT_MANAGE', () => {
    renderApp(['RESIDENT_MANAGE']);
    expect(screen.getByRole('link', { name: /^moradores/i })).toHaveAttribute(
      'href',
      '/minha-unidade/moradores'
    );
  });
});

describe('App (visitante)', () => {
  function renderVisitor() {
    return render(
      <MemoryRouter>
        <AuthContext.Provider
          value={
            {
              status: 'unauthenticated' as const,
              user: null,
              login: vi.fn(),
              logout: vi.fn(),
            } as never
          }
        >
          <App />
        </AuthContext.Provider>
      </MemoryRouter>
    );
  }

  it('não mostra ao visitante as áreas que exigem conta', () => {
    renderVisitor();
    for (const nome of [/vagas/i, /mural de avisos/i, /perguntas frequentes/i]) {
      expect(screen.queryByRole('link', { name: nome })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: nome })).not.toBeInTheDocument();
    }
  });

  it('mantém as áreas abertas como link, sem cadeado', () => {
    renderVisitor();
    for (const nome of [/^informações/i, /^indicações/i, /^classificados/i]) {
      const aberto = screen.getByRole('link', { name: nome });
      expect(aberto.querySelector('[aria-label="Requer login"]')).toBeNull();
    }
  });
});

describe('App — mesma oferta do menu lateral', () => {
  const ALL = [
    'REGISTRATION_VIEW',
    'ROLE_ASSIGN',
    'COMPLAINT_MANAGE',
    'COMPLAINT_CATALOG_MANAGE',
    'RESIDENT_MANAGE',
  ];

  it('mostra um card para cada item do menu que a pessoa pode ver (exceto Início)', () => {
    renderApp(ALL);
    const user = { authorities: ALL, unitId: null };
    const esperados = NAV_ITEMS.filter((i) => !i.sidebarOnly && canSee(i, user));
    expect(esperados.length).toBeGreaterThan(10);
    for (const item of esperados) {
      const links = screen.getAllByRole('link').filter((l) => l.getAttribute('href') === item.to);
      expect(links, item.to).toHaveLength(1);
    }
  });

  it('agrupa Moradores e Alterar cadastro em "Configuração"', () => {
    renderApp(['RESIDENT_MANAGE']);
    const config = screen.getByRole('navigation', { name: 'Configuração' });
    expect(within(config).getByRole('link', { name: /^moradores/i })).toHaveAttribute(
      'href',
      '/minha-unidade/moradores'
    );
    expect(within(config).getByRole('link', { name: /^alterar cadastro/i })).toHaveAttribute(
      'href',
      '/meu-cadastro'
    );
  });
});
