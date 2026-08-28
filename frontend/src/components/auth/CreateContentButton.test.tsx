import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';
import { CreateContentButton } from './CreateContentButton';
import { AuthContext } from '@/features/auth/AuthProvider';
import { LoginPromptContext } from '@/features/auth/LoginPromptProvider';

function renderButton(authorities: string[] | null, promptLogin = vi.fn()) {
  const auth =
    authorities === null
      ? { status: 'unauthenticated' as const, user: null, login: vi.fn(), logout: vi.fn() }
      : {
          status: 'authenticated' as const,
          user: { id: 'u1', authorities },
          login: vi.fn(),
          logout: vi.fn(),
        };
  render(
    <MemoryRouter>
      <AuthContext.Provider value={auth as never}>
        <LoginPromptContext.Provider value={{ promptLogin }}>
          <CreateContentButton to="/classificados/novo" reason="Publicar é para quem tem conta.">
            Novo anúncio
          </CreateContentButton>
        </LoginPromptContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>
  );
  return promptLogin;
}

describe('CreateContentButton', () => {
  it('visitante vê o botão e o clique abre o popup de entrada, sem navegar', async () => {
    const promptLogin = renderButton(null);
    const botao = screen.getByRole('button', { name: /novo anúncio/i });
    expect(screen.queryByRole('link', { name: /novo anúncio/i })).not.toBeInTheDocument();

    await userEvent.click(botao);
    expect(promptLogin).toHaveBeenCalledWith({
      reason: 'Publicar é para quem tem conta.',
      destination: '/classificados/novo',
    });
  });

  it('quem tem CONTENT_CREATE vê o link para o formulário', () => {
    renderButton(['CONTENT_CREATE']);
    expect(screen.getByRole('link', { name: /novo anúncio/i })).toHaveAttribute(
      'href',
      '/classificados/novo'
    );
  });

  it('convidado/proprietário logado sem CONTENT_CREATE não vê o botão', () => {
    renderButton(['GENERAL_AREAS_VIEW']);
    expect(screen.queryByRole('link', { name: /novo anúncio/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /novo anúncio/i })).not.toBeInTheDocument();
  });
});
