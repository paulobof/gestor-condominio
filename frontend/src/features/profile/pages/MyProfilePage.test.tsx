import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/profileApi', () => ({
  getOwnProfile: vi.fn(),
  updateOwnProfile: vi.fn(),
  changeOwnPassword: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { toast } from 'sonner';
import { useAuth } from '@/features/auth/useAuth';
import { MyProfilePage } from './MyProfilePage';
import { changeOwnPassword, getOwnProfile, updateOwnProfile } from '../api/profileApi';

const getMock = vi.mocked(getOwnProfile);
const updateMock = vi.mocked(updateOwnProfile);
const passwordMock = vi.mocked(changeOwnPassword);
const refreshUser = vi.fn();

const profile = {
  fullName: 'Ana Silva',
  greetingName: 'Ana',
  phone: '5511988887777',
  email: 'ana@x.com',
};

function renderPage() {
  return render(
    <MemoryRouter>
      <MyProfilePage />
    </MemoryRouter>
  );
}

async function renderLoaded() {
  renderPage();
  await screen.findByDisplayValue('Ana Silva');
}

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue(profile);
  refreshUser.mockResolvedValue(undefined);
  vi.mocked(useAuth).mockReturnValue({ refreshUser } as never);
});

describe('MyProfilePage — dados', () => {
  it('carrega os dados da conta e mostra o e-mail somente leitura', async () => {
    await renderLoaded();
    expect(screen.getByLabelText('Nome completo')).toHaveValue('Ana Silva');
    expect(screen.getByLabelText('Como quer ser chamado')).toHaveValue('Ana');
    expect(screen.getByLabelText('E-mail')).toHaveValue('ana@x.com');
    expect(screen.getByLabelText('E-mail')).toHaveAttribute('readonly');
    expect(screen.getByText('Para trocar o e-mail, fale com o administrador.')).toBeInTheDocument();
    expect(screen.getByText(/usado só para WhatsApp e recuperação de senha/i)).toBeInTheDocument();
  });

  it('salva os dados, mandando saudação em branco como null, e atualiza a sessão', async () => {
    updateMock.mockResolvedValue({ ...profile, fullName: 'Ana Maria', greetingName: null });
    await renderLoaded();

    const name = screen.getByLabelText('Nome completo');
    await userEvent.clear(name);
    await userEvent.type(name, 'Ana Maria');
    await userEvent.clear(screen.getByLabelText('Como quer ser chamado'));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar dados' }));

    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith({
        fullName: 'Ana Maria',
        greetingName: null,
        phone: '5511988887777',
      })
    );
    await waitFor(() => expect(refreshUser).toHaveBeenCalled());
    expect(toast.success).toHaveBeenCalledWith('Dados atualizados.');
  });

  it('não salva sem nome', async () => {
    await renderLoaded();
    await userEvent.clear(screen.getByLabelText('Nome completo'));
    await userEvent.click(screen.getByRole('button', { name: 'Salvar dados' }));

    expect(await screen.findByText('Informe seu nome completo')).toBeInTheDocument();
    expect(updateMock).not.toHaveBeenCalled();
  });

  it('mostra a mensagem do backend quando falha ao salvar', async () => {
    updateMock.mockRejectedValue({
      response: { data: { code: 'VALIDATION_FAILED', message: 'Verifique os campos.' } },
    });
    await renderLoaded();
    await userEvent.click(screen.getByRole('button', { name: 'Salvar dados' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Verifique os campos.'));
    expect(refreshUser).not.toHaveBeenCalled();
  });

  it('avisa quando não consegue carregar o cadastro', async () => {
    getMock.mockRejectedValue(new Error('boom'));
    renderPage();
    expect(await screen.findByText('Não foi possível carregar seu cadastro.')).toBeInTheDocument();
  });
});

describe('MyProfilePage — trocar senha', () => {
  async function fillPassword(current: string, next: string, confirm: string) {
    await userEvent.type(screen.getByLabelText('Senha atual'), current);
    await userEvent.type(screen.getByLabelText('Nova senha'), next);
    await userEvent.type(screen.getByLabelText('Confirme a nova senha'), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));
  }

  it('troca a senha e limpa o formulário', async () => {
    passwordMock.mockResolvedValue(undefined);
    await renderLoaded();
    await fillPassword('Atual@123', 'Nova@Senha123', 'Nova@Senha123');

    await waitFor(() => expect(passwordMock).toHaveBeenCalledWith('Atual@123', 'Nova@Senha123'));
    expect(toast.success).toHaveBeenCalledWith('Senha alterada.');
    await waitFor(() => expect(screen.getByLabelText('Senha atual')).toHaveValue(''));
  });

  it('não envia quando a confirmação não confere', async () => {
    await renderLoaded();
    await fillPassword('Atual@123', 'Nova@Senha123', 'Outra@Senha123');

    expect(await screen.findByText('As senhas não coincidem')).toBeInTheDocument();
    expect(passwordMock).not.toHaveBeenCalled();
  });

  it('não envia senha fraca', async () => {
    await renderLoaded();
    await fillPassword('Atual@123', 'fraca', 'fraca');
    expect(passwordMock).not.toHaveBeenCalled();
  });

  it('senha atual errada mostra "Senha atual incorreta."', async () => {
    passwordMock.mockRejectedValue({
      response: { data: { code: 'INVALID_PASSWORD', message: 'Senha atual incorreta.' } },
    });
    await renderLoaded();
    await fillPassword('Errada@123', 'Nova@Senha123', 'Nova@Senha123');

    expect(await screen.findByText('Senha atual incorreta.')).toBeInTheDocument();
  });

  it('senha reutilizada mostra a mensagem do backend', async () => {
    passwordMock.mockRejectedValue({
      response: {
        data: { code: 'PASSWORD_REUSED', message: 'Você já usou essa senha recentemente.' },
      },
    });
    await renderLoaded();
    await fillPassword('Atual@123', 'Antiga@123', 'Antiga@123');

    expect(await screen.findByText('Você já usou essa senha recentemente.')).toBeInTheDocument();
  });
});
