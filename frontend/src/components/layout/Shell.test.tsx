import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/featureflags/useFeatures', () => ({ useFeatures: vi.fn() }));
// ThemeToggle depende de matchMedia, que o jsdom nao tem — irrelevante para o que se testa aqui.
vi.mock('@/components/theme/ThemeToggle', () => ({ ThemeToggle: () => null }));

import { Shell } from './Shell';
import { useAuth } from '@/features/auth/useAuth';
import { useFeatures } from '@/features/featureflags/useFeatures';
import { LoginPromptContext } from '@/features/auth/LoginPromptProvider';

const useAuthMock = vi.mocked(useAuth);
const useFeaturesMock = vi.mocked(useFeatures);
const logout = vi.fn();

function renderShell(user: unknown, promptLogin = vi.fn()) {
  useAuthMock.mockReturnValue({ user, logout } as never);
  useFeaturesMock.mockReturnValue({ enabled: () => true, loading: false });
  render(
    <MemoryRouter>
      <LoginPromptContext.Provider value={{ promptLogin }}>
        <Shell />
      </LoginPromptContext.Provider>
    </MemoryRouter>
  );
  return promptLogin;
}

beforeEach(() => vi.clearAllMocks());

describe('Shell', () => {
  it('visitante vê "Entrar" como botão que abre o popup, e nenhum "Sair"', async () => {
    // Todo pedido de entrada e popup: o header nao joga mais o visitante numa tela de senha.
    const promptLogin = renderShell(null);
    expect(screen.queryByRole('link', { name: /^entrar$/i })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /entrar/i }));
    expect(promptLogin).toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /sair/i })).not.toBeInTheDocument();
  });

  it('a home serve visitante e morador — "Início" aponta para a raiz nos dois casos', () => {
    renderShell(null);
    expect(screen.getAllByRole('link', { name: /início/i })[0]).toHaveAttribute('href', '/');
  });

  it('autenticado vê o nome e o botão de sair', () => {
    renderShell({ id: 'u1', fullName: 'Maria Souza', greetingName: 'Maria', authorities: [] });
    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sair/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^entrar$/i })).not.toBeInTheDocument();
  });
});
