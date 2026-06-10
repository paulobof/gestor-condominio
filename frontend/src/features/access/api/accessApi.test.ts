import { describe, it, expect, vi, beforeEach } from 'vitest';

vi.mock('@/lib/api', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));

import { api } from '@/lib/api';
import {
  listUsers,
  listAssignableRoles,
  getUserRoleIds,
  assignRole,
  removeRole,
} from './accessApi';

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const del = vi.mocked(api.delete);

beforeEach(() => {
  vi.clearAllMocks();
});

describe('accessApi — contrato com o backend', () => {
  it('listUsers envia q, page e size como params', async () => {
    get.mockResolvedValue({ data: { content: [], number: 0, totalPages: 0, last: true } });
    await listUsers('ana', 1, 20);
    expect(get).toHaveBeenCalledWith('/access/users', { params: { q: 'ana', page: 1, size: 20 } });
  });

  it('listAssignableRoles faz GET em /access/roles', async () => {
    get.mockResolvedValue({ data: [] });
    await listAssignableRoles();
    expect(get).toHaveBeenCalledWith('/access/roles');
  });

  it('getUserRoleIds usa o id no path', async () => {
    get.mockResolvedValue({ data: [6] });
    const r = await getUserRoleIds('u1');
    expect(get).toHaveBeenCalledWith('/access/users/u1/roles');
    expect(r).toEqual([6]);
  });

  it('assignRole faz POST no path do usuário/role', async () => {
    post.mockResolvedValue({ data: undefined });
    await assignRole('u1', 6);
    expect(post).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });

  it('removeRole faz DELETE no path do usuário/role', async () => {
    del.mockResolvedValue({ data: undefined });
    await removeRole('u1', 6);
    expect(del).toHaveBeenCalledWith('/access/users/u1/roles/6');
  });
});
