import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/features/auth/api/authApi', () => ({ consumePasswordReset: vi.fn() }));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

const navigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

import { ResetPasswordPage } from './ResetPasswordPage';
import { consumePasswordReset } from '@/features/auth/api/authApi';
import { toast } from 'sonner';

const consumeMock = vi.mocked(consumePasswordReset);

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <ResetPasswordPage />
    </MemoryRouter>
  );
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>, button: RegExp) {
  await user.type(screen.getByLabelText('Nova senha', { exact: true }), 'Senha@1234');
  await user.type(screen.getByLabelText('Confirme a nova senha', { exact: true }), 'Senha@1234');
  await user.click(screen.getByRole('button', { name: button }));
}

beforeEach(() => {
  vi.clearAllMocks();
  consumeMock.mockResolvedValue(undefined);
});

describe('ResetPasswordPage — redefinição', () => {
  it('mostra "Nova senha" e "Alterar senha" sem o parâmetro primeiro', async () => {
    const user = userEvent.setup();
    renderAt('/reset?token=abc');
    expect(screen.getByText('Nova senha', { selector: 'div' })).toBeInTheDocument();
    expect(screen.queryByText('Crie sua senha')).not.toBeInTheDocument();

    await fillAndSubmit(user, /^alterar senha$/i);

    await waitFor(() => expect(consumeMock).toHaveBeenCalledWith('abc', 'Senha@1234'));
    expect(toast.success).toHaveBeenCalledWith(
      'Senha alterada com sucesso. Faça login com a nova senha.'
    );
    expect(navigate).toHaveBeenCalledWith('/login', { replace: true });
  });
});

describe('ResetPasswordPage — primeiro acesso (convite)', () => {
  it('com primeiro=1 mostra "Crie sua senha" e o botão "Criar senha"', async () => {
    const user = userEvent.setup();
    renderAt('/reset?token=inv&primeiro=1');
    expect(screen.getByText('Crie sua senha')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^alterar senha$/i })).not.toBeInTheDocument();

    await fillAndSubmit(user, /^criar senha$/i);

    await waitFor(() => expect(consumeMock).toHaveBeenCalledWith('inv', 'Senha@1234'));
    expect(toast.success).toHaveBeenCalledWith('Senha criada. Faça login com a sua senha.');
    expect(navigate).toHaveBeenCalledWith('/login', { replace: true });
  });

  it('convite expirado orienta a pedir um novo link a quem fez o cadastro', async () => {
    consumeMock.mockRejectedValue(
      Object.assign(new Error('x'), {
        isAxiosError: true,
        response: { data: { code: 'INVALID_OR_EXPIRED_TOKEN' } },
      })
    );
    const user = userEvent.setup();
    renderAt('/reset?token=inv&primeiro=1');

    await fillAndSubmit(user, /^criar senha$/i);

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith(
        'Link inválido ou expirado. Peça um novo link a quem fez o seu cadastro.'
      )
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});
