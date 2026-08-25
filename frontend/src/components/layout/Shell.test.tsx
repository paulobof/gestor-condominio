import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/featureflags/useFeatures', () => ({ useFeatures: vi.fn() }));
// ThemeToggle depende de matchMedia, que o jsdom nao tem — irrelevante para o que se testa aqui.
vi.mock('@/components/theme/ThemeToggle', () => ({ ThemeToggle: () => null }));

import { Shell } from './Shell';
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';

const useAuthMock = vi.mocked(useAuth);
const useFeaturesMock = vi.mocked(useFeatures);
const logout = vi.fn();

function renderShell(user: unknown) {
  useAuthMock.mockReturnValue({ user, logout } as never);
  useFeaturesMock.mockReturnValue({ enabled: () => true, loading: false });
  return render(
    <MemoryRouter>
      <Shell />
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('Shell', () => {
  it('visitante vê "Entrar" apontando para o login, e nenhum "Sair"', () => {
    renderShell(null);
    expect(screen.getByRole('link', { name: /entrar/i })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: /sair/i })).not.toBeInTheDocument();
  });

  it('visitante tem "Início" apontando para a apresentação, não para a home autenticada', () => {
    renderShell(null);
    expect(screen.getAllByRole('link', { name: /início/i })[0]).toHaveAttribute('href', '/sobre');
  });

  it('autenticado vê o nome e o botão de sair', () => {
    renderShell({ id: 'u1', fullName: 'Maria Souza', greetingName: 'Maria', authorities: [] });
    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sair/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^entrar$/i })).not.toBeInTheDocument();
  });
});
