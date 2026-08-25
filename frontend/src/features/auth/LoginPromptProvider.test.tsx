import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const login = vi.fn();
vi.mock('@/features/auth/useAuth', () => ({ useAuth: () => ({ login }) }));
vi.mock('@/features/featureflags/useFeatures', () => ({
  useFeatures: () => ({ enabled: () => false, loading: false }),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn(), info: vi.fn() } }));

const navigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

import { LoginPromptProvider } from './LoginPromptProvider';
import { useLoginPrompt } from './useLoginPrompt';

function Consumer() {
  const { promptLogin } = useLoginPrompt();
  return (
    <button
      onClick={() =>
        promptLogin({ reason: 'Avisos é para quem tem conta.', destination: '/avisos' })
      }
    >
      abrir área com cadeado
    </button>
  );
}

function renderWithProvider() {
  return render(
    <MemoryRouter>
      <LoginPromptProvider>
        <Consumer />
      </LoginPromptProvider>
    </MemoryRouter>
  );
}

beforeEach(() => vi.clearAllMocks());

describe('LoginPromptProvider', () => {
  it('não mostra nada até alguém pedir', () => {
    renderWithProvider();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('abre o popup com o motivo, sem sair da página', async () => {
    const user = userEvent.setup();
    renderWithProvider();
    await user.click(screen.getByRole('button', { name: /abrir área com cadeado/i }));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('Avisos é para quem tem conta.')).toBeInTheDocument();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('Esc fecha sem entrar', async () => {
    const user = userEvent.setup();
    renderWithProvider();
    await user.click(screen.getByRole('button', { name: /abrir área com cadeado/i }));
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(login).not.toHaveBeenCalled();
  });

  it('ao entrar, fecha e leva ao destino que estava com cadeado', async () => {
    login.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithProvider();
    await user.click(screen.getByRole('button', { name: /abrir área com cadeado/i }));

    await user.type(screen.getByLabelText('E-mail'), 'maria@example.com');
    await user.type(screen.getByLabelText('Senha'), 'Senha@1234');
    await user.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(login).toHaveBeenCalledWith('maria@example.com', 'Senha@1234'));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/avisos'));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });
});
