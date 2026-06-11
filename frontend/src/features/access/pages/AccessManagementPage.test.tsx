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
  getUser: vi.fn(),
  updateUser: vi.fn(),
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
  getUser,
  updateUser,
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
const getUserMock = vi.mocked(getUser);
const updateMock = vi.mocked(updateUser);
const authMock = vi.mocked(useAuth);

const ROLES = [{ id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' }];

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
  creatableMock.mockResolvedValue([{ id: 4, name: 'RESIDENT', label: 'Morador' }]);
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
  userRolesMock.mockResolvedValue([]);
  assignMock.mockResolvedValue(undefined);
  removeMock.mockResolvedValue(undefined);
  updateMock.mockResolvedValue(undefined);
  getUserMock.mockResolvedValue({
    id: 'u1',
    fullName: 'Ana Lima',
    greetingName: 'Ana',
    phone: '+5511988887777',
    unitId: null,
    unitCode: null,
    email: 'ana@x.com',
    gender: 'FEMALE',
    birthDate: '1990-01-02',
  });
});

describe('AccessManagementPage', () => {
  it('mostra o título "Gestão de usuários" e a lista com telefone', async () => {
    render(<AccessManagementPage />);
    expect(screen.getByRole('heading', { name: /gestão de usuários/i })).toBeInTheDocument();
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('+5511988887777')).toBeInTheDocument();
  });

  it('"Acessos" abre os toggles e marcar role chama assignRole', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^acessos$/i }));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('"Dados" abre o form preenchido e salvar chama updateUser', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^dados$/i }));
    expect(await screen.findByDisplayValue('ana@x.com')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /^salvar$/i }));
    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(updateMock.mock.calls[0][0]).toBe('u1');
  });

  it('abrir "Acessos" com erro no fetch fecha o painel e avisa', async () => {
    userRolesMock.mockRejectedValue(new Error('boom'));
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /^acessos$/i }));
    await waitFor(() => expect(toast.error).toHaveBeenCalled());
    // voltou pra lista (sem checkboxes abertos)
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /voltar à busca/i })).not.toBeInTheDocument()
    );
  });

  it('busca por ap chama listUsers com o termo', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.type(screen.getByLabelText(/buscar/i), '101');
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('101', 0, expect.anything()));
  });

  it('sem USER_MANAGE esconde Dados/Excluir mas mostra Acessos', async () => {
    setAuth(['ROLE_ASSIGN']);
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    expect(screen.getByRole('button', { name: /^acessos$/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^dados$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /excluir ana lima/i })).not.toBeInTheDocument();
  });
});
