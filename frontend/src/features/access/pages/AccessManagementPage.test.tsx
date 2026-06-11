import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/accessApi', () => ({
  listUsers: vi.fn(),
  listAssignableRoles: vi.fn(),
  getUserRoleIds: vi.fn(),
  assignRole: vi.fn(),
  removeRole: vi.fn(),
  getCreatableRoles: vi.fn(),
  createUser: vi.fn(),
  deleteUser: vi.fn(),
  lookupUnit: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));
vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
  getCreatableRoles,
  createUser,
  deleteUser,
  type UserAccessRow,
} from '../api/accessApi';
import { useAuth } from '@/features/auth/useAuth';
import { toast } from 'sonner';

const listMock = vi.mocked(listUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);
const removeMock = vi.mocked(removeRole);
const creatableMock = vi.mocked(getCreatableRoles);
const createMock = vi.mocked(createUser);
const deleteMock = vi.mocked(deleteUser);
const authMock = vi.mocked(useAuth);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];
const CREATABLE = [
  { id: 4, name: 'RESIDENT', label: 'Morador' },
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
];

function pageOf(content: UserAccessRow[], last = true, number = 0) {
  return { content, number, totalPages: last ? number + 1 : number + 2, last };
}

function setAuth(authorities: string[]) {
  authMock.mockReturnValue({ user: { authorities } } as unknown as ReturnType<typeof useAuth>);
}

beforeEach(() => {
  vi.clearAllMocks();
  setAuth(['ROLE_ASSIGN', 'USER_MANAGE']);
  rolesMock.mockResolvedValue(ROLES);
  creatableMock.mockResolvedValue(CREATABLE);
  listMock.mockResolvedValue(
    pageOf([
      {
        id: 'u1',
        displayName: 'Ana Lima',
        unitLabel: 'A-101',
        phone: '+5511988887777',
        roles: [{ id: 6, label: 'Editor do Mural' }],
      },
    ])
  );
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
  removeMock.mockResolvedValue(undefined);
  deleteMock.mockResolvedValue(undefined);
  createMock.mockResolvedValue({ id: 'u9', fullName: 'Novo User', password: 'Abc123!xyZ09__a' });
});

describe('AccessManagementPage', () => {
  it('lista usuários com nome, telefone e badges', async () => {
    render(<AccessManagementPage />);
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
    expect(screen.getByText('Editor do Mural')).toBeInTheDocument();
  });

  it('clicar no nome abre os toggles e marcar role chama assignRole', async () => {
    userRolesMock.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    // âncora no início: a linha-botão começa com o nome; o botão "Excluir Ana Lima" não casa
    await user.click(await screen.findByRole('button', { name: /^ana lima/i }));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('excluir pede confirmação e chama deleteUser', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /excluir ana lima/i }));
    await user.click(await screen.findByRole('button', { name: /^confirmar$/i }));
    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith('u1'));
    await waitFor(() => expect(screen.queryByText('Ana Lima')).not.toBeInTheDocument());
  });

  it('adicionar usuário cria e mostra a senha uma vez', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /adicionar usuário/i }));
    await user.type(screen.getByLabelText(/nome/i), 'Novo User');
    await user.type(screen.getByLabelText(/e-mail/i), 'novo@x.com');
    await user.type(screen.getByLabelText(/telefone/i), '+5511988887777');
    await user.click(screen.getByRole('button', { name: /^criar$/i }));
    await waitFor(() => expect(createMock).toHaveBeenCalled());
    expect(await screen.findByText('Abc123!xyZ09__a')).toBeInTheDocument();
  });

  it('sem USER_MANAGE esconde adicionar e excluir', async () => {
    setAuth(['ROLE_ASSIGN']);
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    expect(screen.queryByRole('button', { name: /adicionar usuário/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir ana lima/i })).not.toBeInTheDocument();
  });

  it('desmarcar uma role chama removeRole', async () => {
    userRolesMock.mockResolvedValue([6]); // já tem a role
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.click(await screen.findByRole('button', { name: /^ana lima/i }));
    const checkbox = await screen.findByLabelText('Editor do Mural');
    expect(checkbox).toBeChecked();
    await user.click(checkbox);
    await waitFor(() => expect(removeMock).toHaveBeenCalledWith('u1', 6));
  });

  it('erro ao atribuir reverte o toggle e mostra a mensagem do servidor', async () => {
    userRolesMock.mockResolvedValue([]); // não tem a role
    assignMock.mockRejectedValue({ response: { data: { message: 'Limite atingido' } } });
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.click(await screen.findByRole('button', { name: /^ana lima/i }));
    const checkbox = await screen.findByLabelText('Editor do Mural');
    await user.click(checkbox);
    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Limite atingido'));
    expect(checkbox).not.toBeChecked();
  });

  it('"Carregar mais" busca a próxima página e faz append', async () => {
    listMock.mockResolvedValueOnce(
      pageOf(
        [{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101', phone: null, roles: [] }],
        false,
        0
      )
    );
    listMock.mockResolvedValueOnce(
      pageOf(
        [{ id: 'u2', displayName: 'Bruno Sá', unitLabel: 'B-202', phone: null, roles: [] }],
        true,
        1
      )
    );
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /carregar mais/i }));
    expect(await screen.findByText('Bruno Sá')).toBeInTheDocument();
    expect(screen.getByText('Ana Lima')).toBeInTheDocument();
  });

  it('digitar no filtro recarrega a página 0 com q', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.type(screen.getByLabelText(/buscar/i), 'bru');
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('bru', 0, expect.anything()));
  });
});
