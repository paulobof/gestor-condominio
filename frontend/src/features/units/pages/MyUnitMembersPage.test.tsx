import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/unitMembersApi', () => ({
  listMembers: vi.fn(),
  createMember: vi.fn(),
  updateMember: vi.fn(),
  deleteMember: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { MyUnitMembersPage } from './MyUnitMembersPage';
import {
  listMembers,
  createMember,
  updateMember,
  deleteMember,
  type UnitMember,
} from '../api/unitMembersApi';
import { useAuth } from '@/features/auth/useAuth';
import { toast } from 'sonner';

const listMock = vi.mocked(listMembers);
const createMock = vi.mocked(createMember);
const updateMock = vi.mocked(updateMember);
const deleteMock = vi.mocked(deleteMember);
const authMock = vi.mocked(useAuth);

const MEMBER: UnitMember = {
  id: 'm1',
  fullName: 'Bia Souza',
  greetingName: 'Bia',
  email: 'bia@x.com',
  phone: '+5511988887777',
  status: 'ACTIVE',
};

function setAuth(authorities: string[]) {
  authMock.mockReturnValue({ user: { authorities } } as unknown as ReturnType<typeof useAuth>);
}

beforeEach(() => {
  vi.clearAllMocks();
  setAuth(['RESIDENT_MANAGE']);
  listMock.mockResolvedValue([MEMBER]);
  createMock.mockResolvedValue({ id: 'm9', fullName: 'Novo Morador', password: 'X1y!aaaa' });
  updateMock.mockResolvedValue(undefined);
  deleteMock.mockResolvedValue(undefined);
});

describe('MyUnitMembersPage — lista', () => {
  it('mostra o título e a lista de moradores com nome, e-mail e telefone', async () => {
    render(<MyUnitMembersPage />);
    expect(
      screen.getByRole('heading', { name: /moradores da minha unidade/i })
    ).toBeInTheDocument();
    expect(await screen.findByText('Bia Souza')).toBeInTheDocument();
    expect(screen.getByText('bia@x.com')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
  });

  it('mostra estado vazio quando não há moradores', async () => {
    listMock.mockResolvedValue([]);
    render(<MyUnitMembersPage />);
    expect(await screen.findByText(/nenhum morador/i)).toBeInTheDocument();
  });

  it('avisa quando a listagem falha', async () => {
    listMock.mockRejectedValue(new Error('boom'));
    render(<MyUnitMembersPage />);
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
  });
});

describe('MyUnitMembersPage — adicionar', () => {
  it('cadastra e mostra a senha provisória uma única vez', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));

    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));

    await waitFor(() => expect(createMock).toHaveBeenCalled());
    const payload = createMock.mock.calls[0][0];
    expect(payload.fullName).toBe('Novo Morador');
    expect(payload.greetingName).toBe('Novo');
    expect(payload.email).toBe('novo@x.com');
    expect(payload.whatsappOptIn).toBe(true);

    expect(await screen.findByText('X1y!aaaa')).toBeInTheDocument();
    expect(screen.getByText(/não será mostrada de novo/i)).toBeInTheDocument();
  });

  it('"Concluir" volta para a lista e recarrega', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));
    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));

    await screen.findByText('X1y!aaaa');
    await user.click(screen.getByRole('button', { name: /concluir/i }));

    await screen.findByRole('button', { name: /adicionar morador/i });
    await waitFor(() => expect(listMock).toHaveBeenCalledTimes(2));
  });

  it('avisa quando o cadastro falha', async () => {
    createMock.mockRejectedValue({ response: { data: { message: 'E-mail já usado.' } } });
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /adicionar morador/i }));
    await user.type(screen.getByLabelText('Nome'), 'Novo Morador');
    await user.type(screen.getByLabelText('Como chamar'), 'Novo');
    await user.type(screen.getByLabelText('E-mail'), 'novo@x.com');
    await user.type(screen.getByLabelText('Telefone'), '+5511977776666');
    await user.click(screen.getByRole('button', { name: /^cadastrar$/i }));
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('E-mail já usado.'));
  });
});

describe('MyUnitMembersPage — editar', () => {
  it('"Dados" abre o form preenchido e salvar chama updateMember', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));

    expect(await screen.findByDisplayValue('bia@x.com')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Bia Souza')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /^salvar$/i }));
    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(updateMock.mock.calls[0][0]).toBe('m1');
    expect(updateMock.mock.calls[0][1].email).toBe('bia@x.com');
  });

  it('cancelar a edição volta para a lista sem salvar', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));
    await screen.findByDisplayValue('bia@x.com');
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }));
    await screen.findByRole('button', { name: /adicionar morador/i });
    expect(updateMock).not.toHaveBeenCalled();
  });
});

describe('MyUnitMembersPage — excluir e gating', () => {
  it('excluir pede confirmação e então chama deleteMember', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /excluir bia souza/i }));
    await user.click(screen.getByRole('button', { name: /^confirmar$/i }));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('m1'));
    await waitFor(() => expect(screen.queryByText('Bia Souza')).not.toBeInTheDocument());
  });

  it('cancelar a confirmação não exclui', async () => {
    const user = userEvent.setup();
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    await user.click(screen.getByRole('button', { name: /excluir bia souza/i }));
    await user.click(screen.getByRole('button', { name: /^cancelar$/i }));
    expect(deleteMock).not.toHaveBeenCalled();
    expect(screen.getByText('Bia Souza')).toBeInTheDocument();
  });

  it('sem RESIDENT_MANAGE esconde os botões de ação mas mostra a lista', async () => {
    setAuth([]);
    render(<MyUnitMembersPage />);
    await screen.findByText('Bia Souza');
    expect(screen.queryByRole('button', { name: /adicionar morador/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^dados$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir bia souza/i })).not.toBeInTheDocument();
  });
});
