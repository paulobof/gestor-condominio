import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/accessApi', () => ({
  listUsers: vi.fn(),
  listAssignableRoles: vi.fn(),
  getUserRoleIds: vi.fn(),
  assignRole: vi.fn(),
  removeRole: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  type UserAccessRow,
} from '../api/accessApi';

const listMock = vi.mocked(listUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];

function pageOf(content: UserAccessRow[], last = true, number = 0) {
  return { content, number, totalPages: last ? number + 1 : number + 2, last };
}

beforeEach(() => {
  vi.clearAllMocks();
  rolesMock.mockResolvedValue(ROLES);
  listMock.mockResolvedValue(
    pageOf([
      {
        id: 'u1',
        displayName: 'Ana Lima',
        unitLabel: 'A-101',
        roles: [{ id: 6, label: 'Editor do Mural' }],
      },
    ])
  );
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
});

describe('AccessManagementPage', () => {
  it('lista usuários ao abrir, sem precisar buscar, com badges', async () => {
    render(<AccessManagementPage />);
    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(screen.getByText('Editor do Mural')).toBeInTheDocument();
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('', 0, expect.anything()));
  });

  it('"Carregar mais" busca a próxima página e faz append', async () => {
    listMock.mockResolvedValueOnce(
      pageOf([{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101', roles: [] }], false, 0)
    );
    listMock.mockResolvedValueOnce(
      pageOf([{ id: 'u2', displayName: 'Bruno Sá', unitLabel: 'B-202', roles: [] }], true, 1)
    );
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.click(screen.getByRole('button', { name: /carregar mais/i }));
    expect(await screen.findByText('Bruno Sá')).toBeInTheDocument();
    expect(screen.getByText('Ana Lima')).toBeInTheDocument();
  });

  it('clicar numa linha abre os toggles e marcar role chama assignRole', async () => {
    userRolesMock.mockResolvedValue([]);
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.click(await screen.findByText('Ana Lima'));
    await user.click(await screen.findByLabelText('Editor do Mural'));
    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 6));
  });

  it('digitar no filtro recarrega a página 0 com q', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await screen.findByText('Ana Lima');
    await user.type(screen.getByLabelText(/buscar/i), 'bru');
    await waitFor(() => expect(listMock).toHaveBeenCalledWith('bru', 0, expect.anything()));
  });
});
