import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('../api/accessApi', () => ({
  searchUsers: vi.fn(),
  listAssignableRoles: vi.fn(),
  getUserRoleIds: vi.fn(),
  assignRole: vi.fn(),
  removeRole: vi.fn(),
}));
vi.mock('sonner', () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { AccessManagementPage } from './AccessManagementPage';
import {
  searchUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
} from '../api/accessApi';
import { toast } from 'sonner';

const searchMock = vi.mocked(searchUsers);
const rolesMock = vi.mocked(listAssignableRoles);
const userRolesMock = vi.mocked(getUserRoleIds);
const assignMock = vi.mocked(assignRole);
const removeMock = vi.mocked(removeRole);

const ROLES = [
  { id: 2, name: 'COUNCIL', label: 'Conselheiro' },
  { id: 6, name: 'MURAL_EDITOR', label: 'Editor do Mural' },
];

beforeEach(() => {
  vi.clearAllMocks();
  rolesMock.mockResolvedValue(ROLES);
  searchMock.mockResolvedValue([{ id: 'u1', displayName: 'Ana Lima', unitLabel: 'A-101' }]);
  userRolesMock.mockResolvedValue([6]);
  assignMock.mockResolvedValue(undefined);
  removeMock.mockResolvedValue(undefined);
});

async function searchAndSelect() {
  const user = userEvent.setup();
  render(<AccessManagementPage />);
  await user.type(screen.getByLabelText(/buscar/i), 'ana');
  await user.click(screen.getByRole('button', { name: /buscar/i }));
  await user.click(await screen.findByText('Ana Lima'));
  return user;
}

describe('AccessManagementPage', () => {
  it('busca e lista usuários', async () => {
    const user = userEvent.setup();
    render(<AccessManagementPage />);
    await user.type(screen.getByLabelText(/buscar/i), 'ana');
    await user.click(screen.getByRole('button', { name: /buscar/i }));

    expect(await screen.findByText('Ana Lima')).toBeInTheDocument();
    expect(searchMock).toHaveBeenCalledWith('ana');
  });

  it('ao selecionar usuário mostra toggles com estado atual', async () => {
    await searchAndSelect();

    const editor = await screen.findByRole('checkbox', { name: 'Editor do Mural' });
    const council = screen.getByRole('checkbox', { name: 'Conselheiro' });
    expect(editor).toBeChecked();
    expect(council).not.toBeChecked();
  });

  it('marcar uma role chama assignRole', async () => {
    const user = await searchAndSelect();
    await user.click(await screen.findByRole('checkbox', { name: 'Conselheiro' }));

    await waitFor(() => expect(assignMock).toHaveBeenCalledWith('u1', 2));
  });

  it('desmarcar uma role chama removeRole', async () => {
    const user = await searchAndSelect();
    await user.click(await screen.findByRole('checkbox', { name: 'Editor do Mural' }));

    await waitFor(() => expect(removeMock).toHaveBeenCalledWith('u1', 6));
  });

  it('erro 409 mostra a mensagem do servidor e reverte o toggle', async () => {
    assignMock.mockRejectedValue({
      response: { data: { message: 'Limite de 3 atingido para Conselheiro.' } },
    });
    const user = await searchAndSelect();
    const council = await screen.findByRole('checkbox', { name: 'Conselheiro' });
    await user.click(council);

    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith('Limite de 3 atingido para Conselheiro.')
    );
    expect(council).not.toBeChecked();
  });
});
